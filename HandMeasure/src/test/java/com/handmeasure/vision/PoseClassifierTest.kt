package com.handmeasure.vision

import com.google.common.truth.Truth.assertThat
import com.handmeasure.api.CaptureStep
import org.junit.Test

class PoseClassifierTest {
    private val classifier = PoseClassifier()

    @Test
    fun classify_frontPalm_prefersZDominantNormal() {
        val score =
            classifier.classify(
                CaptureStep.FRONT_PALM,
                PoseSnapshot(normalX = 0.1f, normalY = 0.1f, normalZ = 0.95f),
            )

        assertThat(score).isGreaterThan(0.8f)
    }

    @Test
    fun classify_leftOblique_prefersNegativeXNormal() {
        val score =
            classifier.classify(
                CaptureStep.LEFT_OBLIQUE,
                PoseSnapshot(normalX = -0.85f, normalY = 0.05f, normalZ = 0.25f),
            )

        assertThat(score).isGreaterThan(0.6f)
    }

    @Test
    fun evaluate_returnsGuidanceForWrongPose() {
        val hand =
            HandDetection(
                imageLandmarks = List(21) { Landmark2D(0f, 0f) },
                worldLandmarks =
                    List(21) { index ->
                        when (index) {
                            0 -> Landmark3D(0f, 0f, 0f)
                            5 -> Landmark3D(1f, 0f, 0f)
                            17 -> Landmark3D(0f, 1f, 0f)
                            else -> Landmark3D(0f, 0f, 0f)
                        }
                    },
                handedness = "Left",
                confidence = 0.8f,
            )
        val evaluation = classifier.evaluate(CaptureStep.LEFT_OBLIQUE, hand)

        assertThat(evaluation.smoothedScore).isLessThan(0.5f)
        assertThat(evaluation.guidanceHint).isNotEmpty()
    }
}
