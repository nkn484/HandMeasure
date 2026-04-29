package com.handtryon.coreengine

import com.handtryon.coreengine.model.TryOnAssetSource
import com.handtryon.coreengine.model.TryOnFingerAnchor
import com.handtryon.coreengine.model.TryOnHandPoseSnapshot
import com.handtryon.coreengine.model.TryOnInputQuality
import com.handtryon.coreengine.model.TryOnMeasurementSnapshot
import com.handtryon.coreengine.model.TryOnMode
import com.handtryon.coreengine.model.TryOnPlacement
import com.handtryon.coreengine.model.TryOnSession
import com.handtryon.coreengine.model.TryOnUpdateAction
import kotlin.math.abs
import kotlin.math.sqrt

data class TryOnSessionResolverConfig(
    val defaultWidthRatioBase: Float = 0.16f,
    val maxCenterDeltaPx: Float = 46f,
    val maxRotationDeltaDeg: Float = 14f,
    val fallbackAnchorMaxAgeMs: Long = 900L,
    val minLastGoodAnchorScore: Float = 0.34f,
    val minUsableMeasurementConfidence: Float = 0.35f,
)

class TryOnSessionResolverPolicy(
    private val fingerAnchorFactory: FingerAnchorFactory = DefaultFingerAnchorFactory(),
    private val trackingPolicy: TryOnTrackingStateMachinePolicy = TryOnTrackingStateMachinePolicy(),
    private val qualityPolicy: TryOnQualityPolicy = TryOnQualityPolicy(),
    private val smootherPolicy: TemporalPlacementSmootherPolicy = TemporalPlacementSmootherPolicy(),
    private val placementValidationPolicy: PlacementValidationPolicy = PlacementValidationPolicy(),
    private val config: TryOnSessionResolverConfig = TryOnSessionResolverConfig(),
) {
    private var lastGoodAnchor: TryOnFingerAnchor? = null
    private var trackingSnapshot = TryOnTrackingSnapshot()

    fun resolve(
        asset: TryOnAssetSource,
        handPose: TryOnHandPoseSnapshot?,
        measurement: TryOnMeasurementSnapshot?,
        manualPlacement: TryOnPlacement?,
        previousSession: TryOnSession?,
        frameWidth: Int,
        frameHeight: Int,
        nowMs: Long = System.currentTimeMillis(),
    ): TryOnSession {
        val detectedAnchor = handPose?.let(fingerAnchorFactory::createAnchor)?.copy(timestampMs = nowMs)
        val anchorAgeMs = lastGoodAnchor?.let { nowMs - it.timestampMs }
        val fallbackAnchor =
            if (detectedAnchor == null && lastGoodAnchor != null && anchorAgeMs != null && anchorAgeMs in 0L..config.fallbackAnchorMaxAgeMs) {
                lastGoodAnchor
            } else {
                null
            }
        val anchor = detectedAnchor ?: fallbackAnchor

        val landmarkUsable = anchor != null
        val measurementUsable = measurement.isUsableMeasurement()
        val mode =
            when {
                landmarkUsable && measurementUsable -> TryOnMode.Measured
                landmarkUsable -> TryOnMode.LandmarkOnly
                else -> TryOnMode.Manual
            }

        val resolvedPlacement =
            when (mode) {
                TryOnMode.Measured -> placementFromMeasured(anchor = anchor!!, measurement = measurement!!, asset = asset)
                TryOnMode.LandmarkOnly -> placementFromLandmark(anchor = anchor!!, asset = asset)
                TryOnMode.Manual ->
                    manualPlacement
                        ?: previousSession?.placement
                        ?: defaultPlacement(asset, frameWidth = frameWidth, frameHeight = frameHeight)
            }
        val biasedPlacement = resolvedPlacement.copy(rotationDegrees = resolvedPlacement.rotationDegrees + asset.rotationBiasDeg)
        val previousPlacement = previousSession?.placement
        val candidatePlacement = clampPlacementJumps(biasedPlacement, previousPlacement)
        val validation =
            placementValidationPolicy.validate(
                placement = candidatePlacement,
                anchor = anchor,
                previousPlacement = previousPlacement,
                frameWidth = frameWidth,
            )
        val anchorJumpPx = computeAnchorJumpPx(anchor, previousSession?.anchor)
        val placementJumpRatio = computePlacementJumpRatio(candidatePlacement, previousPlacement)
        val fallbackUsed = detectedAnchor == null && fallbackAnchor != null

        val qualitySignals =
            TryOnQualitySignals(
                mode = mode,
                landmarkUsable = landmarkUsable,
                measurementUsable = measurementUsable,
                landmarkConfidence = anchor?.confidence ?: 0f,
                measurementConfidence = measurement?.confidence ?: 0f,
                usedLastGoodAnchor = fallbackUsed,
                anchorJumpPx = anchorJumpPx,
                placementJumpRatio = placementJumpRatio,
                validation = validation,
                trackingState = trackingSnapshot.state,
                hasPreviousPlacement = previousPlacement != null,
            )
        val preliminaryScore = qualityPolicy.score(qualitySignals)
        trackingSnapshot =
            trackingPolicy.transition(
                previous = trackingSnapshot,
                input =
                    TryOnTrackingInput(
                        landmarkUsable = landmarkUsable,
                        qualityScore = preliminaryScore,
                        validationUsable = validation.isPlacementUsable,
                        usingFallbackAnchor = fallbackUsed,
                    ),
            )
        val qualityEvaluation = qualityPolicy.evaluate(qualitySignals.copy(trackingState = trackingSnapshot.state))

        val gatedPlacement =
            if (mode == TryOnMode.Manual) {
                candidatePlacement
            } else {
                applyQualityGate(
                    candidate = candidatePlacement,
                    previous = previousPlacement,
                    action = qualityEvaluation.updateAction,
                )
            }
        val deltaMs = if (previousSession == null) 16L else (nowMs - previousSession.updatedAtMs).coerceAtLeast(1L)
        val stabilizedPlacement =
            if (mode == TryOnMode.Manual) {
                gatedPlacement
            } else {
                smootherPolicy.smooth(
                    raw = gatedPlacement,
                    previous = previousPlacement,
                    deltaMs = deltaMs,
                    context =
                        TryOnSmoothingContext(
                            qualityScore = qualityEvaluation.score,
                            trackingState = trackingSnapshot.state,
                            updateAction = qualityEvaluation.updateAction,
                        ),
                )
            }

        if (detectedAnchor != null && preliminaryScore >= config.minLastGoodAnchorScore) {
            lastGoodAnchor = detectedAnchor
        }

        return TryOnSession(
            asset = asset,
            mode = mode,
            quality =
                TryOnInputQuality(
                    measurementUsable = measurementUsable,
                    landmarkUsable = landmarkUsable,
                    measurementConfidence = measurement?.confidence ?: 0f,
                    landmarkConfidence = anchor?.confidence ?: 0f,
                    usedLastGoodAnchor = fallbackUsed,
                    trackingState = trackingSnapshot.state,
                    qualityScore = qualityEvaluation.score,
                    updateAction = qualityEvaluation.updateAction,
                    hints = qualityEvaluation.hints,
                ),
            anchor = anchor,
            placement = stabilizedPlacement,
            updatedAtMs = nowMs,
        )
    }

    private fun placementFromLandmark(
        anchor: TryOnFingerAnchor,
        asset: TryOnAssetSource,
    ): TryOnPlacement =
        TryOnPlacement(
            centerX = anchor.centerX,
            centerY = anchor.centerY,
            ringWidthPx = (anchor.fingerWidthPx * asset.defaultWidthRatio / config.defaultWidthRatioBase).coerceAtLeast(18f),
            rotationDegrees = anchor.angleDegrees,
        )

    private fun placementFromMeasured(
        anchor: TryOnFingerAnchor,
        measurement: TryOnMeasurementSnapshot,
        asset: TryOnAssetSource,
    ): TryOnPlacement {
        val measuredDiameterPx = measurement.mmPerPx?.takeIf { it > 0f }?.let { measurement.equivalentDiameterMm / it }
        val fallbackByRatio =
            if (measurement.fingerWidthMm > 0f) {
                anchor.fingerWidthPx * measurement.equivalentDiameterMm / measurement.fingerWidthMm
            } else {
                anchor.fingerWidthPx
            }
        val ringWidth = (measuredDiameterPx ?: fallbackByRatio).coerceAtLeast(18f)
        val scaledWidth = ringWidth * (asset.defaultWidthRatio / config.defaultWidthRatioBase)
        return TryOnPlacement(
            centerX = anchor.centerX,
            centerY = anchor.centerY,
            ringWidthPx = scaledWidth,
            rotationDegrees = anchor.angleDegrees,
        )
    }

    private fun defaultPlacement(
        asset: TryOnAssetSource,
        frameWidth: Int,
        frameHeight: Int,
    ): TryOnPlacement {
        val safeWidth = frameWidth.coerceAtLeast(1)
        val safeHeight = frameHeight.coerceAtLeast(1)
        return TryOnPlacement(
            centerX = safeWidth * 0.5f,
            centerY = safeHeight * 0.52f,
            ringWidthPx = (safeWidth * asset.defaultWidthRatio).coerceAtLeast(26f),
            rotationDegrees = 0f,
        )
    }

    private fun clampPlacementJumps(
        current: TryOnPlacement,
        previous: TryOnPlacement?,
    ): TryOnPlacement {
        val old = previous ?: return current
        val clampedWidth = clampDelta(current.ringWidthPx, old.ringWidthPx, old.ringWidthPx * 0.16f)
        val clampedCenterX = clampDelta(current.centerX, old.centerX, config.maxCenterDeltaPx)
        val clampedCenterY = clampDelta(current.centerY, old.centerY, config.maxCenterDeltaPx)
        val deltaRotation = normalizeRotationDelta(current.rotationDegrees - old.rotationDegrees)
        val clampedRotation = old.rotationDegrees + deltaRotation.coerceIn(-config.maxRotationDeltaDeg, config.maxRotationDeltaDeg)
        return TryOnPlacement(
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

    private fun computeAnchorJumpPx(
        current: TryOnFingerAnchor?,
        previous: TryOnFingerAnchor?,
    ): Float {
        if (current == null || previous == null) return 0f
        val dx = current.centerX - previous.centerX
        val dy = current.centerY - previous.centerY
        return sqrt(dx * dx + dy * dy)
    }

    private fun computePlacementJumpRatio(
        current: TryOnPlacement,
        previous: TryOnPlacement?,
    ): Float {
        if (previous == null) return 0f
        val dx = current.centerX - previous.centerX
        val dy = current.centerY - previous.centerY
        val jumpPx = sqrt(dx * dx + dy * dy)
        return (jumpPx / previous.ringWidthPx.coerceAtLeast(1f)).coerceIn(0f, 2f)
    }

    private fun applyQualityGate(
        candidate: TryOnPlacement,
        previous: TryOnPlacement?,
        action: TryOnUpdateAction,
    ): TryOnPlacement {
        val old = previous ?: return candidate
        return when (action) {
            TryOnUpdateAction.Update -> candidate
            TryOnUpdateAction.FreezeScaleRotation ->
                candidate.copy(
                    ringWidthPx = old.ringWidthPx,
                    rotationDegrees = old.rotationDegrees,
                )
            TryOnUpdateAction.HoldLastPlacement -> old
            TryOnUpdateAction.Recover ->
                TryOnPlacement(
                    centerX = old.centerX + (candidate.centerX - old.centerX) * 0.75f,
                    centerY = old.centerY + (candidate.centerY - old.centerY) * 0.75f,
                    ringWidthPx = old.ringWidthPx + (candidate.ringWidthPx - old.ringWidthPx) * 0.42f,
                    rotationDegrees = old.rotationDegrees + normalizeRotationDelta(candidate.rotationDegrees - old.rotationDegrees) * 0.45f,
                )
            TryOnUpdateAction.Hide -> old
        }
    }

    private fun normalizeRotationDelta(delta: Float): Float {
        var adjusted = delta
        while (adjusted > 180f) adjusted -= 360f
        while (adjusted < -180f) adjusted += 360f
        return adjusted
    }

    private fun TryOnMeasurementSnapshot?.isUsableMeasurement(): Boolean {
        val measurement = this ?: return false
        if (!measurement.usable) return false
        if (measurement.equivalentDiameterMm <= 0f) return false
        return measurement.confidence >= config.minUsableMeasurementConfidence
    }
}
