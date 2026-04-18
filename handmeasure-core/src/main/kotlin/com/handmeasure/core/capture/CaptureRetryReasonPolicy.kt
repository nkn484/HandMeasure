package com.handmeasure.core.capture

enum class CaptureRetryReason {
    PLACE_HAND_IN_FRAME,
    PLACE_CARD_NEAR_FINGER,
    WAIT_FOR_LOCK,
    HOLD_HAND_STEADIER,
    REDUCE_GLARE,
    ADJUST_HAND_ANGLE,
    KEEP_HAND_AND_CARD_CLOSER,
    TRACKING_UNSTABLE,
}

data class CaptureRetryReasonInput(
    val handDetected: Boolean,
    val cardDetected: Boolean,
    val holdStillState: HoldStillState,
    val qualityScore: Float,
    val poseScore: Float,
    val motionScore: Float,
    val lightingScore: Float,
    val coplanarityScore: Float,
    val penaltyReasons: List<String>,
)

class CaptureRetryReasonPolicy(
    private val lockQualityScore: Float,
    private val motionMinScore: Float,
    private val lightingMinScore: Float,
) {
    fun decide(input: CaptureRetryReasonInput): CaptureRetryReason? {
        if (!input.handDetected) return CaptureRetryReason.PLACE_HAND_IN_FRAME
        if (!input.cardDetected) return CaptureRetryReason.PLACE_CARD_NEAR_FINGER

        if (input.holdStillState == HoldStillState.CANDIDATE) {
            return CaptureRetryReason.WAIT_FOR_LOCK
        }

        if ("motion_high" in input.penaltyReasons || input.motionScore < motionMinScore) {
            return CaptureRetryReason.HOLD_HAND_STEADIER
        }
        if ("lighting_poor" in input.penaltyReasons || input.lightingScore < lightingMinScore) {
            return CaptureRetryReason.REDUCE_GLARE
        }
        if ("pose_low" in input.penaltyReasons || input.poseScore < 0.45f) {
            return CaptureRetryReason.ADJUST_HAND_ANGLE
        }
        if (input.coplanarityScore < 0.40f) {
            return CaptureRetryReason.KEEP_HAND_AND_CARD_CLOSER
        }
        if (input.qualityScore < lockQualityScore * 0.72f && input.penaltyReasons.isNotEmpty()) {
            return CaptureRetryReason.TRACKING_UNSTABLE
        }
        return null
    }
}
