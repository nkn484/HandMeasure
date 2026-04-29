# Try-on telemetry schema

Runtime telemetry is disabled by default. Tests and debug/dev flows can enable
`TryOnTelemetryJsonLinesExporter` and write one `TryOnTelemetryFrame` per line.

JSON Lines fields:

- `timestampMs`: frame timestamp in milliseconds.
- `frameIndex`: replay or camera frame index.
- `rendererMode`: `ARCoreCamera3D`, `CameraRelative3D`, or `Legacy2DOverlay`.
- `trackingState`: public tracking state name.
- `updateAction`: public update action name.
- `qualityScore`: frame quality from 0 to 1.
- `rawTransform`: optional raw center, scale, and rotation before smoothing.
- `smoothedTransform`: optional final center, scale, and rotation consumed by renderer.
- `renderStateUpdateHz`: optional render-state update frequency.
- `detectorLatencyMs`: optional detector latency for the frame.
- `nodeRecreateCount`: renderer node recreation count.
- `rendererErrorStage`: stage name when renderer/replay cannot update.
- `rendererErrorMessage`: human-readable failure reason.
- `approxMemoryDeltaKb`: optional memory delta sampled by runtime.
- `warnings`: stable string warnings for QA dashboards.

Replay output:

```text
<report-dir>/<fixture-name>-telemetry.jsonl
```

Production guidance:

- Keep exporter disabled unless a debug/dev flag or test API explicitly enables it.
- Prefer JSONL for frame-level telemetry and aggregate it offline into dashboards.
- Treat warning strings as compatibility-sensitive once consumed by CI or dashboards.
