#!/usr/bin/env python3
"""Build and promote HandTryOn golden attachment review candidates.

The Android replay test writes per-frame overlay PNGs and JSON reports. This
tool turns those reports into a reviewable candidate set, then promotes only
approved images into the active golden folder used by instrumented visual gates.
"""

from __future__ import annotations

import argparse
import json
import shutil
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any


DEFAULT_REPORTS_DIR = Path("validation/tryon/reports/android/latest-natural-band-position/tryon_replay")
DEFAULT_CANDIDATE_ROOT = Path("validation/tryon/golden-candidates")
DEFAULT_GOLDEN_ROOT = Path("validation/tryon/reference-annotations/goldens")
DEFAULT_CENTER_RATIO_THRESHOLD = 0.35


@dataclass(frozen=True)
class Candidate:
    fixture_id: str
    fixture: str
    file: str
    source_png: Path
    output_png: Path
    frame_index: int | None
    augmentation_id: str
    status: str
    visible_finger: bool
    pass_metric: bool
    attachment_status: str
    center_error_px: float | None
    center_error_ratio: float | None
    width_error_px: float | None
    width_error_ratio: float | None
    rotation_error_deg: float | None
    center_policy: str | None
    center_on_mcp_to_pip: float | None
    expected_zone: dict[str, Any] | None
    predicted_zone: dict[str, Any] | None
    selection_reason: str
    promote_to: Path
    source_report: Path
    approved: bool = False

    def to_manifest_item(self, output_dir: Path) -> dict[str, Any]:
        return {
            "fixtureId": self.fixture_id,
            "fixture": self.fixture,
            "file": self.file,
            "candidatePng": relpath(self.output_png, output_dir),
            "sourcePng": str(self.source_png),
            "sourceReport": str(self.source_report),
            "frameIndex": self.frame_index,
            "augmentationId": self.augmentation_id,
            "status": self.status,
            "visibleFinger": self.visible_finger,
            "pass": self.pass_metric,
            "attachmentStatus": self.attachment_status,
            "centerErrorPx": self.center_error_px,
            "centerErrorRatio": self.center_error_ratio,
            "widthErrorPx": self.width_error_px,
            "widthErrorRatio": self.width_error_ratio,
            "rotationErrorDeg": self.rotation_error_deg,
            "centerPolicy": self.center_policy,
            "centerOnMcpToPip": self.center_on_mcp_to_pip,
            "expectedRingFingerZone": self.expected_zone,
            "predictedRingFingerZone": self.predicted_zone,
            "selectionReason": self.selection_reason,
            "approved": self.approved,
            "promoteTo": str(self.promote_to),
        }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Manage HandTryOn golden attachment candidates.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    build = subparsers.add_parser("build", help="Create a review candidate folder from replay reports.")
    build.add_argument("--reports-dir", type=Path, default=DEFAULT_REPORTS_DIR)
    build.add_argument("--output-dir", type=Path)
    build.add_argument("--name", default="")
    build.add_argument("--golden-root", type=Path, default=DEFAULT_GOLDEN_ROOT)
    build.add_argument("--center-ratio-threshold", type=float, default=DEFAULT_CENTER_RATIO_THRESHOLD)
    build.add_argument("--max-extra-per-augmentation", type=int, default=1)
    build.add_argument("--include-all-identity", action=argparse.BooleanOptionalAction, default=True)
    build.add_argument("--include-hidden-identity", action=argparse.BooleanOptionalAction, default=True)
    build.add_argument("--require-png", action=argparse.BooleanOptionalAction, default=True)

    promote = subparsers.add_parser("promote", help="Promote approved candidate PNGs into active goldens.")
    promote.add_argument("--manifest", type=Path, required=True)
    promote.add_argument("--golden-root", type=Path, default=DEFAULT_GOLDEN_ROOT)
    promote.add_argument("--approved-list", type=Path)
    promote.add_argument("--all", action="store_true", help="Promote every candidate in the manifest.")
    promote.add_argument("--dry-run", action="store_true")

    audit = subparsers.add_parser("audit", help="Check whether active golden PNGs exist for manifest candidates.")
    audit.add_argument("--manifest", type=Path, required=True)

    return parser.parse_args()


def load_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2, ensure_ascii=False)
        handle.write("\n")


def build_candidates(args: argparse.Namespace) -> int:
    reports_dir = args.reports_dir
    if not reports_dir.exists():
        raise SystemExit(f"Reports directory does not exist: {reports_dir}")

    output_dir = args.output_dir or DEFAULT_CANDIDATE_ROOT / default_run_name(args.name)
    output_dir.mkdir(parents=True, exist_ok=True)

    reports = sorted(reports_dir.glob("*-android-report.json"))
    if not reports:
        raise SystemExit(f"No *-android-report.json files found in {reports_dir}")

    candidates: list[Candidate] = []
    warnings: list[str] = []
    for report_path in reports:
        report = load_json(report_path)
        selected = select_report_candidates(
            report=report,
            report_path=report_path,
            output_dir=output_dir,
            golden_root=args.golden_root,
            center_ratio_threshold=args.center_ratio_threshold,
            max_extra_per_augmentation=args.max_extra_per_augmentation,
            include_all_identity=args.include_all_identity,
            include_hidden_identity=args.include_hidden_identity,
        )
        for candidate in selected:
            if not candidate.source_png.exists():
                message = f"missing source PNG: {candidate.source_png}"
                warnings.append(message)
                if args.require_png:
                    raise SystemExit(message)
                continue
            candidate.output_png.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(candidate.source_png, candidate.output_png)
            candidates.append(candidate)

    manifest = {
        "schemaVersion": 1,
        "generatedAt": datetime.now().isoformat(timespec="seconds"),
        "generatedFrom": str(reports_dir),
        "mode": "review_candidates_not_active_golden_gate",
        "reviewPurpose": "golden_attachment",
        "reviewInstruction": (
            "Open REVIEW.md, inspect expected/predicted attachment visually, then set approved=true "
            "in candidate-manifest.json or pass an approved-list to the promote command."
        ),
        "centerRatioThreshold": args.center_ratio_threshold,
        "candidateCount": len(candidates),
        "warnings": warnings,
        "candidates": [candidate.to_manifest_item(output_dir) for candidate in candidates],
    }
    write_json(output_dir / "candidate-manifest.json", manifest)
    write_review_markdown(output_dir / "REVIEW.md", manifest)
    write_review_csv(output_dir / "review.csv", manifest)

    print(f"Created {len(candidates)} golden attachment candidates")
    print(f"Review folder: {output_dir}")
    print(f"Manifest: {output_dir / 'candidate-manifest.json'}")
    print(f"Review: {output_dir / 'REVIEW.md'}")
    return 0


def select_report_candidates(
    report: dict[str, Any],
    report_path: Path,
    output_dir: Path,
    golden_root: Path,
    center_ratio_threshold: float,
    max_extra_per_augmentation: int,
    include_all_identity: bool,
    include_hidden_identity: bool,
) -> list[Candidate]:
    annotation_asset = str(report.get("annotationAsset", ""))
    fixture_id = str(report.get("fixtureMetadata", {}).get("fixtureId") or Path(annotation_asset).stem or report_path.stem.removesuffix("-android-report"))
    fixture = fixture_id
    frames = report.get("frames", [])
    selected_keys: set[tuple[int | None, str]] = set()
    selected: list[Candidate] = []

    def add(row: dict[str, Any], reason: str) -> None:
        key = (frame_index(row), augmentation_id(row))
        if key in selected_keys:
            return
        selected_keys.add(key)
        selected.append(
            candidate_from_row(
                row=row,
                report_path=report_path,
                fixture_id=fixture_id,
                fixture=fixture,
                output_dir=output_dir,
                golden_root=golden_root,
                center_ratio_threshold=center_ratio_threshold,
                selection_reason=reason,
            ),
        )

    identity_rows = [row for row in frames if augmentation_id(row) == "identity"]
    if include_all_identity:
        for row in identity_rows:
            if include_hidden_identity or bool(row.get("visibleFinger", True)):
                add(row, "identity timeline coverage")

    visible_measured = [row for row in frames if bool(row.get("visibleFinger", True)) and row.get("status") == "measured"]
    by_augmentation: dict[str, list[dict[str, Any]]] = {}
    for row in visible_measured:
        aug = augmentation_id(row)
        if aug == "identity":
            continue
        by_augmentation.setdefault(aug, []).append(row)

    for aug, rows in sorted(by_augmentation.items()):
        worst = sorted(rows, key=lambda row: none_last_float(row.get("centerErrorRatio")), reverse=True)
        for row in worst[: max(0, max_extra_per_augmentation)]:
            add(row, f"worst visible measured sample for {aug}")

    false_positive_hidden = [
        row
        for row in frames
        if not bool(row.get("visibleFinger", True)) and row.get("status") == "measured"
    ]
    for row in false_positive_hidden:
        add(row, "hidden finger false-positive coverage")

    return selected


def candidate_from_row(
    row: dict[str, Any],
    report_path: Path,
    fixture_id: str,
    fixture: str,
    output_dir: Path,
    golden_root: Path,
    center_ratio_threshold: float,
    selection_reason: str,
) -> Candidate:
    frame = frame_index(row)
    aug = augmentation_id(row)
    filename = screenshot_name(frame, aug)
    source_png = resolve_source_png(row=row, report_path=report_path, fixture=fixture, filename=filename)
    output_png = output_dir / fixture / filename
    center_ratio = optional_float(row.get("centerErrorRatio"))
    visible = bool(row.get("visibleFinger", True))
    status = str(row.get("status", ""))
    pass_metric = bool(row.get("pass", False))
    attachment_status = attachment_status_for(row, center_ratio_threshold)
    pose = row.get("poseDiagnostics") if isinstance(row.get("poseDiagnostics"), dict) else {}
    return Candidate(
        fixture_id=fixture_id,
        fixture=fixture,
        file=f"{fixture}/{filename}",
        source_png=source_png,
        output_png=output_png,
        frame_index=frame,
        augmentation_id=aug,
        status=status,
        visible_finger=visible,
        pass_metric=pass_metric,
        attachment_status=attachment_status,
        center_error_px=optional_float(row.get("centerErrorPx")),
        center_error_ratio=center_ratio,
        width_error_px=optional_float(row.get("widthErrorPx")),
        width_error_ratio=optional_float(row.get("widthErrorRatio")),
        rotation_error_deg=optional_float(row.get("rotationErrorDeg")),
        center_policy=optional_string(pose.get("centerPolicy")),
        center_on_mcp_to_pip=optional_float(pose.get("centerOnMcpToPip")),
        expected_zone=row.get("expectedRingFingerZone") or row.get("ringFingerZone"),
        predicted_zone=row.get("predictedRingFingerZone"),
        selection_reason=selection_reason,
        promote_to=golden_root / fixture / filename,
        source_report=report_path,
    )


def resolve_source_png(row: dict[str, Any], report_path: Path, fixture: str, filename: str) -> Path:
    visual_diff = row.get("visualDiff") if isinstance(row.get("visualDiff"), dict) else {}
    actual = visual_diff.get("actualPng")
    if actual:
        actual_path = Path(str(actual))
        if actual_path.exists():
            return actual_path
    return report_path.parent / f"{fixture}-screenshots" / filename


def attachment_status_for(row: dict[str, Any], center_ratio_threshold: float) -> str:
    visible = bool(row.get("visibleFinger", True))
    status = str(row.get("status", ""))
    center_ratio = optional_float(row.get("centerErrorRatio"))
    if not visible:
        return "hidden_ok" if status != "measured" else "hidden_false_positive"
    if status != "measured":
        return "visible_missing_prediction"
    if center_ratio is None:
        return "needs_review"
    if center_ratio <= center_ratio_threshold:
        return "attached_within_threshold"
    return "attachment_over_threshold"


def promote_candidates(args: argparse.Namespace) -> int:
    manifest_path = args.manifest
    manifest = load_json(manifest_path)
    approved_paths = load_approved_list(args.approved_list)
    promoted: list[dict[str, Any]] = []
    skipped: list[dict[str, Any]] = []

    for item in manifest.get("candidates", []):
        candidate_rel = str(item.get("candidatePng") or item.get("file") or "")
        approved = args.all or bool(item.get("approved", False)) or candidate_rel in approved_paths or str(item.get("file", "")) in approved_paths
        if not approved:
            skipped.append({"file": item.get("file"), "reason": "not_approved"})
            continue
        source = (manifest_path.parent / candidate_rel).resolve()
        promote_to = Path(str(item.get("promoteTo") or args.golden_root / str(item.get("file", ""))))
        if not source.exists():
            skipped.append({"file": item.get("file"), "reason": f"missing_candidate_png:{source}"})
            continue
        promoted.append({"file": item.get("file"), "source": str(source), "promoteTo": str(promote_to)})
        if not args.dry_run:
            promote_to.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, promote_to)

    report = {
        "schemaVersion": 1,
        "generatedAt": datetime.now().isoformat(timespec="seconds"),
        "dryRun": args.dry_run,
        "manifest": str(manifest_path),
        "promotedCount": len(promoted),
        "skippedCount": len(skipped),
        "promoted": promoted,
        "skipped": skipped,
    }
    report_path = manifest_path.parent / "promotion-report.json"
    write_json(report_path, report)
    action = "Would promote" if args.dry_run else "Promoted"
    print(f"{action} {len(promoted)} candidates")
    print(f"Promotion report: {report_path}")
    return 0


def audit_candidates(args: argparse.Namespace) -> int:
    manifest = load_json(args.manifest)
    rows = []
    missing = 0
    for item in manifest.get("candidates", []):
        target = Path(str(item.get("promoteTo", "")))
        exists = target.exists()
        if not exists:
            missing += 1
        rows.append({"file": item.get("file"), "promoteTo": str(target), "exists": exists})
    print(f"Candidates: {len(rows)}")
    print(f"Active goldens missing: {missing}")
    for row in rows:
        if not row["exists"]:
            print(f"missing: {row['promoteTo']}")
    return 1 if missing else 0


def write_review_markdown(path: Path, manifest: dict[str, Any]) -> None:
    lines = [
        "# Try-on Golden Attachment Candidates",
        "",
        "These PNGs are review candidates only. They do not affect visual regression gates until promoted.",
        "",
        "Review rule:",
        "",
        "- Green circle/text is the expected ring zone.",
        "- Red circle/text is the predicted ring zone.",
        "- Approve only if the ring attachment belongs on the ring-finger wear zone and hidden-finger frames do not show a misleading prediction.",
        "- After review, set `approved: true` in `candidate-manifest.json` for accepted rows, or create an approved-list text file with one candidate path per line.",
        "",
        "Promote approved candidates:",
        "",
        "```powershell",
        "python tools/tryon/golden_candidates.py promote --manifest <candidate-folder>\\candidate-manifest.json",
        "```",
        "",
        "| Fixture | Frame | Augmentation | Status | Visible | Attachment | Center ratio | Rotation deg | Policy | Approved | File |",
        "| --- | ---: | --- | --- | --- | --- | ---: | ---: | --- | --- | --- |",
    ]
    for item in manifest.get("candidates", []):
        lines.append(
            "| {fixture} | {frame} | {aug} | {status} | {visible} | {attachment} | {center} | {rotation} | {policy} | {approved} | [{file}]({file}) |".format(
                fixture=item.get("fixture", ""),
                frame=item.get("frameIndex", ""),
                aug=item.get("augmentationId", ""),
                status=item.get("status", ""),
                visible=item.get("visibleFinger", ""),
                attachment=item.get("attachmentStatus", ""),
                center=format_float(item.get("centerErrorRatio"), 4),
                rotation=format_float(item.get("rotationErrorDeg"), 3),
                policy=item.get("centerPolicy") or "",
                approved=item.get("approved", False),
                file=item.get("candidatePng", item.get("file", "")),
            ),
        )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_review_csv(path: Path, manifest: dict[str, Any]) -> None:
    import csv

    fieldnames = [
        "fixture",
        "frameIndex",
        "augmentationId",
        "status",
        "visibleFinger",
        "attachmentStatus",
        "centerErrorRatio",
        "rotationErrorDeg",
        "centerPolicy",
        "candidatePng",
        "approved",
        "promoteTo",
    ]
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for item in manifest.get("candidates", []):
            writer.writerow({field: item.get(field) for field in fieldnames})


def load_approved_list(path: Path | None) -> set[str]:
    if path is None:
        return set()
    return {
        line.strip().replace("\\", "/")
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.strip().startswith("#")
    }


def default_run_name(name: str) -> str:
    suffix = name.strip() or "attachment-review"
    today = datetime.now().strftime("%Y-%m-%d")
    return f"{today}-{suffix}"


def screenshot_name(frame_index_value: int | None, augmentation: str) -> str:
    frame = 0 if frame_index_value is None else frame_index_value
    return f"frame_{frame:06d}_{augmentation}.png"


def augmentation_id(row: dict[str, Any]) -> str:
    return str(row.get("augmentationId") or "identity")


def frame_index(row: dict[str, Any]) -> int | None:
    value = row.get("frameIndex")
    if value is None:
        return None
    return int(value)


def optional_float(value: Any) -> float | None:
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def optional_string(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value)
    return text if text else None


def none_last_float(value: Any) -> float:
    parsed = optional_float(value)
    return parsed if parsed is not None else float("-inf")


def format_float(value: Any, precision: int) -> str:
    parsed = optional_float(value)
    return "" if parsed is None else f"{parsed:.{precision}f}"


def relpath(path: Path, start: Path) -> str:
    try:
        return str(path.relative_to(start)).replace("\\", "/")
    except ValueError:
        return str(path).replace("\\", "/")


def main() -> int:
    args = parse_args()
    if args.command == "build":
        return build_candidates(args)
    if args.command == "promote":
        return promote_candidates(args)
    if args.command == "audit":
        return audit_candidates(args)
    raise SystemExit(f"Unknown command: {args.command}")


if __name__ == "__main__":
    raise SystemExit(main())
