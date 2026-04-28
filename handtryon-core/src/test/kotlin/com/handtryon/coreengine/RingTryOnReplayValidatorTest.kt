package com.handtryon.coreengine

import com.google.common.truth.Truth.assertThat
import com.handtryon.coreengine.model.RingFingerPose
import com.handtryon.coreengine.model.TryOnPoint2
import com.handtryon.coreengine.model.TryOnVec2
import org.junit.Test

class RingTryOnReplayValidatorTest {
    @Test
    fun evaluate_passesFrameWithinThresholds() {
        val report =
            RingTryOnReplayValidator().evaluate(
                listOf(
                    RingTryOnReplayFrame(
                        frameId = "frame_001",
                        expected = ExpectedRingFingerZone(centerX = 540f, centerY = 720f, widthPx = 72f, rotationDegrees = 90f),
                        predictedPose = pose(centerX = 548f, centerY = 725f, widthPx = 76f, rotationDegrees = 96f),
                    ),
                ),
            )

        assertThat(report.frameCount).isEqualTo(1)
        assertThat(report.passedFrameCount).isEqualTo(1)
        assertThat(report.worstCenterErrorRatio).isLessThan(0.35f)
    }

    @Test
    fun evaluate_failsFrameWhenRingIsFarFromExpectedFingerZone() {
        val report =
            RingTryOnReplayValidator().evaluate(
                listOf(
                    RingTryOnReplayFrame(
                        frameId = "real_screenshot_0047",
                        expected = ExpectedRingFingerZone(centerX = 390f, centerY = 725f, widthPx = 74f, rotationDegrees = 88f),
                        predictedPose = pose(centerX = 40f, centerY = 370f, widthPx = 74f, rotationDegrees = 88f),
                    ),
                ),
            )

        assertThat(report.passedFrameCount).isEqualTo(0)
        assertThat(report.worstCenterErrorRatio).isGreaterThan(3f)
    }

    private fun pose(
        centerX: Float,
        centerY: Float,
        widthPx: Float,
        rotationDegrees: Float,
    ): RingFingerPose =
        RingFingerPose(
            centerPx = TryOnPoint2(centerX, centerY),
            occluderStartPx = TryOnPoint2(centerX, centerY - 60f),
            occluderEndPx = TryOnPoint2(centerX, centerY + 90f),
            tangentPx = TryOnVec2(0f, 1f),
            normalHintPx = TryOnVec2(1f, 0f),
            rotationDegrees = rotationDegrees,
            rollDegrees = 0f,
            fingerWidthPx = widthPx,
            confidence = 0.86f,
        )
}
