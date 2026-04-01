package com.handmeasure.engine

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import com.handmeasure.api.CalibrationStatus
import com.handmeasure.api.CaptureStep
import com.handmeasure.api.HandMeasureConfig
import com.handmeasure.api.HandMeasureResult
import com.handmeasure.api.HandMeasureWarning
import com.handmeasure.api.MeasurementSource
import com.handmeasure.api.QualityLevel
import com.handmeasure.api.ResultMode
import com.handmeasure.coordinator.DebugOverlayFrame
import com.handmeasure.coordinator.SessionProcessingOutput
import com.handmeasure.engine.compat.MeasurementEngineApiMapper
import com.handmeasure.engine.model.MeasurementEngineConfig
import com.handmeasure.engine.model.MeasurementEngineStepCandidate
import com.handmeasure.flow.StepCandidate
import com.handmeasure.measurement.StepMeasurement
import com.handmeasure.measurement.WidthMeasurementSource
import com.handmeasure.vision.HandDetection
import com.handmeasure.vision.HandLandmarkEngine
import com.handmeasure.vision.Landmark2D
import com.handmeasure.vision.Landmark3D
import org.junit.Test

class MeasurementEngineTest {
    private val mapper = MeasurementEngineApiMapper()

    @Test
    fun process_mapsCandidatesDelegatesAndReturnsEngineResult() {
        val apiConfig = HandMeasureConfig()
        val engineConfig: MeasurementEngineConfig = mapper.toEngineConfig(apiConfig)
        val engineCandidate =
            MeasurementEngineStepCandidate(
                step = com.handmeasure.core.measurement.CaptureStep.FRONT_PALM,
                frameBytes = byteArrayOf(1, 2, 3),
                qualityScore = 0.9f,
                poseScore = 0.8f,
                cardScore = 0.8f,
                handScore = 0.9f,
            )
        var processedStepCandidates: List<StepCandidate> = emptyList()
        var assembledStepCandidates: List<StepCandidate> = emptyList()

        val engine =
            MeasurementEngine(
                config = engineConfig,
                handLandmarkEngine = FakeHandLandmarkEngine(),
                mapper = mapper,
                sessionProcessorOverride =
                    MeasurementEngineSessionProcessorPort { stepResults ->
                        processedStepCandidates = stepResults
                        SessionProcessingOutput(
                            warnings = emptySet(),
                            stepMeasurements =
                                listOf(
                                    StepMeasurement(
                                        step = CaptureStep.FRONT_PALM,
                                        widthMm = 18.0,
                                        confidence = 0.9f,
                                        measurementSource = WidthMeasurementSource.EDGE_PROFILE,
                                        usedFallback = false,
                                    ),
                                ),
                            stepDiagnostics = emptyList(),
                            bestScaleMmPerPxX = 0.1,
                            bestScaleMmPerPxY = 0.1,
                            calibrationStatus = CalibrationStatus.CALIBRATED,
                            frontalWidthPx = 120.0,
                            thicknessSamples = listOf(14.0),
                            debugNotes = emptyList(),
                            calibrationNotes = emptyList(),
                            overlays = listOf(DebugOverlayFrame("FRONT_PALM", byteArrayOf(9, 8, 7))),
                        )
                    },
                resultAssemblerOverride =
                    MeasurementEngineResultAssemblerPort { completedSteps, _ ->
                        assembledStepCandidates = completedSteps
                        HandMeasureResult(
                            targetFinger = apiConfig.targetFinger,
                            fingerWidthMm = 18.0,
                            fingerThicknessMm = 14.0,
                            estimatedCircumferenceMm = 52.0,
                            equivalentDiameterMm = 16.5,
                            suggestedRingSizeLabel = "US 6",
                            confidenceScore = 0.8f,
                            warnings = listOf(HandMeasureWarning.CALIBRATION_WEAK),
                            capturedSteps = emptyList(),
                            resultMode = ResultMode.HYBRID_ESTIMATE,
                            qualityLevel = QualityLevel.MEDIUM,
                            retryRecommended = false,
                            calibrationStatus = CalibrationStatus.CALIBRATED,
                            measurementSources = listOf(MeasurementSource.EDGE_PROFILE),
                        )
                    },
            )

        val result = engine.process(listOf(engineCandidate))

        assertThat(processedStepCandidates).hasSize(1)
        assertThat(processedStepCandidates.first().step).isEqualTo(CaptureStep.FRONT_PALM)
        assertThat(assembledStepCandidates).hasSize(1)
        assertThat(result.result.suggestedRingSizeLabel).isEqualTo("US 6")
        assertThat(result.result.warnings).containsExactly(com.handmeasure.core.measurement.HandMeasureWarning.CALIBRATION_WEAK)
        assertThat(result.overlays).hasSize(1)
        assertThat(result.overlays.first().stepName).isEqualTo("FRONT_PALM")
    }

    private class FakeHandLandmarkEngine : HandLandmarkEngine {
        override fun detect(bitmap: Bitmap): HandDetection? =
            HandDetection(
                imageLandmarks = List(21) { index -> Landmark2D(index.toFloat(), index.toFloat()) },
                worldLandmarks = List(21) { Landmark3D(0f, 0f, 0f) },
                handedness = "Right",
                confidence = 0.9f,
            )
    }
}
