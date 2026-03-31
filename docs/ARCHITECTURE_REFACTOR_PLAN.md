# HandMeasure Core Extraction (Phase 1)

## Scope of this phase

- Introduce a new platform-neutral module: `:handmeasure-core`.
- Move low-risk, Android-free measurement and policy logic into core.
- Keep `:HandMeasure` working as the Android-facing adapter layer.
- Avoid broad package renames or UX/API redesign.

## New module graph (current state)

- `:handmeasure-core` (new Kotlin/JVM module)
- `:HandMeasure` now depends on `:handmeasure-core`
- `:app` continues to depend on `:HandMeasure` and `:HandTryOn`

## Logic moved to `:handmeasure-core`

- `com.handmeasure.core.measurement.EllipseMath`
- `com.handmeasure.core.measurement.FrameQualityScorer` (+ input/output models)
- `com.handmeasure.core.measurement.FingerMeasurementFusion`
- `com.handmeasure.core.measurement.ResultReliabilityPolicy`
- `com.handmeasure.core.measurement.RingSizeMapper`
- Core-neutral measurement models/enums for fusion/reliability/ring sizing

## Adapter strategy in `:HandMeasure`

` :HandMeasure` keeps API compatibility by wrapping/delegating to core:

- `com.handmeasure.measurement.EllipseMath` delegates to core
- `com.handmeasure.measurement.FrameQualityScorer` uses type aliases to core
- `com.handmeasure.measurement.FingerMeasurementFusion` maps Android API models <-> core models
- `com.handmeasure.measurement.ResultReliabilityPolicy` maps Android API models <-> core models
- `com.handmeasure.measurement.TableRingSizeMapper` maps `RingSizeTable` <-> core table

This keeps current `HandMeasureCoordinator` and Activity flow unchanged at call-site level.

## Tests added in core

New unit tests in `:handmeasure-core`:

- `EllipseMathTest`
- `FrameQualityScorerTest`
- `FingerMeasurementFusionTest`
- `ResultReliabilityPolicyTest`
- `RingSizeMapperTest`

## Known remaining Android coupling (not handled in this phase)

- `Parcelable` API models (`HandMeasureConfig`, `HandMeasureResult`, `RingSizeTable`) remain Android-side.
- Image/camera stack remains Android-side (`Bitmap`, CameraX, MediaPipe, OpenCV Android).
- `MeasurementSessionProcessor` / finalization still decodes bitmaps and performs Android-bound detection.
- `FingerMeasurementEngine` remains Android/OpenCV-bound.

## Next extraction candidates

- Introduce core-facing config/result domain models and mapper layer from parcelable APIs.
- Extract additional non-Android session/finalization policies from `MeasurementSessionProcessor`.
- Reduce `HandMeasureCoordinator` into a thinner orchestrator using core use-cases.

## Phase 2 update: session finalization engine split

### New core use case and contracts

- Added `com.handmeasure.core.session.MeasurementSessionFinalizationUseCase`
- Added core session contracts/models:
  - `SessionStepCandidate`
  - `SessionQualityThresholds`
  - `SessionStepAnalyzer` (core boundary interface)
  - `SessionStepAnalysis`
  - `SessionProcessingResult`
  - supporting diagnostics/scale/measurement models in `core.session`

### New Android adapter

- Added `com.handmeasure.coordinator.AndroidSessionStepAnalyzer` in `:HandMeasure`
  - decodes frame bytes to `Bitmap`
  - runs MediaPipe/OpenCV detection (`HandLandmarkEngine`, `ReferenceCardDetector`)
  - runs `ScaleCalibrator` + `FingerMeasurementEngine`
  - builds neutral `SessionStepAnalysis` for core use case
- `MeasurementSessionProcessor` is now an adapter/orchestrator:
  - maps `StepCandidate` -> core `SessionStepCandidate`
  - invokes `MeasurementSessionFinalizationUseCase`
  - maps core outputs back to existing Android API models (`StepDiagnostics`, warnings, etc.)

### FingerMeasurementEngine boundary progress

- First boundary step is in place through `SessionStepAnalyzer`:
  - core finalization logic no longer depends on `FingerMeasurementEngine` directly
  - OpenCV measurement remains in Android analyzer implementation only

### Tests

- Added core tests for session finalization logic:
  - `MeasurementSessionFinalizationUseCaseTest`

## Phase 3 update: session processor boundary tightening

### New core contracts/use cases

- Added `com.handmeasure.core.session.MeasurementSessionProcessingUseCase`
- Added `com.handmeasure.core.session.MeasurementSessionProcessingRequest`
- Added `com.handmeasure.core.session.SessionFingerMeasurementPort`

### Android adapters added in `:HandMeasure`

- Added `com.handmeasure.coordinator.AndroidSessionProcessingMapper`
  - maps `StepCandidate` and thresholds into `MeasurementSessionProcessingRequest`
  - maps core `SessionProcessingResult` back to existing Android `SessionProcessingOutput`
- Added `com.handmeasure.measurement.OpenCvSessionFingerMeasurementPort`
  - Android/OpenCV implementation of `SessionFingerMeasurementPort`
  - keeps `Bitmap`/MediaPipe hand detection/OpenCV measurement on Android side
- `MeasurementSessionProcessor` is now an adapter invoking core `MeasurementSessionProcessingUseCase`

### Coordinator simplification

- Added `com.handmeasure.coordinator.MeasurementResultAssembler` to host result assembly domain logic
- `HandMeasureCoordinator.finalizeResult()` now delegates session post-processing composition to this assembler

### Remaining Android-bound hotspots

- `AndroidSessionStepAnalyzer` still performs:
  - frame decode to `Bitmap`
  - MediaPipe hand/card inference and pose scoring
  - OpenCV-based width measurement through Android port implementation
- Public Parcelable API models remain Android-side (`HandMeasureConfig`, `HandMeasureResult`, diagnostics payloads)

## Phase 4 update: step runtime boundary extraction

### New core runtime-analysis contracts

- Added `StepRuntimeAnalysisRequest` in `core.session`
- Added `SessionRuntimeAnalyzerPort` in `core.session`
- Added `StepRuntimeAnalysisUseCase` in `core.session`
- Added core tests for this orchestration:
  - `StepRuntimeAnalysisUseCaseTest`

### Android runtime split

- Added `AndroidSessionRuntimeAnalyzerPort` in `:HandMeasure`
  - owns runtime inference calls (hand/card detect, pose score, coplanarity proxy)
  - owns Android-side scale calibration + card diagnostics mapping + OpenCV width measurement invocation
  - owns debug overlay JPEG generation
- `AndroidSessionStepAnalyzer` is now a thin adapter:
  - decode bytes -> `Bitmap`
  - delegate to core `StepRuntimeAnalysisUseCase`
  - recycle `Bitmap`

### Boundary after this phase

- Core now owns step-analysis orchestration/composition policy for:
  - hand/card/pose/coplanarity flow sequencing
  - effective-scale selection for measurement
  - `SessionStepAnalysis` assembly
  - overlay inclusion policy (based on request flag)
- Android remains responsible for:
  - runtime engines and Android image types
  - MediaPipe/OpenCV execution
  - conversion from runtime outputs to core-neutral contracts
