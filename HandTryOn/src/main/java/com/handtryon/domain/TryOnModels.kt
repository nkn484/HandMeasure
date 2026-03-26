package com.handtryon.domain

import android.graphics.Bitmap

data class RingAssetSource(
    val id: String,
    val name: String,
    val overlayAssetPath: String,
    val metadataAssetPath: String? = null,
    val defaultWidthRatio: Float = 0.16f,
    val rotationBiasDeg: Float = 0f,
)

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

data class TryOnInputQuality(
    val measurementUsable: Boolean,
    val landmarkUsable: Boolean,
    val measurementConfidence: Float,
    val landmarkConfidence: Float,
    val usedLastGoodAnchor: Boolean,
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
