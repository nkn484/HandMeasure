package com.handtryon.coreengine

import com.google.common.truth.Truth.assertThat
import com.handtryon.coreengine.model.TryOnPlacement
import org.junit.Test

class PlacementValidationPolicyTest {
    private val validator = PlacementValidationPolicy()

    @Test
    fun flags_unreasonable_width_ratio() {
        val result =
            validator.validate(
                placement = TryOnPlacement(centerX = 100f, centerY = 100f, ringWidthPx = 900f, rotationDegrees = 0f),
                anchor = null,
                previousPlacement = null,
                frameWidth = 1080,
            )
        assertThat(result.isPlacementUsable).isFalse()
        assertThat(result.notes).contains("width_ratio_out_of_range")
    }
}
