package com.handmeasure.coordinator

import android.graphics.Bitmap
import android.graphics.PointF
import com.handmeasure.api.TargetFinger
import com.handmeasure.vision.CardDetection
import com.handmeasure.vision.HandDetection
import kotlin.math.abs
import kotlin.math.hypot

internal data class FrameSignalScores(
    val blurGlobalScore: Float,
    val blurFingerRoiScore: Float,
    val motionScore: Float,
    val lightingScore: Float,
)

internal class FrameSignalEstimator {
    private var previousFrameLuma: FloatArray? = null

    fun resetTemporalState() {
        previousFrameLuma = null
    }

    fun estimate(
        bitmap: Bitmap,
        hand: HandDetection?,
        card: CardDetection?,
        targetFinger: TargetFinger,
    ): FrameSignalScores {
        val lumaGrid = sampleLumaGrid(bitmap, 32, 32)
        val blurGlobal = laplacianVarianceScore(lumaGrid.values, lumaGrid.width, lumaGrid.height)
        val fingerRoi = estimateFingerRoi(bitmap, hand, card, targetFinger)
        val blurFinger = laplacianVarianceScore(fingerRoi.values, fingerRoi.width, fingerRoi.height)
        val motion = motionScore(fingerRoi.values)
        val lighting = lightingScore(lumaGrid.values)
        return FrameSignalScores(
            blurGlobalScore = blurGlobal,
            blurFingerRoiScore = blurFinger,
            motionScore = motion,
            lightingScore = lighting,
        )
    }

    // This is not true 3D coplanarity. It is only a 2D proximity proxy in image space.
    fun estimateFingerCard2dProximity(
        hand: HandDetection?,
        card: CardDetection?,
        frameWidth: Int,
        frameHeight: Int,
        targetFinger: TargetFinger,
    ): Float {
        if (hand == null || card == null) return 0f
        val jointPair = hand.fingerJointPair(targetFinger) ?: return 0f
        val ringCenter = PointF((jointPair.first.x + jointPair.second.x) / 2f, (jointPair.first.y + jointPair.second.y) / 2f)
        val dx = (ringCenter.x - card.rectangle.centerX.toFloat()) / frameWidth.toFloat().coerceAtLeast(1f)
        val dy = (ringCenter.y - card.rectangle.centerY.toFloat()) / frameHeight.toFloat().coerceAtLeast(1f)
        val distance = hypot(dx.toDouble(), dy.toDouble())
        return (1.0 - distance / 0.55).toFloat().coerceIn(0f, 1f)
    }

    private fun sampleLumaGrid(bitmap: Bitmap, gridX: Int, gridY: Int): GridSample {
        val width = gridX.coerceAtLeast(4)
        val height = gridY.coerceAtLeast(4)
        val values = FloatArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val imageX = ((x + 0.5f) / width * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                val imageY = ((y + 0.5f) / height * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                values[y * width + x] = bitmap.lumaAt(imageX, imageY)
            }
        }
        return GridSample(values, width, height)
    }

    private fun estimateFingerRoi(
        bitmap: Bitmap,
        hand: HandDetection?,
        card: CardDetection?,
        targetFinger: TargetFinger,
    ): GridSample {
        val centerX =
            when {
                hand != null -> {
                    val joint = hand.fingerJointPair(targetFinger)
                    joint?.let { ((it.first.x + it.second.x) / 2f).toInt() } ?: bitmap.width / 2
                }
                card != null -> card.rectangle.centerX.toInt()
                else -> bitmap.width / 2
            }
        val centerY =
            when {
                hand != null -> {
                    val joint = hand.fingerJointPair(targetFinger)
                    joint?.let { ((it.first.y + it.second.y) / 2f).toInt() } ?: bitmap.height / 2
                }
                card != null -> card.rectangle.centerY.toInt()
                else -> bitmap.height / 2
            }
        val halfW = (bitmap.width * 0.18f).toInt().coerceAtLeast(80)
        val halfH = (bitmap.height * 0.18f).toInt().coerceAtLeast(80)
        val left = (centerX - halfW).coerceIn(0, bitmap.width - 1)
        val top = (centerY - halfH).coerceIn(0, bitmap.height - 1)
        val right = (centerX + halfW).coerceIn(left + 1, bitmap.width)
        val bottom = (centerY + halfH).coerceIn(top + 1, bitmap.height)
        val gridW = 24
        val gridH = 24
        val values = FloatArray(gridW * gridH)
        for (y in 0 until gridH) {
            for (x in 0 until gridW) {
                val imageX = (left + ((x + 0.5f) / gridW * (right - left))).toInt().coerceIn(0, bitmap.width - 1)
                val imageY = (top + ((y + 0.5f) / gridH * (bottom - top))).toInt().coerceIn(0, bitmap.height - 1)
                values[y * gridW + x] = bitmap.lumaAt(imageX, imageY)
            }
        }
        return GridSample(values, gridW, gridH)
    }

    private fun laplacianVarianceScore(values: FloatArray, width: Int, height: Int): Float {
        if (width < 3 || height < 3) return 0f
        val lap = ArrayList<Float>((width - 2) * (height - 2))
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = values[y * width + x]
                val left = values[y * width + x - 1]
                val right = values[y * width + x + 1]
                val up = values[(y - 1) * width + x]
                val down = values[(y + 1) * width + x]
                lap += (4f * center - left - right - up - down)
            }
        }
        if (lap.isEmpty()) return 0f
        val mean = lap.average().toFloat()
        val variance = lap.map { (it - mean) * (it - mean) }.average().toFloat()
        return (variance / 420f).coerceIn(0f, 1f)
    }

    private fun motionScore(current: FloatArray): Float {
        val previous = previousFrameLuma
        previousFrameLuma = current.copyOf()
        if (previous == null || previous.size != current.size) return 1f
        var diffSum = 0f
        current.indices.forEach { idx ->
            diffSum += abs(current[idx] - previous[idx])
        }
        val meanDiff = diffSum / current.size.toFloat()
        return (1f - meanDiff / 30f).coerceIn(0f, 1f)
    }

    private fun lightingScore(values: FloatArray): Float {
        if (values.isEmpty()) return 0f
        val mean = values.average().toFloat()
        var clippedDark = 0
        var clippedBright = 0
        values.forEach {
            if (it < 15f) clippedDark++
            if (it > 240f) clippedBright++
        }
        val clippingRatio = (clippedDark + clippedBright).toFloat() / values.size.toFloat()
        val centered = (1f - abs(mean - 140f) / 140f).coerceIn(0f, 1f)
        val clippingPenalty = (1f - clippingRatio / 0.18f).coerceIn(0f, 1f)
        return (centered * 0.65f + clippingPenalty * 0.35f).coerceIn(0f, 1f)
    }

    private fun Bitmap.lumaAt(x: Int, y: Int): Float {
        val pixel = getPixel(x, y)
        return (((pixel shr 16) and 0xff) * 0.299f + ((pixel shr 8) and 0xff) * 0.587f + (pixel and 0xff) * 0.114f)
    }

    private data class GridSample(
        val values: FloatArray,
        val width: Int,
        val height: Int,
    )
}
