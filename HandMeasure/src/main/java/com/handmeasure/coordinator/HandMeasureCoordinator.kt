package com.handmeasure.coordinator

import android.graphics.Bitmap
import com.handmeasure.api.HandMeasureConfig
import com.handmeasure.api.HandMeasureResult
import com.handmeasure.flow.CaptureUiState
import com.handmeasure.flow.HandMeasureStateMachine
import com.handmeasure.flow.ProtocolGuides
import com.handmeasure.flow.StepCandidate
import com.handmeasure.engine.MeasurementEngine
import com.handmeasure.engine.compat.MeasurementEngineApiMapper
import com.handmeasure.measurement.FingerMeasurementFusion
import com.handmeasure.measurement.FrameQualityInput
import com.handmeasure.measurement.FrameQualityScorer
import com.handmeasure.measurement.OpenCvSessionFingerMeasurementPort
import com.handmeasure.measurement.ResultReliabilityPolicy
import com.handmeasure.measurement.ScaleCalibrator
import com.handmeasure.measurement.TableRingSizeMapper
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
    val poseGuidanceHint: String?,
    val poseGuidanceHintKey: PoseGuidanceHintKey? = null,
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
    private var previousStep = stateMachine.currentStep().step
    private val engineApiMapper = MeasurementEngineApiMapper()

    private val frameSignalEstimator = FrameSignalEstimator()
    private val poseGuidanceHintDecider = PoseGuidanceHintDecider()
    private val debugFrameAnnotator = DebugFrameAnnotator()
    private val measurementEngine =
        MeasurementEngine(
            config = engineApiMapper.toEngineConfig(config),
            handLandmarkEngine = handLandmarkEngine,
            referenceCardDetector = referenceCardDetector,
            poseClassifier = poseClassifier,
            scaleCalibrator = scaleCalibrator,
            fingerMeasurementPort = fingerMeasurementPort,
            fingerMeasurementFusion = fingerMeasurementFusion,
            reliabilityPolicy = reliabilityPolicy,
            ringSizeMapper = TableRingSizeMapper(),
            frameSignalEstimator = frameSignalEstimator,
            frameAnnotator = debugFrameAnnotator,
            mapper = engineApiMapper,
        )
    private val debugSessionExporter = DebugSessionExporter(config, debugExportDirProvider)

    fun currentState(): CaptureUiState = stateMachine.snapshot()

    fun analyzeFrame(jpegBytes: ByteArray, bitmap: Bitmap): LiveAnalysisState {
        val currentStep = stateMachine.currentStep()
        val protocolStep = protocolSteps[currentStep.step]
        if (previousStep != currentStep.step) {
            poseClassifier.reset()
            frameSignalEstimator.resetTemporalState()
            previousStep = currentStep.step
        }

        val hand = handLandmarkEngine.detect(bitmap)
        val card = referenceCardDetector.detect(bitmap)
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
                    step = currentStep.step,
                    frameBytes = jpegBytes,
                    qualityScore = quality.totalScore,
                    poseScore = quality.subscores.poseConfidence,
                    cardScore = quality.subscores.cardScore,
                    handScore = quality.subscores.detectionConfidence,
                    blurScore = quality.subscores.blurScore,
                    motionScore = quality.subscores.motionScore,
                    lightingScore = quality.subscores.lightingScore,
                    confidencePenaltyReasons = quality.confidencePenaltyReasons,
                ),
            )

        val hintKey =
            poseGuidanceHintDecider.decide(
                level = poseEvaluation?.level,
                action = poseEvaluation?.guidanceAction,
                hand = hand,
                card = card,
            )

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
            poseGuidanceHintKey = hintKey,
            poseGuidanceHint = hintKey?.let { poseGuidanceHintTextResolver?.resolve(it) },
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
}
