package com.handmeasure.flow

import com.handmeasure.api.CaptureStep
import com.handmeasure.api.QualityThresholds

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
    val confidencePenaltyReasons: List<String> = emptyList(),
)

data class CaptureUiState(
    val currentStep: MeasureStepDefinition,
    val completedSteps: List<StepCandidate>,
    val bestCurrentCandidate: StepCandidate?,
    val progressFraction: Float,
    val canAdvanceWithBest: Boolean,
    val isFlowComplete: Boolean,
)

class HandMeasureStateMachine(
    private val thresholds: QualityThresholds,
    private val stepDefinitions: List<MeasureStepDefinition> = GuidedSteps.all,
) {
    private var currentStepIndex = 0
    private var bestCandidate: StepCandidate? = null
    private val completed = mutableListOf<StepCandidate>()

    fun onFrameEvaluated(candidate: StepCandidate): CaptureUiState {
        if (candidate.step != currentStep().step || isComplete()) {
            return snapshot()
        }
        if (bestCandidate == null || candidate.qualityScore > bestCandidate!!.qualityScore) {
            bestCandidate = candidate
        }
        if (candidate.qualityScore >= thresholds.autoCaptureScore) {
            acceptBestCandidate()
        }
        return snapshot()
    }

    fun advanceWithBestCandidate(): CaptureUiState {
        acceptBestCandidate()
        return snapshot()
    }

    fun retryCurrentStep(): CaptureUiState {
        bestCandidate = null
        return snapshot()
    }

    fun currentStep(): MeasureStepDefinition = stepDefinitions[currentStepIndex.coerceAtMost(stepDefinitions.lastIndex)]

    fun isComplete(): Boolean = completed.size == stepDefinitions.size

    private fun acceptBestCandidate() {
        val candidate = bestCandidate ?: return
        completed.removeAll { it.step == candidate.step }
        completed.add(candidate)
        bestCandidate = null
        if (currentStepIndex < stepDefinitions.lastIndex) {
            currentStepIndex += 1
        } else {
            currentStepIndex = stepDefinitions.lastIndex
        }
    }

    fun snapshot(): CaptureUiState =
        CaptureUiState(
            currentStep = currentStep(),
            completedSteps = completed.toList(),
            bestCurrentCandidate = bestCandidate,
            progressFraction = completed.size.toFloat() / stepDefinitions.size.toFloat(),
            canAdvanceWithBest =
                bestCandidate?.qualityScore?.let { it >= thresholds.bestCandidateProgressScore } == true,
            isFlowComplete = isComplete(),
        )
}
