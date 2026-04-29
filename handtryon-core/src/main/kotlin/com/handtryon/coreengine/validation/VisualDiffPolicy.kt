package com.handtryon.coreengine.validation

import kotlin.math.sqrt

data class VisualDiffRoi(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

data class VisualDiffThresholds(
    val maxMeanAbsoluteError: Double,
    val maxRmsError: Double,
    val maxLumaMeanAbsoluteError: Double? = null,
)

data class VisualDiffResult(
    val width: Int,
    val height: Int,
    val comparedPixels: Int,
    val meanAbsoluteError: Double,
    val rmsError: Double,
    val lumaMeanAbsoluteError: Double,
    val pass: Boolean,
    val warnings: List<String> = emptyList(),
)

class VisualDiffPolicy(
    private val thresholds: VisualDiffThresholds =
        VisualDiffThresholds(
            maxMeanAbsoluteError = 4.0,
            maxRmsError = 9.0,
            maxLumaMeanAbsoluteError = 5.0,
        ),
) {
    fun compare(
        actualArgb: IntArray,
        expectedArgb: IntArray,
        width: Int,
        height: Int,
        roi: VisualDiffRoi? = null,
    ): VisualDiffResult {
        require(width > 0) { "width must be positive." }
        require(height > 0) { "height must be positive." }
        require(actualArgb.size >= width * height) { "actualArgb is smaller than width * height." }
        require(expectedArgb.size >= width * height) { "expectedArgb is smaller than width * height." }

        val safeRoi = roi?.clamp(width, height) ?: VisualDiffRoi(0, 0, width, height)
        if (safeRoi.right <= safeRoi.left || safeRoi.bottom <= safeRoi.top) {
            return VisualDiffResult(
                width = width,
                height = height,
                comparedPixels = 0,
                meanAbsoluteError = 0.0,
                rmsError = 0.0,
                lumaMeanAbsoluteError = 0.0,
                pass = false,
                warnings = listOf("empty_roi"),
            )
        }

        var pixels = 0
        var absSum = 0.0
        var squaredSum = 0.0
        var lumaAbsSum = 0.0
        for (y in safeRoi.top until safeRoi.bottom) {
            for (x in safeRoi.left until safeRoi.right) {
                val index = y * width + x
                val actual = actualArgb[index]
                val expected = expectedArgb[index]
                val dr = ((actual shr 16) and 0xff) - ((expected shr 16) and 0xff)
                val dg = ((actual shr 8) and 0xff) - ((expected shr 8) and 0xff)
                val db = (actual and 0xff) - (expected and 0xff)
                absSum += (kotlin.math.abs(dr) + kotlin.math.abs(dg) + kotlin.math.abs(db)) / 3.0
                squaredSum += (dr * dr + dg * dg + db * db) / 3.0
                lumaAbsSum += kotlin.math.abs(0.299 * dr + 0.587 * dg + 0.114 * db)
                pixels += 1
            }
        }

        val meanAbsoluteError = absSum / pixels
        val rmsError = sqrt(squaredSum / pixels)
        val lumaMeanAbsoluteError = lumaAbsSum / pixels
        val warnings = mutableListOf<String>()
        if (meanAbsoluteError > thresholds.maxMeanAbsoluteError) warnings += "mean_absolute_error_exceeded"
        if (rmsError > thresholds.maxRmsError) warnings += "rms_error_exceeded"
        thresholds.maxLumaMeanAbsoluteError?.let { maxLuma ->
            if (lumaMeanAbsoluteError > maxLuma) warnings += "luma_error_exceeded"
        }

        return VisualDiffResult(
            width = width,
            height = height,
            comparedPixels = pixels,
            meanAbsoluteError = meanAbsoluteError,
            rmsError = rmsError,
            lumaMeanAbsoluteError = lumaMeanAbsoluteError,
            pass = warnings.isEmpty(),
            warnings = warnings,
        )
    }

    private fun VisualDiffRoi.clamp(
        width: Int,
        height: Int,
    ): VisualDiffRoi =
        VisualDiffRoi(
            left = left.coerceIn(0, width),
            top = top.coerceIn(0, height),
            right = right.coerceIn(0, width),
            bottom = bottom.coerceIn(0, height),
        )
}
