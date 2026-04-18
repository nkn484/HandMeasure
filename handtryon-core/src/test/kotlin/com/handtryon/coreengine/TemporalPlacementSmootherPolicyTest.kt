package com.handtryon.coreengine

import com.google.common.truth.Truth.assertThat
import com.handtryon.coreengine.model.TryOnPlacement
import com.handtryon.coreengine.model.TryOnTrackingState
import com.handtryon.coreengine.model.TryOnUpdateAction
import org.junit.Test

class TemporalPlacementSmootherPolicyTest {
    private val smoother = TemporalPlacementSmootherPolicy()

    @Test
    fun returns_raw_when_no_previous_sample() {
        val raw = TryOnPlacement(centerX = 40f, centerY = 65f, ringWidthPx = 72f, rotationDegrees = 12f)

        val smoothed = smoother.smooth(raw = raw, previous = null, deltaMs = 30L)

        assertThat(smoothed).isEqualTo(raw)
    }

    @Test
    fun applies_stronger_smoothing_for_small_movement_high_quality_locked_tracking() {
        val previous = TryOnPlacement(centerX = 100f, centerY = 100f, ringWidthPx = 80f, rotationDegrees = 5f)
        val raw = TryOnPlacement(centerX = 110f, centerY = 108f, ringWidthPx = 84f, rotationDegrees = 9f)

        val lockedHighQuality =
            smoother.smooth(
                raw = raw,
                previous = previous,
                deltaMs = 50L,
                context =
                    TryOnSmoothingContext(
                        qualityScore = 0.9f,
                        trackingState = TryOnTrackingState.Locked,
                        updateAction = TryOnUpdateAction.Update,
                    ),
            )
        val noisyLowQuality =
            smoother.smooth(
                raw = raw,
                previous = previous,
                deltaMs = 50L,
                context =
                    TryOnSmoothingContext(
                        qualityScore = 0.35f,
                        trackingState = TryOnTrackingState.Candidate,
                        updateAction = TryOnUpdateAction.Update,
                    ),
            )

        val lockedDelta = lockedHighQuality.centerX - previous.centerX
        val noisyDelta = noisyLowQuality.centerX - previous.centerX
        assertThat(lockedDelta).isLessThan(noisyDelta)
    }

    @Test
    fun reduces_smoothing_in_recovery_to_avoid_visible_lag() {
        val previous = TryOnPlacement(centerX = 100f, centerY = 100f, ringWidthPx = 80f, rotationDegrees = 5f)
        val raw = TryOnPlacement(centerX = 220f, centerY = 190f, ringWidthPx = 112f, rotationDegrees = 28f)

        val locked =
            smoother.smooth(
                raw = raw,
                previous = previous,
                deltaMs = 50L,
                context =
                    TryOnSmoothingContext(
                        qualityScore = 0.9f,
                        trackingState = TryOnTrackingState.Locked,
                        updateAction = TryOnUpdateAction.Update,
                    ),
            )
        val recovering =
            smoother.smooth(
                raw = raw,
                previous = previous,
                deltaMs = 50L,
                context =
                    TryOnSmoothingContext(
                        qualityScore = 0.45f,
                        trackingState = TryOnTrackingState.Recovering,
                        updateAction = TryOnUpdateAction.Recover,
                    ),
            )

        assertThat(recovering.centerX - previous.centerX).isGreaterThan(locked.centerX - previous.centerX)
    }

    @Test
    fun freeze_scale_rotation_only_updates_center() {
        val previous = TryOnPlacement(centerX = 100f, centerY = 100f, ringWidthPx = 80f, rotationDegrees = 5f)
        val raw = TryOnPlacement(centerX = 170f, centerY = 150f, ringWidthPx = 110f, rotationDegrees = 32f)

        val smoothed =
            smoother.smooth(
                raw = raw,
                previous = previous,
                deltaMs = 45L,
                context =
                    TryOnSmoothingContext(
                        qualityScore = 0.58f,
                        trackingState = TryOnTrackingState.Locked,
                        updateAction = TryOnUpdateAction.FreezeScaleRotation,
                    ),
            )

        assertThat(smoothed.centerX).isGreaterThan(previous.centerX)
        assertThat(smoothed.ringWidthPx).isEqualTo(previous.ringWidthPx)
        assertThat(smoothed.rotationDegrees).isEqualTo(previous.rotationDegrees)
    }

    @Test
    fun hold_action_returns_previous_placement() {
        val previous = TryOnPlacement(centerX = 100f, centerY = 100f, ringWidthPx = 80f, rotationDegrees = 5f)
        val raw = TryOnPlacement(centerX = 210f, centerY = 170f, ringWidthPx = 115f, rotationDegrees = 35f)

        val smoothed =
            smoother.smooth(
                raw = raw,
                previous = previous,
                deltaMs = 80L,
                context =
                    TryOnSmoothingContext(
                        qualityScore = 0.3f,
                        trackingState = TryOnTrackingState.Recovering,
                        updateAction = TryOnUpdateAction.HoldLastPlacement,
                    ),
            )

        assertThat(smoothed).isEqualTo(previous)
    }
}
