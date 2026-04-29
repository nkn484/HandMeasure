# Try-on replay CI and device farm

GitHub Actions runs unit/build validation only. It must not require a connected Android device.

Local build commands:

```bash
./gradlew --no-daemon :handtryon-core:test :HandTryOn:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

Local connected replay:

```bash
./gradlew --no-daemon :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.handmeasure.sample.tryon.validation.TryOnVideoReplayInstrumentedTest
```

Report artifacts on device:

```text
/sdcard/Android/data/com.handmeasure.sample/files/tryon_replay/
```

Pull reports:

```bash
adb pull /sdcard/Android/data/com.handmeasure.sample/files/tryon_replay validation/tryon/reports/android/latest
```

Firebase Test Lab preparation:

```bash
./gradlew --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest
```

Copy/paste run command:

```bash
gcloud firebase test android run \
  --type instrumentation \
  --app app/build/outputs/apk/debug/app-debug.apk \
  --test app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  --test-targets class com.handmeasure.sample.tryon.validation.TryOnVideoReplayInstrumentedTest \
  --device model=oriole,version=34,locale=en,orientation=portrait \
  --device model=redfin,version=33,locale=en,orientation=portrait \
  --directories-to-pull /sdcard/Android/data/com.handmeasure.sample/files/tryon_replay
```

Suggested matrix:

- One recent Pixel on Android 14 or newer.
- One Android 13 mid-range device.
- One lower-memory Android 12 or 13 device when available.
- Portrait orientation first; landscape is a separate fixture track.

Upload artifact paths:

- `tryon_replay/*-android-report.json`
- `tryon_replay/*-telemetry.jsonl`
- `tryon_replay/*-screenshots/*.png`
- `tryon_replay/*-screenshots/*-diff.png`
