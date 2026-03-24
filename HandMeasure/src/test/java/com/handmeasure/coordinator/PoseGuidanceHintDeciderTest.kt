package com.handmeasure.coordinator

import com.google.common.truth.Truth.assertThat
import com.handmeasure.measurement.CardRectangle
import com.handmeasure.vision.CardDetection
import com.handmeasure.vision.HandDetection
import com.handmeasure.vision.Landmark2D
import com.handmeasure.vision.Landmark3D
import com.handmeasure.vision.PoseGuidanceAction
import com.handmeasure.vision.PoseMatchLevel
import org.junit.Test

class PoseGuidanceHintDeciderTest {
    private val decider = PoseGuidanceHintDecider()

    @Test
    fun decide_returnsPlaceHandWhenHandMissing() {
        val hint =
            decider.decide(
                level = PoseMatchLevel.WRONG,
                action = PoseGuidanceAction.ROTATE_LEFT_MORE,
                hand = null,
                card = fakeCard(),
            )

        assertThat(hint).isEqualTo(PoseGuidanceHintKey.PLACE_HAND_IN_FRAME)
    }

    @Test
    fun decide_returnsPlaceCardWhenCardMissing() {
        val hint =
            decider.decide(
                level = PoseMatchLevel.WRONG,
                action = PoseGuidanceAction.ROTATE_LEFT_MORE,
                hand = fakeHand(),
                card = null,
            )

        assertThat(hint).isEqualTo(PoseGuidanceHintKey.PLACE_CARD_NEAR_FINGER)
    }

    @Test
    fun decide_mapsClassifierAction() {
        val hint =
            decider.decide(
                level = PoseMatchLevel.WRONG,
                action = PoseGuidanceAction.TILT_UP_MORE,
                hand = fakeHand(),
                card = fakeCard(),
            )

        assertThat(hint).isEqualTo(PoseGuidanceHintKey.TILT_UP_MORE)
    }

    @Test
    fun decide_returnsNullWhenPoseCorrect() {
        val hint =
            decider.decide(
                level = PoseMatchLevel.CORRECT,
                action = null,
                hand = fakeHand(),
                card = fakeCard(),
            )

        assertThat(hint).isNull()
    }

    @Test
    fun decide_fallsBackToAdjustPoseForWrongLevel() {
        val hint =
            decider.decide(
                level = PoseMatchLevel.WRONG,
                action = null,
                hand = fakeHand(),
                card = fakeCard(),
            )

        assertThat(hint).isEqualTo(PoseGuidanceHintKey.ADJUST_HAND_POSE)
    }

    private fun fakeHand(): HandDetection =
        HandDetection(
            imageLandmarks = List(21) { Landmark2D(0f, 0f) },
            worldLandmarks = List(21) { Landmark3D(0f, 0f, 0f) },
            handedness = "Left",
            confidence = 0.9f,
        )

    private fun fakeCard(): CardDetection =
        CardDetection(
            rectangle =
                CardRectangle(
                    centerX = 100.0,
                    centerY = 120.0,
                    longSidePx = 300.0,
                    shortSidePx = 180.0,
                    angleDeg = 0.0,
                ),
            corners =
                listOf(
                    10f to 20f,
                    110f to 20f,
                    110f to 80f,
                    10f to 80f,
                ),
            contourAreaScore = 0.8f,
            aspectScore = 0.9f,
            confidence = 0.85f,
        )
}
