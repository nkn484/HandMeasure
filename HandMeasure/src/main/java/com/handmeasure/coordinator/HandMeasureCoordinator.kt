package com.handmeasure.coordinator

import android.graphics.Bitmap
import com.handmeasure.api.CalibrationStatus
import com.handmeasure.api.CapturedStepInfo
import com.handmeasure.api.DebugMetadata
import com.handmeasure.api.FusedDiagnostics
import com.handmeasure.api.HandMeasureConfig
import com.handmeasure.api.HandMeasureResult
import com.handmeasure.api.SessionDiagnostics
import com.handmeasure.api.StepDiagnostics
import com.handmeasure.flow.CaptureUiState
import com.handmeasure.flow.HandMeasureStateMachine
import com.handmeasure.flow.StepCandidate
import com.handmeasure.measurement.FingerMeasurementEngine
import com.handmeasure.measurement.FingerMeasurementFusion
import com.handmeasure.measurement.FrameQualityInput
import com.handmeasure.measurement.FrameQualityScorer
import com.handmeasure.measurement.ResultReliabilityPolicy
import com.handmeasure.measurement.ScaleCalibrator
import com.handmeasure.measurement.TableRingSizeMapper
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
    private val fingerMeasurementEngine: FingerMeasurementEngine = FingerMeasurementEngine(),
    private val fingerMeasurementFusion: FingerMeasurementFusion = FingerMeasurementFusion(),
    private val reliabilityPolicy: ResultReliabilityPolicy = ResultReliabilityPolicy(),
    private val debugExportDirProvider: (() -> File?)? = null,
    private val poseGuidanceHintTextResolver: PoseGuidanceHintTextResolver? = null,
) {
    private val stateMachine = HandMeasureStateMachine(config.qualityThresholds)
    private val ringSizeMapper = TableRingSizeMapper()
    private var previousStep = stateMachine.currentStep().step

    private val frameSignalEstimator = FrameSignalEstimator()
    private val poseGuidanceHintDecider = PoseGuidanceHintDecider()
    private val debugFrameAnnotator = DebugFrameAnnotator()
    private val sessionProcessor =
        MeasurementSessionProcessor(
            config = config,
            handLandmarkEngine = handLandmarkEngine,
            referenceCardDetector = referenceCardDetector,
            poseClassifier = poseClassifier,
            scaleCalibrator = scaleCalibrator,
            fingerMeasurementEngine = fingerMeasurementEngine,
            frameSignalEstimator = frameSignalEstimator,
            frameAnnotator = debugFrameAnnotator,
        )
    private val debugSessionExporter = DebugSessionExporter(config, debugExportDirProvider)

    fun currentState(): CaptureUiState = stateMachine.snapshot()

    fun analyzeFrame(jpegBytes: ByteArray, bitmap: Bitmap): LiveAnalysisState {
        val currentStep = stateMachine.currentStep()
        if (previousStep != currentStep.step) {
            poseClassifier.reset()
            frameSignalEstimator.resetTemporalState()
            previousStep = currentStep.step
        }

        val hand = handLandmarkEngine.detect(bitmap)
        val card = referenceCardDetector.detect(bitmap)
        val poseEvaluation = hand?.let { poseClassifier.evaluate(currentStep.step, it) }
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
        val stepResults = snapshot.completedSteps.sortedBy { it.step.ordinal }
        val processing = sessionProcessor.process(stepResults)
        val warnings = processing.warnings.toMutableSet()

        val fused = fingerMeasurementFusion.fuse(processing.stepMeasurements)
        warnings += fused.warnings

        val reliability =
            reliabilityPolicy.assess(
                fused = fused,
                stepMeasurements = processing.stepMeasurements,
                calibrationStatus = processing.calibrationStatus,
                existingWarnings = warnings,
            )
        val ringSize = ringSizeMapper.nearestForDiameter(config.ringSizeTable, fused.equivalentDiameterMm)
        val captured =
            snapshot.completedSteps.map {
                CapturedStepInfo(
                    step = it.step,
                    score = it.qualityScore,
                    poseScore = it.poseScore,
                    cardScore = it.cardScore,
                    handScore = it.handScore,
                )
            }

        val sessionDiagnostics =
            SessionDiagnostics(
                stepDiagnostics = processing.stepDiagnostics,
                fusedDiagnostics =
                    FusedDiagnostics(
                        widthMm = fused.widthMm,
                        thicknessMm = fused.thicknessMm,
                        circumferenceMm = fused.circumferenceMm,
                        equivalentDiameterMm = fused.equivalentDiameterMm,
                        suggestedRingSizeLabel = ringSize.label,
                        finalConfidence = fused.confidenceScore,
                        warningReasons = reliability.warnings.map { it.name },
                        perStepResidualsMm = fused.perStepResidualsMm,
                        resultMode = reliability.resultMode,
                        qualityLevel = reliability.qualityLevel,
                        retryRecommended = reliability.retryRecommended,
                        calibrationStatus = processing.calibrationStatus,
                        measurementSources = reliability.measurementSources,
                    ),
            )

        val result =
            HandMeasureResult(
                targetFinger = config.targetFinger,
                fingerWidthMm = fused.widthMm,
                fingerThicknessMm = fused.thicknessMm,
                estimatedCircumferenceMm = fused.circumferenceMm,
                equivalentDiameterMm = fused.equivalentDiameterMm,
                suggestedRingSizeLabel = ringSize.label,
                confidenceScore = fused.confidenceScore.coerceIn(0f, 1f),
                warnings = reliability.warnings,
                capturedSteps = captured,
                resultMode = reliability.resultMode,
                qualityLevel = reliability.qualityLevel,
                retryRecommended = reliability.retryRecommended,
                calibrationStatus = processing.calibrationStatus,
                measurementSources = reliability.measurementSources,
                debugMetadata =
                    DebugMetadata(
                        mmPerPxX = processing.bestScaleMmPerPxX,
                        mmPerPxY = processing.bestScaleMmPerPxY,
                        frontalWidthPx = processing.frontalWidthPx,
                        thicknessSamplesMm = processing.thicknessSamples,
                        rawNotes = processing.debugNotes + processing.calibrationNotes + fused.debugNotes,
                        sessionDiagnostics = sessionDiagnostics,
                    ),
            )

        debugSessionExporter.export(result, processing.overlays)
        return result
    }
}
