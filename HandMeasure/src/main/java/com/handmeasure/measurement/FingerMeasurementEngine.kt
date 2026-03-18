package com.handmeasure.measurement

import android.graphics.Bitmap
import android.graphics.PointF
import com.handmeasure.api.TargetFinger
import com.handmeasure.vision.HandDetection
import com.handmeasure.vision.Landmark2D
import com.handmeasure.vision.OpenCvBootstrap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class FingerWidthMeasurement(
    val widthPx: Double,
    val widthMm: Double,
    val usedFallback: Boolean,
    val validSamples: Int = 0,
    val widthVarianceMm: Double = 0.0,
    val sampledWidthsMm: List<Double> = emptyList(),
)

class FingerMeasurementEngine {
    fun measureVisibleWidth(
        bitmap: Bitmap,
        handDetection: HandDetection,
        targetFinger: TargetFinger,
        scale: MetricScale,
    ): FingerWidthMeasurement {
        if (!OpenCvBootstrap.ensureLoaded()) {
            return heuristicFallback(handDetection, targetFinger, scale)
        }
        val jointPair = handDetection.fingerJointPair(targetFinger) ?: return heuristicFallback(handDetection, targetFinger, scale)
        val mcp = jointPair.first
        val pip = jointPair.second
        val axis = PointF(pip.x - mcp.x, pip.y - mcp.y)
        val axisLength = hypot(axis.x.toDouble(), axis.y.toDouble()).coerceAtLeast(8.0)
        val axisNorm = PointF((axis.x / axisLength).toFloat(), (axis.y / axisLength).toFloat())
        val perpNorm = PointF(-axisNorm.y, axisNorm.x)
        val ringCenter = PointF(mcp.x + axisNorm.x * (axisLength * 0.45f).toFloat(), mcp.y + axisNorm.y * (axisLength * 0.45f).toFloat())

        val src = Mat()
        val gray = Mat()
        val gradX = Mat()
        val gradY = Mat()
        val gradMag = Mat()
        val binary = Mat()
        return try {
            Utils.bitmapToMat(bitmap, src)
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, org.opencv.core.Size(3.0, 3.0), 0.0)
            Imgproc.Sobel(gray, gradX, CvType.CV_32F, 1, 0)
            Imgproc.Sobel(gray, gradY, CvType.CV_32F, 0, 1)
            Core.magnitude(gradX, gradY, gradMag)
            Imgproc.adaptiveThreshold(gray, binary, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 31, 5.0)

            val roiRadius = (axisLength * 0.95).toInt().coerceIn(72, max(bitmap.width, bitmap.height) / 3)
            val left = (ringCenter.x.toInt() - roiRadius).coerceAtLeast(0)
            val top = (ringCenter.y.toInt() - roiRadius).coerceAtLeast(0)
            val rect = Rect(left, top, min(roiRadius * 2, gradMag.cols() - left), min(roiRadius * 2, gradMag.rows() - top))
            if (rect.width <= 0 || rect.height <= 0) return heuristicFallback(handDetection, targetFinger, scale)
            val roiGradient = Mat(gradMag, rect)
            val roiBinary = Mat(binary, rect)
            val localCenter = PointF(ringCenter.x - rect.x, ringCenter.y - rect.y)
            val bandOffsets = listOf(-0.18f, -0.12f, -0.06f, 0f, 0.06f, 0.12f, 0.18f)
            val widthSamplesPx = mutableListOf<Double>()
            val debugSamplesMm = mutableListOf<Double>()
            bandOffsets.forEach { offset ->
                val sampleCenter =
                    PointF(
                        localCenter.x + axisNorm.x * axisLength.toFloat() * offset,
                        localCenter.y + axisNorm.y * axisLength.toFloat() * offset,
                    )
                val leftEdge = findEdge(roiGradient, roiBinary, sampleCenter, perpNorm, -1)
                val rightEdge = findEdge(roiGradient, roiBinary, sampleCenter, perpNorm, 1)
                if (leftEdge != null && rightEdge != null) {
                    val width = distance(leftEdge, rightEdge)
                    if (width > axisLength * 0.12 && width < axisLength * 1.15) {
                        widthSamplesPx += width
                        debugSamplesMm += width * scale.meanMmPerPx
                    }
                }
            }
            roiBinary.release()
            roiGradient.release()

            val cleanedSamples = rejectOutliers(widthSamplesPx)
            if (cleanedSamples.size < 3) {
                return heuristicFallback(handDetection, targetFinger, scale)
            }
            val widthPx = median(cleanedSamples)
            val sampleMm = cleanedSamples.map { it * scale.meanMmPerPx }
            val variance = variance(sampleMm)
            FingerWidthMeasurement(
                widthPx = widthPx,
                widthMm = widthPx * scale.meanMmPerPx,
                usedFallback = false,
                validSamples = cleanedSamples.size,
                widthVarianceMm = variance,
                sampledWidthsMm = sampleMm,
            )
        } finally {
            binary.release()
            gradMag.release()
            gradY.release()
            gradX.release()
            gray.release()
            src.release()
        }
    }

    private fun heuristicFallback(
        handDetection: HandDetection,
        targetFinger: TargetFinger,
        scale: MetricScale,
    ): FingerWidthMeasurement {
        val jointPair = handDetection.fingerJointPair(targetFinger)
        val widthPx =
            if (jointPair == null) {
                120.0
            } else {
                distance(jointPair.first, jointPair.second) * 0.40
            }
        return FingerWidthMeasurement(
            widthPx = widthPx,
            widthMm = widthPx * scale.meanMmPerPx,
            usedFallback = true,
            validSamples = 0,
            widthVarianceMm = 999.0,
            sampledWidthsMm = emptyList(),
        )
    }

    private fun findEdge(
        gradient: Mat,
        binary: Mat,
        center: PointF,
        direction: PointF,
        sign: Int,
    ): PointF? {
        var bestValue = 0.0
        var best: PointF? = null
        val maxSteps = min(gradient.cols(), gradient.rows()) / 2
        for (step in 4 until maxSteps) {
            val x = (center.x + direction.x * step * sign).toInt()
            val y = (center.y + direction.y * step * sign).toInt()
            if (x !in 1 until gradient.cols() - 1 || y !in 1 until gradient.rows() - 1) break
            val grad = gradient.get(y, x)[0]
            val mask = binary.get(y, x)[0]
            val value = grad + if (mask > 127.0) 18.0 else 0.0
            if (value > bestValue) {
                bestValue = value
                best = PointF(x.toFloat(), y.toFloat())
            }
        }
        return if (bestValue >= 22.0) best else null
    }

    private fun rejectOutliers(values: List<Double>): List<Double> {
        if (values.size <= 3) return values
        val med = median(values)
        val deviations = values.map { abs(it - med) }
        val mad = median(deviations).coerceAtLeast(1.0)
        return values.filter { abs(it - med) <= mad * 2.8 }
    }

    private fun variance(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        return values.map { (it - mean) * (it - mean) }.average()
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
    }

    private fun distance(a: PointF, b: PointF): Double = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

    private fun distance(a: Landmark2D, b: Landmark2D): Double = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
}
