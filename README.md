# HandMeasure

`HandMeasure` is an Android library module that guides the user through a multi-angle hand capture flow and returns a best-effort ring-size estimate on-device.

## Assumptions

- `minSdk=26`, `compileSdk=35`
- Camera stack: CameraX
- Hand landmarks: MediaPipe Hand Landmarker Android Tasks API
- Reference calibration: ID-1 card (`85.60 mm x 53.98 mm`)
- v1 prioritizes deterministic result delivery over lab-grade accuracy
- v1 does not implement automatic timeout
- v1 now uses an auto-bucketing capture flow over 5 measurement categories (the UI still reports category progress)

## Recent improvements (current revision)

- Confidence plumbing now keeps raw MediaPipe hand confidence (removed artificial confidence floor).
- Frame quality now returns structured subscores and explicit penalty reasons:
  - detection confidence
  - pose confidence
  - measurement confidence
  - total frame score
- Blur/motion/lighting scoring is upgraded:
  - blur via Laplacian variance (global + finger ROI)
  - motion via frame-to-frame ROI luma difference
  - lighting via mean exposure + clipping checks
- Card detector now exposes diagnostics:
  - coverage ratio
  - aspect residual
  - rectangularity score
  - edge support score
  - rectification confidence
- Finger width measurement is now band-based with adaptive ROI and robust outlier rejection.
- Multi-view thickness fusion now uses weighted robust aggregation with left/right + up/down consistency penalties.
- Pose guidance is smoothed with hysteresis and user-facing hints.
- Capture flow now auto-buckets frames into angle categories (frontal/left/right/up/down) using tolerant orientation ranges + hysteresis.
- Per-bucket frame selection now uses a short rolling window and best-of-window candidate commit.
- Auto-capture now requires a short hold-still lock window before commit (reduces transient shake captures).
- Retry guidance now maps directly from runtime penalty signals (motion, lighting/glare, pose, coplanarity, lock-wait).
- Adaptive protocol mode assessment (`FAST_PREVIEW`/`STANDARD`/`PRECISE`) is now tracked for runtime diagnostics.
- Debug overlay mapping now uses actual frame dimensions (no hard-coded `1280` scaling).
- Optional replay/debug modes were added:
  - `debugReplayInputPath` for deterministic flow replay
  - `debugExportEnabled` for per-session JSON + annotated frame export

## Modules

- `:handmeasure-core`: platform-neutral session/runtime orchestration + measurement policies/contracts
- `:handtryon-core`: platform-neutral try-on session/placement policies
- `:HandMeasure`: reusable Android library module
- `:HandTryOn`: try-on library module (domain/core/render/realtime/validation)
- `:app`: host demo app

## Architecture layers

- **Android/public compatibility layer (`:HandMeasure`)**
  - Public Android integration surface (`HandMeasureConfig`, `HandMeasureResult`, `RingSizeTable`, `HandMeasureContract`)
  - Guided Activity flow (`HandMeasureActivity`) and Parcelable/ActivityResultContract concerns
  - Android runtime execution (Bitmap decode, CameraX, MediaPipe, OpenCV, overlay encoding)
- **Internal/headless engine-facing layer (`:HandMeasure`)**
  - `MeasurementEngine` facade for engine-style invocation without `ActivityResultContract`
  - Internal engine models + API mappers (`com.handmeasure.engine.model`, `com.handmeasure.engine.compat`)
  - Android composition factory wiring runtime adapters into engine ports (`AndroidMeasurementEngineFactory`)
- **Core orchestration layer (`:handmeasure-core`)**
  - Step/session orchestration use-cases (`StepRuntimeAnalysisUseCase`, `MeasurementSessionProcessingUseCase`)
  - Core request/result contracts (`SessionFingerMeasurementRequest`, `SessionFingerMeasurementPort`)
  - Platform-neutral policies (fusion, reliability, ring-size mapping, quality scoring)

## HandTryOn architecture path

- `:HandTryOn` now has an internal `TryOnEngine` boundary for session resolution orchestration.
- `TryOnEngine` now returns Android-free engine session/render state models, with compatibility mapping in `TryOnEngineDomainMapper`.
- Android/runtime/render concerns remain in `:HandTryOn` (CameraX analyzer, bitmap rendering, Compose overlay).
- `:handtryon-core` now also hosts portable engine-facing result/session/render contracts and state-shaping helpers, in addition to resolver/smoothing/validation/anchor policies.
- `TryOnRenderResult` remains Android-side because it carries `Bitmap`.
- Detailed status and next steps are tracked in `docs/HANDTRYON_REFACTOR_PATH.md`.

## Public API

- `HandMeasureConfig`
- `HandMeasureContract`
- `HandMeasureResult`
- `RingSizeTable`

This Android/public API remains backward-compatible and is implemented as a compatibility layer over the internal engine/runtime boundaries.

Example:

```kotlin
val launcher =
    registerForActivityResult(HandMeasureContract()) { result ->
        // nullable only if the user leaves before finishing the guided flow
    }

launcher.launch(
    HandMeasureConfig(
        targetFinger = TargetFinger.RING,
        debugOverlayEnabled = true,
        ringSizeTable = RingSizeTable.sampleUsLike(),
    ),
)
```

## Capture flow

### Default (privacy-first) dorsal protocol

1. `BACK_OF_HAND`
2. `LEFT_OBLIQUE_DORSAL`
3. `RIGHT_OBLIQUE_DORSAL`
4. `UP_TILT_DORSAL`
5. `DOWN_TILT_DORSAL`

### Legacy palmar protocol (migration/benchmark)

1. `FRONT_PALM`
2. `LEFT_OBLIQUE`
3. `RIGHT_OBLIQUE`
4. `UP_TILT`
5. `DOWN_TILT`

For each protocol category the module now:

- continuously analyzes live frames (no strict user-driven step lock-in)
- auto-classifies each frame into angle buckets using tolerant pose ranges with hysteresis
- keeps a short rolling candidate window per bucket
- requires a short hold-still window before auto-committing
- commits the best frame in the current window (not only the latest frame)
- still allows manual progression using the current best candidate
- still allows retry of the current category
After required bucket coverage is completed, the library stops the camera, shows a processing screen, computes the measurement, then shows a result screen.

## Calibration model

The calibration source is only the detected ID-1 card.

Implementation details:

- the card detector segments the visible outer card contour from the frame
- it fits a tight rotated rectangle around the visible card pixels using `minAreaRect`
- rounded corners or chipped corners do not reduce the fitted rectangle dimensions
- the fitted rectangle long side is treated as card width in pixels
- the fitted rectangle short side is treated as card height in pixels

Metric scale:

- `mmPerPxX = 85.60 / rectWidthPx`
- `mmPerPxY = 53.98 / rectHeightPx`

The module does not use MediaPipe world coordinates as metric scale.

## Measurement approach

The v1 pipeline is practical and best-effort:

- detect hand landmarks
- detect the card and fit the ideal rectangle
- calibrate pixel-to-mm scale
- estimate finger width at the proximal phalanx ring zone in the frontal frame
- estimate thickness from oblique/tilt captures using repeated visible-width measurements plus view correction
- fuse measurements across captures with a robust median-style aggregation
- approximate the finger cross-section as an ellipse
- estimate circumference using Ramanujan's ellipse approximation
- map equivalent diameter to the nearest ring size in the configured table

## Result behavior

The library always tries to return a result after the full capture sequence.

If card quality, pose quality, blur, or motion are weak:

- confidence is reduced
- warnings are attached
- the result is still returned

Warnings may include:

- `BEST_EFFORT_ESTIMATE`
- `LOW_CARD_CONFIDENCE`
- `LOW_POSE_CONFIDENCE`
- `HIGH_BLUR`
- `HIGH_MOTION`
- `THICKNESS_ESTIMATED_FROM_WEAK_ANGLES`

Confidence model:
- per-frame `detection`, `pose`, `measurement` confidences
- final fused confidence from multi-step fusion diagnostics
- low-confidence sessions still return best-effort output (no null final result)

## Tuning knobs

Key knobs live in `HandMeasureConfig` and `QualityThresholds`:

- `autoCaptureScore`
- `bestCandidateProgressScore`
- `handMinScore`
- `cardMinScore`
- `blurMinScore`
- `motionMinScore`
- `lightingMinScore`
- `debugExportEnabled`
- `debugReplayInputPath`

## MediaPipe model

The library bundles `HandMeasure/src/main/assets/hand_landmarker.task`.

Current bundled source:

- `https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task`

## Known limitations

- Card detection remains sensitive to severe glare or very weak card edges.
- Thickness estimation is practical but still heuristic under strong perspective skew.
- Replay mode depends on image quality of provided step files.
- Pose classifier is lightweight and can degrade under extreme hand articulation.
- Final accuracy is still constrained by card/hand coplanarity and capture stability.

## Build

Windows note for Unicode paths:

```powershell
subst X: "$PWD"
pushd X:\
$env:GRADLE_USER_HOME="$PWD\.gradle_user_home_ascii"
.\gradlew.bat :HandMeasure:assembleDebug :HandTryOn:assembleDebug :app:assembleDebug
popd
subst X: /D
```

## Tests

Unit tests cover:

- scale calibration math
- card rectangle fitting helpers
- ellipse math
- ring size mapping
- frame quality scoring
- pose classification helpers
- fusion robustness checks
- try-on engine/policy boundary contracts (`:handtryon-core`, `:HandTryOn`)
- app demo handoff mapping/fallback behavior (`:app`)

Instrumented tests cover:
- replay-path smoke test for `HandMeasureActivity`
- replay runner JSON report generation

## Replay harness

Input folder example:
- `front_palm.jpg`
- `left_oblique.jpg`
- `right_oblique.jpg`
- `up_tilt.jpg`
- `down_tilt.jpg`
- optional `ground_truth.json` with `diameterMm`

Programmatic usage:

```kotlin
val runner = MeasurementReplayRunner { cfg ->
    HandMeasureCoordinator(cfg, MediaPipeHandLandmarkEngine(context))
}
val output = runner.runFromDirectory(HandMeasureConfig(), File("/sdcard/HandMeasureReplay"))
```

For full protocol and metrics, see `VALIDATION_PLAN.md`.

## Architecture note

For the refactor structure and component boundaries, see `docs/refactor-architecture-note.md`.

## Ring try-on POC (single product)

- Core try-on implementation: `:HandTryOn`
- Demo host wiring: `:app` module, package `com.handmeasure.sample.tryon`
- Demo entry path: launch `:app` and use the landing screen actions (`Launch HandMeasure Demo`, `Launch HandTryOn Demo`, `Launch Measure -> TryOn Flow`)
- `HandTryOn` docs: `HandTryOn/README.md`
- POC guide: `docs/tryon-poc.md`
- Asset normalization guide: `docs/tryon-asset-normalization.md`
- Validation guide: `docs/tryon-validation.md`

### Combined demo walkthrough (earliest end-to-end story)

1. Launch `:app`, tap `Launch Measure -> TryOn Flow`.
2. Let HandMeasure finish, then return to TryOn; the bottom panel shows `Handoff: ...` and `Source: Live HandMeasure result`.
3. If measurement is canceled in that combined route, the demo applies `Simulated handoff` automatically so TryOn still has deterministic measurement input.
4. Optional presenter control: in TryOn use `Use Sample Handoff` or `Clear Handoff` to show measured vs non-measured behavior quickly.

Current limitations:
- Handoff mapping is app-layer only (`app/src/main/java/com/handmeasure/sample/tryon/model/TryOnDemoHandoff.kt`), intentionally separate from library public APIs.
- Handoff is demo-host level only (no persistent cross-module session object).
- Only measurement values needed by TryOn (`diameter`, `finger width`, `confidence`) are transferred.
