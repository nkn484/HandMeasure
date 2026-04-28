package com.handtryon.nonar3d

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FingerOccluderMeshFactoryTest {
    @Test
    fun create_returnsClosedCylinderMesh() {
        val mesh =
            FingerOccluderMeshFactory(sideCount = 12).create(
                pose = pose(fingerWidthPx = 72f),
                frameWidth = 1080,
                frameHeight = 1920,
            )

        assertThat(mesh.vertices).hasSize(24)
        assertThat(mesh.indices).hasSize(72)
        assertThat(mesh.radiusMeters).isGreaterThan(0f)
        assertThat(mesh.lengthMeters).isGreaterThan(0f)
        assertThat(mesh.indices.max()).isLessThan(mesh.vertices.size)
    }

    @Test
    fun create_scalesRadiusWithFingerWidth() {
        val factory = FingerOccluderMeshFactory(sideCount = 12)
        val small = factory.create(pose = pose(fingerWidthPx = 48f), frameWidth = 1080, frameHeight = 1920)
        val large = factory.create(pose = pose(fingerWidthPx = 96f), frameWidth = 1080, frameHeight = 1920)

        assertThat(large.radiusMeters).isGreaterThan(small.radiusMeters)
    }

    private fun pose(fingerWidthPx: Float): RingFingerPose3D =
        RingFingerPose3D(
            centerPx = NonAr3dPoint2(540f, 690f),
            occluderStartPx = NonAr3dPoint2(540f, 650f),
            occluderEndPx = NonAr3dPoint2(540f, 820f),
            tangentPx = NonAr3dVec2(0f, 1f),
            normalHintPx = NonAr3dVec2(1f, 0f),
            rotationDegrees = 90f,
            rollDegrees = 0f,
            fingerWidthPx = fingerWidthPx,
            fingerRadiusMm = 9f,
            ringOuterDiameterMm = 20.4f,
            confidence = 0.8f,
        )
}
