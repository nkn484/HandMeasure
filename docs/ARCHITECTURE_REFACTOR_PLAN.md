# HandMeasure Core Extraction (Phase 1)

## Current architecture snapshot (source-of-truth)

### Module boundaries

- `:handmeasure-core`
  - platform-neutral session/runtime orchestration use-cases
  - core measurement contracts/models/policies
  - no Android UI/Parcelable/Activity contracts
- `:HandMeasure`
  - Android/public compatibility API (`HandMeasureConfig`, `HandMeasureResult`, `RingSizeTable`, `HandMeasureContract`)
  - Android runtime adapter layer (Bitmap/MediaPipe/OpenCV execution)
  - internal engine-facing facade (`MeasurementEngine`) + Android composition factory wiring
- `:app`
  - demo host integration of current Android public flow

### Active runtime/measurement boundary

- Upper runtime/session flow delegates through core contract:
  - `SessionFingerMeasurementRequest`
  - `SessionFingerMeasurementPort`
- Android/OpenCV-only conversion + execution remains isolated under:
  - `OpenCvSessionFingerMeasurementPort`
  - `OpenCvSessionFingerMeasurementMapper`
  - `OpenCvFingerMeasurementEngineExecutor`

### Compatibility status

- Existing Activity/Contract/Parcelable flow remains backward-compatible.
- Internal engine-facing invocation exists for headless-style integration, but runtime execution is still Android-bound by design.

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

## Phase 5 update: Android runtime adapter decomposition

### Runtime hotspot split

- `AndroidSessionRuntimeAnalyzerPort` is now a thin delegating composition layer.
- Runtime responsibilities were split into focused adapters:
  - `AndroidHandRuntimeAdapter`
  - `AndroidCardRuntimeAdapter`
  - `AndroidPoseRuntimeAdapter`
  - `AndroidCoplanarityRuntimeAdapter`
  - `AndroidScaleRuntimeAdapter`
  - `AndroidFingerRuntimeAdapter`
  - `AndroidOverlayRuntimeAdapter`

### Assembly wiring

- Added `AndroidSessionRuntimeAnalyzerFactory` to keep construction out of higher-level processor logic.
- `MeasurementSessionProcessor` now requests a composed runtime analyzer from the factory.

### Boundary state

- Core contracts/orchestration stay unchanged in this phase.
- Android runtime remains Android-only by design (Bitmap + MediaPipe/OpenCV + debug overlay rendering), but now in smaller testable units.

## Phase 6 update: finger measurement execution boundary cleanup

### Core-side measurement contract refinement

- `SessionFingerMeasurementPort` was refined to take a single core request model:
  - `SessionFingerMeasurementRequest`
  - includes `SessionScale` directly in the core contract
- This removes executor-scale leakage from higher-level Android runtime/session code.

### Android/OpenCV measurement isolation improvements

- `OpenCvSessionFingerMeasurementPort` now focuses on contract bridging only.
- Added explicit OpenCV execution boundary classes:
  - `OpenCvFingerMeasurementRequest`
  - `OpenCvFingerMeasurementExecutor`
  - `OpenCvFingerMeasurementEngineExecutor`
  - `OpenCvSessionFingerMeasurementMapper`
- `FingerMeasurementEngine` remains Android/OpenCV-only implementation by design in this phase.

### Runtime/session impact

- `AndroidFingerRuntimeAdapter` now depends on core measurement request contract (`SessionFingerMeasurementRequest`) and no longer performs scale-type conversion.
- Higher-level runtime/session composition continues to use core models (`SessionScale` / `SessionFingerMeasurement`) and does not depend on `MetricScale`.

### Tests

- Added `OpenCvSessionFingerMeasurementPortTest` to validate:
  - request/result delegation through execution boundary
  - scale/source mapping behavior from OpenCV layer to core models

## Phase 7 update: higher-layer measurement leakage removal

### Runtime/session leakage removed

- `HandMeasureCoordinator` now depends on the core-facing finger measurement port contract (`AndroidFingerMeasurementPort`) instead of directly owning `FingerMeasurementEngine`.
- Default wiring still uses `OpenCvSessionFingerMeasurementPort`, preserving Android/OpenCV execution behavior.

### Finger runtime adapter boundary

- `AndroidFingerRuntimeAdapter` now only constructs and forwards `SessionFingerMeasurementRequest`.
- No execution-specific scale type conversion remains in higher runtime/session adapters.

### Scale conversion placement

- Core `SessionScale` -> Android `MetricScale` conversion remains isolated inside `OpenCvSessionFingerMeasurementPort` mapper (`OpenCvSessionFingerMeasurementMapper`).
- This keeps conversion and OpenCV execution details at the lowest adapter layer.

### Tests

- Added `AndroidFingerRuntimeAdapterTest` to validate request construction and delegation through the core-facing port contract.

## Phase 8 correction: boundary alignment verification

### Corrected/verified boundary rules

- Higher runtime/session layer (`AndroidFingerRuntimeAdapter`) builds and forwards `SessionFingerMeasurementRequest` directly.
- Higher runtime/session layer no longer constructs `MetricScale` and does not know OpenCV request/executor types.
- `HandMeasureCoordinator` wiring depends on `AndroidFingerMeasurementPort` and no longer references `FingerMeasurementEngine` in constructor dependencies.

### Conversion placement (kept)

- `SessionScale` -> `MetricScale` conversion remains isolated in `OpenCvSessionFingerMeasurementMapper` under `OpenCvSessionFingerMeasurementPort`.
- OpenCV request shaping/execution remains Android-only below the port boundary.

### Tests

- `AndroidFingerRuntimeAdapterTest` now includes an additional constructor-boundary assertion for `HandMeasureCoordinator` (port dependency present, engine dependency absent).

## Phase 9 update: stabilization and composition cleanup

### MeasurementEngine cleanup

- `MeasurementEngine` is now a small facade that depends on:
  - `MeasurementEngineProcessingPort`
- Direct Android-heavy construction was moved out of `MeasurementEngine`.

### Android composition extraction

- Added `AndroidMeasurementEngineFactory` to compose default Android runtime/session collaborators.
- `HandMeasureCoordinator` now creates `MeasurementEngine` through this factory instead of wiring internals directly.

### File decomposition for maintainability

- Split former multi-adapter bundle into focused runtime files:
  - runtime adapter contracts
  - hand/card/pose/coplanarity/scale/finger/overlay adapter implementations
- Split OpenCV measurement boundary into focused files:
  - `OpenCvSessionFingerMeasurementPort`
  - `OpenCvFingerMeasurementRequest`
  - `OpenCvFingerMeasurementExecutor`
  - `OpenCvSessionFingerMeasurementMapper`
  - `OpenCvFingerMeasurementEngineExecutor`

### Verification improvements

- Added `AndroidMeasurementEngineFactoryTest` for factory/engine boundary wiring.
- `MeasurementEngineTest` now verifies constructor dependency boundary (ports-only facade).

## Phase 10 update: public/internal model boundary completion + HandTryOn path prep

### HandMeasure boundary status

- `MeasurementEngine` now consumes internal engine-facing models only (`MeasurementEngineStepCandidate` -> `MeasurementEngineProcessingResult`).
- Android/public models (`HandMeasureConfig`, `HandMeasureResult`, `RingSizeTable`) remain in compatibility boundary and are mapped via `MeasurementEngineApiMapper`.
- Parcelable concerns remain in Android/public API entry points (`HandMeasureContract`, `HandMeasureActivity`).

### HandTryOn preparation status

- Added concrete architecture preparation plan in `docs/HANDTRYON_REFACTOR_PATH.md`.
- The next Try-on phase now has explicit target layers and class candidates for future `:handtryon-core` extraction.
- No broad Try-on rewrite was done in this phase.

## Phase 11 update: HandTryOn engine boundary + first core extraction

### New TryOn engine-facing boundary in `:HandTryOn`

- Added internal `TryOnEngine` facade and engine request/result models.
- `TryOnSessionResolver` now acts as a compatibility adapter delegating to `TryOnEngine`.
- Added `TryOnEngineDomainMapper` for domain <-> engine/core model adaptation.

### New portable module

- Added `:handtryon-core` for Android-free try-on logic and models.
- Extracted core-ready logic:
  - `TryOnSessionResolverPolicy`
  - `TemporalPlacementSmootherPolicy`
  - `PlacementValidationPolicy`
  - `DefaultFingerAnchorFactory`

### HandTryOn compatibility behavior

- Existing Android runtime/render/UI flow remains in `:HandTryOn`.
- `TryOnRenderResult` remains Android-side by design because it contains `Bitmap`.
- Existing mode/fallback/smoothing/validation semantics are preserved via delegation adapters.

## Phase 11.1 prep: HandTryOn AR renderer replacement path

- Added `docs/HANDTRYON_AR_RENDERER_UPGRADE.md` as the source plan for replacing the current PNG/Canvas preview with an inline AR renderer.
- Recommended path is an Android-side `com.handtryon.ar` package using ARCore + Filament/SceneView while keeping `:handtryon-core` Android-free.
- `ring_AR.glb` has been validated as a real-scale glTF 2.0 asset suitable for ring-size-driven placement.
- Current 2D renderer remains the runtime path until AR session lifecycle, GLB model transform, hand landmark frame input, and export capture are proven on device.

## Phase 12 update: HandMeasure capture-flow stability protocol

### Capture protocol behavior shift

- HandMeasure runtime capture no longer depends on strict user-driven step order.
- Frames are continuously analyzed and auto-bucketed to protocol categories using orientation-range classification with hysteresis.
- Existing backend/session categories are preserved (`BACK/LEFT/RIGHT/UP/DOWN` variants by protocol).

### New platform-neutral capture policies in `:handmeasure-core`

- `OrientationBucketClassifier` for tolerant bucket classification + hysteresis.
- `RollingCandidateWindow` for short per-bucket best-of-window selection.
- `HoldStillCaptureController` for explicit lock-before-commit state flow.
- `CaptureRetryReasonPolicy` for deterministic retry guidance mapping from runtime signals.
- `AdaptiveCaptureProtocolAdvisor` for runtime protocol-strength assessment (`FAST_PREVIEW` / `STANDARD` / `PRECISE`).

### Android integration points in `:HandMeasure`

- `HandMeasureCoordinator` now wires:
  - live bucket classification from palm normal vectors
  - per-frame routing into bucket-aware state machine updates
  - retry-reason policy mapping into user-facing hint keys
- `HandMeasureStateMachine` now:
  - tracks bucketed candidates instead of strict sequential-only acceptance
  - applies rolling-window selection and hold-still locking before auto-capture commit
  - preserves manual best-candidate progression and retry controls
  - keeps engine-facing captured step payloads compatible with existing finalization flow

### Boundary status after this phase

- Android-only concerns remain Android-side:
  - `Bitmap`, CameraX analyzers, MediaPipe/OpenCV execution, Compose/UI rendering
- Capture policies/state math remain platform-neutral in `:handmeasure-core`.
- Public Android API contracts remain unchanged.

### Remaining work

- Adaptive protocol assessment is currently diagnostic/state-level only.
- Future phase can safely introduce configurable early-complete behavior once product confidence gates are validated.
