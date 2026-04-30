#!/usr/bin/env python3
"""Replay harness for ring try-on validation fixtures with regression gates."""

from __future__ import annotations

import argparse
import csv
import html
import json
import math
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

try:
    import cv2  # type: ignore
except Exception:  # pragma: no cover - environment dependent import failure
    cv2 = None


DEFAULT_CENTER_THRESHOLD_PX = 24.0
DEFAULT_WIDTH_THRESHOLD_PX = 14.0
DEFAULT_ROTATION_THRESHOLD_DEG = 18.0
DEFAULT_CENTER_RATIO_THRESHOLD = 0.35
DEFAULT_MIN_CENTER_RATIO_PASS_RATE = 0.85
DEFAULT_STEADY_SCALE_DELTA_THRESHOLD = 0.12
DEFAULT_STEADY_ROTATION_JITTER_THRESHOLD = 8.0
DEFAULT_MIN_STEADY_PASS_RATE = 0.85
DEFAULT_MIN_STEADY_PAIR_COUNT = 2
VIDEO_EXTENSIONS = {".mp4", ".mov", ".m4v", ".webm", ".avi", ".mkv"}
STEADY_EXCLUDE_NOTES = {
    "expect_hide",
    "expect_hold",
    "fist",
    "hidden",
    "occlusion",
    "not_fit_ready",
    "challenging",
}


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
    update_action: str | None
    tracking_state: str | None


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Replay ring try-on validation fixtures.")
    parser.add_argument("--annotations", required=True, type=Path)
    parser.add_argument("--image-dir", required=True, type=Path)
    parser.add_argument("--report-dir", required=True, type=Path)
    parser.add_argument("--predictions", type=Path)
    parser.add_argument("--fixture-manifest", type=Path)
    parser.add_argument("--center-threshold-px", type=float, default=DEFAULT_CENTER_THRESHOLD_PX)
    parser.add_argument("--width-threshold-px", type=float, default=DEFAULT_WIDTH_THRESHOLD_PX)
    parser.add_argument("--rotation-threshold-deg", type=float, default=DEFAULT_ROTATION_THRESHOLD_DEG)
    parser.add_argument("--center-ratio-threshold", type=float, default=DEFAULT_CENTER_RATIO_THRESHOLD)
    parser.add_argument("--min-center-ratio-pass-rate", type=float, default=DEFAULT_MIN_CENTER_RATIO_PASS_RATE)
    parser.add_argument("--steady-scale-delta-threshold", type=float, default=DEFAULT_STEADY_SCALE_DELTA_THRESHOLD)
    parser.add_argument(
        "--steady-rotation-jitter-threshold",
        type=float,
        default=DEFAULT_STEADY_ROTATION_JITTER_THRESHOLD,
    )
    parser.add_argument("--min-steady-pass-rate", type=float, default=DEFAULT_MIN_STEADY_PASS_RATE)
    parser.add_argument("--min-steady-pair-count", type=int, default=DEFAULT_MIN_STEADY_PAIR_COUNT)
    parser.add_argument("--prediction-source", choices=["fixture", "generated"], default=None)
    parser.add_argument("--skip-frame-read", action="store_true")
    parser.add_argument("--strict-gate", action="store_true")
    return parser.parse_args()


def load_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def load_fixture_metadata(path: Path | None, annotation_path: Path, media: dict[str, Any] | None) -> dict[str, Any]:
    if path is None or not path.exists():
        return {}
    payload = load_json(path)
    media_file = str((media or {}).get("file", ""))
    for fixture in payload.get("fixtures", []):
        if fixture.get("annotationFile") == annotation_path.name or fixture.get("mediaFile") == media_file:
            return dict(fixture)
    return {}


def load_predictions(path: Path | None) -> dict[str, Prediction]:
    if path is None or not path.exists():
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
            update_action=as_optional_str(frame.get("updateAction") or frame.get("qualityAction")),
            tracking_state=as_optional_str(frame.get("trackingState")),
        )
    return predictions


def as_optional_str(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text if text else None


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
    if cv2 is None:
        return image_dir / file_name, None, "opencv_unavailable"
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
    skip_frame_quality_metrics: bool = False,
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
    elif not skip_frame_quality_metrics:
        notes.append("frame_not_read")

    if visible_finger and zone_present and zone.width_px < 35:
        notes.append("ring_zone_width_too_small")
    if visible_finger and zone_present and zone.width_px > frame_width * 0.35:
        notes.append("ring_zone_width_too_large")
    if declared_quality in {"medium", "challenging", "manual_blue_review"}:
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
    skip_frame_read: bool = False,
) -> dict[str, Any]:
    file_name = media_file_for_frame(frame, media)
    frame_index = frame.get("frameIndex")
    image_path = image_dir / file_name
    if skip_frame_read:
        frame_image = None
        read_status = "ok"
    else:
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
    quality = assess_annotation_quality(
        frame,
        frame_image,
        frame_width,
        frame_height,
        skip_frame_quality_metrics=skip_frame_read,
    )

    base_row = {
        "file": file_name,
        "frameIndex": frame_index,
        "timeSec": frame.get("timeSec"),
        "visibleFinger": visible_finger,
        "expectedCenterX": expected.center_x,
        "expectedCenterY": expected.center_y,
        "expectedWidthPx": expected.width_px,
        "expectedRotationDeg": expected.rotation_deg,
        **quality,
    }

    if read_status != "ok":
        return {
            **base_row,
            "status": read_status,
            "pass": False,
            "centerErrorPx": None,
            "centerErrorOverRingWidth": None,
            "widthErrorPx": None,
            "scaleDelta": None,
            "rotationErrorDeg": None,
            "confidence": 0.0,
            "updateAction": None,
            "trackingState": None,
            "reason": f"Fixture frame is not readable: {image_path} ({read_status})",
        }

    prediction = predictions.get(prediction_key(file_name, frame_index))
    if prediction is None:
        return {
            **base_row,
            "status": "missing_prediction",
            "pass": False,
            "centerErrorPx": None,
            "centerErrorOverRingWidth": None,
            "widthErrorPx": None,
            "scaleDelta": None,
            "rotationErrorDeg": None,
            "confidence": 0.0,
            "updateAction": None,
            "trackingState": None,
            "reason": "No replay prediction was provided for this image.",
        }

    center_error = math.hypot(prediction.center_x - expected.center_x, prediction.center_y - expected.center_y)
    width_error = abs(prediction.width_px - expected.width_px)
    rotation_error = normalize_axis_degrees(prediction.rotation_deg - expected.rotation_deg)
    width_denominator = max(1.0, expected.width_px)
    center_error_ratio = center_error / width_denominator
    scale_delta = width_error / width_denominator
    passed = (
        center_error <= center_threshold_px
        and width_error <= width_threshold_px
        and rotation_error <= rotation_threshold_deg
    )

    return {
        **base_row,
        "status": "measured",
        "pass": passed,
        "predictedCenterX": round(prediction.center_x, 4),
        "predictedCenterY": round(prediction.center_y, 4),
        "predictedWidthPx": round(prediction.width_px, 4),
        "predictedRotationDeg": round(prediction.rotation_deg, 4),
        "centerErrorPx": round(center_error, 4),
        "centerErrorOverRingWidth": round(center_error_ratio, 6),
        "widthErrorPx": round(width_error, 4),
        "scaleDelta": round(scale_delta, 6),
        "rotationErrorDeg": round(rotation_error, 4),
        "confidence": round(prediction.confidence, 4),
        "updateAction": prediction.update_action,
        "trackingState": prediction.tracking_state,
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
        "expectedCenterX",
        "expectedCenterY",
        "expectedWidthPx",
        "expectedRotationDeg",
        "predictedCenterX",
        "predictedCenterY",
        "predictedWidthPx",
        "predictedRotationDeg",
        "centerErrorPx",
        "centerErrorOverRingWidth",
        "widthErrorPx",
        "scaleDelta",
        "rotationErrorDeg",
        "confidence",
        "updateAction",
        "trackingState",
        "brightnessMean",
        "contrastStd",
        "laplacianSharpness",
        "reason",
    ]
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow({field: row.get(field) for field in fieldnames})


def frame_sort_key(row: dict[str, Any]) -> tuple[float, int]:
    time_sec = row.get("timeSec")
    if time_sec is not None:
        return float(time_sec), int(row.get("frameIndex") or 0)
    return float(row.get("frameIndex") or 0), int(row.get("frameIndex") or 0)


def is_steady_candidate(row: dict[str, Any]) -> bool:
    if row.get("status") != "measured" or not row.get("visibleFinger", True):
        return False
    if row.get("annotationQualityStatus") == "bad":
        return False
    notes = [str(note).lower() for note in row.get("annotationQualityNotes", [])]
    for note in notes:
        if any(token in note for token in STEADY_EXCLUDE_NOTES):
            return False
    return True


def steady_pair_metrics(measured_rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    steady_rows = [row for row in measured_rows if is_steady_candidate(row)]
    steady_rows.sort(key=frame_sort_key)
    pairs: list[dict[str, Any]] = []
    for previous, current in zip(steady_rows, steady_rows[1:]):
        prev_expected_width = max(1.0, float(previous["expectedWidthPx"]))
        curr_expected_width = max(1.0, float(current["expectedWidthPx"]))
        expected_avg_width = (prev_expected_width + curr_expected_width) * 0.5
        expected_scale_delta = abs(curr_expected_width - prev_expected_width) / expected_avg_width
        expected_rotation_delta = normalize_axis_degrees(
            float(current["expectedRotationDeg"]) - float(previous["expectedRotationDeg"])
        )
        if expected_scale_delta > 0.10:
            continue
        if expected_rotation_delta > 6.0:
            continue

        prev_pred_width = max(1.0, float(previous["predictedWidthPx"]))
        curr_pred_width = max(1.0, float(current["predictedWidthPx"]))
        scale_delta = abs(curr_pred_width - prev_pred_width) / prev_pred_width
        rotation_jitter = normalize_axis_degrees(
            float(current["predictedRotationDeg"]) - float(previous["predictedRotationDeg"])
        )
        pairs.append(
            {
                "fromFrameIndex": previous.get("frameIndex"),
                "toFrameIndex": current.get("frameIndex"),
                "scaleDelta": round(scale_delta, 6),
                "rotationJitterDeg": round(rotation_jitter, 6),
            }
        )
    return pairs


def write_text_summary(path: Path, report: dict[str, Any]) -> None:
    summary = report["summary"]
    gate = report["gate"]
    lines = [
        "TryOn Replay Validation Summary",
        f"status: {report['status']}",
        f"annotation: {report['annotationFile']}",
        f"frames: total={summary['totalFrames']} measured={summary['measuredFrames']} failed={summary['failedFrames']}",
        f"missing fixtures: {summary['missingFixtures']}, missing predictions: {summary['missingPredictions']}",
        f"center ratio pass rate: {gate['centerRatioPassRate']}",
        f"steady pairs: {gate['steadyPairTotal']} (min required {report['thresholds']['minSteadyPairCount']})",
        f"steady scale pass rate: {gate['steadyScalePassRate']} ({gate['steadyScaleEvaluation']})",
        f"steady rotation pass rate: {gate['steadyRotationPassRate']} ({gate['steadyRotationEvaluation']})",
        f"hidden count: {summary['hiddenCount']}, frozen count: {summary['frozenCount']}",
        f"prediction source: {report['predictionSource']}",
    ]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_html_summary(path: Path, report: dict[str, Any]) -> None:
    summary = report["summary"]
    gate = report["gate"]
    html_content = f"""<!doctype html>
<html>
  <head><meta charset="utf-8"><title>TryOn Replay Validation</title></head>
  <body>
    <h1>TryOn Replay Validation</h1>
    <p><strong>Status:</strong> {html.escape(str(report["status"]))}</p>
    <p><strong>Annotation:</strong> {html.escape(str(report["annotationFile"]))}</p>
    <ul>
      <li>Total frames: {summary["totalFrames"]}</li>
      <li>Measured frames: {summary["measuredFrames"]}</li>
      <li>Failed frames: {summary["failedFrames"]}</li>
      <li>Missing fixtures: {summary["missingFixtures"]}</li>
      <li>Missing predictions: {summary["missingPredictions"]}</li>
      <li>Center ratio pass rate: {gate["centerRatioPassRate"]}</li>
      <li>Steady pairs: {gate["steadyPairTotal"]} (min required {report["thresholds"]["minSteadyPairCount"]})</li>
      <li>Steady scale pass rate: {gate["steadyScalePassRate"]}</li>
      <li>Steady rotation pass rate: {gate["steadyRotationPassRate"]}</li>
      <li>Hidden count: {summary["hiddenCount"]}</li>
      <li>Frozen count: {summary["frozenCount"]}</li>
    </ul>
  </body>
</html>
"""
    path.write_text(html_content, encoding="utf-8")


def rate(numerator: int, denominator: int) -> float:
    if denominator <= 0:
        return 0.0
    return numerator / float(denominator)


def to_percent(value: float) -> float:
    return round(value * 100.0, 2)


def main() -> int:
    args = parse_args()
    args.report_dir.mkdir(parents=True, exist_ok=True)
    prediction_source = args.prediction_source or ("fixture" if args.predictions else "generated")

    annotations = load_json(args.annotations)
    media = annotations.get("media")
    fixture_metadata = load_fixture_metadata(args.fixture_manifest, args.annotations, media)
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
            skip_frame_read=args.skip_frame_read,
        )
        for frame in annotations.get("frames", [])
    ]
    measured_rows = [row for row in rows if row["status"] == "measured"]
    visible_measured_rows = [row for row in measured_rows if row.get("visibleFinger", True)]
    missing_rows = [row for row in rows if row["status"] == "missing_fixture"]
    missing_prediction_rows = [row for row in rows if row["status"] == "missing_prediction"]
    bad_annotation_rows = [row for row in rows if row["annotationQualityStatus"] == "bad"]
    review_annotation_rows = [row for row in rows if row["annotationQualityStatus"] == "review"]
    failed_rows = [row for row in rows if not row["pass"]]
    hidden_count = len(
        [
            row
            for row in measured_rows
            if str(row.get("updateAction", "")).lower().strip() in {"hide", "hidden"}
        ]
    )
    frozen_count = len(
        [
            row
            for row in measured_rows
            if "freeze" in str(row.get("updateAction", "")).lower().strip()
        ]
    )

    center_ratio_pass_count = len(
        [
            row
            for row in visible_measured_rows
            if row.get("centerErrorOverRingWidth") is not None
            and float(row["centerErrorOverRingWidth"]) <= args.center_ratio_threshold
        ]
    )
    center_ratio_total = len(visible_measured_rows)
    center_ratio_pass_rate = rate(center_ratio_pass_count, center_ratio_total)

    steady_pairs = steady_pair_metrics(measured_rows)
    steady_scale_pass_count = len(
        [pair for pair in steady_pairs if float(pair["scaleDelta"]) <= args.steady_scale_delta_threshold]
    )
    steady_rotation_pass_count = len(
        [pair for pair in steady_pairs if float(pair["rotationJitterDeg"]) <= args.steady_rotation_jitter_threshold]
    )
    steady_pair_total = len(steady_pairs)
    steady_scale_pass_rate = rate(steady_scale_pass_count, steady_pair_total)
    steady_rotation_pass_rate = rate(steady_rotation_pass_count, steady_pair_total)
    has_enough_steady_pairs = steady_pair_total >= args.min_steady_pair_count
    steady_scale_evaluated = has_enough_steady_pairs
    steady_rotation_evaluated = has_enough_steady_pairs

    gate_passed = (
        not missing_rows
        and not bad_annotation_rows
        and not missing_prediction_rows
        and center_ratio_pass_rate >= args.min_center_ratio_pass_rate
        and has_enough_steady_pairs
        and steady_scale_pass_rate >= args.min_steady_pass_rate
        and steady_rotation_pass_rate >= args.min_steady_pass_rate
    )

    overall_status = (
        "missing_fixtures"
        if missing_rows
        else "bad_annotations"
        if bad_annotation_rows
        else "awaiting_predictions"
        if missing_prediction_rows and not measured_rows
        else "failed_gate"
        if not gate_passed
        else "passed"
    )
    gate_fail_reasons: list[str] = []
    if center_ratio_pass_rate < args.min_center_ratio_pass_rate:
        gate_fail_reasons.append("center_ratio_below_threshold")
    if not has_enough_steady_pairs:
        gate_fail_reasons.append("insufficient_steady_pairs")
    if has_enough_steady_pairs and steady_scale_pass_rate < args.min_steady_pass_rate:
        gate_fail_reasons.append("steady_scale_pass_rate_below_threshold")
    if has_enough_steady_pairs and steady_rotation_pass_rate < args.min_steady_pass_rate:
        gate_fail_reasons.append("steady_rotation_pass_rate_below_threshold")
    if missing_prediction_rows:
        gate_fail_reasons.append("missing_predictions")
    if bad_annotation_rows:
        gate_fail_reasons.append("bad_annotations")
    if missing_rows:
        gate_fail_reasons.append("missing_fixtures")
    report = {
        "schemaVersion": 2,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "annotationFile": str(args.annotations),
        "imageDir": str(args.image_dir),
        "predictionsFile": str(args.predictions) if args.predictions else None,
        "predictionSource": prediction_source,
        "media": media,
        "fixtureMetadata": fixture_metadata,
        "pipeline": [
            "image_or_video_frame",
            "landmark_fixture_or_prediction",
            "tracked_hand_frame",
            "ring_finger_pose",
            "ring_fit",
            "render_state",
            "metrics",
            "gate",
        ],
        "thresholds": {
            "centerErrorPx": args.center_threshold_px,
            "widthErrorPx": args.width_threshold_px,
            "rotationErrorDeg": args.rotation_threshold_deg,
            "centerErrorOverRingWidth": args.center_ratio_threshold,
            "minCenterRatioPassRate": args.min_center_ratio_pass_rate,
            "steadyScaleDelta": args.steady_scale_delta_threshold,
            "steadyRotationJitterDeg": args.steady_rotation_jitter_threshold,
            "minSteadyPassRate": args.min_steady_pass_rate,
            "minSteadyPairCount": args.min_steady_pair_count,
        },
        "status": overall_status,
        "summary": {
            "totalFrames": len(rows),
            "measuredFrames": len(measured_rows),
            "visibleMeasuredFrames": len(visible_measured_rows),
            "missingFixtures": len(missing_rows),
            "missingPredictions": len(missing_prediction_rows),
            "badAnnotationFrames": len(bad_annotation_rows),
            "reviewAnnotationFrames": len(review_annotation_rows),
            "failedFrames": len(failed_rows),
            "hiddenCount": hidden_count,
            "frozenCount": frozen_count,
            "steadyPairCount": steady_pair_total,
        },
        "gate": {
            "passed": gate_passed,
            "centerRatioPassCount": center_ratio_pass_count,
            "centerRatioTotal": center_ratio_total,
            "centerRatioPassRate": to_percent(center_ratio_pass_rate),
            "steadyScalePassCount": steady_scale_pass_count,
            "steadyRotationPassCount": steady_rotation_pass_count,
            "steadyPairTotal": steady_pair_total,
            "steadyScalePassRate": to_percent(steady_scale_pass_rate),
            "steadyRotationPassRate": to_percent(steady_rotation_pass_rate),
            "steadyScaleEvaluation": "evaluated" if steady_scale_evaluated else "not_evaluated",
            "steadyRotationEvaluation": "evaluated" if steady_rotation_evaluated else "not_evaluated",
            "failReasons": gate_fail_reasons,
        },
        "steadyPairs": steady_pairs,
        "frames": rows,
    }

    report_name = args.annotations.stem
    json_path = args.report_dir / f"{report_name}-report.json"
    csv_path = args.report_dir / f"{report_name}-report.csv"
    text_path = args.report_dir / f"{report_name}-summary.txt"
    html_path = args.report_dir / f"{report_name}-summary.html"
    with json_path.open("w", encoding="utf-8") as handle:
        json.dump(report, handle, indent=2, ensure_ascii=False)
        handle.write("\n")
    write_csv(csv_path, rows)
    write_text_summary(text_path, report)
    write_html_summary(html_path, report)

    print(f"Replay validation status: {overall_status}")
    print(f"JSON report: {json_path}")
    print(f"CSV report: {csv_path}")
    print(f"Text summary: {text_path}")
    print(f"HTML summary: {html_path}")
    for row in missing_rows:
        print(row["reason"])

    if args.strict_gate and not gate_passed:
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
