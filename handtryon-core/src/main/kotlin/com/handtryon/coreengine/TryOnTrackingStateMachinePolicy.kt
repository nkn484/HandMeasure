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

data class TryOnTrackingStateMachineConfig(
    val lockEvidenceFrames: Int = 3,
    val recoveryGraceFrames: Int = 6,
    val lowQualityThreshold: Float = 0.38f,
    val lockQualityThreshold: Float = 0.62f,
)

class TryOnTrackingStateMachinePolicy(
    private val config: TryOnTrackingStateMachineConfig = TryOnTrackingStateMachineConfig(),
) {
    constructor(
        lockEvidenceFrames: Int = TryOnTrackingStateMachineConfig().lockEvidenceFrames,
        recoveryGraceFrames: Int = TryOnTrackingStateMachineConfig().recoveryGraceFrames,
        lowQualityThreshold: Float = TryOnTrackingStateMachineConfig().lowQualityThreshold,
        lockQualityThreshold: Float = TryOnTrackingStateMachineConfig().lockQualityThreshold,
    ) : this(
        TryOnTrackingStateMachineConfig(
            lockEvidenceFrames = lockEvidenceFrames,
            recoveryGraceFrames = recoveryGraceFrames,
            lowQualityThreshold = lowQualityThreshold,
            lockQualityThreshold = lockQualityThreshold,
        ),
    )

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
        val hasStableEvidence = input.landmarkUsable && input.validationUsable && input.qualityScore >= config.lockQualityThreshold
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
        if (!input.landmarkUsable || input.qualityScore < config.lowQualityThreshold) {
            return TryOnTrackingSnapshot(state = TryOnTrackingState.Searching)
        }
        val stableFrames =
            if (input.validationUsable && input.qualityScore >= config.lockQualityThreshold) {
                previous.stableEvidenceFrames + 1
            } else {
                1
            }
        return if (stableFrames >= config.lockEvidenceFrames) {
            TryOnTrackingSnapshot(state = TryOnTrackingState.Locked)
        } else {
            TryOnTrackingSnapshot(
                state = TryOnTrackingState.Candidate,
                stableEvidenceFrames = stableFrames,
            )
        }
    }

    private fun lockedTransition(input: TryOnTrackingInput): TryOnTrackingSnapshot {
        val degraded = !input.validationUsable || input.qualityScore < config.lowQualityThreshold || !input.landmarkUsable
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
        if (input.landmarkUsable && input.validationUsable && input.qualityScore >= config.lockQualityThreshold && !input.usingFallbackAnchor) {
            return TryOnTrackingSnapshot(state = TryOnTrackingState.Locked)
        }
        val nextRecoveryFrames = previous.recoveryFrames + 1
        return if (nextRecoveryFrames > config.recoveryGraceFrames) {
            TryOnTrackingSnapshot(state = TryOnTrackingState.Searching)
        } else {
            TryOnTrackingSnapshot(
                state = TryOnTrackingState.Recovering,
                recoveryFrames = nextRecoveryFrames,
            )
        }
    }
}
