package com.handtryon.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.handtryon.domain.FingerAnchor
import com.handtryon.domain.RingPlacement
import com.handtryon.render.PreviewCoordinateMapper
import com.handtryon.render.StableRingOverlayRenderer

@Composable
fun TryOnOverlay(
    ringBitmap: Bitmap?,
    frameWidth: Int,
    frameHeight: Int,
    placement: RingPlacement?,
    anchor: FingerAnchor?,
    renderer: StableRingOverlayRenderer,
    manualAdjustEnabled: Boolean,
    onManualTransform: (panXFrame: Float, panYFrame: Float, zoom: Float, rotationDeg: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var viewportWidth by remember { mutableIntStateOf(0) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    val transform =
        PreviewCoordinateMapper.frameToViewport(
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        )

    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .pointerInput(manualAdjustEnabled, transform.scale) {
                    detectTransformGestures { _, pan, zoom, rotation ->
                        if (!manualAdjustEnabled) return@detectTransformGestures
                        val delta = PreviewCoordinateMapper.viewportDeltaToFrame(pan.x, pan.y, transform)
                        onManualTransform(delta.x, delta.y, zoom, rotation)
                    }
                },
    ) {
        viewportWidth = size.width.toInt()
        viewportHeight = size.height.toInt()
        if (ringBitmap == null || placement == null) return@Canvas
        val mapped = PreviewCoordinateMapper.placementToViewport(placement, transform)
        renderer.drawOverlay(
            canvas = drawContext.canvas.nativeCanvas,
            ringBitmap = ringBitmap,
            rawPlacement = mapped,
            anchor = anchor?.let {
                it.copy(
                    centerX = it.centerX * transform.scale + transform.offsetX,
                    centerY = it.centerY * transform.scale + transform.offsetY,
                    fingerWidthPx = it.fingerWidthPx * transform.scale,
                )
            },
            frameWidth = viewportWidth.coerceAtLeast(1),
        )
    }
}
