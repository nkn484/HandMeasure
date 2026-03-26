# Try-on validation checklist

## A. Asset normalize validation

- Kiểm tra `normalization_report_v2.json`:
  - `assetQualityScore >= 0.6`
  - `backgroundRemovalConfidence >= 0.7`
  - `contentBounds` và `alphaBounds` nằm gọn trong canvas
- Dùng `AssetNormalizationValidator` để check:
  - alpha coverage
  - border leak
  - metadata consistency

## B. Placement validation

- `PlacementValidator` check:
  - width ratio hợp lý
  - distance từ placement đến anchor
  - rotation jump giữa frame

## C. Runtime validation

- Theo dõi `RuntimeMetrics` trên UI:
  - `avgDetectionMs`
  - `avgUpdateIntervalMs` / Hz
  - `approxMemoryDeltaKb`

## D. Fallback validation

- Unit test đảm bảo:
  - measurement fail -> `LandmarkOnly`
  - landmark fail -> `Manual`
  - landmark drop tạm thời -> dùng `lastGoodAnchor`

## Lệnh test

```powershell
.\gradlew.bat :HandTryOn:testDebugUnitTest :app:assembleDebug
```
