package com.handtryon.validation

import com.google.common.truth.Truth.assertThat
import com.handtryon.domain.IntBounds
import com.handtryon.domain.NormalizedAssetMetadata
import com.handtryon.domain.PointF
import org.junit.Test

class AssetNormalizationValidatorTest {
    private val validator = AssetNormalizationValidator()

    @Test
    fun validates_clean_alpha_asset() {
        val width = 128
        val height = 128
        val alphaValues = IntArray(width * height)
        for (y in 36 until 92) {
            for (x in 28 until 100) {
                alphaValues[y * width + x] = 220
            }
        }
        val metadata =
            NormalizedAssetMetadata(
                sourceFile = "sample.webp",
                contentBounds = IntBounds(28, 36, 100, 92),
                visualCenter = PointF(64f, 64f),
                alphaBounds = IntBounds(28, 36, 100, 92),
                recommendedWidthRatio = 0.17f,
                rotationBiasDeg = 0f,
                assetQualityScore = 0.82f,
                backgroundRemovalConfidence = 0.9f,
                notes = emptyList(),
            )

        val result = validator.validateAlpha(alphaValues, width, height, metadata)
        assertThat(result.isUsable).isTrue()
        assertThat(result.qualityScore).isGreaterThan(0.6f)
    }

    @Test
    fun flags_border_leak_asset() {
        val width = 64
        val height = 64
        val alphaValues = IntArray(width * height)
        for (x in 0 until 64) {
            alphaValues[x] = 255
        }
        val metadata =
            NormalizedAssetMetadata(
                sourceFile = "leak.webp",
                contentBounds = IntBounds(0, 0, 64, 10),
                visualCenter = PointF(32f, 5f),
                alphaBounds = IntBounds(0, 0, 64, 10),
                recommendedWidthRatio = 0.2f,
                rotationBiasDeg = 0f,
                assetQualityScore = 0.4f,
                backgroundRemovalConfidence = 0.4f,
                notes = emptyList(),
            )

        val result = validator.validateAlpha(alphaValues, width, height, metadata)
        assertThat(result.isUsable).isFalse()
        assertThat(result.notes).contains("border_alpha_leak")
    }
}
