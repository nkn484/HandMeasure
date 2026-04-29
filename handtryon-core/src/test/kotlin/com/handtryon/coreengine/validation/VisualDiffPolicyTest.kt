package com.handtryon.coreengine.validation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VisualDiffPolicyTest {
    @Test
    fun compare_passesIdenticalArgbMatrix() {
        val pixels =
            intArrayOf(
                0xff000000.toInt(),
                0xffffffff.toInt(),
                0xffff0000.toInt(),
                0xff00ff00.toInt(),
            )

        val result = VisualDiffPolicy().compare(pixels, pixels.copyOf(), width = 2, height = 2)

        assertThat(result.pass).isTrue()
        assertThat(result.meanAbsoluteError).isEqualTo(0.0)
        assertThat(result.rmsError).isEqualTo(0.0)
        assertThat(result.comparedPixels).isEqualTo(4)
    }

    @Test
    fun compare_failsWhenRoiDiffExceedsThreshold() {
        val expected = IntArray(9) { 0xff000000.toInt() }
        val actual = expected.copyOf()
        actual[4] = 0xffffffff.toInt()

        val result =
            VisualDiffPolicy(
                VisualDiffThresholds(
                    maxMeanAbsoluteError = 10.0,
                    maxRmsError = 10.0,
                    maxLumaMeanAbsoluteError = 10.0,
                ),
            ).compare(
                actualArgb = actual,
                expectedArgb = expected,
                width = 3,
                height = 3,
                roi = VisualDiffRoi(left = 1, top = 1, right = 2, bottom = 2),
            )

        assertThat(result.pass).isFalse()
        assertThat(result.comparedPixels).isEqualTo(1)
        assertThat(result.warnings).contains("mean_absolute_error_exceeded")
        assertThat(result.warnings).contains("rms_error_exceeded")
        assertThat(result.warnings).contains("luma_error_exceeded")
    }
}
