package com.handmeasure.core.session

import com.google.common.truth.Truth.assertThat
import com.handmeasure.core.measurement.CaptureStep
import com.handmeasure.core.measurement.HandMeasureWarning
import org.junit.Test

class MeasurementSessionProcessingUseCaseTest {
    @Test
    fun process_delegatesToFinalizationUseCase() {
        val useCase =
            MeasurementSessionProcessingUseCase(
                stepAnalyzer = SessionStepAnalyzer { _, _ -> null },
            )

        val result =
            useCase.process(
                MeasurementSessionProcessingRequest(
                    stepCandidates =
                        listOf(
                            SessionStepCandidate(
                                step = CaptureStep.FRONT_PALM,
                                frameBytes = byteArrayOf(1, 2, 3),
                                qualityScore = 0.8f,
                                poseScore = 0.8f,
                                cardScore = 0.8f,
                                handScore = 0.8f,
                            ),
                        ),
                    thresholds =
                        SessionQualityThresholds(
                            cardMinScore = 0.45f,
                            lightingMinScore = 0.35f,
                            blurMinScore = 0.35f,
                            motionMinScore = 0.35f,
                        ),
                ),
            )

        assertThat(result.stepMeasurements).hasSize(1)
        assertThat(result.warnings).contains(HandMeasureWarning.BEST_EFFORT_ESTIMATE)
    }
}
