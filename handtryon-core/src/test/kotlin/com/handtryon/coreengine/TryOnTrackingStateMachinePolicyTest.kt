package com.handtryon.coreengine

import com.google.common.truth.Truth.assertThat
import com.handtryon.coreengine.model.TryOnTrackingState
import org.junit.Test

class TryOnTrackingStateMachinePolicyTest {
    private val policy = TryOnTrackingStateMachinePolicy(lockEvidenceFrames = 3, recoveryGraceFrames = 3)

    @Test
    fun transitions_from_searching_to_locked_after_stable_evidence() {
        var state = TryOnTrackingSnapshot()

        repeat(3) {
            state =
                policy.transition(
                    previous = state,
                    input = stableInput(),
                )
        }

        assertThat(state.state).isEqualTo(TryOnTrackingState.Locked)
    }

    @Test
    fun transitions_to_recovering_when_locked_quality_drops() {
        val locked = TryOnTrackingSnapshot(state = TryOnTrackingState.Locked)

        val next =
            policy.transition(
                previous = locked,
                input =
                    TryOnTrackingInput(
                        landmarkUsable = true,
                        qualityScore = 0.2f,
                        validationUsable = false,
                        usingFallbackAnchor = false,
                    ),
            )

        assertThat(next.state).isEqualTo(TryOnTrackingState.Recovering)
        assertThat(next.recoveryFrames).isEqualTo(1)
    }

    @Test
    fun exits_recovering_to_locked_when_tracking_recovers_with_real_anchor() {
        val recovering = TryOnTrackingSnapshot(state = TryOnTrackingState.Recovering, recoveryFrames = 2)

        val next =
            policy.transition(
                previous = recovering,
                input =
                    TryOnTrackingInput(
                        landmarkUsable = true,
                        qualityScore = 0.82f,
                        validationUsable = true,
                        usingFallbackAnchor = false,
                    ),
            )

        assertThat(next.state).isEqualTo(TryOnTrackingState.Locked)
    }

    @Test
    fun exits_recovering_to_searching_when_grace_window_expires() {
        val recovering = TryOnTrackingSnapshot(state = TryOnTrackingState.Recovering, recoveryFrames = 3)

        val next =
            policy.transition(
                previous = recovering,
                input =
                    TryOnTrackingInput(
                        landmarkUsable = false,
                        qualityScore = 0.18f,
                        validationUsable = false,
                        usingFallbackAnchor = true,
                    ),
            )

        assertThat(next.state).isEqualTo(TryOnTrackingState.Searching)
    }

    private fun stableInput(): TryOnTrackingInput =
        TryOnTrackingInput(
            landmarkUsable = true,
            qualityScore = 0.8f,
            validationUsable = true,
            usingFallbackAnchor = false,
        )
}
