package com.handmeasure.measurement

import com.google.common.truth.Truth.assertThat
import com.handmeasure.api.CaptureStep
import org.junit.Test

class FingerMeasurementFusionTest {
    @Test
    fun fuse_penalizesInconsistentSideViews() {
        val fusion = FingerMeasurementFusion()
        val result =
            fusion.fuse(
                listOf(
                    StepMeasurement(CaptureStep.FRONT_PALM, widthMm = 18.0, confidence = 0.85f, measurementConfidence = 0.85f),
                    StepMeasurement(CaptureStep.LEFT_OBLIQUE, widthMm = 17.0, confidence = 0.85f, measurementConfidence = 0.85f),
                    StepMeasurement(CaptureStep.RIGHT_OBLIQUE, widthMm = 12.0, confidence = 0.85f, measurementConfidence = 0.85f),
                ),
            )

        assertThat(result.thicknessMm).isGreaterThan(0.0)
        assertThat(result.perStepResidualsMm).isNotEmpty()
        assertThat(result.confidenceScore).isLessThan(0.95f)
    }
}
