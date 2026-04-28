package com.handmeasure.coordinator

import com.google.common.truth.Truth.assertThat
import com.handmeasure.measurement.CardRectangle
import com.handmeasure.vision.CardDetection
import org.junit.Test

class CardDetectionMemoryTest {
    @Test
    fun resolve_returnsDetectedAndStoresMemory() {
        val memory = CardDetectionMemory(windowMs = 450L, confidenceDecay = 0.82f)
        val detected = sampleCard(confidence = 0.91f, rectificationConfidence = 0.77f)

        val first = memory.resolve(detected = detected, frameTimestampMs = 1_000L)
        val fallback = memory.resolve(detected = null, frameTimestampMs = 1_200L)

        assertThat(first).isEqualTo(detected)
        assertThat(fallback).isNotNull()
        assertThat(fallback?.confidence).isWithin(1e-6f).of(0.91f * 0.82f)
        assertThat(fallback?.rectificationConfidence).isWithin(1e-6f).of(0.77f * 0.82f)
    }

    @Test
    fun resolve_returnsNullWhenMemoryExpires() {
        val memory = CardDetectionMemory(windowMs = 450L, confidenceDecay = 0.82f)
        memory.resolve(detected = sampleCard(confidence = 0.8f, rectificationConfidence = 0.7f), frameTimestampMs = 10_000L)

        val expired = memory.resolve(detected = null, frameTimestampMs = 10_451L)
        val afterExpire = memory.resolve(detected = null, frameTimestampMs = 10_460L)

        assertThat(expired).isNull()
        assertThat(afterExpire).isNull()
    }

    @Test
    fun resolve_replacesMemoryWhenNewDetectionArrives() {
        val memory = CardDetectionMemory(windowMs = 450L, confidenceDecay = 0.82f)
        memory.resolve(detected = sampleCard(confidence = 0.95f, rectificationConfidence = 0.8f), frameTimestampMs = 2_000L)
        val newer = sampleCard(confidence = 0.52f, rectificationConfidence = 0.41f)

        val stored = memory.resolve(detected = newer, frameTimestampMs = 2_200L)
        val fallback = memory.resolve(detected = null, frameTimestampMs = 2_260L)

        assertThat(stored).isEqualTo(newer)
        assertThat(fallback).isNotNull()
        assertThat(fallback?.confidence).isWithin(1e-6f).of(0.52f * 0.82f)
        assertThat(fallback?.rectificationConfidence).isWithin(1e-6f).of(0.41f * 0.82f)
    }

    private fun sampleCard(
        confidence: Float,
        rectificationConfidence: Float,
    ): CardDetection =
        CardDetection(
            rectangle =
                CardRectangle(
                    centerX = 10.0,
                    centerY = 12.0,
                    longSidePx = 200.0,
                    shortSidePx = 120.0,
                    angleDeg = 0.0,
                ),
            corners = listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f),
            contourAreaScore = 0.9f,
            aspectScore = 0.95f,
            confidence = confidence,
            coverageRatio = 0.11f,
            aspectResidual = 0.03f,
            rectangularityScore = 0.92f,
            edgeSupportScore = 0.85f,
            rectificationConfidence = rectificationConfidence,
            perspectiveDistortion = 0.08f,
        )
}
