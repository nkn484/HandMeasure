package com.handmeasure.core.capture

import kotlin.math.sqrt

data class OrientationObservation(
    val normalX: Float,
    val normalY: Float,
    val normalZ: Float,
)

data class OrientationBucketDefinition<BucketT>(
    val bucket: BucketT,
    val targetX: Float,
    val targetY: Float,
    val targetZ: Float,
)

data class OrientationBucketDecision<BucketT>(
    val bucket: BucketT?,
    val score: Float,
    val perBucketScore: Map<BucketT, Float>,
)

class OrientationBucketClassifier<BucketT>(
    private val definitions: List<OrientationBucketDefinition<BucketT>>,
    private val enterThreshold: Float = 0.56f,
    private val stickinessThreshold: Float = 0.48f,
    private val switchMargin: Float = 0.08f,
) {
    private var currentBucket: BucketT? = null

    fun reset() {
        currentBucket = null
    }

    fun classify(observation: OrientationObservation?): OrientationBucketDecision<BucketT> {
        if (observation == null || definitions.isEmpty()) {
            currentBucket = null
            return OrientationBucketDecision(
                bucket = null,
                score = 0f,
                perBucketScore = emptyMap(),
            )
        }

        val normalized = observation.normalized()
        val scores =
            definitions.associate { definition ->
                val rawDot =
                    normalized.normalX * definition.targetX +
                        normalized.normalY * definition.targetY +
                        normalized.normalZ * definition.targetZ
                definition.bucket to ((rawDot + 1f) / 2f).coerceIn(0f, 1f)
            }

        val bestEntry = scores.maxByOrNull { it.value }
        val bestBucket: BucketT? = bestEntry?.key
        val bestScore = bestEntry?.value ?: 0f
        val stickyBucket = currentBucket
        val stickyScore = stickyBucket?.let { scores[it] } ?: 0f

        val resolvedBucket =
            when {
                bestBucket == null -> null
                stickyBucket != null &&
                    stickyScore >= stickinessThreshold &&
                    bestScore - stickyScore < switchMargin -> stickyBucket
                bestScore >= enterThreshold -> bestBucket
                stickyBucket != null && stickyScore >= stickinessThreshold -> stickyBucket
                else -> null
            }

        currentBucket = resolvedBucket
        return OrientationBucketDecision(
            bucket = resolvedBucket,
            score = resolvedBucket?.let { scores[it] } ?: 0f,
            perBucketScore = scores,
        )
    }

    private fun OrientationObservation.normalized(): OrientationObservation {
        val magnitude = sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ).coerceAtLeast(1e-6f)
        return OrientationObservation(
            normalX = normalX / magnitude,
            normalY = normalY / magnitude,
            normalZ = normalZ / magnitude,
        )
    }
}
