package com.handtryon.validation

import com.google.common.truth.Truth.assertThat
import com.handtryon.domain.FingerAnchor
import com.handtryon.domain.RingPlacement
import org.junit.Test

class PlacementValidatorTest {
    private val validator = PlacementValidator()

    @Test
    fun flags_unreasonable_width_ratio() {
        val result =
            validator.validate(
                placement = RingPlacement(centerX = 100f, centerY = 100f, ringWidthPx = 900f, rotationDegrees = 0f),
                anchor = null,
                previousPlacement = null,
                frameWidth = 1080,
            )
        assertThat(result.isPlacementUsable).isFalse()
        assertThat(result.notes).contains("width_ratio_out_of_range")
    }

    @Test
    fun flags_large_rotation_jump() {
        val previous = RingPlacement(centerX = 100f, centerY = 100f, ringWidthPx = 90f, rotationDegrees = 0f)
        val next = previous.copy(rotationDegrees = 60f)
        val result =
            validator.validate(
                placement = next,
                anchor = null,
                previousPlacement = previous,
                frameWidth = 1080,
            )
        assertThat(result.notes).contains("rotation_jump_high")
    }

    @Test
    fun returns_usable_for_stable_placement() {
        val anchor =
            FingerAnchor(
                centerX = 300f,
                centerY = 500f,
                angleDegrees = 8f,
                fingerWidthPx = 90f,
                confidence = 0.9f,
                timestampMs = 1000L,
            )
        val result =
            validator.validate(
                placement = RingPlacement(centerX = 305f, centerY = 504f, ringWidthPx = 92f, rotationDegrees = 9f),
                anchor = anchor,
                previousPlacement = RingPlacement(centerX = 304f, centerY = 503f, ringWidthPx = 91f, rotationDegrees = 8.5f),
                frameWidth = 1080,
            )
        assertThat(result.isPlacementUsable).isTrue()
    }
}
