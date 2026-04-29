package com.handtryon.render3d

import com.handtryon.coreengine.RingTryOnRenderStateResolver
import com.handtryon.coreengine.fit.RingFitSolver
import com.handtryon.coreengine.model.TryOnInputQuality as CoreInputQuality
import com.handtryon.coreengine.model.TryOnMeasurementSnapshot as CoreMeasurementSnapshot
import com.handtryon.coreengine.model.TryOnRenderState3D
import com.handtryon.coreengine.model.TryOnTrackingState as CoreTrackingState
import com.handtryon.coreengine.model.TryOnUpdateAction as CoreUpdateAction
import com.handtryon.coreengine.model.TryOnVec3
import com.handtryon.coreengine.pose.RingFingerPoseSolver
import com.handtryon.domain.GlbAssetSummary
import com.handtryon.domain.MeasurementSnapshot
import com.handtryon.domain.TryOnTrackingState
import com.handtryon.domain.TryOnUpdateAction
import com.handtryon.tracking.TrackedHandFrame
import com.handtryon.tracking.TrackedHandFrameMapper
import com.handtryon.tracking.TrackingFrameQualityPolicy

class TryOnRenderState3DFactory(
    private val poseSolver: RingFingerPoseSolver = RingFingerPoseSolver(),
    private val fitSolver: RingFitSolver = RingFitSolver(),
    private val renderStateResolver: RingTryOnRenderStateResolver = RingTryOnRenderStateResolver(),
    private val trackingQualityPolicy: TrackingFrameQualityPolicy = TrackingFrameQualityPolicy(),
) {
    fun create(
        trackedHandFrame: TrackedHandFrame?,
        measurement: MeasurementSnapshot?,
        glbSummary: GlbAssetSummary?,
        frameWidth: Int,
        frameHeight: Int,
        qualityScore: Float,
        trackingState: TryOnTrackingState,
        updateAction: TryOnUpdateAction,
    ): TryOnRenderState3D? {
        if (trackingQualityPolicy.rejectReason(trackedHandFrame) != null) return null
        val frame = trackedHandFrame ?: return null
        val corePose = poseSolver.solve(TrackedHandFrameMapper.toCorePose(frame)) ?: return null
        val coreMeasurement = measurement.toCoreMeasurement()
        val fit =
            fitSolver.solve(
                fingerPose = corePose,
                measurement = coreMeasurement,
                modelWidthMm = glbSummary?.effectiveModelWidthMm,
            )
        val renderState = renderStateResolver.resolve(
            fingerPose = corePose,
            fitState = fit,
            quality =
                CoreInputQuality(
                    measurementUsable = coreMeasurement?.usable == true,
                    landmarkUsable = true,
                    measurementConfidence = coreMeasurement?.confidence ?: 0f,
                    landmarkConfidence = corePose.confidence,
                    usedLastGoodAnchor = false,
                    trackingState = trackingState.toCoreTrackingState(),
                    qualityScore = qualityScore,
                    updateAction = updateAction.toCoreUpdateAction(),
                ),
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
        return renderState.withAssetScale(glbSummary)
    }

    private fun TryOnRenderState3D.withAssetScale(glbSummary: GlbAssetSummary?): TryOnRenderState3D {
        val multiplier = glbSummary?.scale?.defaultScale?.takeIf { it > 0f } ?: 1f
        if (multiplier == 1f) return this
        val baseScale = ringTransform.scale
        return copy(
            ringTransform =
                ringTransform.copy(
                    scale =
                        TryOnVec3(
                            x = baseScale.x * multiplier,
                            y = baseScale.y * multiplier,
                            z = baseScale.z * multiplier,
                        ),
                ),
        )
    }

    private fun MeasurementSnapshot?.toCoreMeasurement(): CoreMeasurementSnapshot? =
        this?.let { measurement ->
            CoreMeasurementSnapshot(
                equivalentDiameterMm = measurement.equivalentDiameterMm,
                fingerWidthMm = measurement.fingerWidthMm,
                confidence = measurement.confidence,
                mmPerPx = measurement.mmPerPx,
                usable = measurement.usable,
            )
        }

    private fun TryOnTrackingState.toCoreTrackingState(): CoreTrackingState =
        when (this) {
            TryOnTrackingState.Searching -> CoreTrackingState.Searching
            TryOnTrackingState.Candidate -> CoreTrackingState.Candidate
            TryOnTrackingState.Locked -> CoreTrackingState.Locked
            TryOnTrackingState.Recovering -> CoreTrackingState.Recovering
        }

    private fun TryOnUpdateAction.toCoreUpdateAction(): CoreUpdateAction =
        when (this) {
            TryOnUpdateAction.Update -> CoreUpdateAction.Update
            TryOnUpdateAction.FreezeScaleRotation -> CoreUpdateAction.FreezeScaleRotation
            TryOnUpdateAction.HoldLastPlacement -> CoreUpdateAction.HoldLastPlacement
            TryOnUpdateAction.Recover -> CoreUpdateAction.Recover
            TryOnUpdateAction.Hide -> CoreUpdateAction.Hide
        }
}
