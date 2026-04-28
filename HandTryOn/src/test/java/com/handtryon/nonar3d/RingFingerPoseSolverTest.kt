package com.handtryon.nonar3d

import com.google.common.truth.Truth.assertThat
import com.handtryon.domain.GlbAssetSummary
import com.handtryon.domain.GlbBoundsMm
import com.handtryon.domain.HandPoseSnapshot
import com.handtryon.domain.LandmarkPoint
import com.handtryon.domain.MeasurementSnapshot
import org.junit.Test

class RingFingerPoseSolverTest {
    @Test
    fun solve_returnsFingerLocalPoseFromRingFingerLandmarks() {
        val pose =
            RingFingerPoseSolver().solve(
                handPose = handPose(),
                measurement = measurement(),
                glbSummary = glbSummary(),
            )

        assertThat(pose).isNotNull()
        pose!!
        assertThat(pose.centerPx.x).isWithin(0.001f).of(540f)
        assertThat(pose.centerPx.y).isGreaterThan(600f)
        assertThat(pose.tangentPx.y).isGreaterThan(0.9f)
        assertThat(pose.fingerRadiusMm).isEqualTo(9f)
        assertThat(pose.ringOuterDiameterMm).isEqualTo(20.4f)
        assertThat(pose.confidence).isGreaterThan(0.2f)
    }

    @Test
    fun solve_returnsNullWhenRingFingerAxisIsTooShort() {
        val landmarks = defaultLandmarks().toMutableList()
        landmarks[13] = LandmarkPoint(540f, 640f, 0f)
        landmarks[15] = LandmarkPoint(542f, 642f, 0f)

        val pose =
            RingFingerPoseSolver().solve(
                handPose = handPose(landmarks),
                measurement = null,
                glbSummary = null,
            )

        assertThat(pose).isNull()
    }

    private fun handPose(landmarks: List<LandmarkPoint> = defaultLandmarks()): HandPoseSnapshot =
        HandPoseSnapshot(
            frameWidth = 1080,
            frameHeight = 1920,
            landmarks = landmarks,
            confidence = 0.86f,
            timestampMs = 1_000L,
        )

    private fun defaultLandmarks(): List<LandmarkPoint> =
        MutableList(21) { LandmarkPoint(0f, 0f, 0f) }.apply {
            this[9] = LandmarkPoint(500f, 620f, -0.01f)
            this[13] = LandmarkPoint(540f, 640f, 0.0f)
            this[14] = LandmarkPoint(540f, 720f, 0.02f)
            this[15] = LandmarkPoint(540f, 800f, 0.03f)
            this[17] = LandmarkPoint(590f, 650f, 0.02f)
        }

    private fun measurement(): MeasurementSnapshot =
        MeasurementSnapshot(
            equivalentDiameterMm = 20.4f,
            fingerWidthMm = 18f,
            confidence = 0.8f,
            usable = true,
        )

    private fun glbSummary(): GlbAssetSummary =
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
