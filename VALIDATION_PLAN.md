# HandMeasure V1 Validation Plan

## Scope
- Validate guided multi-angle flow stability and deterministic result delivery.
- Validate real-device accuracy against physical ring-size ground truth.
- Validate confidence/warning calibration and replay reproducibility.

## Functional stability validation
Run on at least 3 device tiers (if available):
- Low tier (entry CPU/GPU, Android 10+)
- Mid tier (mainstream, Android 12+)
- High tier (flagship, Android 13+)

For each device:
- Run `>= 30` completed sessions.
- Record:
  - crash count
  - ANR count
  - camera bind failures
  - session completion rate
  - average session duration
  - best-effort-only session ratio (`BEST_EFFORT_ESTIMATE`)

Suggested capture matrix:
- Lighting: bright indoor / dim indoor / mixed shadow
- Background: plain / textured cluttered
- Card condition: clean / worn corners / scratched

## Accuracy validation protocol
Primary ground truth:
- Jeweler ring mandrel or calibrated ring sizer.

Optional secondary ground truth:
- Finger circumference or diameter near ring zone measured by caliper/ring tool.

For each subject/session store:
- subject/session id
- device model + Android version
- lighting condition
- background condition
- card condition
- target finger
- ground-truth ring size
- ground-truth diameter/circumference (if available)
- predicted width/thickness/circumference/diameter
- predicted ring size
- confidence score
- warnings
- absolute diameter error (mm)
- exact ring-size hit
- within-one-size hit

## Proposed acceptance targets
- session completion rate `>= 95%`
- crash-free rate `>= 99%`
- median absolute diameter error `<= 0.40 mm`
- P90 absolute diameter error `<= 0.80 mm`
- exact ring-size hit rate `>= 70%`
- within-one-size hit rate `>= 90%`

If targets fail:
- report actual metrics transparently
- attach top warning/penalty patterns
- identify whether failure is card detection, pose mismatch, blur/motion, or fusion disagreement.

## Replay validation workflow
Input folder format:
- `front_palm.jpg|png`
- `left_oblique.jpg|png`
- `right_oblique.jpg|png`
- `up_tilt.jpg|png`
- `down_tilt.jpg|png`
- optional `ground_truth.json`:
  - `{ "diameterMm": 18.2, "ringSizeLabel": "US 8.5" }`

Output report:
- JSON per replay run:
  - predicted width/thickness/circumference/diameter
  - predicted ring size
  - confidence
  - warnings
  - diameter error if ground truth exists

Run replay using instrumented runner:
- `:HandMeasure:connectedDebugAndroidTest` class `MeasurementReplayRunnerInstrumentedTest`
- pass `replayInputDir` instrumentation arg when needed.

## Logging and diagnostics
Enable in `HandMeasureConfig`:
- `debugOverlayEnabled=true` for visual overlay
- `debugExportEnabled=true` for session JSON + annotated best-frame images

Each completed session should include:
- per-step quality subscores and penalty reasons
- per-step scale + width sample stats
- fused residuals + final confidence + warnings
