package com.handtryon.core

import com.handtryon.domain.FingerAnchor
import com.handtryon.domain.HandPoseSnapshot
import com.handtryon.domain.MeasurementSnapshot
import com.handtryon.domain.RingAssetSource
import com.handtryon.domain.RingPlacement
import com.handtryon.domain.TryOnInputQuality
import com.handtryon.domain.TryOnMode
import com.handtryon.domain.TryOnSession
import kotlin.math.abs

class TryOnSessionResolver(
    private val fingerAnchorProvider: FingerAnchorProvider = DefaultFingerAnchorProvider(),
) {
    private var lastGoodAnchor: FingerAnchor? = null

    fun resolve(
        asset: RingAssetSource,
        handPose: HandPoseSnapshot?,
        measurement: MeasurementSnapshot?,
        manualPlacement: RingPlacement?,
        previousSession: TryOnSession?,
        frameWidth: Int,
        frameHeight: Int,
        nowMs: Long = System.currentTimeMillis(),
    ): TryOnSession {
        val detectedAnchor = handPose?.let(fingerAnchorProvider::createAnchor)
        val fallbackAnchor =
            if (detectedAnchor == null && lastGoodAnchor != null && nowMs - lastGoodAnchor!!.timestampMs <= 900L) {
                lastGoodAnchor
            } else {
                null
            }
        val anchor = detectedAnchor ?: fallbackAnchor
        if (detectedAnchor != null) lastGoodAnchor = detectedAnchor

        val landmarkUsable = anchor != null
        val measurementUsable = measurement.isUsableMeasurement()
        val mode =
            when {
                landmarkUsable && measurementUsable -> TryOnMode.Measured
                landmarkUsable -> TryOnMode.LandmarkOnly
                else -> TryOnMode.Manual
            }
        val quality =
            TryOnInputQuality(
                measurementUsable = measurementUsable,
                landmarkUsable = landmarkUsable,
                measurementConfidence = measurement?.confidence ?: 0f,
                landmarkConfidence = anchor?.confidence ?: 0f,
                usedLastGoodAnchor = detectedAnchor == null && fallbackAnchor != null,
            )

        val resolvedPlacement =
            when (mode) {
                TryOnMode.Measured -> placementFromMeasured(anchor = anchor!!, measurement = measurement!!, asset = asset)
                TryOnMode.LandmarkOnly -> placementFromLandmark(anchor = anchor!!, asset = asset)
                TryOnMode.Manual ->
                    manualPlacement
                        ?: previousSession?.placement
                        ?: defaultPlacement(asset, frameWidth = frameWidth, frameHeight = frameHeight)
            }
        val stabilizedPlacement = clampPlacementJumps(resolvedPlacement, previousSession?.placement)
        val biasedPlacement = stabilizedPlacement.copy(rotationDegrees = stabilizedPlacement.rotationDegrees + asset.rotationBiasDeg)

        return TryOnSession(
            asset = asset,
            mode = mode,
            quality = quality,
            anchor = anchor,
            placement = biasedPlacement,
            updatedAtMs = nowMs,
        )
    }

    private fun placementFromLandmark(
        anchor: FingerAnchor,
        asset: RingAssetSource,
    ): RingPlacement =
        RingPlacement(
            centerX = anchor.centerX,
            centerY = anchor.centerY,
            ringWidthPx = (anchor.fingerWidthPx * asset.defaultWidthRatio / DEFAULT_WIDTH_RATIO_BASE).coerceAtLeast(18f),
            rotationDegrees = anchor.angleDegrees,
        )

    private fun placementFromMeasured(
        anchor: FingerAnchor,
        measurement: MeasurementSnapshot,
        asset: RingAssetSource,
    ): RingPlacement {
        val measuredDiameterPx = measurement.mmPerPx?.takeIf { it > 0f }?.let { measurement.equivalentDiameterMm / it }
        val fallbackByRatio =
            if (measurement.fingerWidthMm > 0f) {
                anchor.fingerWidthPx * measurement.equivalentDiameterMm / measurement.fingerWidthMm
            } else {
                anchor.fingerWidthPx
            }
        val ringWidth = (measuredDiameterPx ?: fallbackByRatio).coerceAtLeast(18f)
        val scaledWidth = ringWidth * (asset.defaultWidthRatio / DEFAULT_WIDTH_RATIO_BASE)
        return RingPlacement(
            centerX = anchor.centerX,
            centerY = anchor.centerY,
            ringWidthPx = scaledWidth,
            rotationDegrees = anchor.angleDegrees,
        )
    }

    private fun defaultPlacement(
        asset: RingAssetSource,
        frameWidth: Int,
        frameHeight: Int,
    ): RingPlacement {
        val safeWidth = frameWidth.coerceAtLeast(1)
        val safeHeight = frameHeight.coerceAtLeast(1)
        return RingPlacement(
            centerX = safeWidth * 0.5f,
            centerY = safeHeight * 0.52f,
            ringWidthPx = (safeWidth * asset.defaultWidthRatio).coerceAtLeast(26f),
            rotationDegrees = 0f,
        )
    }

    private fun clampPlacementJumps(
        current: RingPlacement,
        previous: RingPlacement?,
    ): RingPlacement {
        val old = previous ?: return current
        val clampedWidth = clampDelta(current.ringWidthPx, old.ringWidthPx, old.ringWidthPx * 0.16f)
        val clampedCenterX = clampDelta(current.centerX, old.centerX, MAX_CENTER_DELTA_PX)
        val clampedCenterY = clampDelta(current.centerY, old.centerY, MAX_CENTER_DELTA_PX)
        val deltaRotation = normalizeRotationDelta(current.rotationDegrees - old.rotationDegrees)
        val clampedRotation = old.rotationDegrees + deltaRotation.coerceIn(-MAX_ROTATION_DELTA_DEG, MAX_ROTATION_DELTA_DEG)
        return RingPlacement(
            centerX = clampedCenterX,
            centerY = clampedCenterY,
            ringWidthPx = clampedWidth.coerceAtLeast(18f),
            rotationDegrees = clampedRotation,
        )
    }

    private fun clampDelta(
        value: Float,
        base: Float,
        maxDelta: Float,
    ): Float = base + (value - base).coerceIn(-abs(maxDelta), abs(maxDelta))

    private fun normalizeRotationDelta(delta: Float): Float {
        var adjusted = delta
        while (adjusted > 180f) adjusted -= 360f
        while (adjusted < -180f) adjusted += 360f
        return adjusted
    }

    private fun MeasurementSnapshot?.isUsableMeasurement(): Boolean {
        val measurement = this ?: return false
        if (!measurement.usable) return false
        if (measurement.equivalentDiameterMm <= 0f) return false
        return measurement.confidence >= 0.35f
    }

    private companion object {
        const val DEFAULT_WIDTH_RATIO_BASE = 0.16f
        const val MAX_CENTER_DELTA_PX = 46f
        const val MAX_ROTATION_DELTA_DEG = 14f
    }
}
