package com.handmeasure.core.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OrientationBucketClassifierTest {
    private enum class Bucket {
        FRONTAL,
        LEFT,
    }

    private val classifier =
        OrientationBucketClassifier(
            definitions =
                listOf(
                    OrientationBucketDefinition(
                        bucket = Bucket.FRONTAL,
                        targetX = 0f,
                        targetY = 0f,
                        targetZ = 1f,
                    ),
                    OrientationBucketDefinition(
                        bucket = Bucket.LEFT,
                        targetX = -0.6f,
                        targetY = 0f,
                        targetZ = 0.8f,
                    ),
                ),
            enterThreshold = 0.5f,
            stickinessThreshold = 0.46f,
            switchMargin = 0.12f,
        )

    @Test
    fun classify_prefersClosestBucket() {
        val decision = classifier.classify(OrientationObservation(normalX = 0f, normalY = 0f, normalZ = 1f))

        assertThat(decision.bucket).isEqualTo(Bucket.FRONTAL)
        assertThat(decision.score).isGreaterThan(0.9f)
    }

    @Test
    fun classify_appliesHysteresisBeforeSwitchingBucket() {
        classifier.classify(OrientationObservation(normalX = 0f, normalY = 0f, normalZ = 1f))

        val nearBoundary =
            classifier.classify(
                OrientationObservation(
                    normalX = -0.18f,
                    normalY = 0f,
                    normalZ = 0.98f,
                ),
            )

        assertThat(nearBoundary.bucket).isEqualTo(Bucket.FRONTAL)
    }

    @Test
    fun classify_switchesWhenNewBucketClearlyBetter() {
        classifier.classify(OrientationObservation(normalX = 0f, normalY = 0f, normalZ = 1f))

        val decision = classifier.classify(OrientationObservation(normalX = -0.9f, normalY = 0f, normalZ = 0.2f))

        assertThat(decision.bucket).isEqualTo(Bucket.LEFT)
    }
}
