package com.handtryon.coreengine

import com.google.common.truth.Truth.assertThat
import com.handtryon.coreengine.model.TryOnPlacement
import org.junit.Test

class TemporalPlacementSmootherPolicyTest {
    private val smoother = TemporalPlacementSmootherPolicy()

    @Test
    fun smoothes_toward_raw_values() {
        val previous = TryOnPlacement(centerX = 100f, centerY = 100f, ringWidthPx = 80f, rotationDegrees = 5f)
        val raw = TryOnPlacement(centerX = 200f, centerY = 180f, ringWidthPx = 120f, rotationDegrees = 35f)
        val smoothed = smoother.smooth(raw = raw, previous = previous, deltaMs = 50L)

        assertThat(smoothed.centerX).isGreaterThan(previous.centerX)
        assertThat(smoothed.centerX).isLessThan(raw.centerX)
        assertThat(smoothed.ringWidthPx).isGreaterThan(previous.ringWidthPx)
        assertThat(smoothed.ringWidthPx).isLessThan(raw.ringWidthPx)
    }
}
