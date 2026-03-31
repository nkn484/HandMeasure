package com.handmeasure.coordinator

import android.graphics.Bitmap
import com.handmeasure.api.CaptureStep
import com.handmeasure.api.TargetFinger
import com.handmeasure.measurement.MetricScale
import com.handmeasure.measurement.ScaleCalibrator
import com.handmeasure.measurement.ScaleCalibrationResult
import com.handmeasure.vision.CardDetection
import com.handmeasure.vision.HandDetection
import com.handmeasure.vision.HandLandmarkEngine
import com.handmeasure.vision.PoseClassifier
import com.handmeasure.vision.ReferenceCardDetector
import com.handmeasure.core.measurement.CaptureStep as CoreCaptureStep
import com.handmeasure.core.session.SessionCardDiagnostics
import com.handmeasure.core.session.SessionFingerMeasurement
import com.handmeasure.core.session.SessionScale
import com.handmeasure.core.session.SessionScaleDiagnostics
import com.handmeasure.core.session.SessionScaleResult

internal class AndroidSessionRuntimeAnalyzerPort(
    private val handLandmarkEngine: HandLandmarkEngine,
    private val referenceCardDetector: ReferenceCardDetector,
    private val poseClassifier: PoseClassifier,
    private val scaleCalibrator: ScaleCalibrator,
    private val fingerMeasurementPort: AndroidFingerMeasurementPort,
    private val frameSignalEstimator: FrameSignalEstimator,
    private val frameAnnotator: DebugFrameAnnotator,
    private val poseTargets: Map<CaptureStep, PoseTarget>,
) : AndroidRuntimeAnalyzerPort {
    override fun detectHand(frame: Bitmap): HandDetection? = handLandmarkEngine.detect(frame)

    override fun detectCard(frame: Bitmap): CardDetection? = referenceCardDetector.detect(frame)

    override fun classifyPose(
        step: CoreCaptureStep,
        hand: HandDetection,
    ): Float? {
        val target = poseTargets[step.toApiStep()] ?: return null
        return poseClassifier.classify(target, hand)
    }

    override fun estimateCoplanarity(
        hand: HandDetection?,
        card: CardDetection?,
        frame: Bitmap,
        targetFinger: TargetFinger,
    ): Float =
        frameSignalEstimator.estimateFingerCard2dProximity(
            hand = hand,
            card = card,
            frameWidth = frame.width,
            frameHeight = frame.height,
            targetFinger = targetFinger,
        )

    override fun extractCardDiagnostics(card: CardDetection): SessionCardDiagnostics =
        SessionCardDiagnostics(
            coverageRatio = card.coverageRatio,
            aspectResidual = card.aspectResidual,
            rectangularityScore = card.rectangularityScore,
            edgeSupportScore = card.edgeSupportScore,
            rectificationConfidence = card.rectificationConfidence,
        )

    override fun calibrateScale(card: CardDetection): SessionScaleResult? =
        scaleCalibrator.calibrateWithDiagnostics(card).toCoreScaleResult()

    override fun measureFingerWidth(
        frame: Bitmap,
        hand: HandDetection,
        targetFinger: TargetFinger,
        scale: SessionScale,
    ): SessionFingerMeasurement? =
        fingerMeasurementPort.measureVisibleWidth(
            frame = frame,
            hand = hand,
            targetFinger = targetFinger,
            scale =
                MetricScale(
                    mmPerPxX = scale.mmPerPxX,
                    mmPerPxY = scale.mmPerPxY,
                ),
        )

    override fun encodeOverlay(
        step: CoreCaptureStep,
        frame: Bitmap,
        hand: HandDetection?,
        card: CardDetection?,
    ): ByteArray? = frameAnnotator.encodeAnnotatedJpeg(frame, hand, card)

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
