package com.handmeasure.core.capture

enum class AdaptiveCaptureMode {
    FAST_PREVIEW,
    STANDARD,
    PRECISE,
}

data class AdaptiveCaptureAssessment(
    val mode: AdaptiveCaptureMode,
    val averageQuality: Float,
    val coveredBucketCount: Int,
)

class AdaptiveCaptureProtocolAdvisor {
    fun assess(
        coveredBucketCount: Int,
        capturedScores: List<Float>,
    ): AdaptiveCaptureAssessment {
        val averageQuality = if (capturedScores.isEmpty()) 0f else capturedScores.average().toFloat()
        val mode =
            when {
                coveredBucketCount >= 5 && averageQuality >= 0.82f -> AdaptiveCaptureMode.PRECISE
                coveredBucketCount >= 3 && averageQuality >= 0.66f -> AdaptiveCaptureMode.STANDARD
                else -> AdaptiveCaptureMode.FAST_PREVIEW
            }
        return AdaptiveCaptureAssessment(
            mode = mode,
            averageQuality = averageQuality,
            coveredBucketCount = coveredBucketCount,
        )
    }
}
