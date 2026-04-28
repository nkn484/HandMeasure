package com.handtryon.ar

import com.handtryon.domain.GlbAssetSummary
import com.handtryon.domain.RingPlacement

data class ArRingTransform(
    val xMeters: Float,
    val yMeters: Float,
    val zMeters: Float,
    val scale: Float,
    val rollDegrees: Float,
)

class ArRingPlacementMapper(
    private val defaultDepthMeters: Float = 0.42f,
    private val viewportWidthAtDepthMeters: Float = 0.32f,
) {
    fun map(
        placement: RingPlacement,
        frameWidth: Int,
        frameHeight: Int,
        glbSummary: GlbAssetSummary?,
    ): ArRingTransform {
        val safeWidth = frameWidth.coerceAtLeast(1).toFloat()
        val safeHeight = frameHeight.coerceAtLeast(1).toFloat()
        val normalizedX = (placement.centerX / safeWidth - 0.5f).coerceIn(-0.5f, 0.5f)
        val normalizedY = (placement.centerY / safeHeight - 0.5f).coerceIn(-0.5f, 0.5f)
        val modelWidthMeters = glbSummary?.estimatedBoundsMm?.x?.takeIf { it > 0f }?.div(1000f) ?: DEFAULT_RING_WIDTH_METERS
        val targetWidthMeters = (placement.ringWidthPx / safeWidth) * viewportWidthAtDepthMeters

        return ArRingTransform(
            xMeters = normalizedX * viewportWidthAtDepthMeters,
            yMeters = -normalizedY * viewportWidthAtDepthMeters * (safeHeight / safeWidth),
            zMeters = -defaultDepthMeters,
            scale = (targetWidthMeters / modelWidthMeters).coerceIn(0.2f, 4.0f),
            rollDegrees = placement.rotationDegrees,
        )
    }

    private companion object {
        const val DEFAULT_RING_WIDTH_METERS = 0.0204f
    }
}
