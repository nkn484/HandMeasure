package com.handmeasure.measurement

import com.handmeasure.vision.CardDetection

data class CardRectangle(
    val centerX: Double,
    val centerY: Double,
    val longSidePx: Double,
    val shortSidePx: Double,
    val angleDeg: Double,
)

data class MetricScale(
    val mmPerPxX: Double,
    val mmPerPxY: Double,
) {
    val meanMmPerPx: Double
        get() = (mmPerPxX + mmPerPxY) / 2.0
}

class ScaleCalibrator(
    private val cardWidthMm: Double = 85.60,
    private val cardHeightMm: Double = 53.98,
) {
    fun calibrate(cardRectangle: CardRectangle): MetricScale {
        val rectWidthPx = cardRectangle.longSidePx.coerceAtLeast(1.0)
        val rectHeightPx = cardRectangle.shortSidePx.coerceAtLeast(1.0)
        return MetricScale(
            mmPerPxX = cardWidthMm / rectWidthPx,
            mmPerPxY = cardHeightMm / rectHeightPx,
        )
    }

    fun calibrate(cardDetection: CardDetection): MetricScale = calibrate(cardDetection.rectangle)
}
