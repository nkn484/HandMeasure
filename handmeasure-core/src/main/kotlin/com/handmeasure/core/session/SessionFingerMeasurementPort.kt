package com.handmeasure.core.session

data class SessionFingerMeasurementRequest<FrameT, HandT, TargetFingerT>(
    val frame: FrameT,
    val hand: HandT,
    val targetFinger: TargetFingerT,
    val scale: SessionScale,
)

fun interface SessionFingerMeasurementPort<FrameT, HandT, TargetFingerT> {
    fun measureVisibleWidth(request: SessionFingerMeasurementRequest<FrameT, HandT, TargetFingerT>): SessionFingerMeasurement
}
