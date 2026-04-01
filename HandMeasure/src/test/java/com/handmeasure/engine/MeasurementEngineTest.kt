package com.handmeasure.engine

import com.google.common.truth.Truth.assertThat
import com.handmeasure.core.measurement.CalibrationStatus
import com.handmeasure.core.measurement.CaptureStep
import com.handmeasure.core.measurement.HandMeasureWarning
import com.handmeasure.core.measurement.MeasurementSource
import com.handmeasure.core.measurement.QualityLevel
import com.handmeasure.core.measurement.ResultMode
import com.handmeasure.engine.model.MeasurementEngineCapturedStepInfo
import com.handmeasure.engine.model.MeasurementEngineOverlayFrame
import com.handmeasure.engine.model.MeasurementEngineProcessingResult
import com.handmeasure.engine.model.MeasurementEngineResult
import com.handmeasure.engine.model.MeasurementEngineStepCandidate
import com.handmeasure.engine.model.MeasurementTargetFinger
import org.junit.Test

class MeasurementEngineTest {
    @Test
    fun process_delegatesUsingInternalEngineModelsOnly() {
        val stepCandidate =
            MeasurementEngineStepCandidate(
                step = CaptureStep.FRONT_PALM,
                frameBytes = byteArrayOf(1, 2, 3),
                qualityScore = 0.9f,
                poseScore = 0.8f,
                cardScore = 0.8f,
                handScore = 0.9f,
            )
        var capturedCandidates: List<MeasurementEngineStepCandidate> = emptyList()
        val expected =
            MeasurementEngineProcessingResult(
                result =
                    MeasurementEngineResult(
                        targetFinger = MeasurementTargetFinger.RING,
                        fingerWidthMm = 18.0,
                        fingerThicknessMm = 14.0,
                        estimatedCircumferenceMm = 52.0,
                        equivalentDiameterMm = 16.5,
                        suggestedRingSizeLabel = "US 6",
                        confidenceScore = 0.82f,
                        warnings = listOf(HandMeasureWarning.CALIBRATION_WEAK),
                        capturedSteps =
                            listOf(
                                MeasurementEngineCapturedStepInfo(
                                    step = CaptureStep.FRONT_PALM,
                                    score = 0.9f,
                                    poseScore = 0.8f,
                                    cardScore = 0.8f,
                                    handScore = 0.9f,
                                ),
                            ),
                        resultMode = ResultMode.HYBRID_ESTIMATE,
                        qualityLevel = QualityLevel.MEDIUM,
                        retryRecommended = false,
                        calibrationStatus = CalibrationStatus.CALIBRATED,
                        measurementSources = listOf(MeasurementSource.EDGE_PROFILE),
                    ),
                overlays = listOf(MeasurementEngineOverlayFrame(stepName = "FRONT_PALM", jpegBytes = byteArrayOf(9, 8, 7))),
            )
        val engine =
            MeasurementEngine(
                processingPort =
                    MeasurementEngineProcessingPort { candidates ->
                        capturedCandidates = candidates
                        expected
                    },
            )

        val result = engine.process(listOf(stepCandidate))

        assertThat(capturedCandidates).containsExactly(stepCandidate)
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun constructor_acceptsProcessingPortOnly() {
        val constructor = MeasurementEngine::class.java.declaredConstructors.single()
        val parameterTypeNames = constructor.parameterTypes.map { it.name }

        assertThat(parameterTypeNames).containsExactly(MeasurementEngineProcessingPort::class.java.name)
        assertThat(parameterTypeNames).doesNotContain("com.handmeasure.api.HandMeasureResult")
        assertThat(parameterTypeNames).doesNotContain("com.handmeasure.flow.StepCandidate")
    }
}
