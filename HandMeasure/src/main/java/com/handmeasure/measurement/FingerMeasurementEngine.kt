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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class FingerWidthMeasurement(
    val widthPx: Double,
    val widthMm: Double,
    val usedFallback: Boolean,
    val source: WidthMeasurementSource,
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
            val bandOffsets = SAMPLE_BAND_OFFSETS
            val scanDirections = buildScanDirections(perpNorm)
            val widthSamples = mutableListOf<WeightedWidthSample>()
            bandOffsets.forEach { offset ->
                val sampleCenter =
                    PointF(
                        localCenter.x + axisNorm.x * axisLength.toFloat() * offset,
                        localCenter.y + axisNorm.y * axisLength.toFloat() * offset,
                    )
                var bestSample: WeightedWidthSample? = null
                scanDirections.forEach { scanDirection ->
                    val leftEdge = findEdge(roiGradient, roiBinary, sampleCenter, scanDirection.direction, -1)
                    val rightEdge = findEdge(roiGradient, roiBinary, sampleCenter, scanDirection.direction, 1)
                    if (leftEdge != null && rightEdge != null) {
                        val width = distance(leftEdge.point, rightEdge.point)
                        if (width > axisLength * MIN_WIDTH_AXIS_RATIO && width < axisLength * MAX_WIDTH_AXIS_RATIO) {
                            val edgeStrength = min(leftEdge.score, rightEdge.score)
                            val symmetryScore = edgeSymmetryScore(sampleCenter, leftEdge.point, rightEdge.point, scanDirection.direction)
                            val directionPenalty = (1.0 - scanDirection.absAngleDeg / MAX_SCAN_ANGLE_DEG * 0.18).coerceIn(0.74, 1.0)
                            val confidenceWeight = edgeStrength * symmetryScore * directionPenalty
                            val candidate =
                                WeightedWidthSample(
                                    widthPx = width,
                                    weight = confidenceWeight.coerceAtLeast(0.01),
                                )
                            if (bestSample == null || candidate.weight > bestSample!!.weight) {
                                bestSample = candidate
                            }
                        }
                    }
                }
                if (bestSample != null) widthSamples += bestSample!!
            }
            roiBinary.release()
            roiGradient.release()

            if (widthSamples.size < MIN_VALID_SAMPLES) {
                return heuristicFallback(handDetection, targetFinger, scale)
            }
            val aggregated = robustAggregate(widthSamples)
            if (aggregated.samplesPx.size < MIN_VALID_SAMPLES) {
                return heuristicFallback(handDetection, targetFinger, scale)
            }
            val sampleMm = aggregated.samplesPx.map { it * scale.meanMmPerPx }
            val variance = weightedVariance(sampleMm, aggregated.weights)
            FingerWidthMeasurement(
                widthPx = aggregated.widthPx,
                widthMm = aggregated.widthPx * scale.meanMmPerPx,
                usedFallback = false,
                source = WidthMeasurementSource.EDGE_PROFILE,
                validSamples = aggregated.samplesPx.size,
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
        val (widthPx, source) =
            if (jointPair == null) {
                DEFAULT_WIDTH_PX to WidthMeasurementSource.DEFAULT_HEURISTIC
            } else {
                (distance(jointPair.first, jointPair.second) * JOINT_WIDTH_RATIO) to WidthMeasurementSource.LANDMARK_HEURISTIC
            }
        return FingerWidthMeasurement(
            widthPx = widthPx,
            widthMm = widthPx * scale.meanMmPerPx,
            usedFallback = true,
            source = source,
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
    ): EdgeHit? {
        var bestValue = 0.0
        var bestStep = 0.0
        var prevValue = 0.0
        var valueAtBestMinus = 0.0
        var valueAtBestPlus = 0.0
        val maxSteps = min(gradient.cols(), gradient.rows()) / 2
        for (step in 4 until maxSteps) {
            val sampleStep = step.toDouble()
            val x = center.x + direction.x * sampleStep.toFloat() * sign
            val y = center.y + direction.y * sampleStep.toFloat() * sign
            if (x < 1f || x >= (gradient.cols() - 1).toFloat() || y < 1f || y >= (gradient.rows() - 1).toFloat()) break
            val grad = sampleMat(gradient, x.toDouble(), y.toDouble())
            val mask = sampleMat(binary, x.toDouble(), y.toDouble())
            val transitionBoost = abs(grad - prevValue) * 0.12
            val value = grad + (if (mask > 127.0) MASK_EDGE_BONUS else 0.0) + transitionBoost
            if (value > bestValue) {
                valueAtBestMinus = prevValue
                bestValue = value
                bestStep = sampleStep
                valueAtBestPlus = value
            }
            prevValue = value
        }
        if (bestValue < EDGE_RESPONSE_THRESHOLD) return null
        val bestX = center.x + direction.x * bestStep.toFloat() * sign
        val bestY = center.y + direction.y * bestStep.toFloat() * sign
        val nextX = bestX + direction.x * sign
        val nextY = bestY + direction.y * sign
        if (nextX >= 1f && nextX < (gradient.cols() - 1).toFloat() && nextY >= 1f && nextY < (gradient.rows() - 1).toFloat()) {
            valueAtBestPlus = sampleMat(gradient, nextX.toDouble(), nextY.toDouble()) + MASK_EDGE_BONUS * (if (sampleMat(binary, nextX.toDouble(), nextY.toDouble()) > 127.0) 1.0 else 0.0)
        }
        val refinedStep = refineParabolicPeak(bestStep, valueAtBestMinus, bestValue, valueAtBestPlus)
        val refinedPoint =
            PointF(
                center.x + direction.x * refinedStep.toFloat() * sign,
                center.y + direction.y * refinedStep.toFloat() * sign,
            )
        return EdgeHit(
            point = refinedPoint,
            score = bestValue,
        )
    }

    private fun robustAggregate(samples: List<WeightedWidthSample>): AggregatedWidth {
        if (samples.isEmpty()) return AggregatedWidth(widthPx = 0.0, samplesPx = emptyList(), weights = emptyList())
        var center = weightedMedian(samples)
        var weights = samples.map { it.weight.coerceAtLeast(0.01) }
        repeat(ROBUST_ITERATIONS) {
            val residuals = samples.map { abs(it.widthPx - center) }
            val mad = median(residuals).coerceAtLeast(max(ROBUST_MIN_SCALE_PX, center * ROBUST_SCALE_FLOOR_RATIO))
            weights =
                samples.mapIndexed { index, sample ->
                    val normalized = (sample.widthPx - center) / mad
                    val robustWeight = 1.0 / (1.0 + (normalized / ROBUST_CAUCHY_K).let { it * it })
                    samples[index].weight * robustWeight
                }
            center = weightedMean(samples.map { it.widthPx }, weights)
        }
        val inlierResiduals = samples.map { abs(it.widthPx - center) }
        val inlierMad = median(inlierResiduals).coerceAtLeast(max(ROBUST_MIN_SCALE_PX, center * ROBUST_SCALE_FLOOR_RATIO))
        val maxResidual = max(ROBUST_INLIER_FLOOR_PX, inlierMad * ROBUST_INLIER_MAD_MULTIPLIER)
        val inlierSamples =
            samples.withIndex().filter { abs(it.value.widthPx - center) <= maxResidual }
        val chosen =
            if (inlierSamples.size >= MIN_VALID_SAMPLES) inlierSamples else samples.withIndex().sortedBy { abs(it.value.widthPx - center) }.take(MIN_VALID_SAMPLES)
        val chosenValues = chosen.map { it.value.widthPx }
        val chosenWeights = chosen.map { weights[it.index].coerceAtLeast(0.01) }
        val aggregatedWidth = weightedMean(chosenValues, chosenWeights)
        return AggregatedWidth(
            widthPx = aggregatedWidth,
            samplesPx = chosenValues,
            weights = chosenWeights,
        )
    }

    private fun weightedMean(
        values: List<Double>,
        weights: List<Double>,
    ): Double {
        if (values.isEmpty()) return 0.0
        val total = weights.sum().coerceAtLeast(1e-6)
        return values.zip(weights).sumOf { it.first * it.second } / total
    }

    private fun weightedMedian(samples: List<WeightedWidthSample>): Double {
        if (samples.isEmpty()) return 0.0
        val sorted = samples.sortedBy { it.widthPx }
        val totalWeight = sorted.sumOf { it.weight }.coerceAtLeast(1e-6)
        var cumulative = 0.0
        sorted.forEach { sample ->
            cumulative += sample.weight
            if (cumulative >= totalWeight / 2.0) return sample.widthPx
        }
        return sorted.last().widthPx
    }

    private fun weightedVariance(
        values: List<Double>,
        weights: List<Double>,
    ): Double {
        if (values.isEmpty()) return 0.0
        val mean = weightedMean(values, weights)
        val totalWeight = weights.sum().coerceAtLeast(1e-6)
        return values.zip(weights).sumOf { (value, weight) -> (value - mean) * (value - mean) * weight } / totalWeight
    }

    private fun edgeSymmetryScore(
        center: PointF,
        left: PointF,
        right: PointF,
        direction: PointF,
    ): Double {
        val leftDistance = distance(center, left)
        val rightDistance = distance(center, right)
        val balance = (1.0 - abs(leftDistance - rightDistance) / (leftDistance + rightDistance).coerceAtLeast(1e-6)).coerceIn(0.0, 1.0)
        val midX = (left.x + right.x) * 0.5f
        val midY = (left.y + right.y) * 0.5f
        val offsetAlongDirection = abs((midX - center.x) * direction.x + (midY - center.y) * direction.y).toDouble()
        val centerConsistency = (1.0 - offsetAlongDirection / (max(leftDistance, rightDistance) * 0.4).coerceAtLeast(1e-6)).coerceIn(0.0, 1.0)
        return (balance * 0.68 + centerConsistency * 0.32).coerceIn(0.0, 1.0)
    }

    private fun refineParabolicPeak(
        centerStep: Double,
        valueMinus: Double,
        valueCenter: Double,
        valuePlus: Double,
    ): Double {
        val denominator = valueMinus - 2.0 * valueCenter + valuePlus
        if (abs(denominator) < 1e-6) return centerStep
        val offset = 0.5 * (valueMinus - valuePlus) / denominator
        return centerStep + offset.coerceIn(-0.8, 0.8)
    }

    private fun sampleMat(
        mat: Mat,
        x: Double,
        y: Double,
    ): Double {
        val clampedX = x.coerceIn(0.0, (mat.cols() - 1).toDouble())
        val clampedY = y.coerceIn(0.0, (mat.rows() - 1).toDouble())
        val x0 = clampedX.toInt()
        val y0 = clampedY.toInt()
        val x1 = min(x0 + 1, mat.cols() - 1)
        val y1 = min(y0 + 1, mat.rows() - 1)
        val dx = clampedX - x0
        val dy = clampedY - y0
        val v00 = mat.get(y0, x0)[0]
        val v01 = mat.get(y0, x1)[0]
        val v10 = mat.get(y1, x0)[0]
        val v11 = mat.get(y1, x1)[0]
        return (v00 * (1 - dx) + v01 * dx) * (1 - dy) + (v10 * (1 - dx) + v11 * dx) * dy
    }

    private fun buildScanDirections(perpendicularUnit: PointF): List<ScanDirection> {
        val baseAngle = atan2(perpendicularUnit.y.toDouble(), perpendicularUnit.x.toDouble())
        return SCAN_DIRECTION_ANGLES_DEG.map { angleDeg ->
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val theta = baseAngle + angleRad
            ScanDirection(
                direction =
                    PointF(
                        cos(theta).toFloat(),
                        sin(theta).toFloat(),
                    ),
                absAngleDeg = abs(angleDeg.toDouble()),
            )
        }
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
    }

    private fun distance(a: PointF, b: PointF): Double = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

    private fun distance(a: Landmark2D, b: Landmark2D): Double = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

    private companion object {
        // Offsets sampled along the MCP->PIP axis to robustly estimate visible width around the ring zone.
        val SAMPLE_BAND_OFFSETS = listOf(-0.22f, -0.18f, -0.14f, -0.10f, -0.06f, -0.02f, 0f, 0.02f, 0.06f, 0.10f, 0.14f, 0.18f, 0.22f)
        val SCAN_DIRECTION_ANGLES_DEG = listOf(-16, -11, -7, -4, 0, 4, 7, 11, 16)
        const val MAX_SCAN_ANGLE_DEG = 16.0
        const val MIN_WIDTH_AXIS_RATIO = 0.12
        const val MAX_WIDTH_AXIS_RATIO = 1.15
        const val EDGE_RESPONSE_THRESHOLD = 22.0
        const val MASK_EDGE_BONUS = 18.0
        const val JOINT_WIDTH_RATIO = 0.40
        const val DEFAULT_WIDTH_PX = 120.0
        const val MIN_VALID_SAMPLES = 4
        const val ROBUST_ITERATIONS = 6
        const val ROBUST_CAUCHY_K = 1.55
        const val ROBUST_MIN_SCALE_PX = 0.7
        const val ROBUST_SCALE_FLOOR_RATIO = 0.03
        const val ROBUST_INLIER_MAD_MULTIPLIER = 2.9
        const val ROBUST_INLIER_FLOOR_PX = 1.8
    }

    private data class EdgeHit(
        val point: PointF,
        val score: Double,
    )

    private data class ScanDirection(
        val direction: PointF,
        val absAngleDeg: Double,
    )

    private data class WeightedWidthSample(
        val widthPx: Double,
        val weight: Double,
    )

    private data class AggregatedWidth(
        val widthPx: Double,
        val samplesPx: List<Double>,
        val weights: List<Double>,
    )
}
