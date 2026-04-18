package com.handmeasure.core.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CaptureRetryReasonPolicyTest {
    private val policy =
        CaptureRetryReasonPolicy(
            lockQualityScore = 0.84f,
            motionMinScore = 0.35f,
            lightingMinScore = 0.35f,
        )

    @Test
    fun decide_prioritizesMissingHandAndCard() {
        val missingHand =
            policy.decide(
                input(
                    handDetected = false,
                    cardDetected = true,
                ),
            )
        val missingCard =
            policy.decide(
                input(
                    handDetected = true,
                    cardDetected = false,
                ),
            )

        assertThat(missingHand).isEqualTo(CaptureRetryReason.PLACE_HAND_IN_FRAME)
        assertThat(missingCard).isEqualTo(CaptureRetryReason.PLACE_CARD_NEAR_FINGER)
    }

    @Test
    fun decide_usesHoldStateForWaitForLock() {
        val reason =
            policy.decide(
                input(
                    holdStillState = HoldStillState.CANDIDATE,
                    qualityScore = 0.78f,
                ),
            )

        assertThat(reason).isEqualTo(CaptureRetryReason.WAIT_FOR_LOCK)
    }

    @Test
    fun decide_usesPenaltySignalsForRetryGuidance() {
        val reason =
            policy.decide(
                input(
                    penaltyReasons = listOf("motion_high"),
                    motionScore = 0.2f,
                ),
            )

        assertThat(reason).isEqualTo(CaptureRetryReason.HOLD_HAND_STEADIER)
    }

    private fun input(
        handDetected: Boolean = true,
        cardDetected: Boolean = true,
        holdStillState: HoldStillState = HoldStillState.SEARCHING,
        qualityScore: Float = 0.86f,
        poseScore: Float = 0.8f,
        motionScore: Float = 0.7f,
        lightingScore: Float = 0.7f,
        coplanarityScore: Float = 0.75f,
        penaltyReasons: List<String> = emptyList(),
    ): CaptureRetryReasonInput =
        CaptureRetryReasonInput(
            handDetected = handDetected,
            cardDetected = cardDetected,
            holdStillState = holdStillState,
            qualityScore = qualityScore,
            poseScore = poseScore,
            motionScore = motionScore,
            lightingScore = lightingScore,
            coplanarityScore = coplanarityScore,
            penaltyReasons = penaltyReasons,
        )
}
