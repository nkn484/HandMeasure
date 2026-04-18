package com.handtryon.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.handtryon.coreengine.TryOnOcclusionPolicy
import com.handtryon.engine.compat.TryOnRuntimeStateMapper

internal class LightweightRingOcclusionMaskProvider(
    private val occlusionPolicy: TryOnOcclusionPolicy = TryOnOcclusionPolicy(),
) : ContextAwareOcclusionMaskProvider {
    private val maskPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            style = Paint.Style.FILL
        }

    override fun applyOcclusion(
        canvas: Canvas,
        placement: com.handtryon.domain.RingPlacement,
        ringPaint: Paint,
        context: OcclusionMaskContext,
    ) {
        val decision =
            occlusionPolicy.evaluate(
                mode = context.mode.toCoreMode(),
                trackingState = TryOnRuntimeStateMapper.toCoreTrackingState(context.trackingState),
                updateAction = TryOnRuntimeStateMapper.toCoreUpdateAction(context.updateAction),
                qualityScore = context.qualityScore,
            )
        if (!decision.enabled) return

        val ringWidth = placement.ringWidthPx.coerceAtLeast(1f)
        val ringHeight = ringWidth / context.ringBitmapAspectRatio.coerceAtLeast(0.2f)
        val halfBandWidth = ringWidth * 0.44f
        val halfBandHeight = ringHeight * decision.bandThicknessRatio.coerceAtLeast(0.08f)
        val verticalOffset = ringHeight * decision.verticalOffsetRatio
        val alpha = (decision.maskOpacity * 255f).toInt().coerceIn(0, 255)
        if (alpha < 8) return

        maskPaint.alpha = alpha
        canvas.save()
        canvas.translate(placement.centerX, placement.centerY + verticalOffset)
        canvas.rotate(placement.rotationDegrees)
        canvas.drawOval(
            -halfBandWidth,
            -halfBandHeight,
            halfBandWidth,
            halfBandHeight,
            maskPaint,
        )
        canvas.restore()
    }

    private fun com.handtryon.domain.TryOnMode.toCoreMode(): com.handtryon.coreengine.model.TryOnMode =
        when (this) {
            com.handtryon.domain.TryOnMode.Measured -> com.handtryon.coreengine.model.TryOnMode.Measured
            com.handtryon.domain.TryOnMode.LandmarkOnly -> com.handtryon.coreengine.model.TryOnMode.LandmarkOnly
            com.handtryon.domain.TryOnMode.Manual -> com.handtryon.coreengine.model.TryOnMode.Manual
        }
}
