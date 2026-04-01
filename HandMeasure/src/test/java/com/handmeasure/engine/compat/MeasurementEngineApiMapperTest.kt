package com.handmeasure.engine.compat

import com.google.common.truth.Truth.assertThat
import com.handmeasure.api.CalibrationStatus
import com.handmeasure.api.CaptureProtocol
import com.handmeasure.api.CaptureStep
import com.handmeasure.api.CapturedStepInfo
import com.handmeasure.api.DebugMetadata
import com.handmeasure.api.FusedDiagnostics
import com.handmeasure.api.HandMeasureConfig
import com.handmeasure.api.HandMeasureResult
import com.handmeasure.api.HandMeasureWarning
import com.handmeasure.api.LensFacing
import com.handmeasure.api.MeasurementSource
import com.handmeasure.api.QualityLevel
import com.handmeasure.api.QualityThresholds
import com.handmeasure.api.ResultMode
import com.handmeasure.api.RingSizeEntry
import com.handmeasure.api.RingSizeTable
import com.handmeasure.api.SessionDiagnostics
import com.handmeasure.api.StepDiagnostics
import com.handmeasure.api.TargetFinger
import com.handmeasure.core.measurement.HandMeasureWarning as CoreHandMeasureWarning
import com.handmeasure.core.measurement.MeasurementSource as CoreMeasurementSource
import com.handmeasure.core.measurement.ResultMode as CoreResultMode
import org.junit.Test

class MeasurementEngineApiMapperTest {
    private val mapper = MeasurementEngineApiMapper()

    @Test
    fun configMapping_roundTripsBetweenApiAndEngineModels() {
        val apiConfig =
            HandMeasureConfig(
                targetFinger = TargetFinger.MIDDLE,
                debugOverlayEnabled = true,
                debugExportEnabled = true,
                debugReplayInputPath = "input/path",
                qualityThresholds =
                    QualityThresholds(
                        autoCaptureScore = 0.8f,
                        bestCandidateProgressScore = 0.55f,
                        handMinScore = 0.4f,
                        cardMinScore = 0.42f,
                        lightingMinScore = 0.33f,
                        blurMinScore = 0.31f,
                        motionMinScore = 0.32f,
                    ),
                ringSizeTable =
                    RingSizeTable(
                        name = "Custom",
                        entries = listOf(RingSizeEntry("A", 15.0), RingSizeEntry("B", 16.0)),
                    ),
                lensFacing = LensFacing.FRONT,
                protocol = CaptureProtocol.PALMAR_V1,
            )

        val engineConfig = mapper.toEngineConfig(apiConfig)
        val mappedBack = mapper.toApiConfig(engineConfig)

        assertThat(mappedBack).isEqualTo(apiConfig)
    }

    @Test
    fun resultMapping_mapsEngineResultBackToApiModel() {
        val engineResult =
            mapper.toEngineResult(
                HandMeasureResult(
                    targetFinger = TargetFinger.RING,
                    fingerWidthMm = 18.1,
                    fingerThicknessMm = 14.2,
                    estimatedCircumferenceMm = 52.1,
                    equivalentDiameterMm = 16.6,
                    suggestedRingSizeLabel = "US 6",
                    confidenceScore = 0.83f,
                    warnings = listOf(HandMeasureWarning.CALIBRATION_WEAK),
                    capturedSteps =
                        listOf(
                            CapturedStepInfo(
                                step = CaptureStep.FRONT_PALM,
                                score = 0.9f,
                                poseScore = 0.8f,
                                cardScore = 0.85f,
                                handScore = 0.88f,
                            ),
                        ),
                    resultMode = ResultMode.HYBRID_ESTIMATE,
                    qualityLevel = QualityLevel.MEDIUM,
                    retryRecommended = false,
                    calibrationStatus = CalibrationStatus.DEGRADED,
                    measurementSources = listOf(MeasurementSource.EDGE_PROFILE),
                    debugMetadata =
                        DebugMetadata(
                            mmPerPxX = 0.1,
                            mmPerPxY = 0.11,
                            frontalWidthPx = 123.0,
                            thicknessSamplesMm = listOf(14.1),
                            rawNotes = listOf("note"),
                            sessionDiagnostics =
                                SessionDiagnostics(
                                    stepDiagnostics =
                                        listOf(
                                            StepDiagnostics(
                                                step = CaptureStep.FRONT_PALM,
                                                handScore = 0.9f,
                                                cardScore = 0.8f,
                                                poseScore = 0.8f,
                                                blurScore = 0.9f,
                                                motionScore = 0.9f,
                                                lightingScore = 0.8f,
                                                cardCoverageRatio = 0.3f,
                                                cardAspectResidual = 0.1f,
                                                cardRectangularityScore = 0.8f,
                                                cardEdgeSupportScore = 0.8f,
                                                cardRectificationConfidence = 0.8f,
                                                scaleMmPerPxX = 0.1,
                                                scaleMmPerPxY = 0.1,
                                                widthSamplesMm = listOf(18.1),
                                                widthVarianceMm = 0.3,
                                                accepted = true,
                                                rejectedReason = null,
                                                confidencePenaltyReasons = emptyList(),
                                                measurementSource = MeasurementSource.EDGE_PROFILE,
                                                usedFallback = false,
                                            ),
                                        ),
                                    fusedDiagnostics =
                                        FusedDiagnostics(
                                            widthMm = 18.1,
                                            thicknessMm = 14.2,
                                            circumferenceMm = 52.1,
                                            equivalentDiameterMm = 16.6,
                                            suggestedRingSizeLabel = "US 6",
                                            finalConfidence = 0.83f,
                                            warningReasons = listOf("CALIBRATION_WEAK"),
                                            perStepResidualsMm = listOf(0.2),
                                            resultMode = ResultMode.HYBRID_ESTIMATE,
                                            qualityLevel = QualityLevel.MEDIUM,
                                            retryRecommended = false,
                                            calibrationStatus = CalibrationStatus.DEGRADED,
                                            measurementSources = listOf(MeasurementSource.EDGE_PROFILE),
                                        ),
                                ),
                        ),
                ),
            )

        val apiResult = mapper.toApiResult(engineResult)

        assertThat(apiResult.suggestedRingSizeLabel).isEqualTo("US 6")
        assertThat(apiResult.warnings).containsExactly(HandMeasureWarning.CALIBRATION_WEAK)
        assertThat(apiResult.measurementSources).containsExactly(MeasurementSource.EDGE_PROFILE)
        assertThat(apiResult.resultMode).isEqualTo(ResultMode.HYBRID_ESTIMATE)
    }

    @Test
    fun resultMapping_mapsCoreEnumsFromEngineModel() {
        val apiResult =
            mapper.toApiResult(
                mapper.toEngineResult(
                    HandMeasureResult(
                        targetFinger = TargetFinger.INDEX,
                        fingerWidthMm = 17.0,
                        fingerThicknessMm = 13.0,
                        estimatedCircumferenceMm = 49.0,
                        equivalentDiameterMm = 15.6,
                        suggestedRingSizeLabel = "US 5",
                        confidenceScore = 0.7f,
                        warnings = listOf(HandMeasureWarning.BEST_EFFORT_ESTIMATE),
                        capturedSteps = emptyList(),
                        resultMode = ResultMode.FALLBACK_ESTIMATE,
                        qualityLevel = QualityLevel.LOW,
                        retryRecommended = true,
                        calibrationStatus = CalibrationStatus.MISSING_REFERENCE,
                        measurementSources = listOf(MeasurementSource.DEFAULT_HEURISTIC),
                    ),
                ).copy(
                    warnings = listOf(CoreHandMeasureWarning.LOW_CARD_CONFIDENCE),
                    measurementSources = listOf(CoreMeasurementSource.FUSION_ESTIMATE),
                    resultMode = CoreResultMode.DIRECT_MEASUREMENT,
                ),
            )

        assertThat(apiResult.warnings).containsExactly(HandMeasureWarning.LOW_CARD_CONFIDENCE)
        assertThat(apiResult.measurementSources).containsExactly(MeasurementSource.FUSION_ESTIMATE)
        assertThat(apiResult.resultMode).isEqualTo(ResultMode.DIRECT_MEASUREMENT)
    }
}
