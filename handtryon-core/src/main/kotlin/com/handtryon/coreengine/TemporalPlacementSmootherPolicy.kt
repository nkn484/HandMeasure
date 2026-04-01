package com.handtryon.coreengine

import com.handtryon.coreengine.model.TryOnPlacement
import kotlin.math.max

class TemporalPlacementSmootherPolicy(
    private val minAlpha: Float = 0.18f,
    private val maxAlpha: Float = 0.42f,
) {
    fun smooth(
        raw: TryOnPlacement,
        previous: TryOnPlacement?,
        deltaMs: Long,
    ): TryOnPlacement {
        val old = previous ?: return raw
        val alpha = computeAlpha(deltaMs)
        val centerX = old.centerX + (raw.centerX - old.centerX) * alpha
        val centerY = old.centerY + (raw.centerY - old.centerY) * alpha
        val width = old.ringWidthPx + (raw.ringWidthPx - old.ringWidthPx) * alpha
        val rotationDelta = normalizeRotationDelta(raw.rotationDegrees - old.rotationDegrees)
        val rotation = old.rotationDegrees + rotationDelta * alpha
        return TryOnPlacement(
            centerX = centerX,
            centerY = centerY,
            ringWidthPx = width,
            rotationDegrees = rotation,
        )
    }

    private fun computeAlpha(deltaMs: Long): Float {
        val clamped = max(16L, deltaMs).coerceAtMost(220L)
        val normalized = (clamped - 16f) / (220f - 16f)
        return minAlpha + (maxAlpha - minAlpha) * normalized
    }

    private fun normalizeRotationDelta(delta: Float): Float {
        var adjusted = delta
        while (adjusted > 180f) adjusted -= 360f
        while (adjusted < -180f) adjusted += 360f
        return adjusted
    }
}
