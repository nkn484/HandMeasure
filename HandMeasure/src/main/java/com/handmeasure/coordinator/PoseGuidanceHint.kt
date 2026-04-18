package com.handmeasure.coordinator

import com.handmeasure.vision.CardDetection
import com.handmeasure.vision.HandDetection
import com.handmeasure.vision.PoseGuidanceAction
import com.handmeasure.vision.PoseMatchLevel

enum class PoseGuidanceHintKey {
    PLACE_HAND_IN_FRAME,
    PLACE_CARD_NEAR_FINGER,
    ADJUST_HAND_POSE,
    HOLD_HAND_STEADY,
    WAIT_FOR_LOCK,
    REDUCE_GLARE,
    KEEP_HAND_AND_CARD_CLOSER,
    TRACKING_UNSTABLE,
    FRAME_HAND_CLEARER,
    ROTATE_LEFT_MORE,
    ROTATE_RIGHT_MORE,
    TILT_UP_MORE,
    TILT_DOWN_MORE,
    FACE_PALM_TO_CAMERA,
}

fun interface PoseGuidanceHintTextResolver {
    fun resolve(hint: PoseGuidanceHintKey): String
}

internal class PoseGuidanceHintDecider {
    fun decide(
        level: PoseMatchLevel?,
        action: PoseGuidanceAction?,
        hand: HandDetection?,
        card: CardDetection?,
    ): PoseGuidanceHintKey? {
        if (hand == null) return PoseGuidanceHintKey.PLACE_HAND_IN_FRAME
        if (card == null) return PoseGuidanceHintKey.PLACE_CARD_NEAR_FINGER

        if (level == PoseMatchLevel.CORRECT) return null
        if (action != null) {
            return when (action) {
                PoseGuidanceAction.FRAME_HAND_CLEARER -> PoseGuidanceHintKey.FRAME_HAND_CLEARER
                PoseGuidanceAction.ROTATE_LEFT_MORE -> PoseGuidanceHintKey.ROTATE_LEFT_MORE
                PoseGuidanceAction.ROTATE_RIGHT_MORE -> PoseGuidanceHintKey.ROTATE_RIGHT_MORE
                PoseGuidanceAction.TILT_UP_MORE -> PoseGuidanceHintKey.TILT_UP_MORE
                PoseGuidanceAction.TILT_DOWN_MORE -> PoseGuidanceHintKey.TILT_DOWN_MORE
                PoseGuidanceAction.FACE_PALM_TO_CAMERA -> PoseGuidanceHintKey.FACE_PALM_TO_CAMERA
                PoseGuidanceAction.HOLD_STEADY -> PoseGuidanceHintKey.HOLD_HAND_STEADY
            }
        }

        return when (level) {
            PoseMatchLevel.ALMOST_CORRECT, PoseMatchLevel.WRONG -> PoseGuidanceHintKey.ADJUST_HAND_POSE
            null -> PoseGuidanceHintKey.HOLD_HAND_STEADY
            PoseMatchLevel.CORRECT -> null
        }
    }
}
