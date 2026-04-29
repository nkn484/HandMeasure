package com.handtryon.coreengine

import com.handtryon.coreengine.model.RingFingerPose
import com.handtryon.coreengine.model.RingFingerPoseRejectReason
import com.handtryon.coreengine.model.TryOnHandPoseSnapshot
import com.handtryon.coreengine.model.TryOnLandmarkPoint
import com.handtryon.coreengine.model.TryOnPoint2
import com.handtryon.coreengine.model.TryOnVec2
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sign
import kotlin.math.sqrt

class RingFingerPoseSolver {
    fun solve(handPose: TryOnHandPoseSnapshot?): RingFingerPose? {
        val pose = handPose ?: return null
        if (pose.landmarks.size <= RING_DIP_INDEX) return null

        val mcp = pose.landmarks[RING_MCP_INDEX]
        val pip = pose.landmarks[RING_PIP_INDEX]
        val dip = pose.landmarks[RING_DIP_INDEX]
        val wrist = pose.landmarks[WRIST_INDEX]
        val mcpToPip = vector(from = mcp, to = pip)
        val pipToDip = vector(from = pip, to = dip)
        val axis = vector(from = mcp, to = dip)
        val axisLength = axis.length
        if (axisLength < MIN_AXIS_LENGTH_PX) return null
        if (!isFingerExtensionUsable(
                mcpToPip = mcpToPip,
                pipToDip = pipToDip,
                axis = axis,
                wristToMcp = vector(from = wrist, to = mcp),
            )
        ) {
            return null
        }

        val tangent = axis.normalized()
        val rollDegrees = estimateRollDegrees(pose)
        val confidence =
            (
                pose.confidence *
                    (axisLength / CONFIDENCE_AXIS_NORMALIZER_PX).coerceIn(0.45f, 1f) *
                    (1f - abs(rollDegrees) / ROLL_CONFIDENCE_NORMALIZER_DEGREES).coerceIn(0.62f, 1f)
            ).coerceIn(0f, 1f)
        if (confidence < MIN_CONFIDENCE) return null

        val fingerWidthPx = (mcpToPip.length * LANDMARK_WIDTH_RATIO).coerceAtLeast(MIN_FINGER_WIDTH_PX)
        val normal = normalHint(pose = pose, tangent = tangent)
        val center =
            ringBandCenter(
                mcp = mcp,
                pip = pip,
                tangent = tangent,
                normal = normal,
                mcpToPipLength = mcpToPip.length,
                frameHeight = pose.frameHeight,
            )
        if (!isPointInsideFrame(center, pose.frameWidth, pose.frameHeight)) return null

        return RingFingerPose(
            centerPx = TryOnPoint2(center.x, center.y),
            occluderStartPx = interpolate(mcp, pip, OCCLUDER_START_ON_MCP_TO_PIP).toPoint2(),
            occluderEndPx = interpolate(pip, dip, OCCLUDER_END_ON_PIP_TO_DIP).toPoint2(),
            tangentPx = TryOnVec2(tangent.x, tangent.y),
            normalHintPx = TryOnVec2(normal.x, normal.y),
            rotationDegrees = adjustedRotationDegrees(tangent),
            rollDegrees = rollDegrees,
            fingerWidthPx = fingerWidthPx,
            confidence = confidence,
            rejectReason = null,
        )
    }

    fun rejectReason(handPose: TryOnHandPoseSnapshot?): RingFingerPoseRejectReason? {
        val pose = handPose ?: return RingFingerPoseRejectReason.MissingHand
        if (pose.landmarks.size <= RING_DIP_INDEX) return RingFingerPoseRejectReason.MissingLandmarks

        val mcp = pose.landmarks[RING_MCP_INDEX]
        val pip = pose.landmarks[RING_PIP_INDEX]
        val dip = pose.landmarks[RING_DIP_INDEX]
        val wrist = pose.landmarks[WRIST_INDEX]
        val mcpToPip = vector(from = mcp, to = pip)
        val pipToDip = vector(from = pip, to = dip)
        val axis = vector(from = mcp, to = dip)
        val axisLength = axis.length
        if (axisLength < MIN_AXIS_LENGTH_PX) return RingFingerPoseRejectReason.FingerTooSmall
        if (!isFingerExtensionUsable(
                mcpToPip = mcpToPip,
                pipToDip = pipToDip,
                axis = axis,
                wristToMcp = vector(from = wrist, to = mcp),
            )
        ) {
            return RingFingerPoseRejectReason.UnstableGeometry
        }
        val tangent = axis.normalized()
        val normal = normalHint(pose = pose, tangent = tangent)
        val center =
            ringBandCenter(
                mcp = mcp,
                pip = pip,
                tangent = tangent,
                normal = normal,
                mcpToPipLength = mcpToPip.length,
                frameHeight = pose.frameHeight,
            )
        if (!isPointInsideFrame(center, pose.frameWidth, pose.frameHeight)) return RingFingerPoseRejectReason.OutsideFrame
        if (pose.confidence < MIN_CONFIDENCE) return RingFingerPoseRejectReason.LowConfidence
        return null
    }

    private fun isFingerExtensionUsable(
        mcpToPip: CoreVec2,
        pipToDip: CoreVec2,
        axis: CoreVec2,
        wristToMcp: CoreVec2,
    ): Boolean {
        val segmentLength = mcpToPip.length + pipToDip.length
        if (segmentLength < MIN_AXIS_LENGTH_PX) return false
        val axisLength = axis.length
        if (axis.normalized().dot(wristToMcp.normalized()) < MIN_FORWARD_EXTENSION_COSINE) return false
        val distalRatio = pipToDip.length / mcpToPip.length.coerceAtLeast(1e-4f)
        if (distalRatio < MIN_DISTAL_TO_PROXIMAL_RATIO) return false
        val extensionRatio = axisLength / segmentLength.coerceAtLeast(1e-4f)
        val bendCosine = mcpToPip.normalized().dot(pipToDip.normalized())
        return extensionRatio >= MIN_EXTENSION_RATIO && bendCosine >= MIN_BEND_COSINE
    }

    private fun ringBandCenter(
        mcp: TryOnLandmarkPoint,
        pip: TryOnLandmarkPoint,
        tangent: CoreVec2,
        normal: CoreVec2,
        mcpToPipLength: Float,
        frameHeight: Int,
    ): TryOnLandmarkPoint {
        val absLean = abs(tangent.x)
        val mcpYRatio = mcp.y / frameHeight.coerceAtLeast(1).toFloat()
        val centerT =
            when {
                absLean < VERTICAL_FINGER_LEAN_THRESHOLD -> VERTICAL_CENTER_ON_MCP_TO_PIP
                absLean > OBLIQUE_FINGER_LEAN_THRESHOLD -> DEFAULT_CENTER_ON_MCP_TO_PIP
                mcpYRatio in MID_PALM_Y_RATIO_MIN..MID_PALM_Y_RATIO_MAX -> MID_PALM_CENTER_ON_MCP_TO_PIP
                else -> DEFAULT_CENTER_ON_MCP_TO_PIP
            }
        val baseCenter = interpolate(mcp, pip, centerT)
        val lateralOffsetPx =
            when {
                absLean < VERTICAL_FINGER_LEAN_THRESHOLD -> 0f
                absLean > OBLIQUE_FINGER_LEAN_THRESHOLD -> -mcpToPipLength * OBLIQUE_LATERAL_RATIO
                mcpYRatio in MID_PALM_Y_RATIO_MIN..MID_PALM_Y_RATIO_MAX -> mcpToPipLength * MID_PALM_LATERAL_RATIO
                else -> -mcpToPipLength * TOP_PALM_LATERAL_RATIO
            }
        return TryOnLandmarkPoint(
            x = baseCenter.x + normal.x * lateralOffsetPx,
            y = baseCenter.y + normal.y * lateralOffsetPx,
            z = baseCenter.z,
        )
    }

    private fun normalHint(
        pose: TryOnHandPoseSnapshot,
        tangent: CoreVec2,
    ): CoreVec2 {
        val geometricNormal = CoreVec2(-tangent.y, tangent.x)
        if (pose.landmarks.size <= LITTLE_MCP_INDEX) return geometricNormal

        val middle = pose.landmarks[MIDDLE_MCP_INDEX]
        val little = pose.landmarks[LITTLE_MCP_INDEX]
        val side = CoreVec2(little.x - middle.x, little.y - middle.y)
        val sign = if (geometricNormal.x * side.x + geometricNormal.y * side.y < 0f) -1f else 1f
        return CoreVec2(geometricNormal.x * sign, geometricNormal.y * sign).normalized()
    }

    private fun estimateRollDegrees(pose: TryOnHandPoseSnapshot): Float {
        if (pose.landmarks.size <= LITTLE_MCP_INDEX) return 0f
        val middleZ = pose.landmarks[MIDDLE_MCP_INDEX].z
        val littleZ = pose.landmarks[LITTLE_MCP_INDEX].z
        return ((littleZ - middleZ) * Z_ROLL_GAIN_DEGREES).coerceIn(-65f, 65f)
    }

    private fun adjustedRotationDegrees(tangent: CoreVec2): Float {
        val baseDegrees = (atan2(tangent.y, tangent.x) * RAD_TO_DEG).toFloat()
        val obliqueCorrection =
            (abs(tangent.x) - ROTATION_OBLIQUE_START).coerceAtLeast(0f) * ROTATION_OBLIQUE_GAIN_DEGREES
        return baseDegrees - sign(tangent.x) * obliqueCorrection
    }

    private fun interpolate(
        a: TryOnLandmarkPoint,
        b: TryOnLandmarkPoint,
        t: Float,
    ): TryOnLandmarkPoint =
        TryOnLandmarkPoint(
            x = a.x + (b.x - a.x) * t,
            y = a.y + (b.y - a.y) * t,
            z = a.z + (b.z - a.z) * t,
        )

    private fun vector(
        from: TryOnLandmarkPoint,
        to: TryOnLandmarkPoint,
    ): CoreVec2 = CoreVec2(to.x - from.x, to.y - from.y)

    private fun TryOnLandmarkPoint.toPoint2(): TryOnPoint2 = TryOnPoint2(x = x, y = y)

    private fun isPointInsideFrame(
        point: TryOnLandmarkPoint,
        frameWidth: Int,
        frameHeight: Int,
    ): Boolean =
        point.x in 0f..frameWidth.coerceAtLeast(1).toFloat() &&
            point.y in 0f..frameHeight.coerceAtLeast(1).toFloat()

    private data class CoreVec2(
        val x: Float,
        val y: Float,
    ) {
        val length: Float
            get() = sqrt(x * x + y * y)

        fun normalized(): CoreVec2 {
            val safeLength = length.coerceAtLeast(1e-4f)
            return CoreVec2(x / safeLength, y / safeLength)
        }

        fun dot(other: CoreVec2): Float = x * other.x + y * other.y
    }

    private companion object {
        const val RING_MCP_INDEX = 13
        const val RING_PIP_INDEX = 14
        const val RING_DIP_INDEX = 15
        const val WRIST_INDEX = 0
        const val MIDDLE_MCP_INDEX = 9
        const val LITTLE_MCP_INDEX = 17
        const val MIN_AXIS_LENGTH_PX = 9f
        const val MIN_FINGER_WIDTH_PX = 18f
        const val MIN_CONFIDENCE = 0.22f
        const val LANDMARK_WIDTH_RATIO = 1.34f
        const val DEFAULT_CENTER_ON_MCP_TO_PIP = 0.34f
        const val MID_PALM_CENTER_ON_MCP_TO_PIP = 0.30f
        const val VERTICAL_CENTER_ON_MCP_TO_PIP = 0.64f
        const val MID_PALM_Y_RATIO_MIN = 0.49f
        const val MID_PALM_Y_RATIO_MAX = 0.56f
        const val VERTICAL_FINGER_LEAN_THRESHOLD = 0.06f
        const val OBLIQUE_FINGER_LEAN_THRESHOLD = 0.30f
        const val MID_PALM_LATERAL_RATIO = 0.16f
        const val TOP_PALM_LATERAL_RATIO = 0.32f
        const val OBLIQUE_LATERAL_RATIO = 0.23f
        const val OCCLUDER_START_ON_MCP_TO_PIP = 0.18f
        const val OCCLUDER_END_ON_PIP_TO_DIP = 0.82f
        const val CONFIDENCE_AXIS_NORMALIZER_PX = 90f
        const val ROLL_CONFIDENCE_NORMALIZER_DEGREES = 110f
        const val Z_ROLL_GAIN_DEGREES = 240f
        const val ROTATION_OBLIQUE_START = 0.28f
        const val ROTATION_OBLIQUE_GAIN_DEGREES = 500f
        const val MIN_EXTENSION_RATIO = 0.74f
        const val MIN_BEND_COSINE = 0.28f
        const val MIN_DISTAL_TO_PROXIMAL_RATIO = 0.42f
        const val MIN_FORWARD_EXTENSION_COSINE = 0.04f
        const val RAD_TO_DEG = 180.0 / Math.PI
    }
}
