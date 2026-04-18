package com.handmeasure.core.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdaptiveCaptureProtocolAdvisorTest {
    private val advisor = AdaptiveCaptureProtocolAdvisor()

    @Test
    fun assess_returnsPreciseWhenCoverageAndQualityAreHigh() {
        val assessment =
            advisor.assess(
                coveredBucketCount = 5,
                capturedScores = listOf(0.86f, 0.82f, 0.87f, 0.84f, 0.83f),
            )

        assertThat(assessment.mode).isEqualTo(AdaptiveCaptureMode.PRECISE)
    }

    @Test
    fun assess_returnsFastPreviewForSparseLowQualityCoverage() {
        val assessment =
            advisor.assess(
                coveredBucketCount = 1,
                capturedScores = listOf(0.44f),
            )

        assertThat(assessment.mode).isEqualTo(AdaptiveCaptureMode.FAST_PREVIEW)
    }
}
