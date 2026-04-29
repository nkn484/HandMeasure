package com.handtryon.validation

import com.handtryon.domain.GlbMaterialMetadata

data class GlbMaterialPolicyDecision(
    val supported: Boolean,
    val materialProfile: String,
    val baseColor: List<Float>,
    val metallicFactor: Float,
    val roughnessFactor: Float,
    val alphaMode: String,
    val warnings: List<String> = emptyList(),
    val fallbackReasons: List<String> = emptyList(),
)

class GlbMaterialRenderPolicy {
    fun evaluate(materials: List<GlbMaterialMetadata>): GlbMaterialPolicyDecision {
        if (materials.isEmpty()) {
            return GlbMaterialPolicyDecision(
                supported = false,
                materialProfile = "unknown_metal",
                baseColor = DEFAULT_BASE_COLOR,
                metallicFactor = 1f,
                roughnessFactor = 0.35f,
                alphaMode = "OPAQUE",
                warnings = listOf("missing_material"),
                fallbackReasons = listOf("material_metadata_missing"),
            )
        }

        val material = materials.first()
        val warnings = mutableListOf<String>()
        val fallbackReasons = mutableListOf<String>()
        val color =
            if (material.baseColorFactor.size >= 3) {
                material.baseColorFactor.take(4).let { values ->
                    if (values.size == 3) values + 1f else values
                }.map { value -> value.coerceIn(0f, 1f) }
            } else {
                warnings += "missing_base_color"
                fallbackReasons += "base_color_missing"
                DEFAULT_BASE_COLOR
            }

        val metallic = clampFactor(material.metallicFactor, defaultValue = 1f, "metallic", warnings, fallbackReasons)
        val roughness = clampFactor(material.roughnessFactor, defaultValue = 0.35f, "roughness", warnings, fallbackReasons)
        val alphaMode = material.alphaMode?.uppercase() ?: "OPAQUE"
        if (alphaMode != "OPAQUE") {
            warnings += "transparent_material_unsupported"
            fallbackReasons += "alpha_mode_$alphaMode"
        }
        if (material.doubleSided) warnings += "double_sided_material"

        val profile =
            when {
                alphaMode != "OPAQUE" -> "transparent_unsupported"
                metallic < 0.45f -> "unsupported_unknown_profile"
                color[0] > 0.65f && color[1] > 0.48f && color[2] < 0.35f -> "yellow_gold_polished"
                kotlin.math.abs(color[0] - color[1]) < 0.08f &&
                    kotlin.math.abs(color[1] - color[2]) < 0.08f &&
                    color[0] > 0.55f -> "silver_polished"
                else -> "unknown_metal"
            }

        val supported = alphaMode == "OPAQUE" && profile != "unsupported_unknown_profile"
        if (!supported && profile == "unsupported_unknown_profile") {
            fallbackReasons += "non_metal_or_unknown_surface"
        }

        return GlbMaterialPolicyDecision(
            supported = supported,
            materialProfile = profile,
            baseColor = color,
            metallicFactor = metallic,
            roughnessFactor = roughness,
            alphaMode = alphaMode,
            warnings = warnings.distinct(),
            fallbackReasons = fallbackReasons.distinct(),
        )
    }

    private fun clampFactor(
        value: Float?,
        defaultValue: Float,
        name: String,
        warnings: MutableList<String>,
        fallbackReasons: MutableList<String>,
    ): Float {
        if (value == null) {
            warnings += "missing_${name}_factor"
            fallbackReasons += "${name}_factor_missing"
            return defaultValue
        }
        val clamped = value.coerceIn(0f, 1f)
        if (clamped != value) {
            warnings += "${name}_factor_clamped"
            fallbackReasons += "${name}_factor_out_of_range"
        }
        return clamped
    }

    private companion object {
        val DEFAULT_BASE_COLOR = listOf(1f, 0.82f, 0.34f, 1f)
    }
}
