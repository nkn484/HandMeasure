package com.handmeasure.flow

import com.handmeasure.api.CaptureStep

data class MeasureStepDefinition(
    val step: CaptureStep,
)

object GuidedSteps {
    val all =
        listOf(
            MeasureStepDefinition(CaptureStep.FRONT_PALM),
            MeasureStepDefinition(CaptureStep.LEFT_OBLIQUE),
            MeasureStepDefinition(CaptureStep.RIGHT_OBLIQUE),
            MeasureStepDefinition(CaptureStep.UP_TILT),
            MeasureStepDefinition(CaptureStep.DOWN_TILT),
        )
}
