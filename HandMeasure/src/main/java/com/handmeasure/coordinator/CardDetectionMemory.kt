package com.handmeasure.coordinator

import com.handmeasure.vision.CardDetection

internal class CardDetectionMemory(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val confidenceDecay: Float = DEFAULT_CONFIDENCE_DECAY,
) {
    private var lastDetectedCard: CardDetection? = null
    private var lastDetectedCardAtMs: Long = 0L

    fun resolve(
        detected: CardDetection?,
        frameTimestampMs: Long,
    ): CardDetection? {
        if (detected != null) {
            lastDetectedCard = detected
            lastDetectedCardAtMs = frameTimestampMs
            return detected
        }

        val cached = lastDetectedCard ?: return null
        val ageMs = frameTimestampMs - lastDetectedCardAtMs
        if (ageMs > windowMs) {
            lastDetectedCard = null
            return null
        }

        return cached.copy(
            confidence = (cached.confidence * confidenceDecay).coerceIn(0f, 1f),
            rectificationConfidence = (cached.rectificationConfidence * confidenceDecay).coerceIn(0f, 1f),
        )
    }

    private companion object {
        const val DEFAULT_WINDOW_MS = 450L
        const val DEFAULT_CONFIDENCE_DECAY = 0.82f
    }
}
