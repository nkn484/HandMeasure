package com.handmeasure.coordinator

import android.graphics.BitmapFactory
import com.handmeasure.api.CalibrationStatus
import com.handmeasure.api.CaptureStep
import com.handmeasure.api.HandMeasureConfig
import com.handmeasure.api.HandMeasureWarning
import com.handmeasure.api.StepDiagnostics
import com.handmeasure.flow.StepCandidate
import com.handmeasure.measurement.FingerMeasurementEngine
import com.handmeasure.measurement.MetricScale
import com.handmeasure.measurement.ScaleCalibrator
import com.handmeasure.measurement.StepMeasurement
import com.handmeasure.measurement.WidthMeasurementSource
import com.handmeasure.measurement.toApiSource
import com.handmeasure.vision.HandLandmarkEngine
import com.handmeasure.vision.PoseClassifier

internal data class SessionProcessingOutput(
    val warnings: Set<HandMeasureWarning>,
    val stepMeasurements: List<StepMeasurement>,
    val stepDiagnostics: List<StepDiagnostics>,
    val bestScaleMmPerPxX: Double,
    val bestScaleMmPerPxY: Double,
    val calibrationStatus: CalibrationStatus,
    val frontalWidthPx: Double,
    val thicknessSamples: List<Double>,
    val debugNotes: List<String>,
    val calibrationNotes: List<String>,
    val overlays: List<DebugOverlayFrame>,
)

internal class MeasurementSessionProcessor(
    private val config: HandMeasureConfig,
    private val handLandmarkEngine: HandLandmarkEngine,
    private val referenceCardDetector: com.handmeasure.vision.ReferenceCardDetector,
    private val poseClassifier: PoseClassifier,
    private val scaleCalibrator: ScaleCalibrator,
    private val fingerMeasurementEngine: FingerMeasurementEngine,
    private val frameSignalEstimator: FrameSignalEstimator,
    private val frameAnnotator: DebugFrameAnnotator,
) {
    fun process(stepResults: List<StepCandidate>): SessionProcessingOutput {
        val warnings = mutableSetOf<HandMeasureWarning>()
        val stepMeasurements = mutableListOf<StepMeasurement>()
        val debugNotes = mutableListOf<String>()
        val stepDiagnostics = mutableListOf<StepDiagnostics>()
        var bestScaleMmPerPxX = 0.12
        var bestScaleMmPerPxY = 0.12
        var calibrationStatus = CalibrationStatus.MISSING_REFERENCE
        var frontalWidthPx = 0.0
        val thicknessSamples = mutableListOf<Double>()
        val calibrationNotes = mutableListOf<String>()
        val overlays = mutableListOf<DebugOverlayFrame>()

        stepResults.forEach { candidate ->
            val bitmap = BitmapFactory.decodeByteArray(candidate.frameBytes, 0, candidate.frameBytes.size) ?: return@forEach
            try {
                val hand = handLandmarkEngine.detect(bitmap)
                val card = referenceCardDetector.detect(bitmap)
                val poseScore = hand?.let { poseClassifier.classify(candidate.step, it) } ?: candidate.poseScore
                val coplanarityProxyScore =
                    frameSignalEstimator.estimateFingerCard2dProximity(
                        hand = hand,
                        card = card,
                        frameWidth = bitmap.width,
                        frameHeight = bitmap.height,
                        targetFinger = config.targetFinger,
                    )

                if (candidate.cardScore < config.qualityThresholds.cardMinScore) warnings += HandMeasureWarning.LOW_CARD_CONFIDENCE
                if (poseScore < 0.45f) warnings += HandMeasureWarning.LOW_POSE_CONFIDENCE
                if (candidate.lightingScore < config.qualityThresholds.lightingMinScore) warnings += HandMeasureWarning.LOW_LIGHTING

                val scaleResult =
                    if (card != null) {
                        scaleCalibrator.calibrateWithDiagnostics(card)
                    } else {
                        warnings += HandMeasureWarning.BEST_EFFORT_ESTIMATE
                        null
                    }
                val scale = scaleResult?.scale
                if (scale != null) {
                    bestScaleMmPerPxX = scale.mmPerPxX
                    bestScaleMmPerPxY = scale.mmPerPxY
                }
                if (scaleResult != null) {
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
                }

                val effectiveScale = scale ?: MetricScale(bestScaleMmPerPxX, bestScaleMmPerPxY)
                val measurement =
                    if (hand != null) {
                        fingerMeasurementEngine.measureVisibleWidth(bitmap, hand, config.targetFinger, effectiveScale)
                    } else {
                        warnings += HandMeasureWarning.BEST_EFFORT_ESTIMATE
                        null
                    }

                val measurementSource = measurement?.source ?: WidthMeasurementSource.DEFAULT_HEURISTIC
                val widthMm = measurement?.widthMm ?: 18.0
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
                    StepDiagnostics(
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
                        widthVarianceMm = measurement?.widthVarianceMm ?: 999.0,
                        accepted = measurement != null,
                        rejectedReason = if (measurement == null || measurement.usedFallback) "fallback_or_no_edges" else null,
                        confidencePenaltyReasons = candidate.confidencePenaltyReasons,
                        measurementSource = measurementSource.toApiSource(),
                        usedFallback = usedFallback,
                        coplanarityProxyScore = coplanarityProxyScore,
                    )

                if (config.debugOverlayEnabled) {
                    overlays +=
                        DebugOverlayFrame(
                            stepName = candidate.step.name,
                            jpegBytes = frameAnnotator.encodeAnnotatedJpeg(bitmap, hand, card),
                        )
                }
            } finally {
                bitmap.recycle()
            }
        }

        if (stepMeasurements.isEmpty()) {
            warnings += HandMeasureWarning.BEST_EFFORT_ESTIMATE
            stepMeasurements +=
                StepMeasurement(
                    step = CaptureStep.FRONT_PALM,
                    widthMm = 18.0,
                    confidence = 0.2f,
                    measurementConfidence = 0.2f,
                    measurementSource = WidthMeasurementSource.DEFAULT_HEURISTIC,
                    usedFallback = true,
                )
        }

        if (stepResults.any { it.blurScore < config.qualityThresholds.blurMinScore }) warnings += HandMeasureWarning.HIGH_BLUR
        if (stepResults.any { it.motionScore < config.qualityThresholds.motionMinScore }) warnings += HandMeasureWarning.HIGH_MOTION

        return SessionProcessingOutput(
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

    private fun measurementConfidence(measurement: com.handmeasure.measurement.FingerWidthMeasurement?): Float {
        if (measurement == null) return 0.18f
        return (
            (if (measurement.usedFallback) 0.35f else 0.85f) * 0.35f +
                (1f - (measurement.widthVarianceMm / 4.0).toFloat().coerceIn(0f, 1f)) * 0.35f +
                (measurement.validSamples / 7f).coerceIn(0f, 1f) * 0.30f
        ).coerceIn(0f, 1f)
    }
}
