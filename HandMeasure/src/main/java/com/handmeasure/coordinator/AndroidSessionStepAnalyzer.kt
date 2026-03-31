package com.handmeasure.coordinator

import android.graphics.BitmapFactory
import com.handmeasure.api.CaptureStep
import com.handmeasure.api.HandMeasureConfig
import com.handmeasure.measurement.FingerMeasurementEngine
import com.handmeasure.measurement.FingerWidthMeasurement
import com.handmeasure.measurement.MetricScale
import com.handmeasure.measurement.ScaleCalibrator
import com.handmeasure.measurement.ScaleCalibrationResult
import com.handmeasure.measurement.WidthMeasurementSource
import com.handmeasure.vision.HandLandmarkEngine
import com.handmeasure.vision.PoseClassifier
import com.handmeasure.vision.ReferenceCardDetector
import com.handmeasure.core.measurement.CalibrationStatus as CoreCalibrationStatus
import com.handmeasure.core.measurement.CaptureStep as CoreCaptureStep
import com.handmeasure.core.measurement.WidthMeasurementSource as CoreWidthMeasurementSource
import com.handmeasure.core.session.SessionCardDiagnostics
import com.handmeasure.core.session.SessionFingerMeasurement
import com.handmeasure.core.session.SessionOverlayFrame
import com.handmeasure.core.session.SessionScale
import com.handmeasure.core.session.SessionScaleDiagnostics
import com.handmeasure.core.session.SessionScaleResult
import com.handmeasure.core.session.SessionStepAnalysis
import com.handmeasure.core.session.SessionStepAnalyzer
import com.handmeasure.core.session.SessionStepCandidate

internal class AndroidSessionStepAnalyzer(
    private val config: HandMeasureConfig,
    private val handLandmarkEngine: HandLandmarkEngine,
    private val referenceCardDetector: ReferenceCardDetector,
    private val poseClassifier: PoseClassifier,
    private val scaleCalibrator: ScaleCalibrator,
    private val fingerMeasurementEngine: FingerMeasurementEngine,
    private val frameSignalEstimator: FrameSignalEstimator,
    private val frameAnnotator: DebugFrameAnnotator,
    private val poseTargets: Map<CaptureStep, PoseTarget>,
) : SessionStepAnalyzer {
    override fun analyze(
        candidate: SessionStepCandidate,
        currentScale: SessionScale,
    ): SessionStepAnalysis? {
        val bitmap = BitmapFactory.decodeByteArray(candidate.frameBytes, 0, candidate.frameBytes.size) ?: return null
        return try {
            val step = candidate.step.toApiStep()
            val hand = handLandmarkEngine.detect(bitmap)
            val card = referenceCardDetector.detect(bitmap)
            val poseScore =
                hand?.let { detectedHand ->
                    poseTargets[step]?.let { target -> poseClassifier.classify(target, detectedHand) }
                }
            val coplanarityProxyScore =
                frameSignalEstimator.estimateFingerCard2dProximity(
                    hand = hand,
                    card = card,
                    frameWidth = bitmap.width,
                    frameHeight = bitmap.height,
                    targetFinger = config.targetFinger,
                )
            val scaleResult = card?.let(scaleCalibrator::calibrateWithDiagnostics)
            val effectiveScale =
                scaleResult?.scale
                    ?: MetricScale(
                        mmPerPxX = currentScale.mmPerPxX,
                        mmPerPxY = currentScale.mmPerPxY,
                    )
            val measurement =
                hand?.let { detectedHand ->
                    fingerMeasurementEngine.measureVisibleWidth(
                        bitmap = bitmap,
                        handDetection = detectedHand,
                        targetFinger = config.targetFinger,
                        scale = effectiveScale,
                    )
                }
            val overlayFrame =
                if (config.debugOverlayEnabled) {
                    SessionOverlayFrame(
                        stepName = step.name,
                        jpegBytes = frameAnnotator.encodeAnnotatedJpeg(bitmap, hand, card),
                    )
                } else {
                    null
                }

            SessionStepAnalysis(
                poseScoreOverride = poseScore,
                coplanarityProxyScore = coplanarityProxyScore,
                cardDiagnostics =
                    card?.let { detection ->
                        SessionCardDiagnostics(
                            coverageRatio = detection.coverageRatio,
                            aspectResidual = detection.aspectResidual,
                            rectangularityScore = detection.rectangularityScore,
                            edgeSupportScore = detection.edgeSupportScore,
                            rectificationConfidence = detection.rectificationConfidence,
                        )
                    },
                scaleResult = scaleResult?.toCoreScaleResult(),
                measurement = measurement?.toCoreMeasurement(),
                overlayFrame = overlayFrame,
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun ScaleCalibrationResult.toCoreScaleResult(): SessionScaleResult =
        SessionScaleResult(
            scale =
                SessionScale(
                    mmPerPxX = scale.mmPerPxX,
                    mmPerPxY = scale.mmPerPxY,
                ),
            diagnostics =
                SessionScaleDiagnostics(
                    status = diagnostics.status.toCoreCalibrationStatus(),
                    notes = diagnostics.notes,
                ),
        )

    private fun com.handmeasure.api.CalibrationStatus.toCoreCalibrationStatus(): CoreCalibrationStatus =
        when (this) {
            com.handmeasure.api.CalibrationStatus.CALIBRATED -> CoreCalibrationStatus.CALIBRATED
            com.handmeasure.api.CalibrationStatus.DEGRADED -> CoreCalibrationStatus.DEGRADED
            com.handmeasure.api.CalibrationStatus.MISSING_REFERENCE -> CoreCalibrationStatus.MISSING_REFERENCE
        }

    private fun FingerWidthMeasurement.toCoreMeasurement(): SessionFingerMeasurement =
        SessionFingerMeasurement(
            widthPx = widthPx,
            widthMm = widthMm,
            usedFallback = usedFallback,
            source = source.toCoreWidthMeasurementSource(),
            validSamples = validSamples,
            widthVarianceMm = widthVarianceMm,
            sampledWidthsMm = sampledWidthsMm,
        )

    private fun WidthMeasurementSource.toCoreWidthMeasurementSource(): CoreWidthMeasurementSource =
        when (this) {
            WidthMeasurementSource.EDGE_PROFILE -> CoreWidthMeasurementSource.EDGE_PROFILE
            WidthMeasurementSource.LANDMARK_HEURISTIC -> CoreWidthMeasurementSource.LANDMARK_HEURISTIC
            WidthMeasurementSource.DEFAULT_HEURISTIC -> CoreWidthMeasurementSource.DEFAULT_HEURISTIC
        }

    private fun CoreCaptureStep.toApiStep(): CaptureStep =
        when (this) {
            CoreCaptureStep.FRONT_PALM -> CaptureStep.FRONT_PALM
            CoreCaptureStep.LEFT_OBLIQUE -> CaptureStep.LEFT_OBLIQUE
            CoreCaptureStep.RIGHT_OBLIQUE -> CaptureStep.RIGHT_OBLIQUE
            CoreCaptureStep.UP_TILT -> CaptureStep.UP_TILT
            CoreCaptureStep.DOWN_TILT -> CaptureStep.DOWN_TILT
            CoreCaptureStep.BACK_OF_HAND -> CaptureStep.BACK_OF_HAND
            CoreCaptureStep.LEFT_OBLIQUE_DORSAL -> CaptureStep.LEFT_OBLIQUE_DORSAL
            CoreCaptureStep.RIGHT_OBLIQUE_DORSAL -> CaptureStep.RIGHT_OBLIQUE_DORSAL
            CoreCaptureStep.UP_TILT_DORSAL -> CaptureStep.UP_TILT_DORSAL
            CoreCaptureStep.DOWN_TILT_DORSAL -> CaptureStep.DOWN_TILT_DORSAL
        }
}
