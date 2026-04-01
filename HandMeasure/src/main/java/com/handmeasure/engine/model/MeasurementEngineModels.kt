package com.handmeasure.engine.model

import com.handmeasure.core.measurement.CalibrationStatus
import com.handmeasure.core.measurement.CaptureStep
import com.handmeasure.core.measurement.HandMeasureWarning
import com.handmeasure.core.measurement.MeasurementSource
import com.handmeasure.core.measurement.QualityLevel
import com.handmeasure.core.measurement.ResultMode
import com.handmeasure.core.measurement.RingSizeTable

enum class MeasurementTargetFinger {
    THUMB,
    INDEX,
    MIDDLE,
    RING,
    LITTLE,
}

enum class MeasurementLensFacing {
    BACK,
    FRONT,
}

enum class MeasurementCaptureProtocol {
    DORSAL_V1,
    PALMAR_V1,
}

data class MeasurementEngineQualityThresholds(
    val autoCaptureScore: Float = 0.84f,
    val bestCandidateProgressScore: Float = 0.56f,
    val handMinScore: Float = 0.45f,
    val cardMinScore: Float = 0.45f,
    val lightingMinScore: Float = 0.35f,
    val blurMinScore: Float = 0.35f,
    val motionMinScore: Float = 0.35f,
)

data class MeasurementEngineConfig(
    val targetFinger: MeasurementTargetFinger = MeasurementTargetFinger.RING,
    val debugOverlayEnabled: Boolean = false,
    val debugExportEnabled: Boolean = false,
    val debugReplayInputPath: String? = null,
    val qualityThresholds: MeasurementEngineQualityThresholds = MeasurementEngineQualityThresholds(),
    val ringSizeTable: RingSizeTable,
    val lensFacing: MeasurementLensFacing = MeasurementLensFacing.BACK,
    val protocol: MeasurementCaptureProtocol = MeasurementCaptureProtocol.DORSAL_V1,
)

data class MeasurementEngineStepCandidate(
    val step: CaptureStep,
    val frameBytes: ByteArray,
    val qualityScore: Float,
    val poseScore: Float,
    val cardScore: Float,
    val handScore: Float,
    val blurScore: Float = 0f,
    val motionScore: Float = 0f,
    val lightingScore: Float = 0f,
    val confidencePenaltyReasons: List<String> = emptyList(),
)

data class MeasurementEngineCapturedStepInfo(
    val step: CaptureStep,
    val score: Float,
    val poseScore: Float,
    val cardScore: Float,
    val handScore: Float,
)

data class MeasurementEngineStepDiagnostics(
    val step: CaptureStep,
    val handScore: Float,
    val cardScore: Float,
    val poseScore: Float,
    val blurScore: Float,
    val motionScore: Float,
    val lightingScore: Float,
    val cardCoverageRatio: Float,
    val cardAspectResidual: Float,
    val cardRectangularityScore: Float,
    val cardEdgeSupportScore: Float,
    val cardRectificationConfidence: Float,
    val scaleMmPerPxX: Double,
    val scaleMmPerPxY: Double,
    val widthSamplesMm: List<Double>,
    val widthVarianceMm: Double,
    val accepted: Boolean,
    val rejectedReason: String?,
    val confidencePenaltyReasons: List<String>,
    val measurementSource: MeasurementSource,
    val usedFallback: Boolean = false,
    val coplanarityProxyScore: Float = 0f,
    val coplanarityProxyKind: String = "finger_card_2d_proximity",
)

data class MeasurementEngineFusedDiagnostics(
    val widthMm: Double,
    val thicknessMm: Double,
    val circumferenceMm: Double,
    val equivalentDiameterMm: Double,
    val suggestedRingSizeLabel: String,
    val finalConfidence: Float,
    val warningReasons: List<String>,
    val perStepResidualsMm: List<Double>,
    val resultMode: ResultMode = ResultMode.HYBRID_ESTIMATE,
    val qualityLevel: QualityLevel = QualityLevel.LOW,
    val retryRecommended: Boolean = true,
    val calibrationStatus: CalibrationStatus = CalibrationStatus.MISSING_REFERENCE,
    val measurementSources: List<MeasurementSource> = emptyList(),
)

data class MeasurementEngineSessionDiagnostics(
    val stepDiagnostics: List<MeasurementEngineStepDiagnostics>,
    val fusedDiagnostics: MeasurementEngineFusedDiagnostics,
)

data class MeasurementEngineDebugMetadata(
    val mmPerPxX: Double,
    val mmPerPxY: Double,
    val frontalWidthPx: Double,
    val thicknessSamplesMm: List<Double>,
    val rawNotes: List<String>,
    val sessionDiagnostics: MeasurementEngineSessionDiagnostics? = null,
)

data class MeasurementEngineResult(
    val targetFinger: MeasurementTargetFinger,
    val fingerWidthMm: Double,
    val fingerThicknessMm: Double,
    val estimatedCircumferenceMm: Double,
    val equivalentDiameterMm: Double,
    val suggestedRingSizeLabel: String,
    val confidenceScore: Float,
    val warnings: List<HandMeasureWarning>,
    val capturedSteps: List<MeasurementEngineCapturedStepInfo>,
    val resultMode: ResultMode = ResultMode.HYBRID_ESTIMATE,
    val qualityLevel: QualityLevel = QualityLevel.LOW,
    val retryRecommended: Boolean = true,
    val calibrationStatus: CalibrationStatus = CalibrationStatus.MISSING_REFERENCE,
    val measurementSources: List<MeasurementSource> = emptyList(),
    val debugMetadata: MeasurementEngineDebugMetadata? = null,
)

data class MeasurementEngineOverlayFrame(
    val stepName: String,
    val jpegBytes: ByteArray,
)

data class MeasurementEngineProcessingResult(
    val result: MeasurementEngineResult,
    val overlays: List<MeasurementEngineOverlayFrame>,
)
