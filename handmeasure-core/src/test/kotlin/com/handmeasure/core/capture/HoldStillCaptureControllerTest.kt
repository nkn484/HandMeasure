package com.handmeasure.core.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HoldStillCaptureControllerTest {
    @Test
    fun evaluate_locksAfterStableWindow() {
        val controller =
            HoldStillCaptureController<String>(
                candidateMinScore = 0.56f,
                lockMinScore = 0.84f,
                stableMotionMinScore = 0.45f,
                minStableFrames = 3,
                minStableDurationMs = 300L,
            )

        controller.evaluate(
            HoldStillInput(
                key = "frontal",
                qualityScore = 0.86f,
                motionScore = 0.72f,
                isBucketStable = true,
                timestampMs = 0L,
            ),
        )
        controller.evaluate(
            HoldStillInput(
                key = "frontal",
                qualityScore = 0.87f,
                motionScore = 0.70f,
                isBucketStable = true,
                timestampMs = 180L,
            ),
        )
        val decision =
            controller.evaluate(
                HoldStillInput(
                    key = "frontal",
                    qualityScore = 0.88f,
                    motionScore = 0.73f,
                    isBucketStable = true,
                    timestampMs = 330L,
                ),
            )

        assertThat(decision.state).isEqualTo(HoldStillState.LOCKED)
        assertThat(decision.commitKey).isEqualTo("frontal")
    }

    @Test
    fun evaluate_resetsWhenMotionIsUnstable() {
        val controller =
            HoldStillCaptureController<String>(
                candidateMinScore = 0.56f,
                lockMinScore = 0.84f,
                stableMotionMinScore = 0.45f,
                minStableFrames = 2,
                minStableDurationMs = 150L,
            )

        controller.evaluate(
            HoldStillInput(
                key = "frontal",
                qualityScore = 0.85f,
                motionScore = 0.7f,
                isBucketStable = true,
                timestampMs = 0L,
            ),
        )
        val unstable =
            controller.evaluate(
                HoldStillInput(
                    key = "frontal",
                    qualityScore = 0.85f,
                    motionScore = 0.20f,
                    isBucketStable = true,
                    timestampMs = 120L,
                ),
            )

        assertThat(unstable.state).isEqualTo(HoldStillState.SEARCHING)
        assertThat(unstable.commitKey).isNull()
    }
}
