package com.handtryon.nonar3d

import com.handtryon.domain.GlbAssetSummary
import com.handtryon.domain.HandPoseSnapshot
import com.handtryon.domain.MeasurementSnapshot
import com.handtryon.coreengine.RingFingerPoseSolver as CoreRingFingerPoseSolver
import com.handtryon.coreengine.model.TryOnHandPoseSnapshot
import com.handtryon.coreengine.model.TryOnLandmarkPoint
import kotlin.math.max
import kotlin.math.sqrt

data class NonAr3dPoint2(
    val x: Float,
    val y: Float,
)

data class NonAr3dVec2(
    val x: Float,
    val y: Float,
) {
    val length: Float
        get() = sqrt(x * x + y * y)

    fun normalized(): NonAr3dVec2 {
        val safeLength = length.coerceAtLeast(1e-4f)
        return NonAr3dVec2(x / safeLength, y / safeLength)
    }
}

data class RingFingerPose3D(
    val centerPx: NonAr3dPoint2,
    val occluderStartPx: NonAr3dPoint2,
    val occluderEndPx: NonAr3dPoint2,
    val tangentPx: NonAr3dVec2,
    val normalHintPx: NonAr3dVec2,
    val rotationDegrees: Float,
    val rollDegrees: Float,
    val fingerWidthPx: Float,
    val fingerRadiusMm: Float?,
    val ringOuterDiameterMm: Float,
    val confidence: Float,
)

class RingFingerPoseSolver(
    private val coreSolver: CoreRingFingerPoseSolver = CoreRingFingerPoseSolver(),
) {
    fun solve(
        handPose: HandPoseSnapshot?,
        measurement: MeasurementSnapshot?,
        glbSummary: GlbAssetSummary?,
    ): RingFingerPose3D? {
        val corePose = coreSolver.solve(handPose.toCorePose()) ?: return null
        val landmarkWidthPx = corePose.fingerWidthPx
        val measuredFingerWidthMm = measurement?.fingerWidthMm?.takeIf { it > 0f && measurement.usable }
        val measuredRingDiameterMm = measurement?.equivalentDiameterMm?.takeIf { it > 0f && measurement.usable }
        val modelDiameterMm = glbSummary?.estimatedBoundsMm?.let { max(it.x, it.y) }?.takeIf { it > 0f }

        return RingFingerPose3D(
            centerPx = NonAr3dPoint2(corePose.centerPx.x, corePose.centerPx.y),
            occluderStartPx = NonAr3dPoint2(corePose.occluderStartPx.x, corePose.occluderStartPx.y),
            occluderEndPx = NonAr3dPoint2(corePose.occluderEndPx.x, corePose.occluderEndPx.y),
            tangentPx = NonAr3dVec2(corePose.tangentPx.x, corePose.tangentPx.y),
            normalHintPx = NonAr3dVec2(corePose.normalHintPx.x, corePose.normalHintPx.y),
            rotationDegrees = corePose.rotationDegrees,
            rollDegrees = corePose.rollDegrees,
            fingerWidthPx = measuredFingerWidthMm?.let { widthMm ->
                measuredRingDiameterMm?.let { diameterMm ->
                    landmarkWidthPx * (widthMm / diameterMm).coerceIn(0.45f, 1.35f)
                }
            } ?: landmarkWidthPx,
            fingerRadiusMm = measuredFingerWidthMm?.div(2f),
            ringOuterDiameterMm = measuredRingDiameterMm ?: modelDiameterMm ?: DEFAULT_RING_DIAMETER_MM,
            confidence = corePose.confidence,
        )
    }

    private fun HandPoseSnapshot?.toCorePose(): TryOnHandPoseSnapshot? =
        this?.let { pose ->
            TryOnHandPoseSnapshot(
                frameWidth = pose.frameWidth,
                frameHeight = pose.frameHeight,
                landmarks =
                    pose.landmarks.map { point ->
                        TryOnLandmarkPoint(x = point.x, y = point.y, z = point.z)
                    },
                confidence = pose.confidence,
                timestampMs = pose.timestampMs,
            )
        }

    private companion object {
        const val DEFAULT_RING_DIAMETER_MM = 20.4f
    }
}
