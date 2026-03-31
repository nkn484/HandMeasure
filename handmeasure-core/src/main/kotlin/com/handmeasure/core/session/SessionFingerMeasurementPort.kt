package com.handmeasure.core.session

fun interface SessionFingerMeasurementPort<FrameT, HandT, TargetFingerT, ScaleT> {
    fun measureVisibleWidth(
        frame: FrameT,
        hand: HandT,
        targetFinger: TargetFingerT,
        scale: ScaleT,
    ): SessionFingerMeasurement
}
