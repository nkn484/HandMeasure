package com.handtryon.ar

import com.google.common.truth.Truth.assertThat
import com.handtryon.domain.GlbAssetSummary
import com.handtryon.domain.GlbBoundsMm
import com.handtryon.domain.RingPlacement
import org.junit.Test

class ArRingPlacementMapperTest {
    @Test
    fun map_centersRingInFrontOfCamera() {
        val mapper = ArRingPlacementMapper()
        val transform =
            mapper.map(
                placement = RingPlacement(centerX = 540f, centerY = 960f, ringWidthPx = 108f, rotationDegrees = 12f),
                frameWidth = 1080,
                frameHeight = 1920,
                glbSummary = summary(),
            )

        assertThat(transform.xMeters).isWithin(0.0001f).of(0f)
        assertThat(transform.yMeters).isWithin(0.0001f).of(0f)
        assertThat(transform.zMeters).isLessThan(0f)
        assertThat(transform.rollDegrees).isEqualTo(12f)
        assertThat(transform.scale).isGreaterThan(0f)
    }

    @Test
    fun map_usesModelBoundsForScale() {
        val mapper = ArRingPlacementMapper()
        val small =
            mapper.map(
                placement = RingPlacement(centerX = 540f, centerY = 960f, ringWidthPx = 80f, rotationDegrees = 0f),
                frameWidth = 1080,
                frameHeight = 1920,
                glbSummary = summary(),
            )
        val large =
            mapper.map(
                placement = RingPlacement(centerX = 540f, centerY = 960f, ringWidthPx = 160f, rotationDegrees = 0f),
                frameWidth = 1080,
                frameHeight = 1920,
                glbSummary = summary(),
            )

        assertThat(large.scale).isGreaterThan(small.scale)
    }

    private fun summary(): GlbAssetSummary =
        GlbAssetSummary(
            modelAssetPath = "tryon/ring_AR.glb",
            glbVersion = 2,
            gltfVersion = "2.0",
            generator = "test",
            meshCount = 2,
            materialCount = 2,
            nodeCount = 4,
            estimatedBoundsMm = GlbBoundsMm(x = 20.4f, y = 21.19f, z = 1.82f),
        )
}
