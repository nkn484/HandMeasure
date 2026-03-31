package com.handmeasure.core.session

import com.handmeasure.core.measurement.CalibrationStatus
import com.handmeasure.core.measurement.CaptureStep
import com.handmeasure.core.measurement.HandMeasureWarning
import com.handmeasure.core.measurement.MeasurementSource
import com.handmeasure.core.measurement.StepMeasurement
import com.handmeasure.core.measurement.WidthMeasurementSource

class MeasurementSessionFinalizationUseCase(
    private val stepAnalyzer: SessionStepAnalyzer,
) {
    fun process(
        stepCandidates: List<SessionStepCandidate>,
        thresholds: SessionQualityThresholds,
    ): SessionProcessingResult {
        val warnings = mutableSetOf<HandMeasureWarning>()
        val stepMeasurements = mutableListOf<StepMeasurement>()
        val debugNotes = mutableListOf<String>()
        val stepDiagnostics = mutableListOf<SessionStepDiagnostics>()
        var bestScaleMmPerPxX = DEFAULT_MM_PER_PX
        var bestScaleMmPerPxY = DEFAULT_MM_PER_PX
        var calibrationStatus = CalibrationStatus.MISSING_REFERENCE
        var frontalWidthPx = 0.0
        val thicknessSamples = mutableListOf<Double>()
        val calibrationNotes = mutableListOf<String>()
        val overlays = mutableListOf<SessionOverlayFrame>()

        stepCandidates.forEach { candidate ->
            val currentScale = SessionScale(bestScaleMmPerPxX, bestScaleMmPerPxY)
            val analysis = stepAnalyzer.analyze(candidate, currentScale)
            if (analysis == null) return@forEach
            val poseScore = analysis.poseScoreOverride ?: candidate.poseScore
            val card = analysis.cardDiagnostics
            val scaleResult = analysis.scaleResult
            val measurement = analysis.measurement
            val coplanarityProxyScore = analysis.coplanarityProxyScore

            if (candidate.cardScore < thresholds.cardMinScore) warnings += HandMeasureWarning.LOW_CARD_CONFIDENCE
            if (poseScore < POSE_MIN_SCORE) warnings += HandMeasureWarning.LOW_POSE_CONFIDENCE
            if (candidate.lightingScore < thresholds.lightingMinScore) warnings += HandMeasureWarning.LOW_LIGHTING

            if (scaleResult != null) {
                bestScaleMmPerPxX = scaleResult.scale.mmPerPxX
                bestScaleMmPerPxY = scaleResult.scale.mmPerPxY
                calibrationNotes += "step=${candidate.step.name}:${scaleResult.diagnostics.notes.joinToString("|")}"
                calibrationStatus =
                    when {
                        calibrationStatus == CalibrationStatus.DEGRADED -> CalibrationStatus.DEGRADED
                        scaleResult.diagnostics.status == CalibrationStatus.DEGRADED -> CalibrationStatus.DEGRADED
                        calibrationStatus == CalibrationStatus.CALIBRATED -> CalibrationStatus.CALIBRATED
                        else -> scaleResult.diagnostics.status
                    }
                if (scaleResult.diagnostics.status == CalibrationStatus.DEGRADED) {
                    warnings += HandMeasureWarning.CALIBRATION_WEAK
                }
            } else {
                warnings += HandMeasureWarning.BEST_EFFORT_ESTIMATE
            }

            val effectiveScale = scaleResult?.scale ?: currentScale
            if (measurement == null) {
                warnings += HandMeasureWarning.BEST_EFFORT_ESTIMATE
            }
            val measurementSource = measurement?.source ?: WidthMeasurementSource.DEFAULT_HEURISTIC
            val widthMm = measurement?.widthMm ?: DEFAULT_WIDTH_MM
            val usedFallback = measurement?.usedFallback ?: true
            val measurementConfidence = measurementConfidence(measurement)
            if (usedFallback) warnings += HandMeasureWarning.BEST_EFFORT_ESTIMATE
            if (candidate.step == CaptureStep.FRONT_PALM) {
                frontalWidthPx = measurement?.widthPx ?: frontalWidthPx
            } else {
                thicknessSamples += widthMm
            }

            stepMeasurements +=
                StepMeasurement(
                    step = candidate.step,
                    widthMm = widthMm,
                    confidence = candidate.qualityScore,
                    measurementConfidence = measurementConfidence,
                    rawWidthMm = widthMm,
                    measurementSource = measurementSource,
                    usedFallback = usedFallback,
                    debugNotes =
                        listOf(
                            "validSamples=${measurement?.validSamples ?: 0}",
                            "widthVarianceMm=${measurement?.widthVarianceMm ?: -1.0}",
                            "fallback=$usedFallback",
                            "source=$measurementSource",
                        ),
                )

            stepDiagnostics +=
                SessionStepDiagnostics(
                    step = candidate.step,
                    handScore = candidate.handScore,
                    cardScore = candidate.cardScore,
                    poseScore = poseScore,
                    blurScore = candidate.blurScore,
                    motionScore = candidate.motionScore,
                    lightingScore = candidate.lightingScore,
                    cardCoverageRatio = card?.coverageRatio ?: 0f,
                    cardAspectResidual = card?.aspectResidual ?: 1f,
                    cardRectangularityScore = card?.rectangularityScore ?: 0f,
                    cardEdgeSupportScore = card?.edgeSupportScore ?: 0f,
                    cardRectificationConfidence = card?.rectificationConfidence ?: 0f,
                    scaleMmPerPxX = effectiveScale.mmPerPxX,
                    scaleMmPerPxY = effectiveScale.mmPerPxY,
                    widthSamplesMm = measurement?.sampledWidthsMm ?: emptyList(),
                    widthVarianceMm = measurement?.widthVarianceMm ?: DEFAULT_VARIANCE_FALLBACK,
                    accepted = measurement != null,
                    rejectedReason = if (measurement == null || measurement.usedFallback) "fallback_or_no_edges" else null,
                    confidencePenaltyReasons = candidate.confidencePenaltyReasons,
                    measurementSource = measurementSource.toCoreSource(),
                    usedFallback = usedFallback,
                    coplanarityProxyScore = coplanarityProxyScore,
                )

            analysis.overlayFrame?.let(overlays::add)
        }

        if (stepMeasurements.isEmpty()) {
            warnings += HandMeasureWarning.BEST_EFFORT_ESTIMATE
            stepMeasurements +=
                StepMeasurement(
                    step = CaptureStep.FRONT_PALM,
                    widthMm = DEFAULT_WIDTH_MM,
                    confidence = DEFAULT_FALLBACK_CONFIDENCE,
                    measurementConfidence = DEFAULT_FALLBACK_CONFIDENCE,
                    measurementSource = WidthMeasurementSource.DEFAULT_HEURISTIC,
                    usedFallback = true,
                )
        }

        if (stepCandidates.any { it.blurScore < thresholds.blurMinScore }) warnings += HandMeasureWarning.HIGH_BLUR
        if (stepCandidates.any { it.motionScore < thresholds.motionMinScore }) warnings += HandMeasureWarning.HIGH_MOTION

        return SessionProcessingResult(
            warnings = warnings,
            stepMeasurements = stepMeasurements,
            stepDiagnostics = stepDiagnostics,
            bestScaleMmPerPxX = bestScaleMmPerPxX,
            bestScaleMmPerPxY = bestScaleMmPerPxY,
            calibrationStatus = calibrationStatus,
            frontalWidthPx = frontalWidthPx,
            thicknessSamples = thicknessSamples,
            debugNotes = debugNotes,
            calibrationNotes = calibrationNotes,
            overlays = overlays,
        )
    }

    private fun measurementConfidence(measurement: SessionFingerMeasurement?): Float {
        if (measurement == null) return DEFAULT_FALLBACK_CONFIDENCE
        return (
            (if (measurement.usedFallback) 0.35f else 0.85f) * 0.35f +
                (1f - (measurement.widthVarianceMm / 4.0).toFloat().coerceIn(0f, 1f)) * 0.35f +
                (measurement.validSamples / 7f).coerceIn(0f, 1f) * 0.30f
        ).coerceIn(0f, 1f)
    }

    private fun WidthMeasurementSource.toCoreSource(): MeasurementSource =
        when (this) {
            WidthMeasurementSource.EDGE_PROFILE -> MeasurementSource.EDGE_PROFILE
            WidthMeasurementSource.LANDMARK_HEURISTIC -> MeasurementSource.LANDMARK_HEURISTIC
            WidthMeasurementSource.DEFAULT_HEURISTIC -> MeasurementSource.DEFAULT_HEURISTIC
        }

    private companion object {
        const val DEFAULT_MM_PER_PX = 0.12
        const val POSE_MIN_SCORE = 0.45f
        const val DEFAULT_WIDTH_MM = 18.0
        const val DEFAULT_FALLBACK_CONFIDENCE = 0.18f
        const val DEFAULT_VARIANCE_FALLBACK = 999.0
    }
}
