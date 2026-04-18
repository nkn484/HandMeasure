# HandTryOn Refactor Path (Current Status)

## What is now implemented

### 1) Internal/headless engine-facing layer in `:HandTryOn`

- Added internal `TryOnEngine` (`com.handtryon.engine.TryOnEngine`).
- Kept internal engine request model (`com.handtryon.engine.model.TryOnEngineRequest`).
- Added mapper/adaptation layer (`com.handtryon.engine.compat.TryOnEngineDomainMapper`).
- Added shared runtime-state mapper (`com.handtryon.engine.compat.TryOnRuntimeStateMapper`) for domain/core tracking-action conversions.
- Existing public compatibility entry `TryOnSessionResolver` now delegates to `TryOnEngine`.

### 2) First portable extraction in `:handtryon-core`

- Added new module `:handtryon-core`.
- Extracted Android-free models and policy logic:
  - `TryOnSessionResolverPolicy`
  - `TryOnTrackingStateMachinePolicy`
  - `TryOnQualityPolicy`
  - `TryOnOcclusionPolicy`
  - `TemporalPlacementSmootherPolicy`
  - `PlacementValidationPolicy`
  - `DefaultFingerAnchorFactory`
  - `TryOnEngineResult`, `TryOnEngineSessionState`, `TryOnEngineRenderState`
  - `TryOnSession.toEngineResult(...)` engine-state shaping helper

### 3) Compatibility adapters in `:HandTryOn`

- `DefaultFingerAnchorProvider`, `TemporalPlacementSmoother`, and `PlacementValidator` now delegate to `:handtryon-core` logic through mapper-based adapters.
- Runtime state conversion helpers are normalized through `TryOnRuntimeStateMapper` instead of duplicated inline mappings.

### 4) Engine/result boundary cleanup in `:HandTryOn`

- `TryOnEngine` now returns Android-free engine-facing state:
  - `TryOnEngineSessionState`
  - `TryOnEngineRenderState`
  - packaged as `TryOnEngineResult`
- `TryOnSessionResolver` now exposes additive compatibility output via `resolveState(...)` returning:
  - domain session (`TryOnSession`)
  - render compatibility state (`TryOnRenderState`)
  - wrapped in `TryOnSessionResolution`
- `TryOnEngineDomainMapper` is the explicit boundary mapper:
  - engine state (`TryOnEngineResult`) -> compatibility session (`TryOnSession`)
  - engine render state (`TryOnEngineRenderState`) -> compatibility render input (`TryOnRenderState`)
- `StableRingOverlayRenderer` accepts `TryOnRenderState` for Android render output generation while keeping `TryOnRenderResult` as the `Bitmap` compatibility output.

## Current boundaries

- **Android/public compatibility (`:HandTryOn`)**
  - realtime CameraX analyzer
  - bitmap-based overlay renderer
  - Compose overlay integration
  - render output model carrying `Bitmap` (`TryOnRenderResult`)
- **Engine-facing internal (`:HandTryOn`)**
  - engine facade + request model + compatibility mapper
  - compatibility wrappers for existing API usage
- **Portable core (`:handtryon-core`)**
  - session/mode/fallback policy
  - tracking state machine + quality scoring/gating
  - smoothing/validation policy
  - anchor extraction policy
  - engine-facing result/session/render contracts and shaping helper
  - Android-free session/placement models

## What intentionally remains Android-only

- `TryOnRealtimeAnalyzer` / `RgbaFrameBitmapConverter` (CameraX + `ImageProxy`)
- `StableRingOverlayRenderer` and preview/export rendering (`Bitmap`, `Canvas`)
- `TryOnRenderResult` (contains `Bitmap`)
- Compose overlay UI (`TryOnOverlay`)

## Phase 1 stabilization status (current)

- Build graph is currently aligned:
  - `settings.gradle.kts` includes `:handtryon-core` and `:HandTryOn`.
  - `:HandTryOn` consumes `:handtryon-core` via Gradle project dependency.
- CI gate now validates TryOn modules explicitly:
  - `:handtryon-core:test`
  - `:HandTryOn:compileDebugKotlin`
  - `:HandTryOn:testDebugUnitTest`
- Baseline contract tests are in place for:
  - `TryOnEngine` orchestration boundary
  - `TryOnSessionResolverPolicy`
  - `TryOnTrackingStateMachinePolicy`
  - `TryOnQualityPolicy`
  - `TemporalPlacementSmootherPolicy`
  - `PlacementValidationPolicy`
  - mapper conversions between engine-facing/core models and Android compatibility models

## Runtime stability status (current phase)

- `TryOnSessionResolverPolicy` now executes runtime stability control in this order:
  - mode/fallback resolution (`Measured` / `LandmarkOnly` / `Manual`)
  - placement validation and jump signals
  - tracking state transition (`Searching`, `Candidate`, `Locked`, `Recovering`)
  - quality score + gating action
  - adaptive smoothing with state-aware context
- Quality gate actions now directly affect placement update behavior:
  - `Update`
  - `FreezeScaleRotation`
  - `HoldLastPlacement`
  - `Recover`
  - `Hide`
- `TryOnEngineRenderState` and compatibility `TryOnRenderState` now carry tracking/quality/update metadata (`trackingState`, `qualityScore`, `updateAction`, `hints`, `shouldRenderOverlay`) while keeping `Bitmap` output Android-side.
- `StableRingOverlayRenderer` remains Android-only and consumes compatibility render-state metadata without moving renderer concerns into `:handtryon-core`.

## Phase 2 normalization status (current)

- Normalized compatibility conversion responsibilities:
  - shared runtime state mapper extracted to `TryOnRuntimeStateMapper`
  - touched adapters now use one conversion source for tracking/update enums
- Kept normalization scoped to touched HandTryOn runtime-stability paths only:
  - no broad package sweep
  - no renderer stack replacement
  - no UI/UX redesign.

## Phase 3 lightweight occlusion status (current)

- Added Android-free occlusion decision policy in `:handtryon-core`:
  - `TryOnOcclusionPolicy` -> `TryOnOcclusionDecision`
  - decision inputs: mode, tracking state, update action, quality score
- Added Android-side mask implementation in `:HandTryOn`:
  - `LightweightRingOcclusionMaskProvider`
  - applies a thin DST_OUT occlusion band aligned to ring orientation
  - degrades by reducing/disabling mask when quality/state is weak
- `StableRingOverlayRenderer` now wires runtime state + quality into occlusion context while keeping `TryOnRenderResult` bitmap output unchanged.

## What is still future work after Phase 1

- redesigning `TryOnEngine` internals beyond the current boundary
- deep renderer refactor or rendering technology replacement
- broad package cleanup and module migration beyond current extraction
- KMP migration of try-on components
