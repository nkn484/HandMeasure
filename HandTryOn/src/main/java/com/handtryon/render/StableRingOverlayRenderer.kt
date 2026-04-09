package com.handtryon.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.handtryon.core.TemporalPlacementSmoother
import com.handtryon.domain.FingerAnchor
import com.handtryon.domain.RingPlacement
import com.handtryon.domain.TryOnMode
import com.handtryon.domain.TryOnRenderResult
import com.handtryon.render.model.TryOnRenderState
import com.handtryon.validation.PlacementValidator

class StableRingOverlayRenderer internal constructor(
    private val smoother: TemporalPlacementSmoother,
    private val validator: PlacementValidator,
    private val occlusionMaskProvider: OcclusionMaskProvider?,
) {
    constructor(
        occlusionMaskProvider: OcclusionMaskProvider? = null,
    ) : this(
        smoother = TemporalPlacementSmoother(),
        validator = PlacementValidator(),
        occlusionMaskProvider = occlusionMaskProvider,
    )

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(52, 0, 0, 0)
        }
    private var lastPlacement: RingPlacement? = null
    private var lastTimestampMs: Long = 0L

    fun reset() {
        lastPlacement = null
        lastTimestampMs = 0L
    }

    fun drawOverlay(
        canvas: Canvas,
        ringBitmap: Bitmap,
        rawPlacement: RingPlacement,
        anchor: FingerAnchor?,
        frameWidth: Int,
        nowMs: Long = System.currentTimeMillis(),
        alpha: Int = 255,
    ): RingPlacement {
        val deltaMs = if (lastTimestampMs == 0L) 16L else (nowMs - lastTimestampMs).coerceAtLeast(1L)
        val stablePlacement = smoother.smooth(raw = rawPlacement, previous = lastPlacement, deltaMs = deltaMs)
        val validation = validator.validate(stablePlacement, anchor, lastPlacement, frameWidth)
        val safePlacement =
            if (validation.isPlacementUsable) {
                stablePlacement
            } else {
                lastPlacement ?: stablePlacement
            }

        drawContactShadow(canvas, safePlacement)
        drawRing(canvas, ringBitmap, safePlacement, alpha = alpha)
        lastPlacement = safePlacement
        lastTimestampMs = nowMs
        return safePlacement
    }

    fun renderToBitmap(
        baseFrame: Bitmap,
        ringBitmap: Bitmap,
        rawPlacement: RingPlacement,
        anchor: FingerAnchor?,
        mode: TryOnMode,
        nowMs: Long = System.currentTimeMillis(),
    ): TryOnRenderResult {
        val output = baseFrame.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val previousPlacement = lastPlacement
        val stablePlacement =
            drawOverlay(
                canvas = canvas,
                ringBitmap = ringBitmap,
                rawPlacement = rawPlacement,
                anchor = anchor,
                frameWidth = baseFrame.width,
                nowMs = nowMs,
                alpha = 255,
            )
        val validation = validator.validate(stablePlacement, anchor, previousPlacement, baseFrame.width)
        return TryOnRenderResult(
            bitmap = output,
            mode = mode,
            generatedAtMs = nowMs,
            validation = validation,
        )
    }

    fun renderToBitmap(
        baseFrame: Bitmap,
        ringBitmap: Bitmap,
        renderState: TryOnRenderState,
        nowMs: Long = renderState.generatedAtMs,
    ): TryOnRenderResult =
        renderToBitmap(
            baseFrame = baseFrame,
            ringBitmap = ringBitmap,
            rawPlacement = renderState.placement,
            anchor = renderState.anchor,
            mode = renderState.mode,
            nowMs = nowMs,
        )

    private fun drawRing(
        canvas: Canvas,
        ringBitmap: Bitmap,
        placement: RingPlacement,
        alpha: Int,
    ) {
        val scale = placement.ringWidthPx / ringBitmap.width.coerceAtLeast(1).toFloat()
        ringPaint.alpha = alpha.coerceIn(0, 255)
        canvas.save()
        canvas.translate(placement.centerX, placement.centerY)
        canvas.rotate(placement.rotationDegrees)
        canvas.scale(scale, scale)
        canvas.translate(-ringBitmap.width * 0.5f, -ringBitmap.height * 0.5f)
        canvas.drawBitmap(ringBitmap, 0f, 0f, ringPaint)
        occlusionMaskProvider?.applyOcclusion(canvas, placement, ringPaint)
        canvas.restore()
    }

    private fun drawContactShadow(
        canvas: Canvas,
        placement: RingPlacement,
    ) {
        val shadowWidth = placement.ringWidthPx * 0.56f
        val shadowHeight = placement.ringWidthPx * 0.24f
        canvas.save()
        canvas.translate(placement.centerX, placement.centerY + placement.ringWidthPx * 0.08f)
        canvas.rotate(placement.rotationDegrees)
        canvas.drawOval(
            -shadowWidth * 0.5f,
            -shadowHeight * 0.5f,
            shadowWidth * 0.5f,
            shadowHeight * 0.5f,
            shadowPaint,
        )
        canvas.restore()
    }
}
