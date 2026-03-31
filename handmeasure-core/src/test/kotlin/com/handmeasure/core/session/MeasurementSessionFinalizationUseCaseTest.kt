package com.handmeasure.core.session

import com.google.common.truth.Truth.assertThat
import com.handmeasure.core.measurement.CalibrationStatus
import com.handmeasure.core.measurement.CaptureStep
import com.handmeasure.core.measurement.HandMeasureWarning
import com.handmeasure.core.measurement.WidthMeasurementSource
import org.junit.Test

class MeasurementSessionFinalizationUseCaseTest {
    @Test
    fun process_keepsBestScaleAndBuildsStepOutputs() {
        val useCase =
            MeasurementSessionFinalizationUseCase(
                stepAnalyzer =
                    SessionStepAnalyzer { candidate, _ ->
                        when (candidate.step) {
                            CaptureStep.FRONT_PALM ->
                                SessionStepAnalysis(
                                    poseScoreOverride = 0.82f,
                                    cardDiagnostics =
                                        SessionCardDiagnostics(
                                            coverageRatio = 0.2f,
                                            aspectResidual = 0.05f,
                                            rectangularityScore = 0.9f,
                                            edgeSupportScore = 0.9f,
                                            rectificationConfidence = 0.9f,
                                        ),
                                    scaleResult =
                                        SessionScaleResult(
                                            scale = SessionScale(mmPerPxX = 0.11, mmPerPxY = 0.12),
                                            diagnostics =
                                                SessionScaleDiagnostics(
                                                    status = CalibrationStatus.CALIBRATED,
                                                    notes = listOf("ok"),
                                                ),
                                        ),
                                    measurement =
                                        SessionFingerMeasurement(
                                            widthPx = 120.0,
                                            widthMm = 13.2,
                                            usedFallback = false,
                                            source = WidthMeasurementSource.EDGE_PROFILE,
                                            validSamples = 5,
                                            widthVarianceMm = 0.2,
                                            sampledWidthsMm = listOf(13.0, 13.3),
                                        ),
                                )
                            else ->
                                SessionStepAnalysis(
                                    poseScoreOverride = 0.7f,
                                    scaleResult = null,
                                    measurement =
                                        SessionFingerMeasurement(
                                            widthPx = 118.0,
                                            widthMm = 14.0,
                                            usedFallback = true,
                                            source = WidthMeasurementSource.LANDMARK_HEURISTIC,
                                        ),
                                )
                        }
                    },
            )

        val result =
            useCase.process(
                stepCandidates =
                    listOf(
                        candidate(CaptureStep.FRONT_PALM),
                        candidate(CaptureStep.LEFT_OBLIQUE),
                    ),
                thresholds =
                    SessionQualityThresholds(
                        cardMinScore = 0.45f,
                        lightingMinScore = 0.35f,
                        blurMinScore = 0.35f,
                        motionMinScore = 0.35f,
                    ),
            )

        assertThat(result.stepMeasurements).hasSize(2)
        assertThat(result.stepDiagnostics).hasSize(2)
        assertThat(result.bestScaleMmPerPxX).isEqualTo(0.11)
        assertThat(result.bestScaleMmPerPxY).isEqualTo(0.12)
        assertThat(result.calibrationStatus).isEqualTo(CalibrationStatus.CALIBRATED)
        assertThat(result.warnings).contains(HandMeasureWarning.BEST_EFFORT_ESTIMATE)
    }

    @Test
    fun process_addsFallbackMeasurementWhenAnalysisMissing() {
        val useCase = MeasurementSessionFinalizationUseCase(stepAnalyzer = SessionStepAnalyzer { _, _ -> null })
        val result =
            useCase.process(
                stepCandidates = listOf(candidate(CaptureStep.RIGHT_OBLIQUE, blur = 0.1f, motion = 0.1f)),
                thresholds =
                    SessionQualityThresholds(
                        cardMinScore = 0.45f,
                        lightingMinScore = 0.35f,
                        blurMinScore = 0.35f,
                        motionMinScore = 0.35f,
                    ),
            )

        assertThat(result.stepMeasurements).hasSize(1)
        assertThat(result.stepMeasurements.first().step).isEqualTo(CaptureStep.FRONT_PALM)
        assertThat(result.warnings).contains(HandMeasureWarning.BEST_EFFORT_ESTIMATE)
        assertThat(result.warnings).contains(HandMeasureWarning.HIGH_BLUR)
        assertThat(result.warnings).contains(HandMeasureWarning.HIGH_MOTION)
    }

    private fun candidate(
        step: CaptureStep,
        blur: Float = 0.9f,
        motion: Float = 0.9f,
    ): SessionStepCandidate =
        SessionStepCandidate(
            step = step,
            frameBytes = byteArrayOf(1, 2, 3),
            qualityScore = 0.8f,
            poseScore = 0.75f,
            cardScore = 0.8f,
            handScore = 0.8f,
            blurScore = blur,
            motionScore = motion,
            lightingScore = 0.8f,
        )
}
