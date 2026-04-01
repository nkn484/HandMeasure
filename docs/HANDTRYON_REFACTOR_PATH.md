# HandTryOn Refactor Path (Preparation)

## Scope of this document

This is a concrete preparation map for the next HandTryOn refactor phase so HandTryOn follows the same architecture direction as HandMeasure:

- Android/public compatibility layer
- internal/headless engine-facing layer
- portable/core orchestration layer

This document does **not** claim that the split is already complete in source.

## Current structure (source truth)

`HandTryOn` is currently one Android module with these package areas:

- `com.handtryon.domain`
- `com.handtryon.core`
- `com.handtryon.render`
- `com.handtryon.realtime`
- `com.handtryon.validation`
- `com.handtryon.data`

## Target layering for next phase

### 1) Android/public compatibility layer (`:HandTryOn`)

Owns Android-specific integration concerns:

- CameraX `ImageAnalysis` and `ImageProxy` analyzer path (`TryOnRealtimeAnalyzer`)
- frame conversion (`RgbaFrameBitmapConverter`)
- bitmap rendering integration/output (`TryOnRenderResult` currently holds `Bitmap`)
- any Activity/Compose/public Android entry surface

### 2) Internal/headless engine-facing layer (`:HandTryOn`, first extraction step)

Owns session orchestration and API boundary for try-on invocation:

- `TryOnEngine` facade (internal at first)
- internal request/result/session models
- adapter wiring from Android runtime signals to engine inputs
- compatibility adapters to keep current Android flow stable

### 3) Future portable core layer (`:handtryon-core`, later phase)

Candidate pure logic to move out once contracts are stabilized:

- mode resolution + fallback policy from `TryOnSessionResolver`
- temporal smoothing policy from `TemporalPlacementSmoother`
- anchor/placement math with Android-free models
- placement validation heuristics currently in `validation` package where Android is not required

## Existing classes that are strongest candidates for future `:handtryon-core`

- `com.handtryon.core.TryOnSessionResolver`
- `com.handtryon.core.TemporalPlacementSmoother`
- `com.handtryon.core.DefaultFingerAnchorProvider` (after model boundary cleanup)
- `com.handtryon.validation.PlacementValidator` (if kept Android-free)
- domain models in `com.handtryon.domain` that do not require `Bitmap`

## Models that need boundary cleanup before extraction

Current mixed model to isolate from core contracts:

- `TryOnRenderResult` includes `android.graphics.Bitmap`

Next-phase direction:

- keep Android render output models in compatibility layer
- introduce engine-facing render/session result models without `Bitmap`
- map engine-facing results to Android render outputs in adapter layer

## Recommended next phase (implementation order)

1. Define internal try-on engine request/result/session models (Android-free).
2. Add internal `TryOnEngine` facade using those models.
3. Adapt current Android realtime/render path to call the facade through mappers/adapters.
4. Move pure resolver/smoothing/validation policy logic behind core-ready contracts.
5. Extract first `:handtryon-core` module with minimal, verified pure logic.
