package com.handtryon.validation

import com.google.common.truth.Truth.assertThat
import com.handtryon.domain.GlbMaterialMetadata
import org.junit.Test

class GlbMaterialRenderPolicyTest {
    private val policy = GlbMaterialRenderPolicy()

    @Test
    fun evaluate_acceptsCompletePbrGoldMetal() {
        val result =
            policy.evaluate(
                listOf(
                    GlbMaterialMetadata(
                        baseColorFactor = listOf(1f, 0.78f, 0.18f, 1f),
                        metallicFactor = 1f,
                        roughnessFactor = 0.24f,
                        alphaMode = "OPAQUE",
                    ),
                ),
            )

        assertThat(result.supported).isTrue()
        assertThat(result.materialProfile).isEqualTo("yellow_gold_polished")
        assertThat(result.warnings).isEmpty()
    }

    @Test
    fun evaluate_flagsMissingMaterial() {
        val result = policy.evaluate(emptyList())

        assertThat(result.supported).isFalse()
        assertThat(result.warnings).contains("missing_material")
        assertThat(result.fallbackReasons).contains("material_metadata_missing")
    }

    @Test
    fun evaluate_clampsInvalidMetallicAndRoughness() {
        val result =
            policy.evaluate(
                listOf(
                    GlbMaterialMetadata(
                        baseColorFactor = listOf(0.8f, 0.8f, 0.78f, 1f),
                        metallicFactor = 1.8f,
                        roughnessFactor = -0.2f,
                        alphaMode = "OPAQUE",
                    ),
                ),
            )

        assertThat(result.metallicFactor).isEqualTo(1f)
        assertThat(result.roughnessFactor).isEqualTo(0f)
        assertThat(result.warnings).contains("metallic_factor_clamped")
        assertThat(result.warnings).contains("roughness_factor_clamped")
    }

    @Test
    fun evaluate_rejectsTransparency() {
        val result =
            policy.evaluate(
                listOf(
                    GlbMaterialMetadata(
                        baseColorFactor = listOf(1f, 0.75f, 0.2f, 0.35f),
                        metallicFactor = 1f,
                        roughnessFactor = 0.2f,
                        alphaMode = "BLEND",
                    ),
                ),
            )

        assertThat(result.supported).isFalse()
        assertThat(result.materialProfile).isEqualTo("transparent_unsupported")
        assertThat(result.warnings).contains("transparent_material_unsupported")
    }

    @Test
    fun evaluate_warnsForDoubleSidedMaterial() {
        val result =
            policy.evaluate(
                listOf(
                    GlbMaterialMetadata(
                        baseColorFactor = listOf(0.75f, 0.75f, 0.76f, 1f),
                        metallicFactor = 1f,
                        roughnessFactor = 0.32f,
                        alphaMode = "OPAQUE",
                        doubleSided = true,
                    ),
                ),
            )

        assertThat(result.supported).isTrue()
        assertThat(result.materialProfile).isEqualTo("silver_polished")
        assertThat(result.warnings).contains("double_sided_material")
    }

    @Test
    fun evaluate_marksLowMetallicAsUnsupportedProfile() {
        val result =
            policy.evaluate(
                listOf(
                    GlbMaterialMetadata(
                        baseColorFactor = listOf(0.2f, 0.2f, 0.2f, 1f),
                        metallicFactor = 0.1f,
                        roughnessFactor = 0.5f,
                        alphaMode = "OPAQUE",
                    ),
                ),
            )

        assertThat(result.supported).isFalse()
        assertThat(result.materialProfile).isEqualTo("unsupported_unknown_profile")
        assertThat(result.fallbackReasons).contains("non_metal_or_unknown_surface")
    }
}
