package com.handtryon.validation

import com.google.common.truth.Truth.assertThat
import com.handtryon.domain.GlbAssetSummary
import com.handtryon.domain.GlbBoundsMm
import com.handtryon.domain.GlbMaterialMetadata
import com.handtryon.domain.GlbScaleMetadata
import org.junit.Test

class GlbAssetValidationPolicyTest {
    private val policy = GlbAssetValidationPolicy(maxFileSizeBytes = 5_000_000L)

    @Test
    fun validate_acceptsReasonableAsset() {
        val report = policy.validate(validSummary())

        assertThat(report.hasErrors).isFalse()
        assertThat(report.status).isAnyOf(AssetValidationSeverity.Info, AssetValidationSeverity.Warning)
    }

    @Test
    fun validate_failsMissingMaterial() {
        val report =
            policy.validate(
                validSummary().copy(
                    materialCount = 0,
                    materials = emptyList(),
                ),
            )

        assertThat(report.hasErrors).isTrue()
        assertThat(report.issues.map { it.code }).contains("glb.material.missing")
    }

    @Test
    fun validate_failsOutOfRangeBounds() {
        val report =
            policy.validate(
                validSummary().copy(
                    estimatedBoundsMm = GlbBoundsMm(220f, 150f, 110f),
                ),
            )

        assertThat(report.hasErrors).isTrue()
        assertThat(report.issues.map { it.code }).contains("glb.bounds.out_of_range")
    }

    @Test
    fun validate_failsUnsupportedRequiredExtension() {
        val report =
            policy.validate(
                validSummary().copy(
                    notes = listOf("required_extension:KHR_draco_mesh_compression"),
                ),
            )

        assertThat(report.hasErrors).isTrue()
        assertThat(report.issues.map { it.code }).contains("glb.extension.unsupported")
    }

    private fun validSummary(): GlbAssetSummary =
        GlbAssetSummary(
            modelAssetPath = "tryon/rings/ring-001.glb",
            glbVersion = 2,
            gltfVersion = "2.0",
            generator = "unit-test",
            meshCount = 1,
            materialCount = 1,
            nodeCount = 1,
            estimatedBoundsMm = GlbBoundsMm(22f, 22f, 4.5f),
            scale = GlbScaleMetadata(units = "meters", unitsToMeters = 1f),
            materials = listOf(GlbMaterialMetadata(name = "gold")),
            notes = emptyList(),
            fileSizeBytes = 850_000L,
        )
}
