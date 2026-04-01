package com.handmeasure.engine.factory

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import com.handmeasure.api.HandMeasureConfig
import com.handmeasure.core.measurement.CaptureStep
import com.handmeasure.core.measurement.HandMeasureWarning
import com.handmeasure.engine.MeasurementEngineProcessingPort
import com.handmeasure.engine.compat.MeasurementEngineApiMapper
import com.handmeasure.engine.model.MeasurementEngineCapturedStepInfo
import com.handmeasure.engine.model.MeasurementEngineProcessingResult
import com.handmeasure.engine.model.MeasurementEngineResult
import com.handmeasure.engine.model.MeasurementEngineStepCandidate
import com.handmeasure.engine.model.MeasurementTargetFinger
import com.handmeasure.vision.HandDetection
import com.handmeasure.vision.HandLandmarkEngine
import com.handmeasure.vision.Landmark2D
import com.handmeasure.vision.Landmark3D
import org.junit.Test

class AndroidMeasurementEngineFactoryTest {
    @Test
    fun create_usesProvidedProcessingPortForEngineFacade() {
        val mapper = MeasurementEngineApiMapper()
        val config = mapper.toEngineConfig(HandMeasureConfig())
        val candidate =
            MeasurementEngineStepCandidate(
                step = CaptureStep.FRONT_PALM,
                frameBytes = byteArrayOf(1, 2, 3),
                qualityScore = 0.9f,
                poseScore = 0.8f,
                cardScore = 0.8f,
                handScore = 0.9f,
            )
        var processedCandidates: List<MeasurementEngineStepCandidate> = emptyList()
        val engine =
            AndroidMeasurementEngineFactory.create(
                config = config,
                handLandmarkEngine = FakeHandLandmarkEngine(),
                mapper = mapper,
                processingPortOverride =
                    MeasurementEngineProcessingPort { steps ->
                        processedCandidates = steps
                        MeasurementEngineProcessingResult(
                            result =
                                MeasurementEngineResult(
                                    targetFinger = MeasurementTargetFinger.RING,
                                    fingerWidthMm = 18.0,
                                    fingerThicknessMm = 14.0,
                                    estimatedCircumferenceMm = 52.0,
                                    equivalentDiameterMm = 16.5,
                                    suggestedRingSizeLabel = "US 6",
                                    confidenceScore = 0.8f,
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
                                ),
                            overlays = emptyList(),
                        )
                    },
            )

        val result = engine.process(listOf(candidate))

        assertThat(processedCandidates).containsExactly(candidate)
        assertThat(result.result.suggestedRingSizeLabel).isEqualTo("US 6")
    }

    private class FakeHandLandmarkEngine : HandLandmarkEngine {
        override fun detect(bitmap: Bitmap): HandDetection? =
            HandDetection(
                imageLandmarks = List(21) { index -> Landmark2D(index.toFloat(), index.toFloat()) },
                worldLandmarks = List(21) { Landmark3D(0f, 0f, 0f) },
                handedness = "Right",
                confidence = 0.95f,
            )
    }
}
