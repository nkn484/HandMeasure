package com.handtryon.coreengine

import com.google.common.truth.Truth.assertThat
import com.handtryon.coreengine.model.TryOnMode
import com.handtryon.coreengine.model.TryOnTrackingState
import com.handtryon.coreengine.model.TryOnUpdateAction
import org.junit.Test

class TryOnOcclusionPolicyTest {
    private val policy = TryOnOcclusionPolicy()

    @Test
    fun locked_high_quality_enables_stronger_occlusion() {
        val decision =
            policy.evaluate(
                mode = TryOnMode.Measured,
                trackingState = TryOnTrackingState.Locked,
                updateAction = TryOnUpdateAction.Update,
                qualityScore = 0.9f,
            )

        assertThat(decision.enabled).isTrue()
        assertThat(decision.maskOpacity).isGreaterThan(0.6f)
        assertThat(decision.bandThicknessRatio).isAtLeast(0.22f)
    }

    @Test
    fun recovering_or_hold_state_reduces_occlusion_strength() {
        val lockedDecision =
            policy.evaluate(
                mode = TryOnMode.LandmarkOnly,
                trackingState = TryOnTrackingState.Locked,
                updateAction = TryOnUpdateAction.Update,
                qualityScore = 0.75f,
            )
        val recoveringDecision =
            policy.evaluate(
                mode = TryOnMode.LandmarkOnly,
                trackingState = TryOnTrackingState.Recovering,
                updateAction = TryOnUpdateAction.HoldLastPlacement,
                qualityScore = 0.5f,
            )

        assertThat(recoveringDecision.enabled).isTrue()
        assertThat(recoveringDecision.maskOpacity).isLessThan(lockedDecision.maskOpacity)
    }

    @Test
    fun searching_low_quality_disables_occlusion() {
        val decision =
            policy.evaluate(
                mode = TryOnMode.LandmarkOnly,
                trackingState = TryOnTrackingState.Searching,
                updateAction = TryOnUpdateAction.Update,
                qualityScore = 0.3f,
            )

        assertThat(decision.enabled).isFalse()
    }

    @Test
    fun hide_action_always_disables_occlusion() {
        val decision =
            policy.evaluate(
                mode = TryOnMode.Measured,
                trackingState = TryOnTrackingState.Locked,
                updateAction = TryOnUpdateAction.Hide,
                qualityScore = 1f,
            )

        assertThat(decision.enabled).isFalse()
        assertThat(decision.maskOpacity).isEqualTo(0f)
    }
}
