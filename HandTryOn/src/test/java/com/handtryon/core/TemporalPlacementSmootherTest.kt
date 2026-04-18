package com.handtryon.core

import com.google.common.truth.Truth.assertThat
import com.handtryon.domain.RingPlacement
import com.handtryon.domain.TryOnTrackingState
import com.handtryon.domain.TryOnUpdateAction
import org.junit.Test

class TemporalPlacementSmootherTest {
    private val smoother = TemporalPlacementSmoother()

    @Test
    fun smoothes_toward_raw_values() {
        val previous = RingPlacement(centerX = 100f, centerY = 100f, ringWidthPx = 80f, rotationDegrees = 5f)
        val raw = RingPlacement(centerX = 200f, centerY = 180f, ringWidthPx = 120f, rotationDegrees = 35f)
        val smoothed = smoother.smooth(raw = raw, previous = previous, deltaMs = 50L)

        assertThat(smoothed.centerX).isGreaterThan(previous.centerX)
        assertThat(smoothed.centerX).isLessThan(raw.centerX)
        assertThat(smoothed.ringWidthPx).isGreaterThan(previous.ringWidthPx)
        assertThat(smoothed.ringWidthPx).isLessThan(raw.ringWidthPx)
    }

    @Test
    fun handles_rotation_wraparound() {
        val previous = RingPlacement(centerX = 0f, centerY = 0f, ringWidthPx = 100f, rotationDegrees = 175f)
        val raw = RingPlacement(centerX = 0f, centerY = 0f, ringWidthPx = 100f, rotationDegrees = -176f)
        val smoothed = smoother.smooth(raw = raw, previous = previous, deltaMs = 90L)

        assertThat(smoothed.rotationDegrees).isGreaterThan(150f)
    }

    @Test
    fun freeze_action_preserves_scale_and_rotation() {
        val previous = RingPlacement(centerX = 100f, centerY = 120f, ringWidthPx = 80f, rotationDegrees = 5f)
        val raw = RingPlacement(centerX = 180f, centerY = 150f, ringWidthPx = 112f, rotationDegrees = 25f)

        val smoothed =
            smoother.smooth(
                raw = raw,
                previous = previous,
                deltaMs = 45L,
                qualityScore = 0.55f,
                trackingState = TryOnTrackingState.Locked,
                updateAction = TryOnUpdateAction.FreezeScaleRotation,
            )

        assertThat(smoothed.centerX).isGreaterThan(previous.centerX)
        assertThat(smoothed.ringWidthPx).isEqualTo(previous.ringWidthPx)
        assertThat(smoothed.rotationDegrees).isEqualTo(previous.rotationDegrees)
    }
}
