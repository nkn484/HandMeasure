package com.handmeasure.coordinator

import android.graphics.Bitmap
import com.handmeasure.api.HandMeasureConfig
import com.handmeasure.api.HandMeasureResult
import com.handmeasure.api.CaptureStep
import com.handmeasure.core.capture.CaptureRetryReason
import com.handmeasure.core.capture.CaptureRetryReasonInput
import com.handmeasure.core.capture.CaptureRetryReasonPolicy
import com.handmeasure.core.capture.HoldStillState
import com.handmeasure.core.capture.OrientationBucketClassifier
import com.handmeasure.core.capture.OrientationBucketDefinition
import com.handmeasure.core.capture.OrientationObservation
import com.handmeasure.flow.CaptureUiState
import com.handmeasure.flow.HandMeasureStateMachine
import com.handmeasure.flow.ProtocolGuides
import com.handmeasure.flow.StepCandidate
import com.handmeasure.engine.compat.MeasurementEngineApiMapper
import com.handmeasure.engine.factory.AndroidMeasurementEngineFactory
import com.handmeasure.measurement.FingerMeasurementFusion
import com.handmeasure.measurement.FrameQualityInput
import com.handmeasure.measurement.FrameQualityScorer
import com.handmeasure.measurement.OpenCvSessionFingerMeasurementPort
import com.handmeasure.measurement.ResultReliabilityPolicy
import com.handmeasure.measurement.ScaleCalibrator
import com.handmeasure.protocol.CaptureProtocols
import com.handmeasure.vision.CardDetection
import com.handmeasure.vision.HandDetection
import com.handmeasure.vision.HandLandmarkEngine
import com.handmeasure.vision.OpenCvReferenceCardDetector
import com.handmeasure.vision.PoseClassifier
import com.handmeasure.vision.ReferenceCardDetector
import java.io.File

data class LiveAnalysisState(
    val captureState: CaptureUiState,
    val qualityScore: Float,
    val detectionConfidence: Float,
    val poseConfidence: Float,
    val measurementConfidence: Float,
    val handScore: Float,
    val cardScore: Float,
    val blurScore: Float,
    val motionScore: Float,
    val lightingScore: Float,
    val handDetection: HandDetection?,
    val cardDetection: CardDetection?,
    val frameWidth: Int,
    val frameHeight: Int,
    val bucketStep: CaptureStep?,
    val holdStillState: HoldStillState = HoldStillState.SEARCHING,
    val poseGuidanceHint: String?,
    val poseGuidanceHintKey: PoseGuidanceHintKey? = null,
    val retryReasonHintKey: PoseGuidanceHintKey? = null,
)

class HandMeasureCoordinator(
    private val config: HandMeasureConfig,
    private val handLandmarkEngine: HandLandmarkEngine,
    private val referenceCardDetector: ReferenceCardDetector = OpenCvReferenceCardDetector(),
    private val poseClassifier: PoseClassifier = PoseClassifier(),
    private val frameQualityScorer: FrameQualityScorer = FrameQualityScorer(),
    private val scaleCalibrator: ScaleCalibrator = ScaleCalibrator(),
    private val fingerMeasurementPort: AndroidFingerMeasurementPort = OpenCvSessionFingerMeasurementPort(),
    private val fingerMeasurementFusion: FingerMeasurementFusion = FingerMeasurementFusion(),
    private val reliabilityPolicy: ResultReliabilityPolicy = ResultReliabilityPolicy(),
    private val debugExportDirProvider: (() -> File?)? = null,
    private val poseGuidanceHintTextResolver: PoseGuidanceHintTextResolver? = null,
) {
    private val protocolSteps = CaptureProtocols.steps(config.protocol).associateBy { it.step }
    private val stateMachine = HandMeasureStateMachine(config.qualityThresholds, ProtocolGuides.steps(config.protocol))
    private val engineApiMapper = MeasurementEngineApiMapper()
    private val bucketClassifier =
        OrientationBucketClassifier(
            definitions =
                CaptureProtocols
                    .steps(config.protocol)
                    .map { protocolStep ->
                        OrientationBucketDefinition(
                            bucket = protocolStep.step,
                            targetX = protocolStep.poseTarget.nx,
                            targetY = protocolStep.poseTarget.ny,
                            targetZ = protocolStep.poseTarget.nz,
                        )
                    },
        )
    private val retryReasonPolicy =
        CaptureRetryReasonPolicy(
            lockQualityScore = config.qualityThresholds.autoCaptureScore,
            motionMinScore = config.qualityThresholds.motionMinScore,
            lightingMinScore = config.qualityThresholds.lightingMinScore,
        )

    private val frameSignalEstimator = FrameSignalEstimator()
    private val poseGuidanceHintDecider = PoseGuidanceHintDecider()
    private val cardDetectionMemory = CardDetectionMemory()
    private val measurementEngine =
        AndroidMeasurementEngineFactory.create(
            config = engineApiMapper.toEngineConfig(config),
            handLandmarkEngine = handLandmarkEngine,
            referenceCardDetector = referenceCardDetector,
            poseClassifier = poseClassifier,
            scaleCalibrator = scaleCalibrator,
            fingerMeasurementPort = fingerMeasurementPort,
            fingerMeasurementFusion = fingerMeasurementFusion,
            reliabilityPolicy = reliabilityPolicy,
            frameSignalEstimator = frameSignalEstimator,
            mapper = engineApiMapper,
        )
    private val debugSessionExporter = DebugSessionExporter(config, debugExportDirProvider)

    fun currentState(): CaptureUiState = stateMachine.snapshot()

    fun analyzeFrame(jpegBytes: ByteArray, bitmap: Bitmap): LiveAnalysisState {
        val fallbackStep = stateMachine.currentStep().step
        val frameTimestampMs = System.currentTimeMillis()
        val hand = handLandmarkEngine.detect(bitmap)
        val detectedCard = referenceCardDetector.detect(bitmap)
        val card = cardDetectionMemory.resolve(detectedCard, frameTimestampMs)
        val bucketDecision =
            bucketClassifier.classify(
                hand?.let { detectedHand ->
                    poseClassifier.extractPalmNormal(detectedHand)?.let { snapshot ->
                        OrientationObservation(
                            normalX = snapshot.normalX,
                            normalY = snapshot.normalY,
                            normalZ = snapshot.normalZ,
                        )
                    }
                },
            )
        val resolvedStep = bucketDecision.bucket ?: fallbackStep
        val protocolStep = protocolSteps[resolvedStep]
        val poseEvaluation = hand?.let { ps -> protocolStep?.poseTarget?.let { target -> poseClassifier.evaluate(target, ps) } }
        val ringZoneScore = if (hand?.fingerJointPair(config.targetFinger) != null) 1f else 0f
        val imageSignals = frameSignalEstimator.estimate(bitmap, hand, card, config.targetFinger)
        val coplanarityProxyScore =
            frameSignalEstimator.estimateFingerCard2dProximity(
                hand = hand,
                card = card,
                frameWidth = bitmap.width,
                frameHeight = bitmap.height,
                targetFinger = config.targetFinger,
            )

        val quality =
            frameQualityScorer.score(
                FrameQualityInput(
                    handDetectionScore = hand?.detectionConfidence ?: 0f,
                    handLandmarkScore = hand?.presenceConfidence ?: 0f,
                    ringZoneScore = ringZoneScore,
                    cardDetectionScore = card?.confidence ?: 0f,
                    cardRectangularityScore = card?.rectangularityScore ?: 0f,
                    cardEdgeSupportScore = card?.edgeSupportScore ?: 0f,
                    blurScoreGlobal = imageSignals.blurGlobalScore,
                    blurScoreFingerRoi = imageSignals.blurFingerRoiScore,
                    motionScore = imageSignals.motionScore,
                    lightingScore = imageSignals.lightingScore,
                    poseScore = poseEvaluation?.smoothedScore ?: 0f,
                    coplanarityProxyScore = coplanarityProxyScore,
                ),
            )

        val updatedState =
            stateMachine.onFrameEvaluated(
                StepCandidate(
                    step = resolvedStep,
                    frameBytes = jpegBytes,
                    qualityScore = quality.totalScore,
                    poseScore = quality.subscores.poseConfidence,
                    cardScore = quality.subscores.cardScore,
                    handScore = quality.subscores.detectionConfidence,
                    blurScore = quality.subscores.blurScore,
                    motionScore = quality.subscores.motionScore,
                    lightingScore = quality.subscores.lightingScore,
                    bucketScore = bucketDecision.score,
                    confidencePenaltyReasons = quality.confidencePenaltyReasons,
                ),
                isBucketStable = bucketDecision.score >= BUCKET_STABILITY_SCORE_MIN,
            )

        val poseHintKey =
            poseGuidanceHintDecider.decide(
                level = poseEvaluation?.level,
                action = poseEvaluation?.guidanceAction,
                hand = hand,
                card = card,
            )
        val retryReason =
            retryReasonPolicy.decide(
                CaptureRetryReasonInput(
                    handDetected = hand != null,
                    cardDetected = card != null,
                    holdStillState = updatedState.holdStillState,
                    qualityScore = quality.totalScore,
                    poseScore = quality.subscores.poseConfidence,
                    motionScore = quality.subscores.motionScore,
                    lightingScore = quality.subscores.lightingScore,
                    coplanarityScore = coplanarityProxyScore,
                    penaltyReasons = quality.confidencePenaltyReasons,
                ),
            )
        val retryHintKey = retryReason?.toHintKey()
        val resolvedHintKey = retryHintKey ?: poseHintKey

        return LiveAnalysisState(
            captureState = updatedState,
            qualityScore = quality.totalScore,
            detectionConfidence = quality.subscores.detectionConfidence,
            poseConfidence = quality.subscores.poseConfidence,
            measurementConfidence = quality.subscores.measurementConfidence,
            handScore = hand?.confidence ?: 0f,
            cardScore = card?.confidence ?: 0f,
            blurScore = quality.subscores.blurScore,
            motionScore = quality.subscores.motionScore,
            lightingScore = quality.subscores.lightingScore,
            handDetection = hand,
            cardDetection = card,
            frameWidth = bitmap.width,
            frameHeight = bitmap.height,
            bucketStep = bucketDecision.bucket,
            holdStillState = updatedState.holdStillState,
            poseGuidanceHintKey = resolvedHintKey,
            retryReasonHintKey = retryHintKey,
            poseGuidanceHint = resolvedHintKey?.let { poseGuidanceHintTextResolver?.resolve(it) },
        )
    }

    fun advanceWithBestCandidate(): CaptureUiState = stateMachine.advanceWithBestCandidate()

    fun retryCurrentStep(): CaptureUiState = stateMachine.retryCurrentStep()

    fun isCaptureComplete(): Boolean = stateMachine.isComplete()

    fun finalizeResult(): HandMeasureResult {
        val snapshot = stateMachine.snapshot()
        val stepResults = snapshot.completedSteps.sortedBy { it.step.ordinal }.map(engineApiMapper::toEngineStepCandidate)
        val processing = measurementEngine.process(stepResults)
        val result = engineApiMapper.toApiResult(processing.result)

        debugSessionExporter.export(result, processing.overlays.map(engineApiMapper::toApiOverlay))
        return result
    }

    private fun CaptureRetryReason.toHintKey(): PoseGuidanceHintKey =
        when (this) {
            CaptureRetryReason.PLACE_HAND_IN_FRAME -> PoseGuidanceHintKey.PLACE_HAND_IN_FRAME
            CaptureRetryReason.PLACE_CARD_NEAR_FINGER -> PoseGuidanceHintKey.PLACE_CARD_NEAR_FINGER
            CaptureRetryReason.WAIT_FOR_LOCK -> PoseGuidanceHintKey.WAIT_FOR_LOCK
            CaptureRetryReason.HOLD_HAND_STEADIER -> PoseGuidanceHintKey.HOLD_HAND_STEADY
            CaptureRetryReason.REDUCE_GLARE -> PoseGuidanceHintKey.REDUCE_GLARE
            CaptureRetryReason.ADJUST_HAND_ANGLE -> PoseGuidanceHintKey.ADJUST_HAND_POSE
            CaptureRetryReason.KEEP_HAND_AND_CARD_CLOSER -> PoseGuidanceHintKey.KEEP_HAND_AND_CARD_CLOSER
            CaptureRetryReason.TRACKING_UNSTABLE -> PoseGuidanceHintKey.TRACKING_UNSTABLE
        }

    private companion object {
        const val BUCKET_STABILITY_SCORE_MIN = 0.52f
    }
}
