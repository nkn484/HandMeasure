package com.handtryon.domain

import android.graphics.Bitmap

data class RingAssetSource(
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
            "RingAssetSource requires at least one asset path (overlayAssetPath or modelAssetPath)."
        }
    }

    val assetKind: RingAssetKind
        get() =
            when {
                !modelAssetPath.isNullOrBlank() -> RingAssetKind.ModelGlb
                else -> RingAssetKind.Overlay2D
            }
}

enum class RingAssetKind {
    Overlay2D,
    ModelGlb,
}

data class NormalizedAssetMetadata(
    val sourceFile: String,
    val contentBounds: IntBounds,
    val visualCenter: PointF,
    val alphaBounds: IntBounds,
    val recommendedWidthRatio: Float,
    val rotationBiasDeg: Float,
    val assetQualityScore: Float,
    val backgroundRemovalConfidence: Float,
    val notes: List<String>,
)

data class GlbBoundsMm(
    val x: Float,
    val y: Float,
    val z: Float,
)

data class GlbPivotMetadata(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val units: String = "model",
)

data class GlbScaleMetadata(
    val units: String? = null,
    val unitsToMeters: Float = 1f,
    val defaultScale: Float = 1f,
    val authoredWidthMm: Float? = null,
)

data class GlbMaterialMetadata(
    val name: String? = null,
    val baseColorFactor: List<Float> = emptyList(),
    val metallicFactor: Float? = null,
    val roughnessFactor: Float? = null,
    val alphaMode: String? = null,
    val doubleSided: Boolean = false,
)

data class GlbAssetSummary(
    val modelAssetPath: String,
    val glbVersion: Int,
    val gltfVersion: String?,
    val generator: String?,
    val meshCount: Int,
    val materialCount: Int,
    val nodeCount: Int,
    val estimatedBoundsMm: GlbBoundsMm?,
    val pivot: GlbPivotMetadata? = null,
    val scale: GlbScaleMetadata = GlbScaleMetadata(),
    val materials: List<GlbMaterialMetadata> = emptyList(),
    val notes: List<String> = emptyList(),
) {
    val effectiveModelWidthMm: Float?
        get() =
            scale.authoredWidthMm?.takeIf { it > 0f }
                ?: estimatedBoundsMm?.let { kotlin.math.max(it.x, it.y) }
}

data class IntBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

data class PointF(
    val x: Float,
    val y: Float,
)

data class LandmarkPoint(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
)

data class HandPoseSnapshot(
    val frameWidth: Int,
    val frameHeight: Int,
    val landmarks: List<LandmarkPoint>,
    val confidence: Float,
    val timestampMs: Long,
)

data class MeasurementSnapshot(
    val equivalentDiameterMm: Float,
    val fingerWidthMm: Float,
    val confidence: Float,
    val mmPerPx: Float? = null,
    val usable: Boolean = true,
)

enum class MeasurementInputSource {
    HandMeasure,
    ProductSize,
    VisualEstimate,
    Unknown,
}

data class TryOnMeasurementInput(
    val equivalentDiameterMm: Float,
    val fingerWidthMm: Float?,
    val confidence: Float,
    val source: MeasurementInputSource,
)

data class ProductTryOnAsset(
    val productId: String,
    val variantId: String?,
    val sku: String,
    val modelAssetUrl: String?,
    val localAssetPath: String?,
    val metadataUrl: String?,
    val materialProfile: String?,
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

data class FingerAnchor(
    val centerX: Float,
    val centerY: Float,
    val angleDegrees: Float,
    val fingerWidthPx: Float,
    val confidence: Float,
    val timestampMs: Long,
)

data class RingPlacement(
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
    val asset: RingAssetSource,
    val mode: TryOnMode,
    val quality: TryOnInputQuality,
    val anchor: FingerAnchor?,
    val placement: RingPlacement,
    val updatedAtMs: Long,
)

data class TryOnRenderResult(
    val bitmap: Bitmap,
    val mode: TryOnMode,
    val generatedAtMs: Long,
    val validation: PlacementValidation,
)

data class PlacementValidation(
    val widthRatio: Float,
    val anchorDistancePx: Float,
    val rotationJumpDeg: Float,
    val isPlacementUsable: Boolean,
    val notes: List<String>,
)
