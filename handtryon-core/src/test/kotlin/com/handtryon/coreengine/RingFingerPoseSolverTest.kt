package com.handtryon.coreengine

import com.google.common.truth.Truth.assertThat
import com.handtryon.coreengine.model.TryOnHandPoseSnapshot
import com.handtryon.coreengine.model.TryOnLandmarkPoint
import org.junit.Test

class RingFingerPoseSolverTest {
    @Test
    fun solve_returnsFingerLocalPoseFromRingFingerLandmarks() {
        val pose = RingFingerPoseSolver().solve(handPose())

        assertThat(pose).isNotNull()
        pose!!
        assertThat(pose.centerPx.x).isWithin(0.001f).of(540f)
        assertThat(pose.centerPx.y).isGreaterThan(600f)
        assertThat(pose.tangentPx.y).isGreaterThan(0.9f)
        assertThat(pose.fingerWidthPx).isGreaterThan(18f)
        assertThat(pose.confidence).isGreaterThan(0.2f)
    }

    @Test
    fun solve_returnsNullWhenRingFingerAxisIsTooShort() {
        val landmarks = defaultLandmarks().toMutableList()
        landmarks[13] = TryOnLandmarkPoint(540f, 640f, 0f)
        landmarks[15] = TryOnLandmarkPoint(542f, 642f, 0f)

        val pose = RingFingerPoseSolver().solve(handPose(landmarks))

        assertThat(pose).isNull()
    }

    @Test
    fun solve_returnsNullWhenRingFingerIsCurled() {
        val landmarks = defaultLandmarks().toMutableList()
        landmarks[13] = TryOnLandmarkPoint(540f, 640f, 0f)
        landmarks[14] = TryOnLandmarkPoint(540f, 720f, 0f)
        landmarks[15] = TryOnLandmarkPoint(600f, 705f, 0f)

        val pose = RingFingerPoseSolver().solve(handPose(landmarks))

        assertThat(pose).isNull()
        assertThat(RingFingerPoseSolver().rejectReason(handPose(landmarks)))
            .isEqualTo(com.handtryon.coreengine.model.RingFingerPoseRejectReason.UnstableGeometry)
    }

    @Test
    fun solve_returnsNullWhenRingFingerDistalSegmentIsHidden() {
        val landmarks = defaultLandmarks().toMutableList()
        landmarks[13] = TryOnLandmarkPoint(540f, 640f, 0f)
        landmarks[14] = TryOnLandmarkPoint(540f, 740f, 0f)
        landmarks[15] = TryOnLandmarkPoint(550f, 762f, 0.08f)

        val pose = RingFingerPoseSolver().solve(handPose(landmarks))

        assertThat(pose).isNull()
        assertThat(RingFingerPoseSolver().rejectReason(handPose(landmarks)))
            .isEqualTo(com.handtryon.coreengine.model.RingFingerPoseRejectReason.UnstableGeometry)
    }

    @Test
    fun solve_returnsNullWhenRingFingerPointsBackTowardWrist() {
        val landmarks = defaultLandmarks().toMutableList()
        landmarks[0] = TryOnLandmarkPoint(540f, 840f, 0f)
        landmarks[13] = TryOnLandmarkPoint(540f, 640f, 0f)
        landmarks[14] = TryOnLandmarkPoint(540f, 720f, 0f)
        landmarks[15] = TryOnLandmarkPoint(540f, 800f, 0f)

        val pose = RingFingerPoseSolver().solve(handPose(landmarks))

        assertThat(pose).isNull()
        assertThat(RingFingerPoseSolver().rejectReason(handPose(landmarks)))
            .isEqualTo(com.handtryon.coreengine.model.RingFingerPoseRejectReason.UnstableGeometry)
    }

    private fun handPose(landmarks: List<TryOnLandmarkPoint> = defaultLandmarks()): TryOnHandPoseSnapshot =
        TryOnHandPoseSnapshot(
            frameWidth = 1080,
            frameHeight = 1920,
            landmarks = landmarks,
            confidence = 0.86f,
            timestampMs = 1_000L,
        )

    private fun defaultLandmarks(): List<TryOnLandmarkPoint> =
        MutableList(21) { TryOnLandmarkPoint(0f, 0f, 0f) }.apply {
            this[0] = TryOnLandmarkPoint(540f, 480f, 0.02f)
            this[9] = TryOnLandmarkPoint(500f, 620f, -0.01f)
            this[13] = TryOnLandmarkPoint(540f, 640f, 0.0f)
            this[14] = TryOnLandmarkPoint(540f, 720f, 0.02f)
            this[15] = TryOnLandmarkPoint(540f, 800f, 0.03f)
            this[17] = TryOnLandmarkPoint(590f, 650f, 0.02f)
        }
}
