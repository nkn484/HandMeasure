#!/usr/bin/env python3
"""Minimal replay harness for ring try-on validation fixtures."""

from __future__ import annotations

import argparse
import csv
import json
import math
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import cv2


DEFAULT_CENTER_THRESHOLD_PX = 24.0
DEFAULT_WIDTH_THRESHOLD_PX = 14.0
DEFAULT_ROTATION_THRESHOLD_DEG = 18.0
VIDEO_EXTENSIONS = {".mp4", ".mov", ".m4v", ".webm", ".avi", ".mkv"}


@dataclass(frozen=True)
class ExpectedZone:
    center_x: float
    center_y: float
    width_px: float
    rotation_deg: float


@dataclass(frozen=True)
class Prediction:
    center_x: float
    center_y: float
    width_px: float
    rotation_deg: float
    confidence: float


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Replay ring try-on validation fixtures.")
    parser.add_argument("--annotations", required=True, type=Path)
    parser.add_argument("--image-dir", required=True, type=Path)
    parser.add_argument("--report-dir", required=True, type=Path)
    parser.add_argument("--predictions", type=Path)
    parser.add_argument("--center-threshold-px", type=float, default=DEFAULT_CENTER_THRESHOLD_PX)
    parser.add_argument("--width-threshold-px", type=float, default=DEFAULT_WIDTH_THRESHOLD_PX)
    parser.add_argument("--rotation-threshold-deg", type=float, default=DEFAULT_ROTATION_THRESHOLD_DEG)
    return parser.parse_args()


def load_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def load_predictions(path: Path | None) -> dict[str, Prediction]:
    if path is None:
        return {}
    payload = load_json(path)
    frames = payload.get("frames", [])
    predictions: dict[str, Prediction] = {}
    for frame in frames:
        zone = frame.get("predictedRingFingerZone") or frame.get("prediction")
        if not zone:
            continue
        predictions[prediction_key(str(frame["file"]), frame.get("frameIndex"))] = Prediction(
            center_x=float(zone["centerX"]),
            center_y=float(zone["centerY"]),
            width_px=float(zone["widthPx"]),
            rotation_deg=float(zone["rotationDeg"]),
            confidence=float(zone.get("confidence", frame.get("confidence", 0.0))),
        )
    return predictions


def prediction_key(file_name: str, frame_index: Any = None) -> str:
    if frame_index is None:
        return file_name
    return f"{file_name}#{int(frame_index)}"


def expected_zone(frame: dict[str, Any]) -> ExpectedZone:
    zone = frame["ringFingerZone"]
    return ExpectedZone(
        center_x=float(zone["centerX"]),
        center_y=float(zone["centerY"]),
        width_px=float(zone["widthPx"]),
        rotation_deg=float(zone["rotationDeg"]),
    )


def normalize_degrees(value: float) -> float:
    normalized = (value + 180.0) % 360.0 - 180.0
    return abs(normalized)


def normalize_axis_degrees(value: float) -> float:
    return min(normalize_degrees(value), normalize_degrees(value + 180.0))


def media_file_for_frame(frame: dict[str, Any], media: dict[str, Any] | None) -> str:
    return str(frame.get("file") or (media or {}).get("file") or "")


def is_video_file(path: Path, media: dict[str, Any] | None) -> bool:
    media_type = str((media or {}).get("type", "")).lower()
    return media_type == "video" or path.suffix.lower() in VIDEO_EXTENSIONS


def read_fixture_frame(
    image_dir: Path,
    file_name: str,
    frame_index: int | None,
    media: dict[str, Any] | None,
) -> tuple[Path, Any | None, str]:
    fixture_path = image_dir / file_name
    if not fixture_path.exists():
        return fixture_path, None, "missing_fixture"
    if is_video_file(fixture_path, media):
        if frame_index is None:
            return fixture_path, None, "missing_frame_index"
        cap = cv2.VideoCapture(str(fixture_path))
        if not cap.isOpened():
            return fixture_path, None, "unreadable_video"
        frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        if frame_index < 0 or frame_index >= frame_count:
            cap.release()
            return fixture_path, None, "frame_index_out_of_range"
        cap.set(cv2.CAP_PROP_POS_FRAMES, frame_index)
        ok, frame_image = cap.read()
        cap.release()
        return fixture_path, frame_image if ok else None, "ok" if ok else "unreadable_frame"

    frame_image = cv2.imread(str(fixture_path), cv2.IMREAD_COLOR)
    return fixture_path, frame_image, "ok" if frame_image is not None else "unreadable_image"


def assess_annotation_quality(
    frame: dict[str, Any],
    frame_image: Any | None,
    frame_width: int,
    frame_height: int,
) -> dict[str, Any]:
    visible_finger = bool(frame.get("visibleFinger", True))
    declared_quality = str(frame.get("annotationQuality", "")).lower()
    zone = expected_zone(frame)
    zone_inside = (
        0 <= zone.center_x <= frame_width
        and 0 <= zone.center_y <= frame_height
        and 0 <= zone.width_px <= max(frame_width, frame_height)
    )
    zone_present = zone.width_px > 0 and zone.center_x > 0 and zone.center_y > 0
    notes: list[str] = []

    if visible_finger and not zone_present:
        notes.append("visible_frame_missing_ring_zone")
    if not visible_finger and zone_present:
        notes.append("hidden_frame_has_ring_zone")
    if not zone_inside:
        notes.append("ring_zone_outside_frame")

    brightness = contrast = sharpness = None
    if frame_image is not None:
        gray = cv2.cvtColor(frame_image, cv2.COLOR_BGR2GRAY)
        brightness = float(gray.mean())
        contrast = float(gray.std())
        sharpness = float(cv2.Laplacian(gray, cv2.CV_64F).var())
        if brightness < 55:
            notes.append("frame_too_dark")
        if brightness > 215:
            notes.append("frame_too_bright")
        if contrast < 18:
            notes.append("low_contrast")
        if sharpness < 90:
            notes.append("motion_blur_or_soft_frame")
    else:
        notes.append("frame_not_read")

    if visible_finger and zone_present and zone.width_px < 35:
        notes.append("ring_zone_width_too_small")
    if visible_finger and zone_present and zone.width_px > frame_width * 0.35:
        notes.append("ring_zone_width_too_large")
    if declared_quality in {"medium", "challenging"}:
        notes.append(f"manual_quality_{declared_quality}")

    status = "good" if not notes else "review"
    if "frame_not_read" in notes or "ring_zone_outside_frame" in notes or "visible_frame_missing_ring_zone" in notes:
        status = "bad"

    return {
        "annotationQualityStatus": status,
        "declaredAnnotationQuality": frame.get("annotationQuality"),
        "annotationQualityNotes": notes,
        "brightnessMean": round(brightness, 2) if brightness is not None else None,
        "contrastStd": round(contrast, 2) if contrast is not None else None,
        "laplacianSharpness": round(sharpness, 2) if sharpness is not None else None,
    }


def row_for_frame(
    frame: dict[str, Any],
    media: dict[str, Any] | None,
    image_dir: Path,
    predictions: dict[str, Prediction],
    center_threshold_px: float,
    width_threshold_px: float,
    rotation_threshold_deg: float,
) -> dict[str, Any]:
    file_name = media_file_for_frame(frame, media)
    frame_index = frame.get("frameIndex")
    image_path, frame_image, read_status = read_fixture_frame(
        image_dir=image_dir,
        file_name=file_name,
        frame_index=int(frame_index) if frame_index is not None else None,
        media=media,
    )
    visible_finger = bool(frame.get("visibleFinger", True))
    expected = expected_zone(frame)
    frame_width = int((media or {}).get("frameWidth") or frame.get("frameWidth") or (frame_image.shape[1] if frame_image is not None else 0))
    frame_height = int((media or {}).get("frameHeight") or frame.get("frameHeight") or (frame_image.shape[0] if frame_image is not None else 0))
    quality = assess_annotation_quality(frame, frame_image, frame_width, frame_height)

    base_row = {
        "file": file_name,
        "frameIndex": frame_index,
        "timeSec": frame.get("timeSec"),
        "visibleFinger": visible_finger,
        **quality,
    }

    if read_status != "ok":
        return {
            **base_row,
            "status": read_status,
            "pass": False,
            "centerErrorPx": None,
            "widthErrorPx": None,
            "rotationErrorDeg": None,
            "confidence": 0.0,
            "reason": f"Fixture frame is not readable: {image_path} ({read_status})",
        }

    prediction = predictions.get(prediction_key(file_name, frame_index))
    if prediction is None:
        return {
            **base_row,
            "status": "missing_prediction",
            "pass": False,
            "centerErrorPx": None,
            "widthErrorPx": None,
            "rotationErrorDeg": None,
            "confidence": 0.0,
            "reason": "No replay prediction was provided for this image.",
        }

    center_error = math.hypot(prediction.center_x - expected.center_x, prediction.center_y - expected.center_y)
    width_error = abs(prediction.width_px - expected.width_px)
    rotation_error = normalize_axis_degrees(prediction.rotation_deg - expected.rotation_deg)
    passed = (
        center_error <= center_threshold_px
        and width_error <= width_threshold_px
        and rotation_error <= rotation_threshold_deg
    )

    return {
        **base_row,
        "status": "measured",
        "pass": passed,
        "centerErrorPx": round(center_error, 4),
        "widthErrorPx": round(width_error, 4),
        "rotationErrorDeg": round(rotation_error, 4),
        "confidence": round(prediction.confidence, 4),
        "reason": "" if passed else "Metric exceeded replay threshold.",
    }


def write_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    fieldnames = [
        "file",
        "frameIndex",
        "timeSec",
        "status",
        "pass",
        "visibleFinger",
        "annotationQualityStatus",
        "declaredAnnotationQuality",
        "annotationQualityNotes",
        "brightnessMean",
        "contrastStd",
        "laplacianSharpness",
        "centerErrorPx",
        "widthErrorPx",
        "rotationErrorDeg",
        "confidence",
        "reason",
    ]
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow({field: row.get(field) for field in fieldnames})


def main() -> int:
    args = parse_args()
    args.report_dir.mkdir(parents=True, exist_ok=True)

    annotations = load_json(args.annotations)
    media = annotations.get("media")
    predictions = load_predictions(args.predictions)
    rows = [
        row_for_frame(
            frame=frame,
            media=media,
            image_dir=args.image_dir,
            predictions=predictions,
            center_threshold_px=args.center_threshold_px,
            width_threshold_px=args.width_threshold_px,
            rotation_threshold_deg=args.rotation_threshold_deg,
        )
        for frame in annotations.get("frames", [])
    ]
    measured_rows = [row for row in rows if row["status"] == "measured"]
    missing_rows = [row for row in rows if row["status"] == "missing_fixture"]
    missing_prediction_rows = [row for row in rows if row["status"] == "missing_prediction"]
    bad_annotation_rows = [row for row in rows if row["annotationQualityStatus"] == "bad"]
    review_annotation_rows = [row for row in rows if row["annotationQualityStatus"] == "review"]
    failed_rows = [row for row in rows if not row["pass"]]
    overall_status = (
        "missing_fixtures"
        if missing_rows
        else "bad_annotations"
        if bad_annotation_rows
        else "awaiting_predictions"
        if missing_prediction_rows and not measured_rows
        else "failed"
        if failed_rows
        else "passed"
    )
    report = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "annotationFile": str(args.annotations),
        "imageDir": str(args.image_dir),
        "media": media,
        "pipeline": [
            "image_or_video_frame",
            "mediapipe_landmarks",
            "tracked_hand_frame",
            "ring_finger_pose",
            "ring_fit",
            "render_state",
            "metrics",
        ],
        "thresholds": {
            "centerErrorPx": args.center_threshold_px,
            "widthErrorPx": args.width_threshold_px,
            "rotationErrorDeg": args.rotation_threshold_deg,
        },
        "status": overall_status,
        "summary": {
            "totalFrames": len(rows),
            "measuredFrames": len(measured_rows),
            "missingFixtures": len(missing_rows),
            "missingPredictions": len(missing_prediction_rows),
            "badAnnotationFrames": len(bad_annotation_rows),
            "reviewAnnotationFrames": len(review_annotation_rows),
            "failedFrames": len(failed_rows),
        },
        "frames": rows,
    }

    report_name = args.annotations.stem
    json_path = args.report_dir / f"{report_name}-report.json"
    csv_path = args.report_dir / f"{report_name}-report.csv"
    with json_path.open("w", encoding="utf-8") as handle:
        json.dump(report, handle, indent=2, ensure_ascii=False)
        handle.write("\n")
    write_csv(csv_path, rows)

    print(f"Replay validation status: {overall_status}")
    print(f"JSON report: {json_path}")
    print(f"CSV report: {csv_path}")
    for row in missing_rows:
        print(row["reason"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
