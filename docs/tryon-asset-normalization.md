# Try-on asset normalization v2

Nguồn input hiện tại (ngoài repo):

- `../Thử nhẫn/assets/*.webp`

Script normalize:

- `tools/tryon/normalize_ring_asset.py`

## Pipeline v2

1. Decode `.webp` bằng OpenCV (hỗ trợ đường dẫn Unicode).
2. Tạo foreground mask đa tín hiệu:
   - LAB color-distance theo border background model
   - saturation/value gating
   - edge refinement
3. Connected-component filtering để loại text/logo/noise ngoài vùng sản phẩm.
4. GrabCut refinement để tách nền sạch hơn.
5. Morphological cleanup + trim bounds.
6. Center sản phẩm vào canvas chuẩn (`--canvas-size`, mặc định `1024`).
7. Tính metadata phục vụ renderer:
   - `sourceFile`
   - `contentBounds`
   - `visualCenter`
   - `alphaBounds`
   - `recommendedWidthRatio`
   - `rotationBiasDeg`
   - `assetQualityScore`
   - `backgroundRemovalConfidence`
   - `notes`

## Cách chạy

```powershell
python tools/tryon/normalize_ring_asset.py --source-dir "..\Thử nhẫn\assets" --output-dir "app\src\main\assets\tryon"
```

Tuỳ chọn debug mask:

```powershell
python tools/tryon/normalize_ring_asset.py --source-dir "..\Thử nhẫn\assets" --output-dir "app\src\main\assets\tryon" --write-debug
```

Tuỳ chọn manual correction (hybrid auto + override):

```powershell
python tools/tryon/normalize_ring_asset.py --source-dir "..\Thử nhẫn\assets" --output-dir "app\src\main\assets\tryon" --manual-config "docs\tryon-normalize-manual.json"
```

## Output v2

- `app/src/main/assets/tryon/ring_overlay_v2.png`
- `app/src/main/assets/tryon/normalization_report_v2.json`
- `app/src/main/assets/tryon/ring_source_selected.webp`

## Ghi chú chất lượng

- V2 giảm phụ thuộc threshold nền trắng đơn giản như bản cũ.
- Nếu ảnh marketing quá nhiễu hoặc phản quang mạnh, dùng `--manual-config` để override metadata tối thiểu thay vì sửa code.
