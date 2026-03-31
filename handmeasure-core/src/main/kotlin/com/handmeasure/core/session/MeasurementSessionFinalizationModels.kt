package com.handmeasure.core.session

import com.handmeasure.core.measurement.CalibrationStatus
import com.handmeasure.core.measurement.CaptureStep
import com.handmeasure.core.measurement.MeasurementSource
import com.handmeasure.core.measurement.StepMeasurement
import com.handmeasure.core.measurement.WidthMeasurementSource

data class SessionStepCandidate(
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

data class SessionQualityThresholds(
    val cardMinScore: Float,
    val lightingMinScore: Float,
    val blurMinScore: Float,
    val motionMinScore: Float,
)

data class SessionScale(
    val mmPerPxX: Double,
    val mmPerPxY: Double,
)

data class SessionScaleDiagnostics(
    val status: CalibrationStatus,
    val notes: List<String>,
)

data class SessionScaleResult(
    val scale: SessionScale,
    val diagnostics: SessionScaleDiagnostics,
)

data class SessionCardDiagnostics(
    val coverageRatio: Float,
    val aspectResidual: Float,
    val rectangularityScore: Float,
    val edgeSupportScore: Float,
    val rectificationConfidence: Float,
)

data class SessionFingerMeasurement(
    val widthPx: Double,
    val widthMm: Double,
    val usedFallback: Boolean,
    val source: WidthMeasurementSource,
    val validSamples: Int = 0,
    val widthVarianceMm: Double = 0.0,
    val sampledWidthsMm: List<Double> = emptyList(),
)

data class SessionOverlayFrame(
    val stepName: String,
    val jpegBytes: ByteArray,
)

data class SessionStepAnalysis(
    val poseScoreOverride: Float? = null,
    val coplanarityProxyScore: Float = 0f,
    val cardDiagnostics: SessionCardDiagnostics? = null,
    val scaleResult: SessionScaleResult? = null,
    val measurement: SessionFingerMeasurement? = null,
    val overlayFrame: SessionOverlayFrame? = null,
)

data class SessionStepDiagnostics(
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
    val usedFallback: Boolean,
    val coplanarityProxyScore: Float = 0f,
    val coplanarityProxyKind: String = "finger_card_2d_proximity",
)

data class SessionProcessingResult(
    val warnings: Set<com.handmeasure.core.measurement.HandMeasureWarning>,
    val stepMeasurements: List<StepMeasurement>,
    val stepDiagnostics: List<SessionStepDiagnostics>,
    val bestScaleMmPerPxX: Double,
    val bestScaleMmPerPxY: Double,
    val calibrationStatus: CalibrationStatus,
    val frontalWidthPx: Double,
    val thicknessSamples: List<Double>,
    val debugNotes: List<String>,
    val calibrationNotes: List<String>,
    val overlays: List<SessionOverlayFrame>,
)

fun interface SessionStepAnalyzer {
    fun analyze(
        candidate: SessionStepCandidate,
        currentScale: SessionScale,
    ): SessionStepAnalysis?
}
