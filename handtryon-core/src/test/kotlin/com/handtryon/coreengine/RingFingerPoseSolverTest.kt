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
    fun solve_usesProximalFallbackWhenDistalFingerIsCurled() {
        val landmarks = defaultLandmarks().toMutableList()
        landmarks[13] = TryOnLandmarkPoint(540f, 640f, 0f)
        landmarks[14] = TryOnLandmarkPoint(540f, 720f, 0f)
        landmarks[15] = TryOnLandmarkPoint(600f, 705f, 0f)

        val pose = RingFingerPoseSolver().solve(handPose(landmarks))

        assertThat(pose).isNotNull()
        assertThat(pose!!.diagnostics!!.centerPolicy).isEqualTo("proximal_fallback_FingerCurled")
        assertThat(pose.diagnostics!!.centerOnMcpToPip).isWithin(0.001f).of(0.90f)
        assertThat(RingFingerPoseSolver().rejectReason(handPose(landmarks))).isNull()
    }

    @Test
    fun solve_usesProximalFallbackWhenRingFingerDistalSegmentIsHidden() {
        val landmarks = defaultLandmarks().toMutableList()
        landmarks[13] = TryOnLandmarkPoint(540f, 640f, 0f)
        landmarks[14] = TryOnLandmarkPoint(540f, 740f, 0f)
        landmarks[15] = TryOnLandmarkPoint(550f, 762f, 0.08f)

        val pose = RingFingerPoseSolver().solve(handPose(landmarks))

        assertThat(pose).isNotNull()
        assertThat(pose!!.diagnostics!!.centerPolicy).isEqualTo("proximal_fallback_DistalSegmentHidden")
        assertThat(pose.diagnostics!!.centerOnMcpToPip).isWithin(0.001f).of(0.90f)
        assertThat(RingFingerPoseSolver().rejectReason(handPose(landmarks))).isNull()
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
        landmarks[0] = TryOnLandmarkPoint(540f, 840f, 0f)
        landmarks[13] = TryOnLandmarkPoint(540f, 640f, 0f)
        landmarks[14] = TryOnLandmarkPoint(540f, 720f, 0f)
        landmarks[15] = TryOnLandmarkPoint(540f, 800f, 0f)

        val result = RingFingerPoseSolver().evaluate(handPose(landmarks))

        assertThat(result.usable).isFalse()
        assertThat(result.rejectReason).isEqualTo(RingFingerPoseRejectReason.PointsTowardWrist)
        assertThat(result.diagnostics.rejectReason).isEqualTo(RingFingerPoseRejectReason.PointsTowardWrist)
        assertThat(result.diagnostics.forwardExtensionCosine).isLessThan(0f)
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
        assertThat(pose.diagnostics!!.centerPolicy).isEqualTo("vertical_lower_palm")
    }

    @Test
    fun solve_movesUpperPalmNearCameraFingerTowardBlueReviewedBand() {
        val landmarks = defaultLandmarks().toMutableList()
        landmarks[0] = TryOnLandmarkPoint(465f, 900f, 0f)
        landmarks[9] = TryOnLandmarkPoint(395f, 606f, 0f)
        landmarks[13] = TryOnLandmarkPoint(465f, 606f, 0f)
        landmarks[14] = TryOnLandmarkPoint(461f, 506f, 0f)
        landmarks[15] = TryOnLandmarkPoint(456f, 451f, 0f)
        landmarks[17] = TryOnLandmarkPoint(535f, 606f, 0f)

        val pose = RingFingerPoseSolver().solve(handPose(landmarks, frameWidth = 720, frameHeight = 1280))

        assertThat(pose).isNotNull()
        assertThat(pose!!.diagnostics!!.centerOnMcpToPip).isWithin(0.001f).of(0.72f)
        assertThat(pose.diagnostics!!.centerPolicy).isEqualTo("upper_palm_blue_review")
        assertThat(pose.diagnostics!!.lateralOffsetPx).isWithin(0.001f).of(0f)
    }

    @Test
    fun solve_placesUpperPalmWearableBandCloserToMcpThanPip() {
        val landmarks = defaultLandmarks().toMutableList()
        landmarks[0] = TryOnLandmarkPoint(494.7f, 810.4f, 0.02f)
        landmarks[9] = TryOnLandmarkPoint(382.6f, 637.2f, -0.01f)
        landmarks[13] = TryOnLandmarkPoint(436.34f, 615.89f, 0.105f)
        landmarks[14] = TryOnLandmarkPoint(424.24f, 537.03f, 0.162f)
        landmarks[15] = TryOnLandmarkPoint(416.20f, 492.30f, 0.177f)
        landmarks[17] = TryOnLandmarkPoint(502.9f, 617.5f, 0.12f)

        val pose = RingFingerPoseSolver().solve(handPose(landmarks, frameWidth = 720, frameHeight = 1280))

        assertThat(pose).isNotNull()
        pose!!
        assertThat(pose.diagnostics!!.centerPolicy).isEqualTo("upper_palm_natural")
        assertThat(pose.diagnostics!!.centerOnMcpToPip).isWithin(0.001f).of(0.34f)
        assertThat(pose.centerPx.x).isWithin(0.001f).of(432.225f)
        assertThat(pose.centerPx.y).isWithin(0.001f).of(589.0786f)
    }

    @Test
    fun solve_reportsRotationBucketForObliqueRightFinger() {
        val landmarks = defaultLandmarks().toMutableList()
        landmarks[0] = TryOnLandmarkPoint(220f, 900f, 0f)
        landmarks[9] = TryOnLandmarkPoint(160f, 641f, 0f)
        landmarks[13] = TryOnLandmarkPoint(221f, 641f, 0f)
        landmarks[14] = TryOnLandmarkPoint(277f, 456f, 0f)
        landmarks[15] = TryOnLandmarkPoint(334f, 340f, 0f)
        landmarks[17] = TryOnLandmarkPoint(285f, 641f, 0f)

        val pose = RingFingerPoseSolver().solve(handPose(landmarks, frameWidth = 720, frameHeight = 1280))

        assertThat(pose).isNotNull()
        assertThat(pose!!.diagnostics!!.centerPolicy).isEqualTo("oblique_default")
        assertThat(pose.diagnostics!!.rotationCorrectionBucket).isNotEmpty()
        assertThat(pose.diagnostics!!.finalRotationDegrees).isWithin(0.001f).of(pose.rotationDegrees)
    }

    @Test
    fun solve_reportsRotationBucketForObliqueLeftFinger() {
        val landmarks = defaultLandmarks().toMutableList()
        landmarks[0] = TryOnLandmarkPoint(500f, 900f, 0f)
        landmarks[9] = TryOnLandmarkPoint(560f, 641f, 0f)
        landmarks[13] = TryOnLandmarkPoint(499f, 641f, 0f)
        landmarks[14] = TryOnLandmarkPoint(443f, 456f, 0f)
        landmarks[15] = TryOnLandmarkPoint(386f, 340f, 0f)
        landmarks[17] = TryOnLandmarkPoint(435f, 641f, 0f)

        val pose = RingFingerPoseSolver().solve(handPose(landmarks, frameWidth = 720, frameHeight = 1280))

        assertThat(pose).isNotNull()
        assertThat(pose!!.diagnostics!!.centerPolicy).isEqualTo("oblique_default")
        assertThat(pose.diagnostics!!.rotationCorrectionBucket).isNotEmpty()
    }

    @Test
    fun evaluate_rejectsOutsideFrameCenter() {
        val landmarks = defaultLandmarks().toMutableList()
        landmarks[0] = TryOnLandmarkPoint(540f, 480f, 0f)
        landmarks[9] = TryOnLandmarkPoint(500f, 620f, 0f)
        landmarks[13] = TryOnLandmarkPoint(1_140f, 640f, 0f)
        landmarks[14] = TryOnLandmarkPoint(1_140f, 720f, 0f)
        landmarks[15] = TryOnLandmarkPoint(1_140f, 800f, 0f)
        landmarks[17] = TryOnLandmarkPoint(1_190f, 650f, 0f)

        val result = RingFingerPoseSolver().evaluate(handPose(landmarks, frameWidth = 1080, frameHeight = 1920))

        assertThat(result.rejectReason).isEqualTo(RingFingerPoseRejectReason.OutsideFrame)
        assertThat(result.diagnostics.rejectReason).isEqualTo(RingFingerPoseRejectReason.OutsideFrame)
    }

    @Test
    fun evaluate_rejectsLowConfidence() {
        val result = RingFingerPoseSolver().evaluate(handPose(confidence = 0.04f))

        assertThat(result.rejectReason).isEqualTo(RingFingerPoseRejectReason.LowConfidence)
        assertThat(result.diagnostics.confidence).isLessThan(0.22f)
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
        assertThat(pose!!.diagnostics!!.centerOnMcpToPip).isWithin(0.001f).of(0.34f)
        assertThat(pose.diagnostics!!.lateralOffsetPx).isWithin(0.001f).of(0f)
    }

    @Test
    fun solve_movesObliqueFingerTowardBlueReviewedBand() {
        val landmarks = defaultLandmarks().toMutableList()
        landmarks[0] = TryOnLandmarkPoint(220f, 900f, 0f)
        landmarks[9] = TryOnLandmarkPoint(160f, 641f, 0f)
        landmarks[13] = TryOnLandmarkPoint(221f, 641f, 0f)
        landmarks[14] = TryOnLandmarkPoint(277f, 456f, 0f)
        landmarks[15] = TryOnLandmarkPoint(334f, 340f, 0f)
        landmarks[17] = TryOnLandmarkPoint(285f, 641f, 0f)

        val pose = RingFingerPoseSolver().solve(handPose(landmarks, frameWidth = 720, frameHeight = 1280))

        assertThat(pose).isNotNull()
        assertThat(pose!!.diagnostics!!.centerOnMcpToPip).isWithin(0.001f).of(0.78f)
        assertThat(pose.diagnostics!!.lateralOffsetPx).isWithin(0.001f).of(0f)
    }

    private fun handPose(
        landmarks: List<TryOnLandmarkPoint> = defaultLandmarks(),
        frameWidth: Int = 1080,
        frameHeight: Int = 1920,
        confidence: Float = 0.86f,
    ): TryOnHandPoseSnapshot =
        TryOnHandPoseSnapshot(
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            landmarks = landmarks,
            confidence = confidence,
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
