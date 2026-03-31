package com.handmeasure.coordinator

import android.graphics.BitmapFactory
import com.handmeasure.api.CaptureStep
import com.handmeasure.api.HandMeasureConfig
import com.handmeasure.measurement.MetricScale
import com.handmeasure.measurement.ScaleCalibrator
import com.handmeasure.measurement.ScaleCalibrationResult
import com.handmeasure.vision.HandLandmarkEngine
import com.handmeasure.vision.PoseClassifier
import com.handmeasure.vision.ReferenceCardDetector
import com.handmeasure.core.session.SessionCardDiagnostics
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
    private val fingerMeasurementPort: AndroidFingerMeasurementPort,
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
                    fingerMeasurementPort.measureVisibleWidth(bitmap, detectedHand, config.targetFinger, effectiveScale)
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
                measurement = measurement,
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
}
