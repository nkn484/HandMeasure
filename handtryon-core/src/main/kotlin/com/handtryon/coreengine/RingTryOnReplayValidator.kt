package com.handtryon.coreengine

import com.handtryon.coreengine.model.RingFingerPose
import kotlin.math.abs
import kotlin.math.sqrt

data class ExpectedRingFingerZone(
    val centerX: Float,
    val centerY: Float,
    val widthPx: Float,
    val rotationDegrees: Float,
    val visibleFinger: Boolean = true,
)

data class RingTryOnReplayFrame(
    val frameId: String,
    val expected: ExpectedRingFingerZone,
    val predictedPose: RingFingerPose?,
)

data class RingTryOnReplayFrameMetric(
    val frameId: String,
    val hasPrediction: Boolean,
    val centerErrorPx: Float,
    val centerErrorRingWidthRatio: Float,
    val widthErrorRatio: Float,
    val rotationErrorDegrees: Float,
    val passed: Boolean,
)

data class RingTryOnReplayReport(
    val frameCount: Int,
    val predictedFrameCount: Int,
    val passedFrameCount: Int,
    val averageCenterErrorRatio: Float,
    val worstCenterErrorRatio: Float,
    val frameMetrics: List<RingTryOnReplayFrameMetric>,
)

class RingTryOnReplayValidator(
    private val maxCenterErrorRatio: Float = DEFAULT_MAX_CENTER_ERROR_RATIO,
    private val maxWidthErrorRatio: Float = DEFAULT_MAX_WIDTH_ERROR_RATIO,
    private val maxRotationErrorDegrees: Float = DEFAULT_MAX_ROTATION_ERROR_DEGREES,
) {
    fun evaluate(frames: List<RingTryOnReplayFrame>): RingTryOnReplayReport {
        val metrics = frames.map(::evaluateFrame)
        val predictedMetrics = metrics.filter { it.hasPrediction }
        return RingTryOnReplayReport(
            frameCount = frames.size,
            predictedFrameCount = predictedMetrics.size,
            passedFrameCount = metrics.count { it.passed },
            averageCenterErrorRatio = predictedMetrics.map { it.centerErrorRingWidthRatio }.averageOrZero(),
            worstCenterErrorRatio = predictedMetrics.maxOfOrNull { it.centerErrorRingWidthRatio } ?: 0f,
            frameMetrics = metrics,
        )
    }

    private fun evaluateFrame(frame: RingTryOnReplayFrame): RingTryOnReplayFrameMetric {
        val predicted = frame.predictedPose
            ?: return RingTryOnReplayFrameMetric(
                frameId = frame.frameId,
                hasPrediction = false,
                centerErrorPx = Float.POSITIVE_INFINITY,
                centerErrorRingWidthRatio = Float.POSITIVE_INFINITY,
                widthErrorRatio = Float.POSITIVE_INFINITY,
                rotationErrorDegrees = Float.POSITIVE_INFINITY,
                passed = !frame.expected.visibleFinger,
            )
        val dx = predicted.centerPx.x - frame.expected.centerX
        val dy = predicted.centerPx.y - frame.expected.centerY
        val centerErrorPx = sqrt(dx * dx + dy * dy)
        val safeExpectedWidth = frame.expected.widthPx.coerceAtLeast(1f)
        val centerErrorRatio = centerErrorPx / safeExpectedWidth
        val widthErrorRatio = abs(predicted.fingerWidthPx - safeExpectedWidth) / safeExpectedWidth
        val rotationError = abs(normalizeDegrees(predicted.rotationDegrees - frame.expected.rotationDegrees))
        val passed =
            frame.expected.visibleFinger &&
                centerErrorRatio <= maxCenterErrorRatio &&
                widthErrorRatio <= maxWidthErrorRatio &&
                rotationError <= maxRotationErrorDegrees
        return RingTryOnReplayFrameMetric(
            frameId = frame.frameId,
            hasPrediction = true,
            centerErrorPx = centerErrorPx,
            centerErrorRingWidthRatio = centerErrorRatio,
            widthErrorRatio = widthErrorRatio,
            rotationErrorDegrees = rotationError,
            passed = passed,
        )
    }

    private fun normalizeDegrees(value: Float): Float {
        var result = value
        while (result > 180f) result -= 360f
        while (result < -180f) result += 360f
        return result
    }

    private fun List<Float>.averageOrZero(): Float =
        if (isEmpty()) {
            0f
        } else {
            average().toFloat()
        }

    private companion object {
        const val DEFAULT_MAX_CENTER_ERROR_RATIO = 0.35f
        const val DEFAULT_MAX_WIDTH_ERROR_RATIO = 0.28f
        const val DEFAULT_MAX_ROTATION_ERROR_DEGREES = 18f
    }
}
