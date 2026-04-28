package com.handtryon.coreengine.model

data class TryOnPoint2(
    val x: Float,
    val y: Float,
)

data class TryOnVec2(
    val x: Float,
    val y: Float,
)

data class TryOnVec3(
    val x: Float,
    val y: Float,
    val z: Float,
)

enum class RingFingerPoseRejectReason {
    MissingHand,
    MissingLandmarks,
    FingerTooSmall,
    OutsideFrame,
    LowConfidence,
    UnstableGeometry,
}

data class RingFingerPose(
    val centerPx: TryOnPoint2,
    val occluderStartPx: TryOnPoint2,
    val occluderEndPx: TryOnPoint2,
    val tangentPx: TryOnVec2,
    val normalHintPx: TryOnVec2,
    val rotationDegrees: Float,
    val rollDegrees: Float,
    val fingerWidthPx: Float,
    val confidence: Float,
    val rejectReason: RingFingerPoseRejectReason? = null,
)

enum class RingFitSource {
    Measured,
    SelectedSize,
    VisualEstimate,
    AssetDefault,
}

data class RingFitState(
    val ringOuterDiameterMm: Float,
    val ringInnerDiameterMm: Float?,
    val modelWidthMm: Float,
    val targetWidthPx: Float,
    val depthMeters: Float,
    val modelScale: Float,
    val confidence: Float,
    val source: RingFitSource,
)

data class RingTransform3D(
    val positionMeters: TryOnVec3,
    val rotationDegrees: TryOnVec3,
    val scale: TryOnVec3,
)

data class FingerOccluderState(
    val startPx: TryOnPoint2,
    val endPx: TryOnPoint2,
    val radiusPx: Float,
    val normalHintPx: TryOnVec2,
    val confidence: Float,
)

data class TryOnRenderState3D(
    val ringTransform: RingTransform3D,
    val fingerPose: RingFingerPose,
    val fitState: RingFitState,
    val fingerOccluder: FingerOccluderState?,
    val quality: TryOnInputQuality,
)
