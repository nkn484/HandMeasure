# HandTryOn Refactor Path (Current Status)

## What is now implemented

### 1) Internal/headless engine-facing layer in `:HandTryOn`

- Added internal `TryOnEngine` (`com.handtryon.engine.TryOnEngine`).
- Added internal engine request/result models (`com.handtryon.engine.model`).
- Added mapper/adaptation layer (`com.handtryon.engine.compat.TryOnEngineDomainMapper`).
- Existing public compatibility entry `TryOnSessionResolver` now delegates to `TryOnEngine`.

### 2) First portable extraction in `:handtryon-core`

- Added new module `:handtryon-core`.
- Extracted Android-free models and policy logic:
  - `TryOnSessionResolverPolicy`
  - `TemporalPlacementSmootherPolicy`
  - `PlacementValidationPolicy`
  - `DefaultFingerAnchorFactory`

### 3) Compatibility adapters in `:HandTryOn`

- `DefaultFingerAnchorProvider`, `TemporalPlacementSmoother`, and `PlacementValidator` now delegate to `:handtryon-core` logic through mapper-based adapters.

## Current boundaries

- **Android/public compatibility (`:HandTryOn`)**
  - realtime CameraX analyzer
  - bitmap-based overlay renderer
  - Compose overlay integration
  - render output model carrying `Bitmap` (`TryOnRenderResult`)
- **Engine-facing internal (`:HandTryOn`)**
  - engine facade + request/result + mapper
  - compatibility wrappers for existing API usage
- **Portable core (`:handtryon-core`)**
  - session/mode/fallback policy
  - smoothing/validation policy
  - anchor extraction policy
  - Android-free session/placement models

## What intentionally remains Android-only

- `TryOnRealtimeAnalyzer` / `RgbaFrameBitmapConverter` (CameraX + `ImageProxy`)
- `StableRingOverlayRenderer` and preview/export rendering (`Bitmap`, `Canvas`)
- `TryOnRenderResult` (contains `Bitmap`)
- Compose overlay UI (`TryOnOverlay`)

## Next phase recommendation

1. Move renderer-adjacent non-Android math helpers behind engine-facing contracts where beneficial.
2. Introduce engine-facing render/session result models to reduce direct domain-model coupling.
3. Keep `TryOnRenderResult` as Android compatibility output while expanding core portability.
