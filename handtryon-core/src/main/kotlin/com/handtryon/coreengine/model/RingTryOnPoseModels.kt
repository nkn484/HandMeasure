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
    FingerCurled,
    DistalSegmentHidden,
    PointsTowardWrist,
    UnstableGeometry,
}

data class RingFingerPoseDiagnostics(
    val extensionRatio: Float = 0f,
    val bendCosine: Float = 0f,
    val distalToProximalRatio: Float = 0f,
    val forwardExtensionCosine: Float = 0f,
    val centerOnMcpToPip: Float = 0f,
    val lateralOffsetPx: Float = 0f,
    val axisLengthPx: Float = 0f,
    val rawRotationDegrees: Float = 0f,
    val rotationCorrectionDegrees: Float = 0f,
    val rotationCorrectionBucket: String = "",
    val finalRotationDegrees: Float = 0f,
    val rawConfidence: Float = 0f,
    val confidence: Float = 0f,
    val centerPolicy: String = "",
    val rejectReason: RingFingerPoseRejectReason? = null,
)

data class RingFingerPoseSolveResult(
    val pose: RingFingerPose?,
    val rejectReason: RingFingerPoseRejectReason?,
    val diagnostics: RingFingerPoseDiagnostics,
) {
    val usable: Boolean
        get() = pose != null && rejectReason == null
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
    val diagnostics: RingFingerPoseDiagnostics? = null,
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
    val diagnostics: RingFitDiagnostics? = null,
)

data class RingFitDiagnostics(
    val visualRingToFingerWidthRatio: Float,
    val measuredWidthRatio: Float? = null,
    val unclampedTargetWidthPx: Float,
    val unclampedDepthMeters: Float,
    val unclampedModelScale: Float,
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
    val depthMeters: Float = 0.42f,
    val confidence: Float,
)

enum class TryOnRenderPass {
    FingerDepthPrepass,
    RingModel,
}

enum class TryOnRenderQuality {
    Hidden,
    Degraded,
    Stable,
}

data class TryOnVisualQaSnapshot(
    val attachmentRatio: Float,
    val occluderRadiusToRingWidthRatio: Float,
    val occluderDepthMeters: Float,
    val ringDepthMeters: Float,
    val renderScale: Float,
    val warnings: List<String> = emptyList(),
) {
    val passesBasicGate: Boolean
        get() = warnings.isEmpty()
}

data class TryOnRenderState3D(
    val ringTransform: RingTransform3D,
    val fingerPose: RingFingerPose,
    val fitState: RingFitState,
    val fingerOccluder: FingerOccluderState?,
    val quality: TryOnInputQuality,
    val renderPasses: List<TryOnRenderPass> = emptyList(),
    val renderQuality: TryOnRenderQuality = TryOnRenderQuality.Stable,
    val visualQa: TryOnVisualQaSnapshot? = null,
)
