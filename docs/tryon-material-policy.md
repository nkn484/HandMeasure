# GLB material render policy

`GlbMaterialRenderPolicy` converts parsed GLB material metadata into a renderer-facing decision.
It does not attempt photorealistic rendering; it makes support and fallback explicit.

Inputs:

- `GlbAssetSummary.materials`
- PBR base color, metallic factor, roughness factor
- `alphaMode`
- `doubleSided`

Output:

- `supported`
- `materialProfile`: `yellow_gold_polished`, `silver_polished`, `unknown_metal`,
  `transparent_unsupported`, or `unsupported_unknown_profile`
- clamped `baseColor`, `metallicFactor`, `roughnessFactor`
- `alphaMode`
- `warnings`
- `fallbackReasons`

Policy rules:

- Missing material is not a silent success. It returns `supported=false` and
  `missing_material`.
- Metallic and roughness outside `[0, 1]` are clamped and reported.
- Non-opaque alpha modes are unsupported for now because try-on depth/occlusion is not ready for
  transparent rings.
- `doubleSided` is allowed but warned because it can hide bad normals or cause unexpected lighting.
- Unknown opaque metal remains renderable as `unknown_metal`; non-metal surfaces are unsupported.

Asset reports should surface `material_warning_*` notes from `RingAssetRepository` so QA can catch
missing or invalid material metadata before runtime.
