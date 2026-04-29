# Try-on fixture manifest schema

`fixture-manifest.json` is optional metadata for replay fixtures. Replay must keep running when
the manifest is absent; when present, reports should include the matching `fixtureMetadata`.

Top-level shape:

```json
{
  "schemaVersion": 1,
  "fixtures": [
    {
      "fixtureId": "video-fixture-2026-04-29",
      "mediaFile": "video_fixture.mp4",
      "annotationFile": "video-fixture-2026-04-29.json",
      "deviceModel": "Xiaomi test phone",
      "androidVersion": "unknown",
      "lightingBucket": "indoor_mixed",
      "backgroundBucket": "home_table",
      "handSkinBucket": "anonymous_bucket_unknown",
      "poseBucket": "front_near_open_hand",
      "expectedDifficulty": "medium",
      "tags": ["baseline", "visible_ring_finger"]
    }
  ]
}
```

Required fields per fixture: `fixtureId`, `mediaFile`, `annotationFile`, `expectedDifficulty`,
and `tags`.

Recommended buckets:

- `lightingBucket`: `indoor_bright`, `indoor_mixed`, `low_light`, `backlit`, `outdoor`.
- `backgroundBucket`: anonymous scene category only, no personal location data.
- `handSkinBucket`: anonymous coarse bucket or `anonymous_bucket_unknown`.
- `poseBucket`: include orientation and distance, for example `left_oblique_far_open_hand`.
- `expectedDifficulty`: `easy`, `medium`, `hard`, or `stress`.
