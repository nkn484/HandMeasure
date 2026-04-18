package com.handmeasure.flow

import com.google.common.truth.Truth.assertThat
import com.handmeasure.api.CaptureStep
import com.handmeasure.api.QualityThresholds
import com.handmeasure.core.capture.HoldStillState
import com.handmeasure.protocol.ProtocolStepRole
import org.junit.Test

class HandMeasureStateMachineTest {
    private val thresholds =
        QualityThresholds(
            autoCaptureScore = 0.84f,
            bestCandidateProgressScore = 0.56f,
        )
    private val steps =
        listOf(
            MeasureStepDefinition(step = CaptureStep.BACK_OF_HAND, role = ProtocolStepRole.FRONTAL),
            MeasureStepDefinition(step = CaptureStep.LEFT_OBLIQUE_DORSAL, role = ProtocolStepRole.LEFT_OBLIQUE),
        )

    @Test
    fun canAdvanceWithBest_isFalseBeforeRelaxedGate_whenScoreBelowConfiguredThreshold() {
        val machine = HandMeasureStateMachine(thresholds = thresholds, stepDefinitions = steps)

        val state = machine.onFrameEvaluated(candidate(step = CaptureStep.BACK_OF_HAND, qualityScore = 0.50f))

        assertThat(state.canAdvanceWithBest).isFalse()
        assertThat(state.bestCurrentCandidate).isNotNull()
    }

    @Test
    fun canAdvanceWithBest_relaxesAfterSustainedFrames_forNearThresholdCandidates() {
        val machine = HandMeasureStateMachine(thresholds = thresholds, stepDefinitions = steps)
        var state = machine.snapshot()

        repeat(40) {
            state = machine.onFrameEvaluated(candidate(step = CaptureStep.BACK_OF_HAND, qualityScore = 0.50f))
        }

        assertThat(state.canAdvanceWithBest).isTrue()
    }

    @Test
    fun canAdvanceWithBest_relaxesAfterRetry_forNearThresholdCandidates() {
        val machine = HandMeasureStateMachine(thresholds = thresholds, stepDefinitions = steps)

        machine.onFrameEvaluated(candidate(step = CaptureStep.BACK_OF_HAND, qualityScore = 0.50f))
        machine.retryCurrentStep()
        val state = machine.onFrameEvaluated(candidate(step = CaptureStep.BACK_OF_HAND, qualityScore = 0.50f))

        assertThat(state.canAdvanceWithBest).isTrue()
    }

    @Test
    fun canAdvanceWithBest_staysFalse_forVeryLowQuality_evenAfterRelaxedGate() {
        val machine = HandMeasureStateMachine(thresholds = thresholds, stepDefinitions = steps)
        var state = machine.snapshot()

        repeat(45) {
            state = machine.onFrameEvaluated(candidate(step = CaptureStep.BACK_OF_HAND, qualityScore = 0.30f))
        }

        assertThat(state.canAdvanceWithBest).isFalse()
    }

    @Test
    fun relaxedGate_resets_afterAcceptingCandidate_andMovingToNextStep() {
        val machine = HandMeasureStateMachine(thresholds = thresholds, stepDefinitions = steps)
        repeat(45) {
            machine.onFrameEvaluated(candidate(step = CaptureStep.BACK_OF_HAND, qualityScore = 0.50f))
        }
        machine.advanceWithBestCandidate()

        val nextStepState =
            machine.onFrameEvaluated(
                candidate(step = CaptureStep.LEFT_OBLIQUE_DORSAL, qualityScore = 0.50f),
            )

        assertThat(nextStepState.currentStep.step).isEqualTo(CaptureStep.LEFT_OBLIQUE_DORSAL)
        assertThat(nextStepState.canAdvanceWithBest).isFalse()
    }

    @Test
    fun onFrameEvaluated_allowsOutOfOrderBuckets_withoutRejectingCandidate() {
        val machine = HandMeasureStateMachine(thresholds = thresholds, stepDefinitions = steps)

        val state =
            machine.onFrameEvaluated(
                candidate(step = CaptureStep.LEFT_OBLIQUE_DORSAL, qualityScore = 0.63f),
            )

        assertThat(state.currentStep.step).isEqualTo(CaptureStep.LEFT_OBLIQUE_DORSAL)
        assertThat(state.bestCurrentCandidate).isNotNull()
    }

    @Test
    fun autoCapture_requiresHoldStillWindow_beforeCommit() {
        val machine = HandMeasureStateMachine(thresholds = thresholds, stepDefinitions = steps)

        machine.onFrameEvaluated(
            candidate(step = CaptureStep.BACK_OF_HAND, qualityScore = 0.90f, motionScore = 0.72f),
            timestampMs = 0L,
            isBucketStable = true,
        )
        machine.onFrameEvaluated(
            candidate(step = CaptureStep.BACK_OF_HAND, qualityScore = 0.89f, motionScore = 0.69f),
            timestampMs = 160L,
            isBucketStable = true,
        )
        val beforeLock =
            machine.onFrameEvaluated(
                candidate(step = CaptureStep.BACK_OF_HAND, qualityScore = 0.91f, motionScore = 0.68f),
                timestampMs = 300L,
                isBucketStable = true,
            )

        assertThat(beforeLock.completedSteps).isEmpty()
        assertThat(beforeLock.holdStillState).isEqualTo(HoldStillState.CANDIDATE)

        val locked =
            machine.onFrameEvaluated(
                candidate(step = CaptureStep.BACK_OF_HAND, qualityScore = 0.88f, motionScore = 0.70f),
                timestampMs = 380L,
                isBucketStable = true,
            )

        assertThat(locked.completedSteps).hasSize(1)
        assertThat(locked.completedSteps.first().step).isEqualTo(CaptureStep.BACK_OF_HAND)
        assertThat(locked.holdStillState).isEqualTo(HoldStillState.CAPTURED)
    }

    @Test
    fun autoCapture_commitsBestFrameFromRecentWindow_notJustLastFrame() {
        val machine = HandMeasureStateMachine(thresholds = thresholds, stepDefinitions = steps)

        machine.onFrameEvaluated(
            candidate(step = CaptureStep.BACK_OF_HAND, qualityScore = 0.86f, motionScore = 0.72f),
            timestampMs = 0L,
            isBucketStable = true,
        )
        machine.onFrameEvaluated(
            candidate(step = CaptureStep.BACK_OF_HAND, qualityScore = 0.93f, motionScore = 0.71f),
            timestampMs = 170L,
            isBucketStable = true,
        )
        val state =
            machine.onFrameEvaluated(
                candidate(step = CaptureStep.BACK_OF_HAND, qualityScore = 0.88f, motionScore = 0.73f),
                timestampMs = 360L,
                isBucketStable = true,
            )

        val captured = state.completedSteps.single { it.step == CaptureStep.BACK_OF_HAND }
        assertThat(captured.qualityScore).isWithin(1e-4f).of(0.93f)
    }

    private fun candidate(
        step: CaptureStep,
        qualityScore: Float,
        motionScore: Float = 0.7f,
    ): StepCandidate =
        StepCandidate(
            step = step,
            frameBytes = byteArrayOf(1, 2, 3),
            qualityScore = qualityScore,
            poseScore = qualityScore,
            cardScore = qualityScore,
            handScore = qualityScore,
            motionScore = motionScore,
        )
}
