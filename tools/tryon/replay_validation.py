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


DEFAULT_CENTER_THRESHOLD_PX = 24.0
DEFAULT_WIDTH_THRESHOLD_PX = 14.0
DEFAULT_ROTATION_THRESHOLD_DEG = 18.0


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
        predictions[str(frame["file"])] = Prediction(
            center_x=float(zone["centerX"]),
            center_y=float(zone["centerY"]),
            width_px=float(zone["widthPx"]),
            rotation_deg=float(zone["rotationDeg"]),
            confidence=float(zone.get("confidence", frame.get("confidence", 0.0))),
        )
    return predictions


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


def row_for_frame(
    frame: dict[str, Any],
    image_dir: Path,
    predictions: dict[str, Prediction],
    center_threshold_px: float,
    width_threshold_px: float,
    rotation_threshold_deg: float,
) -> dict[str, Any]:
    file_name = str(frame["file"])
    image_path = image_dir / file_name
    visible_finger = bool(frame.get("visibleFinger", True))
    expected = expected_zone(frame)

    if not image_path.exists():
        return {
            "file": file_name,
            "status": "missing_fixture",
            "pass": False,
            "visibleFinger": visible_finger,
            "centerErrorPx": None,
            "widthErrorPx": None,
            "rotationErrorDeg": None,
            "confidence": 0.0,
            "reason": f"Missing input image: {image_path}",
        }

    prediction = predictions.get(file_name)
    if prediction is None:
        return {
            "file": file_name,
            "status": "missing_prediction",
            "pass": False,
            "visibleFinger": visible_finger,
            "centerErrorPx": None,
            "widthErrorPx": None,
            "rotationErrorDeg": None,
            "confidence": 0.0,
            "reason": "No replay prediction was provided for this image.",
        }

    center_error = math.hypot(prediction.center_x - expected.center_x, prediction.center_y - expected.center_y)
    width_error = abs(prediction.width_px - expected.width_px)
    rotation_error = normalize_degrees(prediction.rotation_deg - expected.rotation_deg)
    passed = (
        center_error <= center_threshold_px
        and width_error <= width_threshold_px
        and rotation_error <= rotation_threshold_deg
    )

    return {
        "file": file_name,
        "status": "measured",
        "pass": passed,
        "visibleFinger": visible_finger,
        "centerErrorPx": round(center_error, 4),
        "widthErrorPx": round(width_error, 4),
        "rotationErrorDeg": round(rotation_error, 4),
        "confidence": round(prediction.confidence, 4),
        "reason": "" if passed else "Metric exceeded replay threshold.",
    }


def write_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    fieldnames = [
        "file",
        "status",
        "pass",
        "visibleFinger",
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
    predictions = load_predictions(args.predictions)
    rows = [
        row_for_frame(
            frame=frame,
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
    failed_rows = [row for row in rows if not row["pass"]]
    overall_status = "missing_fixtures" if missing_rows else "failed" if failed_rows else "passed"
    report = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "annotationFile": str(args.annotations),
        "imageDir": str(args.image_dir),
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
            "failedFrames": len(failed_rows),
        },
        "frames": rows,
    }

    json_path = args.report_dir / "real-screenshots-2026-04-29-report.json"
    csv_path = args.report_dir / "real-screenshots-2026-04-29-report.csv"
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
