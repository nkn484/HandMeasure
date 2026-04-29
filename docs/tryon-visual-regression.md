# Try-on visual regression

Visual regression is intentionally lightweight and fixture-based. The Android replay always exports
actual overlay PNGs into the report screenshot folder. If a matching golden exists, it compares the
actual PNG against that golden and writes a diff PNG on failure.

Golden path convention inside Android test assets:

```text
goldens/<fixture-name>/frame_<frameIndex>_<augmentationId>.png
```

Example:

```text
goldens/video-fixture2-2026-04-29/frame_000021_rot_p5.png
```

Current behavior:

- Missing golden: report field `visualDiff.goldenMissing=true`; replay does not fail.
- Present golden: replay computes RGB mean absolute error, RMS error, and luma mean absolute error.
- Diff failure: replay writes `<actual-name>-diff.png` next to the actual PNG and sets
  `visualDiff.pass=false`.

To create or update goldens:

1. Run local Android replay and pull `tryon_replay/*-screenshots`.
2. Review overlays manually, especially expected/predicted alignment and hidden-finger frames.
3. Copy approved PNGs into `validation/tryon/reference-annotations/goldens/<fixture-name>/`.
4. Re-run replay; only frames with goldens become strict visual regression checks.

Do not bulk-promote all frames. Start with representative frames covering near/far, left/right
oblique, vertical fingers, fist, and hidden ring finger.
