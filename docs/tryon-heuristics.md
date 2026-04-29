# Try-on center and rotation heuristics

`RingFingerPoseSolverConfig` intentionally separates runtime safety gates from heuristic tuning.

Calibrated runtime gates:

- Minimum axis length and finger width prevent tiny detections from producing visible rings.
- Minimum confidence rejects low-quality hand detections.
- Confidence normalizers down-rank very short axes and extreme roll.

Heuristic tuning:

- Center policy buckets choose vertical, mid-palm, upper-palm, or oblique placement.
- `upper_palm_natural` intentionally sits closer to MCP than PIP so the band appears at a
  wearable ring position instead of drifting toward the finger joint.
- Lateral offsets are visual alignment heuristics, not calibrated anatomy.
- Oblique rotation buckets compensate for fixture-observed camera-relative ring roll.
- Curled, hidden distal segment, and wrist-pointing gates are geometry heuristics.

Replay diagnostics now include:

- `centerPolicy`
- `rawRotationDegrees`
- `rotationCorrectionBucket`
- `rotationCorrectionDegrees`
- `finalRotationDegrees`
- `rejectReason`

Limitations:

- The current three-video set covers useful stress cases but is not a calibration dataset.
- Threshold changes should be justified by broader fixture coverage, not by improving one fixture
  while regressing left/right, near/far, fist, or hidden-finger cases.
- Future calibration should report metrics by fixture bucket from `fixture-manifest.json`.
