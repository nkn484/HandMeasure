package com.handmeasure.measurement

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FrameQualityScorerTest {
    @Test
    fun score_penalizesWeakCardAndPose() {
        val scorer = FrameQualityScorer()
        val result =
            scorer.score(
                FrameQualityInput(
                    handDetectionScore = 0.9f,
                    handLandmarkScore = 0.9f,
                    ringZoneScore = 0.8f,
                    cardDetectionScore = 0.2f,
                    cardRectangularityScore = 0.4f,
                    cardEdgeSupportScore = 0.3f,
                    blurScoreGlobal = 0.8f,
                    blurScoreFingerRoi = 0.8f,
                    motionScore = 0.8f,
                    lightingScore = 0.8f,
                    poseScore = 0.3f,
                    planeScore = 0.7f,
                ),
            )

        assertThat(result.totalScore).isLessThan(0.7f)
        assertThat(result.confidencePenaltyReasons).contains("card_detection_low")
        assertThat(result.confidencePenaltyReasons).contains("pose_low")
    }
}
