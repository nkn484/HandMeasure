package com.handtryon.coreengine

import com.google.common.truth.Truth.assertThat
import com.handtryon.coreengine.model.RingFingerPoseRejectReason
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
        assertThat(pose.diagnostics).isNotNull()
        assertThat(pose.diagnostics!!.extensionRatio).isGreaterThan(0.9f)
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
            .isEqualTo(RingFingerPoseRejectReason.FingerCurled)
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
            .isEqualTo(RingFingerPoseRejectReason.DistalSegmentHidden)
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
            .isEqualTo(RingFingerPoseRejectReason.PointsTowardWrist)
    }

    @Test
    fun evaluate_returnsDiagnosticsForRejectedPose() {
        val landmarks = defaultLandmarks().toMutableList()
        landmarks[13] = TryOnLandmarkPoint(540f, 640f, 0f)
        landmarks[14] = TryOnLandmarkPoint(540f, 720f, 0f)
        landmarks[15] = TryOnLandmarkPoint(600f, 705f, 0f)

        val result = RingFingerPoseSolver().evaluate(handPose(landmarks))

        assertThat(result.usable).isFalse()
        assertThat(result.rejectReason).isEqualTo(RingFingerPoseRejectReason.FingerCurled)
        assertThat(result.diagnostics.rejectReason).isEqualTo(RingFingerPoseRejectReason.FingerCurled)
        assertThat(result.diagnostics.bendCosine).isLessThan(0.5f)
    }

    @Test
    fun solve_keepsLowerPalmNearVerticalFingerOnVerticalCenter() {
        val landmarks = defaultLandmarks().toMutableList()
        landmarks[0] = TryOnLandmarkPoint(240f, 1050f, 0f)
        landmarks[9] = TryOnLandmarkPoint(310f, 760f, 0f)
        landmarks[13] = TryOnLandmarkPoint(240f, 755f, 0f)
        landmarks[14] = TryOnLandmarkPoint(226f, 603f, 0f)
        landmarks[15] = TryOnLandmarkPoint(216f, 511f, 0f)
        landmarks[17] = TryOnLandmarkPoint(170f, 765f, 0f)

        val pose = RingFingerPoseSolver().solve(handPose(landmarks, frameWidth = 720, frameHeight = 1280))

        assertThat(pose).isNotNull()
        assertThat(pose!!.diagnostics!!.centerOnMcpToPip).isWithin(0.001f).of(0.64f)
        assertThat(pose.diagnostics!!.lateralOffsetPx).isWithin(0.001f).of(0f)
    }

    @Test
    fun solve_usesDefaultCenterForUpperPalmNearVerticalFinger() {
        val landmarks = defaultLandmarks().toMutableList()
        landmarks[0] = TryOnLandmarkPoint(465f, 900f, 0f)
        landmarks[9] = TryOnLandmarkPoint(395f, 606f, 0f)
        landmarks[13] = TryOnLandmarkPoint(465f, 606f, 0f)
        landmarks[14] = TryOnLandmarkPoint(461f, 506f, 0f)
        landmarks[15] = TryOnLandmarkPoint(456f, 451f, 0f)
        landmarks[17] = TryOnLandmarkPoint(535f, 606f, 0f)

        val pose = RingFingerPoseSolver().solve(handPose(landmarks, frameWidth = 720, frameHeight = 1280))

        assertThat(pose).isNotNull()
        assertThat(pose!!.diagnostics!!.centerOnMcpToPip).isWithin(0.001f).of(0.34f)
        assertThat(pose.diagnostics!!.lateralOffsetPx).isLessThan(0f)
    }

    @Test
    fun solve_keepsMidPalmNearVerticalFingerOnMidPalmCenter() {
        val landmarks = defaultLandmarks().toMutableList()
        landmarks[0] = TryOnLandmarkPoint(343f, 960f, 0f)
        landmarks[9] = TryOnLandmarkPoint(285f, 698f, 0f)
        landmarks[13] = TryOnLandmarkPoint(343f, 698f, 0f)
        landmarks[14] = TryOnLandmarkPoint(345f, 611f, 0f)
        landmarks[15] = TryOnLandmarkPoint(346f, 562f, 0f)
        landmarks[17] = TryOnLandmarkPoint(405f, 698f, 0f)

        val pose = RingFingerPoseSolver().solve(handPose(landmarks, frameWidth = 720, frameHeight = 1280))

        assertThat(pose).isNotNull()
        assertThat(pose!!.diagnostics!!.centerOnMcpToPip).isWithin(0.001f).of(0.30f)
        assertThat(pose.diagnostics!!.lateralOffsetPx).isGreaterThan(0f)
    }

    @Test
    fun solve_usesDefaultCenterForObliqueFingerNearMidPalmBoundary() {
        val landmarks = defaultLandmarks().toMutableList()
        landmarks[0] = TryOnLandmarkPoint(220f, 900f, 0f)
        landmarks[9] = TryOnLandmarkPoint(160f, 641f, 0f)
        landmarks[13] = TryOnLandmarkPoint(221f, 641f, 0f)
        landmarks[14] = TryOnLandmarkPoint(277f, 456f, 0f)
        landmarks[15] = TryOnLandmarkPoint(334f, 340f, 0f)
        landmarks[17] = TryOnLandmarkPoint(285f, 641f, 0f)

        val pose = RingFingerPoseSolver().solve(handPose(landmarks, frameWidth = 720, frameHeight = 1280))

        assertThat(pose).isNotNull()
        assertThat(pose!!.diagnostics!!.centerOnMcpToPip).isWithin(0.001f).of(0.34f)
        assertThat(pose.diagnostics!!.lateralOffsetPx).isLessThan(0f)
    }

    private fun handPose(
        landmarks: List<TryOnLandmarkPoint> = defaultLandmarks(),
        frameWidth: Int = 1080,
        frameHeight: Int = 1920,
    ): TryOnHandPoseSnapshot =
        TryOnHandPoseSnapshot(
            frameWidth = frameWidth,
            frameHeight = frameHeight,
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
