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
import com.handtryon.domain.TryOnTrackingState
import com.handtryon.domain.TryOnUpdateAction
import com.handtryon.render.model.TryOnRenderState
import com.handtryon.validation.PlacementValidator

class StableRingOverlayRenderer internal constructor(
    private val smoother: TemporalPlacementSmoother,
    private val validator: PlacementValidator,
    private val occlusionMaskProvider: OcclusionMaskProvider?,
) {
    constructor(
        occlusionMaskProvider: OcclusionMaskProvider? = LightweightRingOcclusionMaskProvider(),
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
        qualityScore: Float = anchor?.confidence ?: 0.62f,
        trackingState: TryOnTrackingState = if (anchor == null) TryOnTrackingState.Recovering else TryOnTrackingState.Locked,
        updateAction: TryOnUpdateAction = TryOnUpdateAction.Update,
        mode: TryOnMode = TryOnMode.LandmarkOnly,
    ): RingPlacement {
        val deltaMs = if (lastTimestampMs == 0L) 16L else (nowMs - lastTimestampMs).coerceAtLeast(1L)
        val stablePlacement =
            smoother.smooth(
                raw = rawPlacement,
                previous = lastPlacement,
                deltaMs = deltaMs,
                qualityScore = qualityScore,
                trackingState = trackingState,
                updateAction = updateAction,
            )
        val validation = validator.validate(stablePlacement, anchor, lastPlacement, frameWidth)
        val safePlacement =
            if (validation.isPlacementUsable) {
                stablePlacement
            } else {
                lastPlacement ?: stablePlacement
            }

        drawContactShadow(canvas, safePlacement)
        drawRing(
            canvas = canvas,
            ringBitmap = ringBitmap,
            placement = safePlacement,
            alpha = alpha,
            mode = mode,
            qualityScore = qualityScore,
            trackingState = trackingState,
            updateAction = updateAction,
            frameWidth = frameWidth,
        )
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
        qualityScore: Float = anchor?.confidence ?: 0.62f,
        trackingState: TryOnTrackingState = if (anchor == null) TryOnTrackingState.Recovering else TryOnTrackingState.Locked,
        updateAction: TryOnUpdateAction = TryOnUpdateAction.Update,
        shouldRenderOverlay: Boolean = true,
    ): TryOnRenderResult {
        val output = baseFrame.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        if (!shouldRenderOverlay) {
            val validation = validator.validate(rawPlacement, anchor, lastPlacement, baseFrame.width)
            lastTimestampMs = nowMs
            return TryOnRenderResult(
                bitmap = output,
                mode = mode,
                generatedAtMs = nowMs,
                validation = validation.copy(notes = (validation.notes + "suppressed_by_quality_gate").distinct()),
            )
        }
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
                qualityScore = qualityScore,
                trackingState = trackingState,
                updateAction = updateAction,
                mode = mode,
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
            qualityScore = renderState.qualityScore,
            trackingState = renderState.trackingState,
            updateAction = renderState.updateAction,
            shouldRenderOverlay = renderState.shouldRenderOverlay,
        )

    private fun drawRing(
        canvas: Canvas,
        ringBitmap: Bitmap,
        placement: RingPlacement,
        alpha: Int,
        mode: TryOnMode,
        qualityScore: Float,
        trackingState: TryOnTrackingState,
        updateAction: TryOnUpdateAction,
        frameWidth: Int,
    ) {
        val ringAspectRatio = ringBitmap.width.coerceAtLeast(1).toFloat() / ringBitmap.height.coerceAtLeast(1).toFloat()
        val layerPadding = placement.ringWidthPx * 0.7f
        val layerCount =
            canvas.saveLayer(
                placement.centerX - layerPadding,
                placement.centerY - layerPadding,
                placement.centerX + layerPadding,
                placement.centerY + layerPadding,
                null,
            )
        val scale = placement.ringWidthPx / ringBitmap.width.coerceAtLeast(1).toFloat()
        ringPaint.alpha = alpha.coerceIn(0, 255)
        canvas.save()
        canvas.translate(placement.centerX, placement.centerY)
        canvas.rotate(placement.rotationDegrees)
        canvas.scale(scale, scale)
        canvas.translate(-ringBitmap.width * 0.5f, -ringBitmap.height * 0.5f)
        canvas.drawBitmap(ringBitmap, 0f, 0f, ringPaint)
        canvas.restore()
        val context =
            OcclusionMaskContext(
                mode = mode,
                qualityScore = qualityScore,
                trackingState = trackingState,
                updateAction = updateAction,
                ringBitmapAspectRatio = ringAspectRatio,
                frameWidth = frameWidth,
            )
        when (val provider = occlusionMaskProvider) {
            is ContextAwareOcclusionMaskProvider -> provider.applyOcclusion(canvas, placement, ringPaint, context)
            null -> Unit
            else -> provider.applyOcclusion(canvas, placement, ringPaint)
        }
        canvas.restoreToCount(layerCount)
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
