# Ring try-on demo (stabilized)

## Module layout

- `:HandMeasure`: đo tay/landmark pipeline hiện có
- `:HandTryOn`: core try-on mới (domain/core/render/realtime/validation)
- `:app`: host demo screen + permission + wiring provider

## Asset đang dùng

- Overlay chính: `app/src/main/assets/tryon/ring_overlay_v2.png`
- Metadata normalize: `app/src/main/assets/tryon/normalization_report_v2.json`
- Source được chọn: `app/src/main/assets/tryon/ring_source_selected.webp`

## Demo flow

1. Mở app sample, vào `MainActivity` (đã gắn `TryOnDemoScreen`).
2. Bấm **Thử detect tay**:
   - measurement usable + landmark usable -> `Measured` (`Fit theo đo tay`)
   - measurement fail + landmark usable -> `LandmarkOnly` (`Preview theo landmark`)
   - landmark fail -> `Manual` (`Tự căn chỉnh`)
3. Bấm **Manual adjust** để drag/scale/rotate.
4. Bấm **Export/capture** để xuất bitmap vào `files/tryon_exports`.

## Runtime/validation notes

- Realtime metrics hiển thị ngay trên demo (`detect ms`, `update Hz`, `memΔ`).
- Placement validator kiểm tra width ratio / anchor distance / rotation jump.
- Resolver có `lastGoodAnchor` để giảm giật khi landmark drop ngắn.

## Cách tự test nhanh trên điện thoại

1. Build và cài app debug.
2. Test trong ánh sáng trong nhà + ngoài trời nhẹ:
   - giữ tay ổn định 3-5 giây
   - xoay cổ tay nhẹ để kiểm tra jitter
   - che landmark tạm thời để kiểm tra fallback/manual
3. Xác nhận:
   - mode fallback đúng
   - ring không nhảy mạnh giữa frame
   - export ảnh có ring + shadow đúng vị trí
