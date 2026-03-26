package com.handtryon.render

import android.graphics.Canvas
import android.graphics.Paint
import com.handtryon.domain.RingPlacement

fun interface OcclusionMaskProvider {
    fun applyOcclusion(
        canvas: Canvas,
        placement: RingPlacement,
        ringPaint: Paint,
    )
}
