package com.handmeasure.flow

import com.handmeasure.api.CaptureStep
import com.handmeasure.api.QualityThresholds
import com.handmeasure.api.CaptureProtocol
import com.handmeasure.core.capture.AdaptiveCaptureMode
import com.handmeasure.core.capture.AdaptiveCaptureProtocolAdvisor
import com.handmeasure.core.capture.HoldStillCaptureController
import com.handmeasure.core.capture.HoldStillInput
import com.handmeasure.core.capture.HoldStillState
import com.handmeasure.core.capture.RollingCandidateWindow

data class StepCandidate(
    val step: CaptureStep,
    val frameBytes: ByteArray,
    val qualityScore: Float,
    val poseScore: Float,
    val cardScore: Float,
    val handScore: Float,
    val blurScore: Float = 0f,
    val motionScore: Float = 0f,
    val lightingScore: Float = 0f,
    val bucketScore: Float = 0f,
    val confidencePenaltyReasons: List<String> = emptyList(),
)

data class CaptureUiState(
    val currentStep: MeasureStepDefinition,
    val completedSteps: List<StepCandidate>,
    val bestCurrentCandidate: StepCandidate?,
    val progressFraction: Float,
    val canAdvanceWithBest: Boolean,
    val isFlowComplete: Boolean,
    val totalSteps: Int,
    val activeBucketStep: CaptureStep? = null,
    val holdStillState: HoldStillState = HoldStillState.SEARCHING,
    val adaptiveMode: AdaptiveCaptureMode = AdaptiveCaptureMode.STANDARD,
    val requiredSteps: Int = 0,
)

class HandMeasureStateMachine(
    private val thresholds: QualityThresholds,
    private val stepDefinitions: List<MeasureStepDefinition> = ProtocolGuides.steps(CaptureProtocol.DORSAL_V1),
) {
    private val stepByCaptureStep = stepDefinitions.associateBy { it.step }
    private val orderedSteps = stepDefinitions.map { it.step }
    private val rollingCandidates =
        RollingCandidateWindow<CaptureStep, StepCandidate>(
            maxPerKey = WINDOW_SIZE_PER_BUCKET,
            scoreSelector = { it.qualityScore },
        )
    private val holdController =
        HoldStillCaptureController<CaptureStep>(
            candidateMinScore = thresholds.bestCandidateProgressScore,
            lockMinScore = thresholds.autoCaptureScore,
            stableMotionMinScore = thresholds.motionMinScore,
            minStableFrames = HOLD_MIN_FRAMES,
            minStableDurationMs = HOLD_MIN_DURATION_MS,
        )
    private val adaptiveAdvisor = AdaptiveCaptureProtocolAdvisor()

    private val evaluatedFramesByStep = mutableMapOf<CaptureStep, Int>()
    private val retryCountByStep = mutableMapOf<CaptureStep, Int>()
    private val completedByStep = linkedMapOf<CaptureStep, StepCandidate>()
    private var activeBucketStep: CaptureStep? = null
    private var holdState: HoldStillState = HoldStillState.SEARCHING
    private var adaptiveMode: AdaptiveCaptureMode = AdaptiveCaptureMode.STANDARD

    fun onFrameEvaluated(
        candidate: StepCandidate,
        timestampMs: Long = System.currentTimeMillis(),
        isBucketStable: Boolean = true,
    ): CaptureUiState {
        if (candidate.step !in stepByCaptureStep.keys || isComplete()) {
            return snapshot()
        }

        activeBucketStep = candidate.step
        evaluatedFramesByStep[candidate.step] = (evaluatedFramesByStep[candidate.step] ?: 0) + 1
        rollingCandidates.add(candidate.step, candidate)

        val isIncompleteBucket = !completedByStep.containsKey(candidate.step)
        val holdDecision =
            holdController.evaluate(
                HoldStillInput(
                    key = if (isIncompleteBucket) candidate.step else null,
                    qualityScore = candidate.qualityScore,
                    motionScore = candidate.motionScore,
                    isBucketStable = isBucketStable && candidate.poseScore >= POSE_STABLE_MIN_SCORE,
                    timestampMs = timestampMs,
                ),
            )
        holdState = holdDecision.state
        val commitKey = holdDecision.commitKey
        if (commitKey != null) {
            acceptBestCandidate(commitKey)
        }

        return snapshot()
    }

    fun advanceWithBestCandidate(): CaptureUiState {
        if (isComplete()) return snapshot()
        val preferredStep =
            when {
                activeBucketStep != null &&
                    !completedByStep.containsKey(activeBucketStep) &&
                    rollingCandidates.best(activeBucketStep!!) != null -> activeBucketStep
                else -> orderedSteps.firstOrNull { step ->
                    !completedByStep.containsKey(step) && rollingCandidates.best(step) != null
                }
            }
        preferredStep?.let(::acceptBestCandidate)
        return snapshot()
    }

    fun retryCurrentStep(): CaptureUiState {
        if (isComplete()) return snapshot()
        val retryStep = currentStep().step
        rollingCandidates.clear(retryStep)
        evaluatedFramesByStep[retryStep] = 0
        retryCountByStep[retryStep] = (retryCountByStep[retryStep] ?: 0) + 1
        holdController.clearCapturedKey(retryStep)
        holdState = HoldStillState.SEARCHING
        return snapshot()
    }

    fun currentStep(): MeasureStepDefinition {
        val activeStep =
            activeBucketStep
                ?.takeUnless { completedByStep.containsKey(it) }
                ?.let(stepByCaptureStep::get)
        if (activeStep != null) return activeStep
        return orderedSteps
            .firstOrNull { !completedByStep.containsKey(it) }
            ?.let(stepByCaptureStep::get)
            ?: stepDefinitions.last()
    }

    fun isComplete(): Boolean = completedByStep.size >= requiredStepCount()

    private fun acceptBestCandidate(step: CaptureStep) {
        val candidate = rollingCandidates.best(step) ?: return
        completedByStep[step] = candidate
        rollingCandidates.clear(step)
        evaluatedFramesByStep[step] = 0
        retryCountByStep[step] = 0
        holdController.markCaptured(step)
        holdState = HoldStillState.CAPTURED
        updateAdaptiveMode()
    }

    fun snapshot(): CaptureUiState =
        currentStep().let { current ->
            val required = requiredStepCount()
            val bestCurrentCandidate = rollingCandidates.best(current.step)
            CaptureUiState(
                currentStep = current,
                completedSteps = orderedSteps.mapNotNull(completedByStep::get),
                bestCurrentCandidate = bestCurrentCandidate,
                progressFraction = completedByStep.size.toFloat() / required.toFloat().coerceAtLeast(1f),
                canAdvanceWithBest = canAdvanceWithBest(current.step, bestCurrentCandidate),
                isFlowComplete = isComplete(),
                totalSteps = stepDefinitions.size,
                activeBucketStep = activeBucketStep,
                holdStillState = holdState,
                adaptiveMode = adaptiveMode,
                requiredSteps = required,
            )
        }

    private fun canAdvanceWithBest(
        step: CaptureStep,
        candidate: StepCandidate?,
    ): Boolean {
        val score = candidate?.qualityScore ?: return false
        if (score >= thresholds.bestCandidateProgressScore) return true
        val frameCount = evaluatedFramesByStep[step] ?: 0
        val retries = retryCountByStep[step] ?: 0
        val relaxedGateSatisfied = frameCount >= RELAXED_PROGRESS_FRAME_GATE || retries > 0
        return relaxedGateSatisfied && score >= RELAXED_PROGRESS_MIN_SCORE
    }

    private fun updateAdaptiveMode() {
        adaptiveMode =
            adaptiveAdvisor.assess(
                coveredBucketCount = completedByStep.size,
                capturedScores = completedByStep.values.map { it.qualityScore },
            ).mode
    }

    private fun requiredStepCount(): Int = stepDefinitions.size

    private companion object {
        const val WINDOW_SIZE_PER_BUCKET = 8
        const val HOLD_MIN_FRAMES = 3
        const val HOLD_MIN_DURATION_MS = 320L
        const val POSE_STABLE_MIN_SCORE = 0.5f
        const val RELAXED_PROGRESS_FRAME_GATE = 40
        const val RELAXED_PROGRESS_MIN_SCORE = 0.46f
    }
}
