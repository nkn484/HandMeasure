package com.handtryon.coreengine

import com.handtryon.coreengine.model.TryOnPlacement
import com.handtryon.coreengine.model.TryOnTrackingState
import com.handtryon.coreengine.model.TryOnUpdateAction
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

data class TryOnSmoothingContext(
    val qualityScore: Float = 1f,
    val trackingState: TryOnTrackingState = TryOnTrackingState.Locked,
    val updateAction: TryOnUpdateAction = TryOnUpdateAction.Update,
)

class TemporalPlacementSmootherPolicy(
    private val minAlpha: Float = 0.16f,
    private val maxAlpha: Float = 0.5f,
) {
    fun smooth(
        raw: TryOnPlacement,
        previous: TryOnPlacement?,
        deltaMs: Long,
    ): TryOnPlacement = smooth(raw = raw, previous = previous, deltaMs = deltaMs, context = TryOnSmoothingContext())

    fun smooth(
        raw: TryOnPlacement,
        previous: TryOnPlacement?,
        deltaMs: Long,
        context: TryOnSmoothingContext,
    ): TryOnPlacement {
        val old = previous ?: return raw
        if (context.updateAction == TryOnUpdateAction.HoldLastPlacement || context.updateAction == TryOnUpdateAction.Hide) {
            return old
        }

        val baseAlpha = computeBaseAlpha(deltaMs)
        val quality = context.qualityScore.coerceIn(0f, 1f)
        val movementRatio = computeMovementRatio(raw = raw, old = old)
        val centerAlpha = computeCenterAlpha(baseAlpha, quality, movementRatio, context)
        val rotationAlpha = computeRotationAlpha(centerAlpha, context)
        val scaleAlpha = computeScaleAlpha(centerAlpha, context)

        val centerX = old.centerX + (raw.centerX - old.centerX) * centerAlpha
        val centerY = old.centerY + (raw.centerY - old.centerY) * centerAlpha
        val rotationDelta = normalizeRotationDelta(raw.rotationDegrees - old.rotationDegrees)
        val rotation =
            if (context.updateAction == TryOnUpdateAction.FreezeScaleRotation) {
                old.rotationDegrees
            } else {
                old.rotationDegrees + rotationDelta * rotationAlpha
            }
        val width =
            if (context.updateAction == TryOnUpdateAction.FreezeScaleRotation) {
                old.ringWidthPx
            } else {
                old.ringWidthPx + (raw.ringWidthPx - old.ringWidthPx) * scaleAlpha
            }
        return TryOnPlacement(
            centerX = centerX,
            centerY = centerY,
            ringWidthPx = width,
            rotationDegrees = rotation,
        )
    }

    private fun computeBaseAlpha(deltaMs: Long): Float {
        val clamped = max(16L, deltaMs).coerceAtMost(220L)
        val normalized = (clamped - 16f) / (220f - 16f)
        return minAlpha + (maxAlpha - minAlpha) * normalized
    }

    private fun computeMovementRatio(
        raw: TryOnPlacement,
        old: TryOnPlacement,
    ): Float {
        val dx = raw.centerX - old.centerX
        val dy = raw.centerY - old.centerY
        val positionDelta = sqrt(dx * dx + dy * dy)
        val positionRatio = positionDelta / old.ringWidthPx.coerceAtLeast(1f)
        val scaleRatio = abs(raw.ringWidthPx - old.ringWidthPx) / old.ringWidthPx.coerceAtLeast(1f)
        val rotationRatio = abs(normalizeRotationDelta(raw.rotationDegrees - old.rotationDegrees)) / 45f
        return (positionRatio * 0.7f + scaleRatio * 0.2f + rotationRatio * 0.1f).coerceIn(0f, 1.4f)
    }

    private fun computeCenterAlpha(
        baseAlpha: Float,
        quality: Float,
        movementRatio: Float,
        context: TryOnSmoothingContext,
    ): Float {
        var alpha = baseAlpha
        if (movementRatio < 0.12f && quality > 0.72f && context.trackingState == TryOnTrackingState.Locked) {
            alpha *= 0.62f
        }
        if (movementRatio > 0.45f) {
            alpha *= 1.5f
        }
        if (context.trackingState == TryOnTrackingState.Recovering || context.updateAction == TryOnUpdateAction.Recover) {
            alpha = max(alpha, 0.68f)
        }
        if (quality < 0.4f && context.updateAction == TryOnUpdateAction.Update) {
            alpha *= 1.2f
        }
        return alpha.coerceIn(0.12f, 0.88f)
    }

    private fun computeRotationAlpha(
        centerAlpha: Float,
        context: TryOnSmoothingContext,
    ): Float {
        val alpha =
            if (context.trackingState == TryOnTrackingState.Recovering || context.updateAction == TryOnUpdateAction.Recover) {
                max(centerAlpha, 0.6f)
            } else {
                centerAlpha * 0.84f
            }
        return alpha.coerceIn(0.1f, 0.88f)
    }

    private fun computeScaleAlpha(
        centerAlpha: Float,
        context: TryOnSmoothingContext,
    ): Float {
        val alpha =
            if (context.trackingState == TryOnTrackingState.Recovering || context.updateAction == TryOnUpdateAction.Recover) {
                max(centerAlpha * 0.92f, 0.55f)
            } else {
                centerAlpha * 0.72f
            }
        return alpha.coerceIn(0.08f, 0.84f)
    }

    private fun normalizeRotationDelta(delta: Float): Float {
        var adjusted = delta
        while (adjusted > 180f) adjusted -= 360f
        while (adjusted < -180f) adjusted += 360f
        return adjusted
    }
}
