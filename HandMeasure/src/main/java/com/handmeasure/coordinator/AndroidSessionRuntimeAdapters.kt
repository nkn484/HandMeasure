package com.handmeasure.coordinator

import android.graphics.Bitmap
import com.handmeasure.api.CaptureStep
import com.handmeasure.api.TargetFinger
import com.handmeasure.measurement.ScaleCalibrator
import com.handmeasure.vision.CardDetection
import com.handmeasure.vision.HandDetection
import com.handmeasure.vision.HandLandmarkEngine
import com.handmeasure.vision.PoseClassifier
import com.handmeasure.vision.ReferenceCardDetector
import com.handmeasure.core.measurement.CaptureStep as CoreCaptureStep
import com.handmeasure.core.session.SessionCardDiagnostics
import com.handmeasure.core.session.SessionFingerMeasurement
import com.handmeasure.core.session.SessionFingerMeasurementRequest
import com.handmeasure.core.session.SessionScale
import com.handmeasure.core.session.SessionScaleDiagnostics
import com.handmeasure.core.session.SessionScaleResult

internal fun interface HandRuntimeAdapter {
    fun detect(frame: Bitmap): HandDetection?
}

internal interface CardRuntimeAdapter {
    fun detect(frame: Bitmap): CardDetection?

    fun toCardDiagnostics(card: CardDetection): SessionCardDiagnostics
}

internal fun interface PoseRuntimeAdapter {
    fun classify(
        step: CoreCaptureStep,
        hand: HandDetection,
    ): Float?
}

internal fun interface CoplanarityRuntimeAdapter {
    fun estimate(
        hand: HandDetection?,
        card: CardDetection?,
        frame: Bitmap,
        targetFinger: TargetFinger,
    ): Float
}

internal fun interface ScaleRuntimeAdapter {
    fun calibrate(card: CardDetection): SessionScaleResult?
}

internal fun interface FingerRuntimeAdapter {
    fun measure(
        frame: Bitmap,
        hand: HandDetection,
        targetFinger: TargetFinger,
        scale: SessionScale,
    ): SessionFingerMeasurement?
}

internal fun interface OverlayRuntimeAdapter {
    fun encode(
        step: CoreCaptureStep,
        frame: Bitmap,
        hand: HandDetection?,
        card: CardDetection?,
    ): ByteArray?
}

internal class AndroidHandRuntimeAdapter(
    private val handLandmarkEngine: HandLandmarkEngine,
) : HandRuntimeAdapter {
    override fun detect(frame: Bitmap): HandDetection? = handLandmarkEngine.detect(frame)
}

internal class AndroidCardRuntimeAdapter(
    private val referenceCardDetector: ReferenceCardDetector,
) : CardRuntimeAdapter {
    override fun detect(frame: Bitmap): CardDetection? = referenceCardDetector.detect(frame)

    override fun toCardDiagnostics(card: CardDetection): SessionCardDiagnostics =
        SessionCardDiagnostics(
            coverageRatio = card.coverageRatio,
            aspectResidual = card.aspectResidual,
            rectangularityScore = card.rectangularityScore,
            edgeSupportScore = card.edgeSupportScore,
            rectificationConfidence = card.rectificationConfidence,
        )
}

internal class AndroidPoseRuntimeAdapter(
    private val poseClassifier: PoseClassifier,
    private val poseTargets: Map<CaptureStep, PoseTarget>,
) : PoseRuntimeAdapter {
    override fun classify(
        step: CoreCaptureStep,
        hand: HandDetection,
    ): Float? {
        val target = poseTargets[step.toApiStep()] ?: return null
        return poseClassifier.classify(target, hand)
    }
}

internal class AndroidCoplanarityRuntimeAdapter(
    private val frameSignalEstimator: FrameSignalEstimator,
) : CoplanarityRuntimeAdapter {
    override fun estimate(
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
}

internal class AndroidScaleRuntimeAdapter(
    private val scaleCalibrator: ScaleCalibrator,
) : ScaleRuntimeAdapter {
    override fun calibrate(card: CardDetection): SessionScaleResult? {
        val result = scaleCalibrator.calibrateWithDiagnostics(card)
        return SessionScaleResult(
            scale =
                SessionScale(
                    mmPerPxX = result.scale.mmPerPxX,
                    mmPerPxY = result.scale.mmPerPxY,
                ),
            diagnostics =
                SessionScaleDiagnostics(
                    status = result.diagnostics.status.toCoreCalibrationStatus(),
                    notes = result.diagnostics.notes,
                ),
        )
    }
}

internal class AndroidFingerRuntimeAdapter(
    private val fingerMeasurementPort: AndroidFingerMeasurementPort,
) : FingerRuntimeAdapter {
    override fun measure(
        frame: Bitmap,
        hand: HandDetection,
        targetFinger: TargetFinger,
        scale: SessionScale,
    ): SessionFingerMeasurement? =
        fingerMeasurementPort.measureVisibleWidth(
            SessionFingerMeasurementRequest(
                frame = frame,
                hand = hand,
                targetFinger = targetFinger,
                scale = scale,
            ),
        )
}

internal class AndroidOverlayRuntimeAdapter(
    private val frameAnnotator: DebugFrameAnnotator,
) : OverlayRuntimeAdapter {
    override fun encode(
        step: CoreCaptureStep,
        frame: Bitmap,
        hand: HandDetection?,
        card: CardDetection?,
    ): ByteArray? = frameAnnotator.encodeAnnotatedJpeg(frame, hand, card)
}
