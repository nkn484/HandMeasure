# HandTryOn Module

`HandTryOn` provides real-time ring overlay for Android preview and export flows.

## Goals

- Keep try-on independent from `HandMeasureContract`.
- Support three runtime modes: `Measured`, `LandmarkOnly`, `Manual`.
- Keep realtime behavior lightweight with deterministic fallback paths.

## Current package areas (`:HandTryOn`)

- `com.handtryon.domain`
- `com.handtryon.core`
- `com.handtryon.engine`
- `com.handtryon.render`
- `com.handtryon.realtime`
- `com.handtryon.validation`
- `com.handtryon.data`
- `com.handtryon.ui`

## Architecture status (source truth)

- **Android/public compatibility layer (`:HandTryOn`)**
  - CameraX analyzer/runtime (`TryOnRealtimeAnalyzer`, `RgbaFrameBitmapConverter`)
  - bitmap rendering output (`StableRingOverlayRenderer`, `TryOnRenderResult`)
  - overlay/UI integration (`TryOnOverlay`)
- **Internal/headless engine-facing layer (`:HandTryOn`)**
  - `TryOnEngine` orchestration entry
  - internal engine request model (`com.handtryon.engine.model.TryOnEngineRequest`)
  - mapper + compatibility adapter (`TryOnEngineDomainMapper`, `TryOnRuntimeStateMapper`, `TryOnSessionResolver`)
  - compatibility session+render-state output (`TryOnSessionResolution`)
- **Portable policy layer (`:handtryon-core`)**
  - `TryOnSessionResolverPolicy`
  - `TryOnTrackingStateMachinePolicy`
  - `TryOnQualityPolicy`
  - `TryOnOcclusionPolicy`
  - `TemporalPlacementSmootherPolicy`
  - `PlacementValidationPolicy`
  - `DefaultFingerAnchorFactory`
  - engine-facing session/render result contracts (`TryOnEngineResult`, `TryOnEngineSessionState`, `TryOnEngineRenderState`)
  - engine-state shaping helper (`TryOnSession.toEngineResult(...)`)
  - Android-free try-on session/placement models

## Boundary notes

- `TryOnEngine` now primarily returns Android-free engine state (`TryOnEngineResult` with session + render state).
- engine-facing result/session/render contracts live in `:handtryon-core` and are consumed by `:HandTryOn`.
- runtime tracking state, quality score, and update action are computed in `:handtryon-core` and carried through engine-facing state.
- `TryOnEngineDomainMapper` explicitly maps engine-facing render/session state to Android compatibility models (`TryOnRenderState`, `TryOnSession`).
- `StableRingOverlayRenderer` explicitly maps Android compatibility render input (`TryOnRenderState`) to bitmap output (`TryOnRenderResult`).
- `TryOnRenderState` is Android/public compatibility render input without `Bitmap`.
- `TryOnRenderResult` remains Android-side because it carries `Bitmap`.
- `:handtryon-core` contains no `Bitmap`, `ImageProxy`, CameraX, Compose, or Android UI classes.

## Phase 1 stabilization gate

- Build graph wiring:
  - `settings.gradle.kts` includes both `:HandTryOn` and `:handtryon-core`.
  - `:HandTryOn` depends on `project(":handtryon-core")`.
- CI verifies:
  - `:handtryon-core:test`
  - `:HandTryOn:compileDebugKotlin`
  - `:HandTryOn:testDebugUnitTest`
- Baseline boundary tests protect:
  - engine orchestration (`TryOnEngineTest`)
  - session resolver policy (`TryOnSessionResolverPolicyTest`)
  - tracking state transitions (`TryOnTrackingStateMachinePolicyTest`)
  - quality scoring/gating decisions (`TryOnQualityPolicyTest`)
  - temporal smoothing policy (`TemporalPlacementSmootherPolicyTest`)
  - placement validation policy (`PlacementValidationPolicyTest`)
  - engine/domain mapper contracts (`TryOnEngineDomainMapperTest`)

## Runtime stability (current)

- Tracking lifecycle is explicit and stateful (`Searching` -> `Candidate` -> `Locked` -> `Recovering`) through `TryOnTrackingStateMachinePolicy`.
- Quality gating is explicit through `TryOnQualityPolicy` and drives per-frame update action:
  - `Update`
  - `FreezeScaleRotation`
  - `HoldLastPlacement`
  - `Recover`
  - `Hide`
- `TryOnSessionResolverPolicy` integrates:
  - state transition
  - quality scoring + gating
  - adaptive smoothing context
  - existing fallback semantics (`Measured` / `LandmarkOnly` / `Manual`) without changing mode meaning.
- Temporal smoothing is now adaptive (movement/quality/state-aware) and separated by center, rotation, and scale behavior.

## Normalization after stability work

- Runtime state enum mapping was normalized into `TryOnRuntimeStateMapper` to avoid duplicated conversion logic across compatibility adapters.
- `TemporalPlacementSmoother` now uses the shared runtime state mapper instead of embedding local conversion helpers.
- New boundary test `TryOnRuntimeStateMapperTest` protects this mapping contract.

## Lightweight occlusion heuristic

- `TryOnOcclusionPolicy` (Android-free, in `:handtryon-core`) computes whether occlusion should apply and with what strength.
- `LightweightRingOcclusionMaskProvider` (Android-side, in `:HandTryOn`) applies a narrow DST_OUT mask band over the ring centerline to hide the finger-overlapped segment.
- `StableRingOverlayRenderer` now:
  - enables the lightweight mask provider by default
  - evaluates occlusion using runtime quality/tracking/update state
  - degrades gracefully by reducing or disabling the mask when confidence/state is poor.

## Still future work (not implemented in Phase 1)

- deeper `TryOnEngine` redesign beyond current orchestration boundary
- broad renderer internals refactor or rendering stack replacement
- UI/UX redesign and broad package cleanup
- KMP migration
