package com.handmeasure.vision

import android.graphics.Bitmap
import android.util.Log
import com.handmeasure.measurement.CardRectangle
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.RotatedRect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

interface ReferenceCardDetector {
    fun detect(bitmap: Bitmap): CardDetection?
}

class OpenCvReferenceCardDetector : ReferenceCardDetector {
    override fun detect(bitmap: Bitmap): CardDetection? {
        if (!OpenCvBootstrap.ensureLoaded()) return null

        val rgba = Mat()
        val gray = Mat()
        val blur = Mat()
        val edges = Mat()
        val hierarchy = Mat()
        val contours = ArrayList<MatOfPoint>()

        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blur, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blur, edges, 28.0, 95.0)
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
            Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel)
            kernel.release()

            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            val frameArea = (bitmap.width * bitmap.height).toDouble().coerceAtLeast(1.0)

            contours
                .mapNotNull { contour ->
                    val area = Imgproc.contourArea(contour)
                    if (area < frameArea * 0.006) return@mapNotNull null
                    val contour2f = MatOfPoint2f(*contour.toArray())
                    val rotated = Imgproc.minAreaRect(contour2f)
                    val rect = RectangleFitHelper.fromRotatedRect(rotated)
                    if (rect.shortSidePx <= 2.0) {
                        contour2f.release()
                        return@mapNotNull null
                    }

                    val rotatedArea = rotated.size.area().coerceAtLeast(1.0)
                    val rectangularity = (area / rotatedArea).toFloat().coerceIn(0f, 1f)
                    val aspect = rect.longSidePx / rect.shortSidePx
                    val aspectResidual = (abs(aspect - ID1_ASPECT) / ID1_ASPECT).toFloat()
                    val aspectScore = (1f - aspectResidual / 0.30f).coerceIn(0f, 1f)
                    val coverageRatio = (area / frameArea).toFloat().coerceIn(0f, 1f)
                    val coverageScore = (coverageRatio / 0.10f).coerceIn(0f, 1f)
                    val corners = RectangleFitHelper.extractCorners(rotated)
                    val edgeSupport = edgeSupportScore(edges, corners)
                    val perspective = estimatePerspectiveDistortion(corners)
                    val rectificationConfidence = ((1f - perspective) * 0.45f + edgeSupport * 0.55f).coerceIn(0f, 1f)
                    val confidence =
                        (
                            aspectScore * 0.34f +
                                rectangularity * 0.25f +
                                edgeSupport * 0.22f +
                                coverageScore * 0.10f +
                                rectificationConfidence * 0.09f
                        ).coerceIn(0f, 1f)

                    contour2f.release()
                    if (confidence < 0.22f || aspectScore < 0.25f || rectangularity < 0.35f) return@mapNotNull null
                    CardDetection(
                        rectangle = rect,
                        corners = corners,
                        contourAreaScore = rectangularity,
                        aspectScore = aspectScore,
                        confidence = confidence,
                        coverageRatio = coverageRatio,
                        aspectResidual = aspectResidual,
                        rectangularityScore = rectangularity,
                        edgeSupportScore = edgeSupport,
                        rectificationConfidence = rectificationConfidence,
                        perspectiveDistortion = perspective,
                    )
                }
                .maxByOrNull { it.confidence }
        } catch (error: Throwable) {
            Log.w("ReferenceCardDetector", "Card detection failed: ${error.message}")
            null
        } finally {
            contours.forEach { it.release() }
            hierarchy.release()
            edges.release()
            blur.release()
            gray.release()
            rgba.release()
        }
    }

    private fun edgeSupportScore(edges: Mat, corners: List<Pair<Float, Float>>): Float {
        if (corners.size != 4) return 0f
        val points = corners.map { Point(it.first.toDouble(), it.second.toDouble()) }
        var totalSamples = 0
        var hitSamples = 0
        points.indices.forEach { index ->
            val a = points[index]
            val b = points[(index + 1) % points.size]
            val length = distance(a, b)
            val samples = max(8, (length / 12.0).toInt())
            for (i in 0..samples) {
                val t = i.toDouble() / samples.toDouble()
                val x = (a.x + (b.x - a.x) * t).toInt()
                val y = (a.y + (b.y - a.y) * t).toInt()
                if (x in 1 until edges.cols() - 1 && y in 1 until edges.rows() - 1) {
                    totalSamples++
                    val edge = edges.get(y, x)[0]
                    if (edge > 0.0) {
                        hitSamples++
                    } else {
                        val localPatch = edges.submat(max(0, y - 1), min(edges.rows(), y + 2), max(0, x - 1), min(edges.cols(), x + 2))
                        if (Core.countNonZero(localPatch) > 0) hitSamples++
                        localPatch.release()
                    }
                }
            }
        }
        if (totalSamples == 0) return 0f
        return (hitSamples.toFloat() / totalSamples.toFloat()).coerceIn(0f, 1f)
    }

    private fun estimatePerspectiveDistortion(corners: List<Pair<Float, Float>>): Float {
        if (corners.size != 4) return 1f
        val ordered = RectangleFitHelper.orderCorners(corners.map { Point(it.first.toDouble(), it.second.toDouble()) })
        val top = distance(ordered[0], ordered[1])
        val bottom = distance(ordered[3], ordered[2])
        val left = distance(ordered[0], ordered[3])
        val right = distance(ordered[1], ordered[2])
        val horizontalSkew = abs(top - bottom) / max((top + bottom) / 2.0, 1.0)
        val verticalSkew = abs(left - right) / max((left + right) / 2.0, 1.0)
        return (horizontalSkew + verticalSkew).toFloat().coerceIn(0f, 1f)
    }

    private fun distance(a: Point, b: Point): Double = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))

    private companion object {
        const val ID1_ASPECT = 85.60 / 53.98
    }
}

object RectangleFitHelper {
    fun fromRotatedRect(rotatedRect: RotatedRect): CardRectangle {
        val longSide = max(rotatedRect.size.width, rotatedRect.size.height)
        val shortSide = min(rotatedRect.size.width, rotatedRect.size.height)
        val normalizedAngle =
            if (rotatedRect.size.width >= rotatedRect.size.height) rotatedRect.angle.toDouble() else rotatedRect.angle.toDouble() + 90.0
        return CardRectangle(
            centerX = rotatedRect.center.x,
            centerY = rotatedRect.center.y,
            longSidePx = longSide,
            shortSidePx = shortSide,
            angleDeg = normalizedAngle,
        )
    }

    fun extractCorners(rotatedRect: RotatedRect): List<Pair<Float, Float>> {
        val points = arrayOfNulls<Point>(4)
        rotatedRect.points(points)
        val ordered = orderCorners(points.filterNotNull())
        return ordered.map { it.x.toFloat() to it.y.toFloat() }
    }

    fun orderCorners(corners: List<Point>): List<Point> {
        if (corners.size != 4) return corners
        val sortedBySum = corners.sortedBy { it.x + it.y }
        val tl = sortedBySum.first()
        val br = sortedBySum.last()
        val remaining = corners.filter { it != tl && it != br }
        val tr = remaining.minByOrNull { it.y - it.x } ?: remaining.first()
        val bl = remaining.maxByOrNull { it.y - it.x } ?: remaining.last()
        return listOf(tl, tr, br, bl)
    }

    fun rectifyCard(bitmap: Bitmap, detection: CardDetection, outputWidth: Int = 856, outputHeight: Int = 540): Bitmap? {
        if (!OpenCvBootstrap.ensureLoaded()) return null
        val src = Mat()
        val dst = Mat()
        return try {
            Utils.bitmapToMat(bitmap, src)
            val corners = detection.corners.map { Point(it.first.toDouble(), it.second.toDouble()) }
            if (corners.size != 4) return null
            val ordered = orderCorners(corners)
            val input = MatOfPoint2f(*ordered.toTypedArray())
            val output =
                MatOfPoint2f(
                    Point(0.0, 0.0),
                    Point(outputWidth.toDouble(), 0.0),
                    Point(outputWidth.toDouble(), outputHeight.toDouble()),
                    Point(0.0, outputHeight.toDouble()),
                )
            val transform = Imgproc.getPerspectiveTransform(input, output)
            Imgproc.warpPerspective(src, dst, transform, Size(outputWidth.toDouble(), outputHeight.toDouble()))
            val outBitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(dst, outBitmap)
            transform.release()
            input.release()
            output.release()
            outBitmap
        } finally {
            dst.release()
            src.release()
        }
    }
}
