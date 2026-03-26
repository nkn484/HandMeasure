package com.handtryon.validation

import android.graphics.Bitmap
import com.handtryon.domain.NormalizedAssetMetadata

data class AssetValidationResult(
    val alphaCoverage: Float,
    val alphaBoundsCoverage: Float,
    val borderLeakRatio: Float,
    val qualityScore: Float,
    val isUsable: Boolean,
    val notes: List<String>,
)

class AssetNormalizationValidator {
    fun validate(
        overlayBitmap: Bitmap,
        metadata: NormalizedAssetMetadata,
    ): AssetValidationResult {
        val width = overlayBitmap.width.coerceAtLeast(1)
        val height = overlayBitmap.height.coerceAtLeast(1)
        val pixels = IntArray(width * height)
        overlayBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val alphaValues = IntArray(width * height) { index -> pixels[index] ushr 24 }
        return validateAlpha(alphaValues, width, height, metadata)
    }

    fun validateAlpha(
        alphaValues: IntArray,
        width: Int,
        height: Int,
        metadata: NormalizedAssetMetadata,
    ): AssetValidationResult {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        var alphaCount = 0
        var borderAlphaCount = 0
        val borderMargin = 2
        alphaValues.forEachIndexed { index, alpha ->
            if (alpha > 12) {
                alphaCount += 1
                val x = index % safeWidth
                val y = index / safeWidth
                val onBorder =
                    x <= borderMargin || y <= borderMargin || x >= safeWidth - 1 - borderMargin || y >= safeHeight - 1 - borderMargin
                if (onBorder) borderAlphaCount += 1
            }
        }
        val alphaCoverage = alphaCount.toFloat() / (safeWidth * safeHeight).toFloat()
        val boundsWidth = (metadata.alphaBounds.right - metadata.alphaBounds.left).coerceAtLeast(1)
        val boundsHeight = (metadata.alphaBounds.bottom - metadata.alphaBounds.top).coerceAtLeast(1)
        val alphaBoundsCoverage = (boundsWidth * boundsHeight).toFloat() / (safeWidth * safeHeight).toFloat()
        val borderLeakRatio = borderAlphaCount.toFloat() / alphaCount.coerceAtLeast(1).toFloat()

        val notes = mutableListOf<String>()
        if (alphaCoverage !in 0.02f..0.55f) notes += "alpha_coverage_out_of_range"
        if (alphaBoundsCoverage !in 0.04f..0.72f) notes += "alpha_bounds_not_reasonable"
        if (borderLeakRatio > 0.025f) notes += "border_alpha_leak"
        if (metadata.assetQualityScore < 0.6f) notes += "metadata_quality_low"

        val qualityScore =
            (0.36f * (1f - borderLeakRatio).coerceIn(0f, 1f) +
                0.28f * (1f - kotlin.math.abs(alphaCoverage - 0.2f)).coerceIn(0f, 1f) +
                0.36f * metadata.assetQualityScore.coerceIn(0f, 1f))
        return AssetValidationResult(
            alphaCoverage = alphaCoverage,
            alphaBoundsCoverage = alphaBoundsCoverage,
            borderLeakRatio = borderLeakRatio,
            qualityScore = qualityScore,
            isUsable = notes.isEmpty(),
            notes = notes,
        )
    }
}
