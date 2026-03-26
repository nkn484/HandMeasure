package com.handtryon.render

import androidx.compose.ui.geometry.Offset
import com.handtryon.domain.RingPlacement

data class FrameToViewportTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
)

object PreviewCoordinateMapper {
    fun frameToViewport(
        frameWidth: Int,
        frameHeight: Int,
        viewportWidth: Int,
        viewportHeight: Int,
    ): FrameToViewportTransform {
        if (frameWidth <= 0 || frameHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) {
            return FrameToViewportTransform(scale = 1f, offsetX = 0f, offsetY = 0f)
        }
        val scale = minOf(viewportWidth / frameWidth.toFloat(), viewportHeight / frameHeight.toFloat())
        val mappedWidth = frameWidth * scale
        val mappedHeight = frameHeight * scale
        return FrameToViewportTransform(
            scale = scale,
            offsetX = (viewportWidth - mappedWidth) * 0.5f,
            offsetY = (viewportHeight - mappedHeight) * 0.5f,
        )
    }

    fun placementToViewport(
        placement: RingPlacement,
        transform: FrameToViewportTransform,
    ): RingPlacement =
        placement.copy(
            centerX = placement.centerX * transform.scale + transform.offsetX,
            centerY = placement.centerY * transform.scale + transform.offsetY,
            ringWidthPx = placement.ringWidthPx * transform.scale,
        )

    fun viewportDeltaToFrame(
        deltaX: Float,
        deltaY: Float,
        transform: FrameToViewportTransform,
    ): Offset {
        val divisor = transform.scale.takeIf { it > 0f } ?: 1f
        return Offset(deltaX / divisor, deltaY / divisor)
    }
}
