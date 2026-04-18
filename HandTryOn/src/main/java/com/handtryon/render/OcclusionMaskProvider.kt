package com.handtryon.render

import android.graphics.Canvas
import android.graphics.Paint
import com.handtryon.domain.RingPlacement
import com.handtryon.domain.TryOnMode
import com.handtryon.domain.TryOnTrackingState
import com.handtryon.domain.TryOnUpdateAction

fun interface OcclusionMaskProvider {
    fun applyOcclusion(
        canvas: Canvas,
        placement: RingPlacement,
        ringPaint: Paint,
    )
}

data class OcclusionMaskContext(
    val mode: TryOnMode,
    val qualityScore: Float,
    val trackingState: TryOnTrackingState,
    val updateAction: TryOnUpdateAction,
    val ringBitmapAspectRatio: Float,
    val frameWidth: Int,
)

internal interface ContextAwareOcclusionMaskProvider : OcclusionMaskProvider {
    fun applyOcclusion(
        canvas: Canvas,
        placement: RingPlacement,
        ringPaint: Paint,
        context: OcclusionMaskContext,
    )

    override fun applyOcclusion(
        canvas: Canvas,
        placement: RingPlacement,
        ringPaint: Paint,
    ) {
        applyOcclusion(
            canvas = canvas,
            placement = placement,
            ringPaint = ringPaint,
            context =
                OcclusionMaskContext(
                    mode = TryOnMode.LandmarkOnly,
                    qualityScore = 1f,
                    trackingState = TryOnTrackingState.Locked,
                    updateAction = TryOnUpdateAction.Update,
                    ringBitmapAspectRatio = 1f,
                    frameWidth = 1080,
                ),
        )
    }
}
