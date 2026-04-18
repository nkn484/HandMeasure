package com.handtryon.coreengine

import com.handtryon.coreengine.model.TryOnTrackingState

data class TryOnTrackingSnapshot(
    val state: TryOnTrackingState = TryOnTrackingState.Searching,
    val stableEvidenceFrames: Int = 0,
    val recoveryFrames: Int = 0,
)

data class TryOnTrackingInput(
    val landmarkUsable: Boolean,
    val qualityScore: Float,
    val validationUsable: Boolean,
    val usingFallbackAnchor: Boolean,
)

class TryOnTrackingStateMachinePolicy(
    private val lockEvidenceFrames: Int = 3,
    private val recoveryGraceFrames: Int = 6,
    private val lowQualityThreshold: Float = 0.38f,
    private val lockQualityThreshold: Float = 0.62f,
) {
    fun transition(
        previous: TryOnTrackingSnapshot,
        input: TryOnTrackingInput,
    ): TryOnTrackingSnapshot =
        when (previous.state) {
            TryOnTrackingState.Searching -> searchingTransition(input)
            TryOnTrackingState.Candidate -> candidateTransition(previous, input)
            TryOnTrackingState.Locked -> lockedTransition(input)
            TryOnTrackingState.Recovering -> recoveringTransition(previous, input)
        }

    private fun searchingTransition(input: TryOnTrackingInput): TryOnTrackingSnapshot {
        val hasStableEvidence = input.landmarkUsable && input.validationUsable && input.qualityScore >= lockQualityThreshold
        return if (hasStableEvidence) {
            TryOnTrackingSnapshot(
                state = TryOnTrackingState.Candidate,
                stableEvidenceFrames = 1,
            )
        } else {
            TryOnTrackingSnapshot(state = TryOnTrackingState.Searching)
        }
    }

    private fun candidateTransition(
        previous: TryOnTrackingSnapshot,
        input: TryOnTrackingInput,
    ): TryOnTrackingSnapshot {
        if (!input.landmarkUsable || input.qualityScore < lowQualityThreshold) {
            return TryOnTrackingSnapshot(state = TryOnTrackingState.Searching)
        }
        val stableFrames =
            if (input.validationUsable && input.qualityScore >= lockQualityThreshold) {
                previous.stableEvidenceFrames + 1
            } else {
                1
            }
        return if (stableFrames >= lockEvidenceFrames) {
            TryOnTrackingSnapshot(state = TryOnTrackingState.Locked)
        } else {
            TryOnTrackingSnapshot(
                state = TryOnTrackingState.Candidate,
                stableEvidenceFrames = stableFrames,
            )
        }
    }

    private fun lockedTransition(input: TryOnTrackingInput): TryOnTrackingSnapshot {
        val degraded = !input.validationUsable || input.qualityScore < lowQualityThreshold || !input.landmarkUsable
        return if (degraded) {
            TryOnTrackingSnapshot(state = TryOnTrackingState.Recovering, recoveryFrames = 1)
        } else {
            TryOnTrackingSnapshot(state = TryOnTrackingState.Locked)
        }
    }

    private fun recoveringTransition(
        previous: TryOnTrackingSnapshot,
        input: TryOnTrackingInput,
    ): TryOnTrackingSnapshot {
        if (input.landmarkUsable && input.validationUsable && input.qualityScore >= lockQualityThreshold && !input.usingFallbackAnchor) {
            return TryOnTrackingSnapshot(state = TryOnTrackingState.Locked)
        }
        val nextRecoveryFrames = previous.recoveryFrames + 1
        return if (nextRecoveryFrames > recoveryGraceFrames) {
            TryOnTrackingSnapshot(state = TryOnTrackingState.Searching)
        } else {
            TryOnTrackingSnapshot(
                state = TryOnTrackingState.Recovering,
                recoveryFrames = nextRecoveryFrames,
            )
        }
    }
}
