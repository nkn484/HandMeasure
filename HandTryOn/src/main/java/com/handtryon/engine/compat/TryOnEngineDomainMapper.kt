package com.handtryon.engine.compat

import com.handtryon.coreengine.model.TryOnAssetSource as CoreAssetSource
import com.handtryon.coreengine.model.TryOnFingerAnchor as CoreFingerAnchor
import com.handtryon.coreengine.model.TryOnHandPoseSnapshot as CoreHandPoseSnapshot
import com.handtryon.coreengine.model.TryOnInputQuality as CoreInputQuality
import com.handtryon.coreengine.model.TryOnLandmarkPoint as CoreLandmarkPoint
import com.handtryon.coreengine.model.TryOnMeasurementSnapshot as CoreMeasurementSnapshot
import com.handtryon.coreengine.model.TryOnMode as CoreTryOnMode
import com.handtryon.coreengine.model.TryOnPlacement as CorePlacement
import com.handtryon.coreengine.model.TryOnPlacementValidation as CorePlacementValidation
import com.handtryon.coreengine.model.TryOnSession as CoreSession
import com.handtryon.coreengine.model.TryOnEngineRenderState
import com.handtryon.coreengine.model.TryOnEngineResult
import com.handtryon.coreengine.model.TryOnEngineSessionState
import com.handtryon.core.TryOnSessionResolution
import com.handtryon.domain.FingerAnchor
import com.handtryon.domain.HandPoseSnapshot
import com.handtryon.domain.LandmarkPoint
import com.handtryon.domain.MeasurementSnapshot
import com.handtryon.domain.PlacementValidation
import com.handtryon.domain.RingAssetSource
import com.handtryon.domain.RingPlacement
import com.handtryon.domain.TryOnInputQuality
import com.handtryon.domain.TryOnMode
import com.handtryon.domain.TryOnSession
import com.handtryon.engine.model.TryOnEngineRequest
import com.handtryon.render.model.TryOnRenderState

internal class TryOnEngineDomainMapper {
    fun toEngineRequest(
        asset: RingAssetSource,
        handPose: HandPoseSnapshot?,
        measurement: MeasurementSnapshot?,
        manualPlacement: RingPlacement?,
        previousSession: TryOnSession?,
        frameWidth: Int,
        frameHeight: Int,
        nowMs: Long,
    ): TryOnEngineRequest =
        TryOnEngineRequest(
            asset = asset.toCoreAsset(),
            handPose = handPose?.toCoreHandPoseInternal(),
            measurement = measurement?.toCoreMeasurement(),
            manualPlacement = manualPlacement?.toCorePlacementInternal(),
            previousSession = previousSession?.toCoreSession(),
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            nowMs = nowMs,
        )

    fun toDomainSession(result: TryOnEngineResult): TryOnSession = result.session.toDomainSession()

    fun toRenderState(result: TryOnEngineResult): TryOnRenderState = result.renderState.toDomainRenderState()

    fun toSessionResolution(result: TryOnEngineResult): TryOnSessionResolution =
        TryOnSessionResolution(
            session = toDomainSession(result),
            renderState = toRenderState(result),
        )

    fun toCoreHandPose(pose: HandPoseSnapshot): CoreHandPoseSnapshot = pose.toCoreHandPoseInternal()

    fun toDomainAnchor(anchor: CoreFingerAnchor): FingerAnchor = anchor.toDomainAnchorInternal()

    fun toCoreAnchor(anchor: FingerAnchor): CoreFingerAnchor = anchor.toCoreAnchorInternal()

    fun toCorePlacement(placement: RingPlacement): CorePlacement = placement.toCorePlacementInternal()

    fun toDomainPlacement(placement: CorePlacement): RingPlacement = placement.toDomainPlacementInternal()

    fun toDomainPlacementValidation(validation: CorePlacementValidation): PlacementValidation =
        validation.toDomainPlacementValidationInternal()

    private fun RingAssetSource.toCoreAsset(): CoreAssetSource =
        CoreAssetSource(
            id = id,
            name = name,
            overlayAssetPath = overlayAssetPath,
            metadataAssetPath = metadataAssetPath,
            defaultWidthRatio = defaultWidthRatio,
            rotationBiasDeg = rotationBiasDeg,
        )

    private fun CoreAssetSource.toDomainAsset(): RingAssetSource =
        RingAssetSource(
            id = id,
            name = name,
            overlayAssetPath = overlayAssetPath,
            metadataAssetPath = metadataAssetPath,
            defaultWidthRatio = defaultWidthRatio,
            rotationBiasDeg = rotationBiasDeg,
        )

    private fun HandPoseSnapshot.toCoreHandPoseInternal(): CoreHandPoseSnapshot =
        CoreHandPoseSnapshot(
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            landmarks = landmarks.map { it.toCoreLandmark() },
            confidence = confidence,
            timestampMs = timestampMs,
        )

    private fun LandmarkPoint.toCoreLandmark(): CoreLandmarkPoint = CoreLandmarkPoint(x = x, y = y, z = z)

    private fun MeasurementSnapshot.toCoreMeasurement(): CoreMeasurementSnapshot =
        CoreMeasurementSnapshot(
            equivalentDiameterMm = equivalentDiameterMm,
            fingerWidthMm = fingerWidthMm,
            confidence = confidence,
            mmPerPx = mmPerPx,
            usable = usable,
        )

    private fun RingPlacement.toCorePlacementInternal(): CorePlacement =
        CorePlacement(
            centerX = centerX,
            centerY = centerY,
            ringWidthPx = ringWidthPx,
            rotationDegrees = rotationDegrees,
        )

    private fun CorePlacement.toDomainPlacementInternal(): RingPlacement =
        RingPlacement(
            centerX = centerX,
            centerY = centerY,
            ringWidthPx = ringWidthPx,
            rotationDegrees = rotationDegrees,
        )

    private fun FingerAnchor.toCoreAnchorInternal(): CoreFingerAnchor =
        CoreFingerAnchor(
            centerX = centerX,
            centerY = centerY,
            angleDegrees = angleDegrees,
            fingerWidthPx = fingerWidthPx,
            confidence = confidence,
            timestampMs = timestampMs,
        )

    private fun CoreFingerAnchor.toDomainAnchorInternal(): FingerAnchor =
        FingerAnchor(
            centerX = centerX,
            centerY = centerY,
            angleDegrees = angleDegrees,
            fingerWidthPx = fingerWidthPx,
            confidence = confidence,
            timestampMs = timestampMs,
        )

    private fun TryOnSession.toCoreSession(): CoreSession =
        CoreSession(
            asset = asset.toCoreAsset(),
            mode = mode.toCoreMode(),
            quality = quality.toCoreQuality(),
            anchor = anchor?.toCoreAnchorInternal(),
            placement = placement.toCorePlacementInternal(),
            updatedAtMs = updatedAtMs,
        )

    private fun TryOnEngineSessionState.toDomainSession(): TryOnSession =
        TryOnSession(
            asset = asset.toDomainAsset(),
            mode = mode.toDomainMode(),
            quality = quality.toDomainQuality(),
            anchor = anchor?.toDomainAnchorInternal(),
            placement = placement.toDomainPlacementInternal(),
            updatedAtMs = updatedAtMs,
        )

    private fun TryOnEngineRenderState.toDomainRenderState(): TryOnRenderState =
        TryOnRenderState(
            mode = mode.toDomainMode(),
            anchor = anchor?.toDomainAnchorInternal(),
            placement = placement.toDomainPlacementInternal(),
            generatedAtMs = generatedAtMs,
        )

    private fun TryOnMode.toCoreMode(): CoreTryOnMode =
        when (this) {
            TryOnMode.Measured -> CoreTryOnMode.Measured
            TryOnMode.LandmarkOnly -> CoreTryOnMode.LandmarkOnly
            TryOnMode.Manual -> CoreTryOnMode.Manual
        }

    private fun CoreTryOnMode.toDomainMode(): TryOnMode =
        when (this) {
            CoreTryOnMode.Measured -> TryOnMode.Measured
            CoreTryOnMode.LandmarkOnly -> TryOnMode.LandmarkOnly
            CoreTryOnMode.Manual -> TryOnMode.Manual
        }

    private fun TryOnInputQuality.toCoreQuality(): CoreInputQuality =
        CoreInputQuality(
            measurementUsable = measurementUsable,
            landmarkUsable = landmarkUsable,
            measurementConfidence = measurementConfidence,
            landmarkConfidence = landmarkConfidence,
            usedLastGoodAnchor = usedLastGoodAnchor,
        )

    private fun CoreInputQuality.toDomainQuality(): TryOnInputQuality =
        TryOnInputQuality(
            measurementUsable = measurementUsable,
            landmarkUsable = landmarkUsable,
            measurementConfidence = measurementConfidence,
            landmarkConfidence = landmarkConfidence,
            usedLastGoodAnchor = usedLastGoodAnchor,
        )

    private fun CorePlacementValidation.toDomainPlacementValidationInternal(): PlacementValidation =
        PlacementValidation(
            widthRatio = widthRatio,
            anchorDistancePx = anchorDistancePx,
            rotationJumpDeg = rotationJumpDeg,
            isPlacementUsable = isPlacementUsable,
            notes = notes,
        )
}
