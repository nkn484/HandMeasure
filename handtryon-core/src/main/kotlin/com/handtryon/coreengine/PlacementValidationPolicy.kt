package com.handtryon.coreengine

import com.handtryon.coreengine.model.TryOnFingerAnchor
import com.handtryon.coreengine.model.TryOnPlacement
import com.handtryon.coreengine.model.TryOnPlacementValidation
import kotlin.math.abs
import kotlin.math.sqrt

class PlacementValidationPolicy {
    fun validate(
        placement: TryOnPlacement,
        anchor: TryOnFingerAnchor?,
        previousPlacement: TryOnPlacement?,
        frameWidth: Int,
    ): TryOnPlacementValidation {
        val widthRatio = placement.ringWidthPx / frameWidth.coerceAtLeast(1)
        val anchorDistancePx =
            if (anchor == null) {
                0f
            } else {
                val dx = placement.centerX - anchor.centerX
                val dy = placement.centerY - anchor.centerY
                sqrt(dx * dx + dy * dy)
            }
        val rotationJump =
            if (previousPlacement == null) {
                0f
            } else {
                abs(normalizeRotationDelta(placement.rotationDegrees - previousPlacement.rotationDegrees))
            }

        val notes = mutableListOf<String>()
        if (widthRatio !in 0.02f..0.55f) notes += "width_ratio_out_of_range"
        if (anchor != null && anchorDistancePx > placement.ringWidthPx * 1.1f) notes += "far_from_anchor"
        if (rotationJump > 26f) notes += "rotation_jump_high"
        return TryOnPlacementValidation(
            widthRatio = widthRatio,
            anchorDistancePx = anchorDistancePx,
            rotationJumpDeg = rotationJump,
            isPlacementUsable = notes.isEmpty(),
            notes = notes,
        )
    }

    private fun normalizeRotationDelta(delta: Float): Float {
        var adjusted = delta
        while (adjusted > 180f) adjusted -= 360f
        while (adjusted < -180f) adjusted += 360f
        return adjusted
    }
}
