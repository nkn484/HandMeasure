# HandMeasure Refactor Architecture Note

> Status note: this document captures an earlier coordinator-focused slice.
> Current source-of-truth architecture and phase status live in:
> - `docs/ARCHITECTURE_REFACTOR_PLAN.md`
> - `docs/HANDTRYON_REFACTOR_PATH.md`
> - `.github/workflows/android-ci.yml` (includes `:handmeasure-core:test`)

## Goals
- Keep public API compatibility for `HandMeasureConfig`, `HandMeasureContract`, `HandMeasureResult`, and `RingSizeTable`.
- Improve maintainability/testability by reducing `HandMeasureCoordinator` responsibilities.
- Preserve measurement behavior and debug replay/export features.

## New Coordinator-Centric Design
- `HandMeasureCoordinator` now focuses on orchestration only:
  - capture state transitions
  - live frame pipeline coordination
  - final result assembly
- Live frame signal math moved to `FrameSignalEstimator`:
  - blur (global/ROI), motion, lighting, 2D finger-card proximity
  - keeps temporal luma state scoped to this component
- Pose guidance split into typed decision + UI text mapping:
  - `PoseClassifier` returns `PoseGuidanceAction` (no UI strings)
  - `PoseGuidanceHintDecider` maps vision/domain signals to `PoseGuidanceHintKey`
  - UI resolves keys to localized strings via Android resources
- Finalization domain processing moved to `MeasurementSessionProcessor`:
  - per-step decode/detect/scale/measure diagnostics
  - warning accumulation and step measurement models
- Debug/export concerns moved out:
  - `DebugFrameAnnotator` creates annotated JPEG overlays
  - `DebugSessionExporter` writes JSON + overlay assets

## Performance/Behavior Notes
- Finalize+debug flow no longer re-runs hand/card detection during export.
- Overlay JPEGs are produced while step bitmaps are already decoded in finalize processing.
- Core measurement algorithms and fusion/reliability policies were preserved.

## Engineering Hygiene Added
- `detekt` + `ktlint` integrated at Gradle root for Kotlin Android subprojects.
- `ktlint` baselines added per module for staged adoption without mass reformatting.
- GitHub Actions workflow added (`.github/workflows/android-ci.yml`) running:
  - `ktlintCheck`
  - `detekt`
  - `:HandMeasure:testDebugUnitTest`
  - `:app:compileDebugKotlin`
