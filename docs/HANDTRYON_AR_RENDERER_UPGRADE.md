# HandTryOn AR Renderer Upgrade Plan

## Goal

Replace the current 2D PNG preview/export renderer with an inline AR renderer that loads `ring_AR.glb` and keeps the ring anchored to the tracked ring finger.

The upgrade should keep `:handtryon-core` Android-free. Core policies should continue to own placement, quality, smoothing, and tracking state. Android rendering, ARCore session lifecycle, GLB loading, camera texture, and device fallback belong in `:HandTryOn` and the sample `:app`.

## Current System

- `:app` owns the demo screen and CameraX preview through `TryOnDemoScreen`.
- `:HandTryOn` owns Android runtime, realtime analyzer, Compose overlay, bitmap renderer, occlusion mask, asset loading, and compatibility adapters.
- `:handtryon-core` owns Android-free placement and tracking policies.
- `StableRingOverlayRenderer` draws a PNG overlay on an Android `Canvas`.
- `TryOnOverlay` is a Compose `Canvas` sitting above `PreviewView`.
- `RingAssetSource` already supports `modelAssetPath`, and `RingAssetLoader.loadGlbSummary(...)` can validate/inspect the local GLB.
- `ring_AR.glb` is packaged at `app/src/main/assets/tryon/ring_AR.glb`.

## GLB Findings

`ring_AR.glb` is a valid binary glTF 2.0 asset:

- GLB version: `2`
- Size: `1,733,292 bytes`
- Meshes: `2`
- Materials: `2`
- Nodes: `4`
- Estimated bounds: `20.40 x 21.19 x 1.82 mm`
- Scene extras declare meter units with source dimensions converted from millimeters by `0.001`

This scale is suitable for a ring-size-driven renderer because the model bounds are close to real ring dimensions.

## Renderer Options

### Option A: Scene Viewer Intent

Google Scene Viewer can show a remote glTF/GLB in native AR or 3D fallback, but it launches an external viewer and requires a URL-backed model. It cannot replace the current inline try-on preview because HandTryOn needs camera frames, hand landmarks, session quality, and product UI inside the app.

Use only as a fallback product detail CTA, not as the HandTryOn renderer.

### Option B: Direct ARCore + Filament

This gives maximum control over camera frame, projection matrices, anchors, GLB loading, lighting, occlusion, and export. It is also the highest implementation cost because the app must own ARCore session lifecycle, camera texture rendering, GLB entity transforms, and device fallback.

Use if SceneView blocks hand-tracking integration or export capture.

### Option C: SceneView inline AR wrapper

SceneView uses ARCore and Filament and offers Compose/View integration for GLB loading. This is the best first implementation path because the app is already Compose-first and the target is an inline AR scene rather than a separate viewer.

Use this as Phase 1 of the AR upgrade. Keep an escape hatch toward direct Filament if SceneView cannot support the hand-anchored transform/export requirements.

## Recommended Architecture

### New Android-side package

Add `com.handtryon.ar` in `:HandTryOn`.

Candidate classes:

- `ArTryOnScene`: Compose entry point replacing `TryOnOverlay` on AR-capable devices.
- `ArTryOnController`: owns AR session availability, model loading, node creation, and frame updates.
- `ArRingModelRepository`: resolves `RingAssetSource.modelAssetPath`, validates GLB summary, and loads model instances.
- `ArRingPlacementMapper`: maps `RingPlacement` plus camera/frame calibration into AR node transform.
- `ArTryOnAvailability`: checks ARCore support and reports fallback states.

### Existing classes to keep

- `TryOnSessionResolver`: keep as the placement source.
- `TryOnRealtimeAnalyzer`: keep or adapt so hand landmarks still flow into the session resolver.
- `RingAssetLoader`: keep GLB summary validation and asset metadata loading.
- `StableRingOverlayRenderer`: keep temporarily as fallback, then remove only after AR preview/export are stable.

### Boundary rule

Do not add ARCore, SceneView, Filament, Android `View`, `Bitmap`, or Compose dependencies to `:handtryon-core`.

## Migration Plan

### Phase 0: dependency and capability gate

- Add ARCore/SceneView dependencies only to `:HandTryOn` or `:app`, not `:handtryon-core`.
- Add manifest capability as optional first: `android.hardware.camera.ar`.
- Add runtime AR availability check and keep 2D fallback for unsupported devices during rollout.
- Keep `minSdk 26`.

### Phase 1: inline AR preview spike

- Create `ArTryOnScene` beside the current `PreviewView`.
- Load `tryon/ring_AR.glb` as a model node.
- Feed `TryOnSession.placement` into a model transform each frame.
- Preserve the existing hand landmark analyzer until ARCore camera integration is proven.
- Show GLB summary and AR availability diagnostics in debug/demo UI only.

### Phase 2: replace visible preview

- Replace `AndroidView(PreviewView)` + `TryOnOverlay` with the AR scene on supported devices.
- Keep existing controls and handoff behavior.
- Keep manual adjust semantics by mapping gestures to the AR transform instead of 2D placement pixels.
- Keep 2D fallback hidden behind a runtime switch while validation continues.

### Phase 3: AR-quality placement

- Move from screen-space placement to camera-aware transform:
  - Use camera intrinsics/projection when available.
  - Derive approximate depth from measured finger width, ring model bounds, and session confidence.
  - Clamp depth/scale changes through the existing quality gates.
- Add lighting estimation if supported.
- Add depth/occlusion support when device capability is stable.

### Phase 4: export and fallback cleanup

- Add AR scene capture/export.
- Remove PNG fallback from the primary path after AR export, placement stability, and device fallback are tested.
- Keep a simple error state for devices without ARCore support.

## First Implementation Tasks

1. Add `ArTryOnAvailability` with unit-testable result mapping.
2. Add `ArTryOnScene` behind a feature flag in the sample screen.
3. Add SceneView/ARCore dependencies and compile gate.
4. Load `ring_AR.glb` into the inline scene.
5. Map current `RingPlacement` into a model transform using a conservative depth estimate.
6. Verify on a physical ARCore-supported Android device.

## Phase 1 Implementation Status

Implemented:

- `:HandTryOn` now depends on `io.github.sceneview:arsceneview:2.0.4`.
- `com.handtryon.ar.ArTryOnAvailability` gates ARCore support before exposing AR preview.
- `com.handtryon.ar.ArTryOnScene` starts an inline SceneView AR session and loads `tryon/ring_AR.glb`.
- `com.handtryon.ar.ArRingPlacementMapper` maps existing screen-space `RingPlacement` to a conservative fixed-depth 3D transform.
- The sample `TryOnDemoScreen` can toggle between the existing 2D preview and the AR renderer.
- The app manifest declares `android.hardware.camera.ar` as optional so non-AR devices remain installable.

Current limitations:

- The transform is a Phase 1 screen-space-to-fixed-depth approximation, not a true camera-intrinsics/depth placement.
- Export is still the 2D bitmap renderer path and is disabled while AR preview is active.
- The ARCore CPU image is currently converted through a throttled YUV-to-JPEG-to-Bitmap path; this is correct for integration but should be optimized after device profiling.
- AR camera-image orientation/calibration still needs physical-device validation before removing the 2D fallback.

Next engineering step:

- Move the AR transform from fixed-depth screen-space mapping to camera-aware placement using ARCore camera intrinsics, measured finger width, and the GLB physical bounds.

## Phase 2 Implementation Status

Implemented:

- `ArCoreCameraFrameSampler` samples `Frame.acquireCameraImage()` from SceneView's `onSessionUpdated` callback.
- The sampler converts ARCore `YUV_420_888` CPU frames to `Bitmap` and throttles analysis to the existing realtime cadence.
- `TryOnDemoScreen` now runs MediaPipe hand landmark detection from AR camera frames on a single background executor.
- `TryOnSession` continues to update while AR preview is active, so the GLB node can follow the detected ring-finger placement instead of staying on the last CameraX placement.

## Phase 3 AR-First Migration Status

Implemented:

- AR preview is now selected automatically when ARCore is usable and a GLB model is available.
- The 2D preview path remains available only as an explicit fallback toggle.
- 2D-only manual adjustment and bitmap export controls are disabled while AR preview is active.
- CameraX and ARCore MediaPipe detection share a lock so the current `MediaPipeHandLandmarkEngine` is not called concurrently during preview-mode transitions.
- AR frame timestamps now use the same elapsed-realtime basis as the CameraX analyzer to avoid smoothing/tracking deltas when switching renderer modes.
- AR preview updates `RuntimeMetrics` from its own analyzer path, reducing stale CameraX-only diagnostics.

Remaining before deleting the 2D renderer:

- Add AR-native manual transform gestures directly to `ArTryOnScene`.
- Add AR scene capture/export.
- Validate ARCore CPU image orientation/calibration on physical devices.
- Replace the temporary YUV-to-JPEG conversion with a lower-copy analyzer path if profiling shows it is a bottleneck.

## Known Risks

- ARCore owns camera access, so the current CameraX analyzer may conflict with a full AR session.
- Hand landmark detection currently expects `Bitmap` frames from CameraX; ARCore camera image access may need a new analyzer adapter.
- Screen-space 2D placement is not enough for stable 3D scale; depth estimation must be introduced carefully.
- Export behavior changes from bitmap composition to scene capture.
- SceneView API/version churn should be isolated behind `com.handtryon.ar` so the rest of the module remains stable.

## Verification Gates

- `:handtryon-core:test`
- `:HandTryOn:testDebugUnitTest`
- `:app:compileDebugKotlin`
- Manual device test on ARCore-supported phone:
  - AR scene starts.
  - GLB appears with correct scale direction.
  - Ring follows ring-finger landmark without severe jitter.
  - Unsupported device path does not crash.
