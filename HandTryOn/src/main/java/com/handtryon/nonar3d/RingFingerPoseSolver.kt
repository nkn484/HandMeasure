package com.handtryon.nonar3d

import com.handtryon.domain.GlbAssetSummary
import com.handtryon.domain.HandPoseSnapshot
import com.handtryon.domain.LandmarkPoint
import com.handtryon.domain.MeasurementSnapshot
import kotlin.math.abs
import kotlin.math.atan2
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

class RingFingerPoseSolver {
    fun solve(
        handPose: HandPoseSnapshot?,
        measurement: MeasurementSnapshot?,
        glbSummary: GlbAssetSummary?,
    ): RingFingerPose3D? {
        val pose = handPose ?: return null
        if (pose.landmarks.size <= RING_DIP_INDEX) return null

        val mcp = pose.landmarks[RING_MCP_INDEX]
        val pip = pose.landmarks[RING_PIP_INDEX]
        val dip = pose.landmarks[RING_DIP_INDEX]
        val axis = vector(from = mcp, to = dip)
        val axisLength = axis.length
        if (axisLength < MIN_AXIS_LENGTH_PX) return null

        val tangent = axis.normalized()
        val center = interpolate(mcp, pip, 0.54f)
        val occluderStart = interpolate(mcp, pip, 0.18f)
        val occluderEnd = interpolate(pip, dip, 0.82f)
        val normal = normalHint(pose = pose, tangent = tangent)
        val mcpToPipLength = vector(from = mcp, to = pip).length
        val landmarkWidthPx = (mcpToPipLength * LANDMARK_WIDTH_RATIO).coerceAtLeast(MIN_FINGER_WIDTH_PX)
        val measuredFingerWidthMm = measurement?.fingerWidthMm?.takeIf { it > 0f && measurement.usable }
        val measuredRingDiameterMm = measurement?.equivalentDiameterMm?.takeIf { it > 0f && measurement.usable }
        val modelDiameterMm = glbSummary?.estimatedBoundsMm?.let { max(it.x, it.y) }?.takeIf { it > 0f }
        val rollDegrees = estimateRollDegrees(pose)
        val confidence =
            (
                pose.confidence *
                    (axisLength / 90f).coerceIn(0.45f, 1f) *
                    (1f - abs(rollDegrees) / 110f).coerceIn(0.62f, 1f)
            ).coerceIn(0f, 1f)

        if (confidence < MIN_CONFIDENCE) return null

        return RingFingerPose3D(
            centerPx = NonAr3dPoint2(center.x, center.y),
            occluderStartPx = NonAr3dPoint2(occluderStart.x, occluderStart.y),
            occluderEndPx = NonAr3dPoint2(occluderEnd.x, occluderEnd.y),
            tangentPx = tangent,
            normalHintPx = normal,
            rotationDegrees = (atan2(tangent.y, tangent.x) * RAD_TO_DEG).toFloat(),
            rollDegrees = rollDegrees,
            fingerWidthPx = measuredFingerWidthMm?.let { widthMm ->
                measuredRingDiameterMm?.let { diameterMm ->
                    landmarkWidthPx * (widthMm / diameterMm).coerceIn(0.45f, 1.35f)
                }
            } ?: landmarkWidthPx,
            fingerRadiusMm = measuredFingerWidthMm?.div(2f),
            ringOuterDiameterMm = measuredRingDiameterMm ?: modelDiameterMm ?: DEFAULT_RING_DIAMETER_MM,
            confidence = confidence,
        )
    }

    private fun normalHint(
        pose: HandPoseSnapshot,
        tangent: NonAr3dVec2,
    ): NonAr3dVec2 {
        val geometricNormal = NonAr3dVec2(-tangent.y, tangent.x)
        if (pose.landmarks.size <= LITTLE_MCP_INDEX) return geometricNormal

        val middle = pose.landmarks[MIDDLE_MCP_INDEX]
        val little = pose.landmarks[LITTLE_MCP_INDEX]
        val side = NonAr3dVec2(little.x - middle.x, little.y - middle.y)
        val sign = if (geometricNormal.x * side.x + geometricNormal.y * side.y < 0f) -1f else 1f
        return NonAr3dVec2(geometricNormal.x * sign, geometricNormal.y * sign).normalized()
    }

    private fun estimateRollDegrees(pose: HandPoseSnapshot): Float {
        if (pose.landmarks.size <= LITTLE_MCP_INDEX) return 0f
        val middleZ = pose.landmarks[MIDDLE_MCP_INDEX].z
        val littleZ = pose.landmarks[LITTLE_MCP_INDEX].z
        return ((littleZ - middleZ) * Z_ROLL_GAIN_DEGREES).coerceIn(-65f, 65f)
    }

    private fun interpolate(
        a: LandmarkPoint,
        b: LandmarkPoint,
        t: Float,
    ): LandmarkPoint =
        LandmarkPoint(
            x = a.x + (b.x - a.x) * t,
            y = a.y + (b.y - a.y) * t,
            z = a.z + (b.z - a.z) * t,
        )

    private fun vector(
        from: LandmarkPoint,
        to: LandmarkPoint,
    ): NonAr3dVec2 = NonAr3dVec2(to.x - from.x, to.y - from.y)

    private companion object {
        const val RING_MCP_INDEX = 13
        const val RING_PIP_INDEX = 14
        const val RING_DIP_INDEX = 15
        const val MIDDLE_MCP_INDEX = 9
        const val LITTLE_MCP_INDEX = 17
        const val MIN_AXIS_LENGTH_PX = 9f
        const val MIN_FINGER_WIDTH_PX = 18f
        const val MIN_CONFIDENCE = 0.22f
        const val LANDMARK_WIDTH_RATIO = 1.34f
        const val DEFAULT_RING_DIAMETER_MM = 20.4f
        const val Z_ROLL_GAIN_DEGREES = 240f
        const val RAD_TO_DEG = 180.0 / Math.PI
    }
}
