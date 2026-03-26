#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

import cv2
import numpy as np


@dataclass
class CandidateResult:
    filename: str
    score: float
    quality_score: float
    bg_confidence: float
    recommended_width_ratio: float
    rotation_bias_deg: float
    visual_center: tuple[float, float]
    content_bounds: tuple[int, int, int, int]
    alpha_bounds: tuple[int, int, int, int]
    notes: list[str]
    overlay_rgba: np.ndarray


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Robust ring asset normalization for try-on overlay.")
    parser.add_argument("--source-dir", required=True, help="Directory containing source .webp files.")
    parser.add_argument("--output-dir", required=True, help="Directory to write normalized outputs.")
    parser.add_argument("--canvas-size", type=int, default=1024, help="Output canvas size (square).")
    parser.add_argument("--padding-ratio", type=float, default=0.12, help="Padding around detected content.")
    parser.add_argument("--manual-config", help="Optional JSON overrides for final metadata.")
    parser.add_argument("--write-debug", action="store_true", help="Write debug masks for each candidate.")
    return parser.parse_args()


def read_image(path: Path) -> np.ndarray:
    data = np.fromfile(str(path), dtype=np.uint8)
    image = cv2.imdecode(data, cv2.IMREAD_COLOR)
    if image is None:
        raise ValueError(f"Cannot decode image: {path}")
    return image


def write_image(path: Path, image: np.ndarray) -> None:
    ext = path.suffix or ".png"
    ok, encoded = cv2.imencode(ext, image)
    if not ok:
        raise ValueError(f"Cannot encode image: {path}")
    encoded.tofile(str(path))


def collect_candidates(source_dir: Path) -> Iterable[Path]:
    return sorted(path for path in source_dir.glob("*.webp") if path.is_file())


def sample_border_pixels(image_bgr: np.ndarray) -> np.ndarray:
    height, width = image_bgr.shape[:2]
    border = max(8, int(min(height, width) * 0.06))
    strips = [
        image_bgr[:border, :, :],
        image_bgr[-border:, :, :],
        image_bgr[:, :border, :],
        image_bgr[:, -border:, :],
    ]
    return np.concatenate([segment.reshape(-1, 3) for segment in strips], axis=0)


def compute_initial_mask(image_bgr: np.ndarray) -> np.ndarray:
    height, width = image_bgr.shape[:2]
    image_lab = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2LAB).astype(np.float32)
    image_hsv = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2HSV).astype(np.float32)
    border = sample_border_pixels(image_bgr)
    bg_lab = cv2.cvtColor(border.reshape(1, -1, 3), cv2.COLOR_BGR2LAB).reshape(-1, 3).astype(np.float32)
    bg_median = np.median(bg_lab, axis=0)

    lab_dist = np.linalg.norm(image_lab - bg_median, axis=2)
    lab_dist = cv2.normalize(lab_dist, None, 0, 255, cv2.NORM_MINMAX).astype(np.uint8)
    saturation = image_hsv[:, :, 1].astype(np.uint8)
    value = image_hsv[:, :, 2].astype(np.uint8)

    gray = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2GRAY)
    edges = cv2.Canny(gray, 40, 120)
    edges = cv2.dilate(edges, np.ones((3, 3), np.uint8), iterations=1)

    score = cv2.addWeighted(lab_dist, 0.65, saturation, 0.25, 0)
    score = cv2.addWeighted(score, 0.86, edges, 0.14, 0)
    score = cv2.GaussianBlur(score, (5, 5), 0.0)

    _, otsu_mask = cv2.threshold(score, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    _, sat_mask = cv2.threshold(saturation, 28, 255, cv2.THRESH_BINARY)
    _, value_mask = cv2.threshold(value, 252, 255, cv2.THRESH_BINARY_INV)
    combined = cv2.bitwise_or(otsu_mask, sat_mask)
    combined = cv2.bitwise_and(combined, value_mask)

    close_kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (7, 7))
    open_kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))
    combined = cv2.morphologyEx(combined, cv2.MORPH_CLOSE, close_kernel, iterations=2)
    combined = cv2.morphologyEx(combined, cv2.MORPH_OPEN, open_kernel, iterations=1)
    combined = remove_border_noise(combined, width, height)
    return combined


def remove_border_noise(mask: np.ndarray, width: int, height: int) -> np.ndarray:
    num_labels, labels, stats, _ = cv2.connectedComponentsWithStats(mask, connectivity=8)
    filtered = np.zeros_like(mask)
    min_area = max(80, int(width * height * 0.00025))
    center_x = width * 0.5
    center_y = height * 0.5
    for label_index in range(1, num_labels):
        left = stats[label_index, cv2.CC_STAT_LEFT]
        top = stats[label_index, cv2.CC_STAT_TOP]
        comp_width = stats[label_index, cv2.CC_STAT_WIDTH]
        comp_height = stats[label_index, cv2.CC_STAT_HEIGHT]
        area = stats[label_index, cv2.CC_STAT_AREA]
        if area < min_area:
            continue
        cx = left + comp_width * 0.5
        cy = top + comp_height * 0.5
        center_distance = np.hypot((cx - center_x) / width, (cy - center_y) / height)
        touches_border = left <= 1 or top <= 1 or (left + comp_width) >= width - 1 or (top + comp_height) >= height - 1
        if touches_border and center_distance > 0.33:
            continue
        if center_distance > 0.58:
            continue
        filtered[labels == label_index] = 255

    if filtered.any():
        return filtered
    return mask


def refine_mask_with_grabcut(image_bgr: np.ndarray, coarse_mask: np.ndarray) -> np.ndarray:
    mask = coarse_mask.copy()
    if mask.max() == 0:
        return mask
    gc_mask = np.full(mask.shape, cv2.GC_PR_BGD, dtype=np.uint8)
    gc_mask[mask == 0] = cv2.GC_BGD
    sure_fg = cv2.erode(mask, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5)), iterations=1)
    gc_mask[sure_fg > 0] = cv2.GC_FGD
    bgd_model = np.zeros((1, 65), np.float64)
    fgd_model = np.zeros((1, 65), np.float64)
    try:
        cv2.grabCut(image_bgr, gc_mask, None, bgd_model, fgd_model, 2, cv2.GC_INIT_WITH_MASK)
    except cv2.error:
        return mask
    refined = np.where((gc_mask == cv2.GC_FGD) | (gc_mask == cv2.GC_PR_FGD), 255, 0).astype(np.uint8)
    refined = cv2.medianBlur(refined, 5)
    refined = cv2.morphologyEx(refined, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5)))
    return refined


def largest_component_bounds(mask: np.ndarray) -> tuple[int, int, int, int]:
    ys, xs = np.where(mask > 0)
    if len(xs) == 0:
        return 0, 0, mask.shape[1], mask.shape[0]
    return int(xs.min()), int(ys.min()), int(xs.max() + 1), int(ys.max() + 1)


def crop_with_padding(
    image_bgr: np.ndarray,
    alpha_mask: np.ndarray,
    padding_ratio: float,
) -> tuple[np.ndarray, np.ndarray, tuple[int, int, int, int]]:
    left, top, right, bottom = largest_component_bounds(alpha_mask)
    width = right - left
    height = bottom - top
    pad_x = max(4, int(width * padding_ratio))
    pad_y = max(4, int(height * padding_ratio))
    crop_left = max(0, left - pad_x)
    crop_top = max(0, top - pad_y)
    crop_right = min(image_bgr.shape[1], right + pad_x)
    crop_bottom = min(image_bgr.shape[0], bottom + pad_y)
    return (
        image_bgr[crop_top:crop_bottom, crop_left:crop_right].copy(),
        alpha_mask[crop_top:crop_bottom, crop_left:crop_right].copy(),
        (crop_left, crop_top, crop_right, crop_bottom),
    )


def center_on_canvas(
    crop_bgr: np.ndarray,
    crop_alpha: np.ndarray,
    canvas_size: int,
) -> tuple[np.ndarray, np.ndarray]:
    canvas_rgba = np.zeros((canvas_size, canvas_size, 4), dtype=np.uint8)
    content_height, content_width = crop_alpha.shape[:2]
    scale = min((canvas_size * 0.82) / max(content_width, 1), (canvas_size * 0.82) / max(content_height, 1), 1.0)
    target_width = max(1, int(content_width * scale))
    target_height = max(1, int(content_height * scale))
    resized_bgr = cv2.resize(crop_bgr, (target_width, target_height), interpolation=cv2.INTER_CUBIC)
    resized_alpha = cv2.resize(crop_alpha, (target_width, target_height), interpolation=cv2.INTER_CUBIC)
    offset_x = (canvas_size - target_width) // 2
    offset_y = (canvas_size - target_height) // 2

    canvas_rgba[offset_y : offset_y + target_height, offset_x : offset_x + target_width, :3] = resized_bgr
    canvas_rgba[offset_y : offset_y + target_height, offset_x : offset_x + target_width, 3] = resized_alpha
    return canvas_rgba, resized_alpha


def compute_metadata(
    source_file: str,
    canvas_rgba: np.ndarray,
    source_bounds: tuple[int, int, int, int],
) -> dict:
    alpha = canvas_rgba[:, :, 3]
    alpha_threshold = (alpha > 20).astype(np.uint8) * 255
    alpha_bounds = largest_component_bounds(alpha_threshold)
    content_bounds = alpha_bounds
    ys, xs = np.where(alpha > 0)
    if len(xs) == 0:
        visual_center = (canvas_rgba.shape[1] * 0.5, canvas_rgba.shape[0] * 0.5)
    else:
        weights = alpha[ys, xs].astype(np.float32)
        visual_center = (float(np.average(xs, weights=weights)), float(np.average(ys, weights=weights)))

    area = max(1, (alpha_bounds[2] - alpha_bounds[0]) * (alpha_bounds[3] - alpha_bounds[1]))
    alpha_coverage = float((alpha > 15).sum()) / float(canvas_rgba.shape[0] * canvas_rgba.shape[1])
    border_alpha = np.concatenate([alpha[0, :], alpha[-1, :], alpha[:, 0], alpha[:, -1]])
    border_clean_score = 1.0 - float((border_alpha > 8).sum()) / max(1.0, float(border_alpha.size))
    edge = cv2.Canny(alpha, 20, 80)
    edge_density = float((edge > 0).sum()) / float(area)
    quality = np.clip(0.45 * border_clean_score + 0.25 * (1.0 - min(alpha_coverage, 0.5)) + 0.3 * min(edge_density * 18.0, 1.0), 0.0, 1.0)
    bg_confidence = np.clip(border_clean_score * 0.8 + (1.0 - min(alpha_coverage, 0.65)) * 0.2, 0.0, 1.0)

    ring_visual_width = max(1, alpha_bounds[2] - alpha_bounds[0])
    ideal_width = canvas_rgba.shape[1] * 0.66
    recommended_width_ratio = np.clip(0.16 * ideal_width / ring_visual_width, 0.12, 0.21)
    rotation_bias = 0.0

    notes: list[str] = []
    if bg_confidence < 0.7:
        notes.append("background_removal_maybe_noisy")
    if quality < 0.62:
        notes.append("consider_manual_cleanup")
    if alpha_coverage > 0.55:
        notes.append("overlay_coverage_high_check_crop")

    return {
        "sourceFile": source_file,
        "sourceContentBounds": {
            "left": int(source_bounds[0]),
            "top": int(source_bounds[1]),
            "right": int(source_bounds[2]),
            "bottom": int(source_bounds[3]),
        },
        "contentBounds": {
            "left": int(content_bounds[0]),
            "top": int(content_bounds[1]),
            "right": int(content_bounds[2]),
            "bottom": int(content_bounds[3]),
        },
        "visualCenter": {"x": round(visual_center[0], 2), "y": round(visual_center[1], 2)},
        "alphaBounds": {
            "left": int(alpha_bounds[0]),
            "top": int(alpha_bounds[1]),
            "right": int(alpha_bounds[2]),
            "bottom": int(alpha_bounds[3]),
        },
        "recommendedWidthRatio": round(float(recommended_width_ratio), 4),
        "rotationBiasDeg": round(rotation_bias, 2),
        "assetQualityScore": round(float(quality), 4),
        "backgroundRemovalConfidence": round(float(bg_confidence), 4),
        "notes": notes,
    }


def evaluate_candidate(path: Path, canvas_size: int, padding_ratio: float, write_debug: bool, debug_dir: Path) -> CandidateResult:
    image_bgr = read_image(path)
    coarse_mask = compute_initial_mask(image_bgr)
    refined_mask = refine_mask_with_grabcut(image_bgr, coarse_mask)
    crop_bgr, crop_mask, source_bounds = crop_with_padding(image_bgr, refined_mask, padding_ratio)
    canvas_rgba, _ = center_on_canvas(crop_bgr, crop_mask, canvas_size)
    metadata = compute_metadata(path.name, canvas_rgba, source_bounds)

    if write_debug:
        debug_dir.mkdir(parents=True, exist_ok=True)
        write_image(debug_dir / f"{path.stem}_coarse_mask.png", coarse_mask)
        write_image(debug_dir / f"{path.stem}_refined_mask.png", refined_mask)
        write_image(debug_dir / f"{path.stem}_overlay.png", canvas_rgba)

    quality = metadata["assetQualityScore"]
    bg_conf = metadata["backgroundRemovalConfidence"]
    score = 0.68 * quality + 0.32 * bg_conf
    return CandidateResult(
        filename=path.name,
        score=float(score),
        quality_score=float(quality),
        bg_confidence=float(bg_conf),
        recommended_width_ratio=float(metadata["recommendedWidthRatio"]),
        rotation_bias_deg=float(metadata["rotationBiasDeg"]),
        visual_center=(float(metadata["visualCenter"]["x"]), float(metadata["visualCenter"]["y"])),
        content_bounds=(
            int(metadata["contentBounds"]["left"]),
            int(metadata["contentBounds"]["top"]),
            int(metadata["contentBounds"]["right"]),
            int(metadata["contentBounds"]["bottom"]),
        ),
        alpha_bounds=(
            int(metadata["alphaBounds"]["left"]),
            int(metadata["alphaBounds"]["top"]),
            int(metadata["alphaBounds"]["right"]),
            int(metadata["alphaBounds"]["bottom"]),
        ),
        notes=list(metadata["notes"]),
        overlay_rgba=canvas_rgba,
    )


def load_manual_overrides(path: str | None) -> dict:
    if not path:
        return {}
    content = Path(path).read_text(encoding="utf-8")
    return json.loads(content)


def apply_manual_overrides(selected: CandidateResult, overrides: dict) -> tuple[CandidateResult, bool]:
    if not overrides:
        return selected, False
    applied = False
    if "recommendedWidthRatio" in overrides:
        selected.recommended_width_ratio = float(np.clip(overrides["recommendedWidthRatio"], 0.1, 0.3))
        applied = True
    if "rotationBiasDeg" in overrides:
        selected.rotation_bias_deg = float(overrides["rotationBiasDeg"])
        applied = True
    if "visualCenter" in overrides:
        center = overrides["visualCenter"]
        if isinstance(center, dict) and "x" in center and "y" in center:
            selected.visual_center = (float(center["x"]), float(center["y"]))
            applied = True
    if "notes" in overrides and isinstance(overrides["notes"], list):
        selected.notes = [str(item) for item in overrides["notes"]]
        applied = True
    return selected, applied


def main() -> int:
    args = parse_args()
    source_dir = Path(args.source_dir)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    debug_dir = output_dir / "debug_masks"

    source_files = list(collect_candidates(source_dir))
    if not source_files:
        raise SystemExit(f"No .webp files found in: {source_dir}")

    results = [evaluate_candidate(path, args.canvas_size, args.padding_ratio, args.write_debug, debug_dir) for path in source_files]
    selected = max(results, key=lambda item: item.score)
    selected, manual_override_applied = apply_manual_overrides(selected, load_manual_overrides(args.manual_config))

    overlay_path = output_dir / "ring_overlay_v2.png"
    write_image(overlay_path, selected.overlay_rgba)
    source_copy_path = output_dir / "ring_source_selected.webp"
    source_copy_path.write_bytes((source_dir / selected.filename).read_bytes())

    report = {
        "pipelineVersion": "2.0",
        "selected": selected.filename,
        "normalizedOutput": overlay_path.name,
        "sourceCopy": source_copy_path.name,
        "sourceFile": selected.filename,
        "contentBounds": {
            "left": selected.content_bounds[0],
            "top": selected.content_bounds[1],
            "right": selected.content_bounds[2],
            "bottom": selected.content_bounds[3],
        },
        "visualCenter": {"x": selected.visual_center[0], "y": selected.visual_center[1]},
        "alphaBounds": {
            "left": selected.alpha_bounds[0],
            "top": selected.alpha_bounds[1],
            "right": selected.alpha_bounds[2],
            "bottom": selected.alpha_bounds[3],
        },
        "recommendedWidthRatio": round(selected.recommended_width_ratio, 4),
        "rotationBiasDeg": round(selected.rotation_bias_deg, 2),
        "assetQualityScore": round(selected.quality_score, 4),
        "backgroundRemovalConfidence": round(selected.bg_confidence, 4),
        "notes": selected.notes,
        "manualOverrideApplied": manual_override_applied,
        "candidates": [
            {
                "filename": item.filename,
                "score": round(item.score, 4),
                "assetQualityScore": round(item.quality_score, 4),
                "backgroundRemovalConfidence": round(item.bg_confidence, 4),
                "recommendedWidthRatio": round(item.recommended_width_ratio, 4),
                "rotationBiasDeg": round(item.rotation_bias_deg, 2),
                "visualCenter": {"x": item.visual_center[0], "y": item.visual_center[1]},
                "contentBounds": {
                    "left": item.content_bounds[0],
                    "top": item.content_bounds[1],
                    "right": item.content_bounds[2],
                    "bottom": item.content_bounds[3],
                },
                "alphaBounds": {
                    "left": item.alpha_bounds[0],
                    "top": item.alpha_bounds[1],
                    "right": item.alpha_bounds[2],
                    "bottom": item.alpha_bounds[3],
                },
                "notes": item.notes,
            }
            for item in sorted(results, key=lambda row: row.score, reverse=True)
        ],
    }
    report_path = output_dir / "normalization_report_v2.json"
    report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")

    print(f"Selected source: {selected.filename}")
    print(f"Normalized overlay: {overlay_path}")
    print(f"Metadata report: {report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
