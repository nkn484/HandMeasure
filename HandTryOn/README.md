# HandTryOn module

`HandTryOn` là module try-on nhẫn tách riêng khỏi `HandMeasure`, tập trung demo realtime 2D/2.5D ổn định trên điện thoại.

## Mục tiêu

- Không phụ thuộc cứng vào measurement.
- Chạy được với 3 mode: `Measured`, `LandmarkOnly`, `Manual`.
- Ưu tiên pipeline nhẹ, dễ fallback và dễ validate.

## Package chính

- `com.handtryon.domain`: model/contract (`RingAssetSource`, `TryOnSession`, `FingerAnchor`, ...)
- `com.handtryon.core`: provider abstraction + resolver (`HandPoseProvider`, `FingerAnchorProvider`, `OptionalMeasurementProvider`, `TryOnSessionResolver`)
- `com.handtryon.render`: renderer + mapping (`StableRingOverlayRenderer`, `PreviewCoordinateMapper`)
- `com.handtryon.realtime`: analyzer realtime (`TryOnRealtimeAnalyzer`, `RgbaFrameBitmapConverter`)
- `com.handtryon.validation`: validator + runtime metrics
- `com.handtryon.data`: asset loader/repository abstraction

## Dependency nguyên tắc

- `HandTryOn` không import `HandMeasureContract`.
- Measurement được đưa vào qua `MeasurementSnapshot` và `OptionalMeasurementProvider`.
- App host tự map dữ liệu landmark/measurement từ nguồn bất kỳ sang model của `HandTryOn`.
