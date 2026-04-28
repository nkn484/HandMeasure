package com.handtryon.coreengine.model

data class TryOnAssetSource(
    val id: String,
    val name: String,
    val overlayAssetPath: String? = null,
    val modelAssetPath: String? = null,
    val metadataAssetPath: String? = null,
    val defaultWidthRatio: Float = 0.16f,
    val rotationBiasDeg: Float = 0f,
) {
    init {
        require(!overlayAssetPath.isNullOrBlank() || !modelAssetPath.isNullOrBlank()) {
            "TryOnAssetSource requires at least one asset path (overlayAssetPath or modelAssetPath)."
        }
    }
}

data class TryOnLandmarkPoint(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
)

data class TryOnHandPoseSnapshot(
    val frameWidth: Int,
    val frameHeight: Int,
    val landmarks: List<TryOnLandmarkPoint>,
    val confidence: Float,
    val timestampMs: Long,
)

data class TryOnMeasurementSnapshot(
    val equivalentDiameterMm: Float,
    val fingerWidthMm: Float,
    val confidence: Float,
    val mmPerPx: Float? = null,
    val usable: Boolean = true,
)

enum class TryOnTrackingState {
    Searching,
    Candidate,
    Locked,
    Recovering,
}

enum class TryOnUpdateAction {
    Update,
    FreezeScaleRotation,
    HoldLastPlacement,
    Recover,
    Hide,
}

data class TryOnInputQuality(
    val measurementUsable: Boolean,
    val landmarkUsable: Boolean,
    val measurementConfidence: Float,
    val landmarkConfidence: Float,
    val usedLastGoodAnchor: Boolean,
    val trackingState: TryOnTrackingState = TryOnTrackingState.Searching,
    val qualityScore: Float = 0f,
    val updateAction: TryOnUpdateAction = TryOnUpdateAction.Update,
    val hints: List<String> = emptyList(),
)

data class TryOnFingerAnchor(
    val centerX: Float,
    val centerY: Float,
    val angleDegrees: Float,
    val fingerWidthPx: Float,
    val confidence: Float,
    val timestampMs: Long,
)

data class TryOnPlacement(
    val centerX: Float,
    val centerY: Float,
    val ringWidthPx: Float,
    val rotationDegrees: Float,
)

enum class TryOnMode {
    Measured,
    LandmarkOnly,
    Manual,
}

data class TryOnSession(
    val asset: TryOnAssetSource,
    val mode: TryOnMode,
    val quality: TryOnInputQuality,
    val anchor: TryOnFingerAnchor?,
    val placement: TryOnPlacement,
    val updatedAtMs: Long,
)

data class TryOnPlacementValidation(
    val widthRatio: Float,
    val anchorDistancePx: Float,
    val rotationJumpDeg: Float,
    val isPlacementUsable: Boolean,
    val notes: List<String>,
)
