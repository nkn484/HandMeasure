package com.handtryon.coreengine.pose

import com.handtryon.coreengine.model.RingFingerPose
import com.handtryon.coreengine.model.RingFingerPoseDiagnostics
import com.handtryon.coreengine.model.RingFingerPoseRejectReason
import com.handtryon.coreengine.model.RingFingerPoseSolveResult
import com.handtryon.coreengine.model.TryOnHandPoseSnapshot
import com.handtryon.coreengine.model.TryOnLandmarkPoint
import com.handtryon.coreengine.model.TryOnPoint2
import com.handtryon.coreengine.model.TryOnVec2
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sign
import kotlin.math.sqrt

data class RingFingerPoseSolverConfig(
    val minAxisLengthPx: Float = 9f,
    val minFingerWidthPx: Float = 18f,
    val minConfidence: Float = 0.22f,
    val landmarkWidthRatio: Float = 1.34f,
    val defaultCenterOnMcpToPip: Float = 0.34f,
    val midPalmCenterOnMcpToPip: Float = 0.30f,
    val verticalCenterOnMcpToPip: Float = 0.64f,
    val midPalmYRatioMin: Float = 0.49f,
    val midPalmYRatioMax: Float = 0.56f,
    val strictVerticalFingerLeanThreshold: Float = 0.015f,
    val verticalFingerLeanThreshold: Float = 0.12f,
    val lowerPalmVerticalYRatioMin: Float = 0.555f,
    val obliqueFingerLeanThreshold: Float = 0.28f,
    val midPalmLateralRatio: Float = 0.16f,
    val topPalmLateralRatio: Float = 0.32f,
    val obliqueLateralRatio: Float = 0.23f,
    val occluderStartOnMcpToPip: Float = 0.18f,
    val occluderEndOnPipToDip: Float = 0.82f,
    val confidenceAxisNormalizerPx: Float = 90f,
    val rollConfidenceNormalizerDegrees: Float = 110f,
    val zRollGainDegrees: Float = 240f,
    val rotationObliqueStart: Float = 0.28f,
    val positiveObliqueRotationBuckets: List<ObliqueRotationBucket> =
        listOf(
            ObliqueRotationBucket(minAbsLean = 0.28f, correctionDegrees = 35f),
            ObliqueRotationBucket(minAbsLean = 0.43f, correctionDegrees = 70f),
            ObliqueRotationBucket(minAbsLean = 0.55f, correctionDegrees = 140f),
            ObliqueRotationBucket(minAbsLean = 0.68f, correctionDegrees = 208f),
        ),
    val negativeObliqueRotationBuckets: List<ObliqueRotationBucket> =
        listOf(
            ObliqueRotationBucket(minAbsLean = 0.28f, correctionDegrees = 67f),
            ObliqueRotationBucket(minAbsLean = 0.50f, correctionDegrees = 140f),
            ObliqueRotationBucket(minAbsLean = 0.59f, correctionDegrees = 177f),
            ObliqueRotationBucket(minAbsLean = 0.68f, correctionDegrees = 208f),
        ),
    val minExtensionRatio: Float = 0.74f,
    val minBendCosine: Float = 0.28f,
    val minDistalToProximalRatio: Float = 0.42f,
    val minForwardExtensionCosine: Float = 0.04f,
)

data class ObliqueRotationBucket(
    val minAbsLean: Float,
    val correctionDegrees: Float,
)

class RingFingerPoseSolver(
    private val config: RingFingerPoseSolverConfig = RingFingerPoseSolverConfig(),
) {
    fun solve(handPose: TryOnHandPoseSnapshot?): RingFingerPose? = evaluate(handPose).pose

    fun rejectReason(handPose: TryOnHandPoseSnapshot?): RingFingerPoseRejectReason? = evaluate(handPose).rejectReason

    fun evaluate(handPose: TryOnHandPoseSnapshot?): RingFingerPoseSolveResult {
        val pose = handPose
            ?: return rejected(RingFingerPoseRejectReason.MissingHand)
        if (pose.landmarks.size <= RING_DIP_INDEX) {
            return rejected(RingFingerPoseRejectReason.MissingLandmarks)
        }

        val mcp = pose.landmarks[RING_MCP_INDEX]
        val pip = pose.landmarks[RING_PIP_INDEX]
        val dip = pose.landmarks[RING_DIP_INDEX]
        val wrist = pose.landmarks[WRIST_INDEX]
        val mcpToPip = vector(from = mcp, to = pip)
        val pipToDip = vector(from = pip, to = dip)
        val axis = vector(from = mcp, to = dip)
        val axisLength = axis.length
        if (axisLength < config.minAxisLengthPx) {
            return rejected(RingFingerPoseRejectReason.FingerTooSmall, axisLengthPx = axisLength)
        }

        val geometryDiagnostics =
            geometryDiagnostics(
                mcpToPip = mcpToPip,
                pipToDip = pipToDip,
                axis = axis,
                wristToMcp = vector(from = wrist, to = mcp),
            )
        val geometryRejectReason = rejectReasonForGeometry(geometryDiagnostics)
        if (geometryRejectReason != null) {
            return rejected(geometryRejectReason, geometryDiagnostics)
        }

        val tangent = axis.normalized()
        val rollDegrees = estimateRollDegrees(pose)
        val rawConfidence =
            pose.confidence *
                (axisLength / config.confidenceAxisNormalizerPx).coerceIn(0.45f, 1f) *
                (1f - abs(rollDegrees) / config.rollConfidenceNormalizerDegrees).coerceIn(0.62f, 1f)
        val confidence = rawConfidence.coerceIn(0f, 1f)
        if (confidence < config.minConfidence) {
            return rejected(
                RingFingerPoseRejectReason.LowConfidence,
                geometryDiagnostics.copy(rawConfidence = rawConfidence, confidence = confidence),
            )
        }

        val fingerWidthPx = (mcpToPip.length * config.landmarkWidthRatio).coerceAtLeast(config.minFingerWidthPx)
        val normal = normalHint(pose = pose, tangent = tangent)
        val centerPolicy =
            ringBandCenterPolicy(
                mcp = mcp,
                tangent = tangent,
                mcpToPipLength = mcpToPip.length,
                frameHeight = pose.frameHeight,
            )
        val baseCenter = interpolate(mcp, pip, centerPolicy.centerOnMcpToPip)
        val center =
            TryOnLandmarkPoint(
                x = baseCenter.x + normal.x * centerPolicy.lateralOffsetPx,
                y = baseCenter.y + normal.y * centerPolicy.lateralOffsetPx,
                z = baseCenter.z,
            )
        val rotation = adjustedRotation(tangent)
        val diagnostics =
            geometryDiagnostics.copy(
                centerOnMcpToPip = centerPolicy.centerOnMcpToPip,
                lateralOffsetPx = centerPolicy.lateralOffsetPx,
                rawRotationDegrees = rotation.rawDegrees,
                rotationCorrectionDegrees = rotation.correctionDegrees,
                rawConfidence = rawConfidence,
                confidence = confidence,
            )
        if (!isPointInsideFrame(center, pose.frameWidth, pose.frameHeight)) {
            return rejected(RingFingerPoseRejectReason.OutsideFrame, diagnostics)
        }

        val ringPose =
            RingFingerPose(
                centerPx = TryOnPoint2(center.x, center.y),
                occluderStartPx = interpolate(mcp, pip, config.occluderStartOnMcpToPip).toPoint2(),
                occluderEndPx = interpolate(pip, dip, config.occluderEndOnPipToDip).toPoint2(),
                tangentPx = TryOnVec2(tangent.x, tangent.y),
                normalHintPx = TryOnVec2(normal.x, normal.y),
                rotationDegrees = rotation.rotationDegrees,
                rollDegrees = rollDegrees,
                fingerWidthPx = fingerWidthPx,
                confidence = confidence,
                rejectReason = null,
                diagnostics = diagnostics,
            )
        return RingFingerPoseSolveResult(pose = ringPose, rejectReason = null, diagnostics = diagnostics)
    }

    private fun rejectReasonForGeometry(diagnostics: RingFingerPoseDiagnostics): RingFingerPoseRejectReason? =
        when {
            diagnostics.forwardExtensionCosine < config.minForwardExtensionCosine ->
                RingFingerPoseRejectReason.PointsTowardWrist
            diagnostics.distalToProximalRatio < config.minDistalToProximalRatio ->
                RingFingerPoseRejectReason.DistalSegmentHidden
            diagnostics.extensionRatio < config.minExtensionRatio || diagnostics.bendCosine < config.minBendCosine ->
                RingFingerPoseRejectReason.FingerCurled
            else -> null
        }

    private fun geometryDiagnostics(
        mcpToPip: CoreVec2,
        pipToDip: CoreVec2,
        axis: CoreVec2,
        wristToMcp: CoreVec2,
    ): RingFingerPoseDiagnostics {
        val segmentLength = mcpToPip.length + pipToDip.length
        val distalRatio = pipToDip.length / mcpToPip.length.coerceAtLeast(1e-4f)
        return RingFingerPoseDiagnostics(
            extensionRatio = axis.length / segmentLength.coerceAtLeast(1e-4f),
            bendCosine = mcpToPip.normalized().dot(pipToDip.normalized()),
            distalToProximalRatio = distalRatio,
            forwardExtensionCosine = axis.normalized().dot(wristToMcp.normalized()),
            axisLengthPx = axis.length,
        )
    }

    private fun rejected(
        reason: RingFingerPoseRejectReason,
        diagnostics: RingFingerPoseDiagnostics = RingFingerPoseDiagnostics(),
        axisLengthPx: Float = diagnostics.axisLengthPx,
    ): RingFingerPoseSolveResult =
        RingFingerPoseSolveResult(
            pose = null,
            rejectReason = reason,
            diagnostics = diagnostics.copy(axisLengthPx = axisLengthPx, rejectReason = reason),
        )

    private fun ringBandCenterPolicy(
        mcp: TryOnLandmarkPoint,
        tangent: CoreVec2,
        mcpToPipLength: Float,
        frameHeight: Int,
    ): RingBandCenterPolicy {
        val absLean = abs(tangent.x)
        val mcpYRatio = mcp.y / frameHeight.coerceAtLeast(1).toFloat()
        val isVertical =
            absLean < config.strictVerticalFingerLeanThreshold ||
                (mcpYRatio >= config.lowerPalmVerticalYRatioMin && absLean < config.verticalFingerLeanThreshold)
        val centerT =
            when {
                isVertical -> config.verticalCenterOnMcpToPip
                absLean > config.obliqueFingerLeanThreshold -> config.defaultCenterOnMcpToPip
                mcpYRatio in config.midPalmYRatioMin..config.midPalmYRatioMax -> config.midPalmCenterOnMcpToPip
                else -> config.defaultCenterOnMcpToPip
            }
        val lateralOffsetPx =
            when {
                isVertical -> 0f
                absLean > config.obliqueFingerLeanThreshold -> -mcpToPipLength * config.obliqueLateralRatio
                mcpYRatio in config.midPalmYRatioMin..config.midPalmYRatioMax -> mcpToPipLength * config.midPalmLateralRatio
                else -> -mcpToPipLength * config.topPalmLateralRatio
            }
        return RingBandCenterPolicy(centerOnMcpToPip = centerT, lateralOffsetPx = lateralOffsetPx)
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
        val sign = if (geometricNormal.dot(side) < 0f) -1f else 1f
        return CoreVec2(geometricNormal.x * sign, geometricNormal.y * sign).normalized()
    }

    private fun estimateRollDegrees(pose: TryOnHandPoseSnapshot): Float {
        if (pose.landmarks.size <= LITTLE_MCP_INDEX) return 0f
        val middleZ = pose.landmarks[MIDDLE_MCP_INDEX].z
        val littleZ = pose.landmarks[LITTLE_MCP_INDEX].z
        return ((littleZ - middleZ) * config.zRollGainDegrees).coerceIn(-65f, 65f)
    }

    private fun adjustedRotation(tangent: CoreVec2): AdjustedRotation {
        val baseDegrees = (atan2(tangent.y, tangent.x) * RAD_TO_DEG).toFloat()
        val obliqueCorrection = obliqueRotationCorrection(tangent)
        return AdjustedRotation(
            rawDegrees = baseDegrees,
            correctionDegrees = obliqueCorrection,
            rotationDegrees = baseDegrees - sign(tangent.x) * obliqueCorrection,
        )
    }

    private fun obliqueRotationCorrection(tangent: CoreVec2): Float {
        val absLean = abs(tangent.x)
        if (absLean < config.rotationObliqueStart) return 0f
        val buckets =
            if (tangent.x < 0f) {
                config.negativeObliqueRotationBuckets
            } else {
                config.positiveObliqueRotationBuckets
            }
        return buckets
            .filter { absLean >= it.minAbsLean }
            .maxByOrNull { it.minAbsLean }
            ?.correctionDegrees
            ?: 0f
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

    private data class RingBandCenterPolicy(
        val centerOnMcpToPip: Float,
        val lateralOffsetPx: Float,
    )

    private data class AdjustedRotation(
        val rawDegrees: Float,
        val correctionDegrees: Float,
        val rotationDegrees: Float,
    )

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
        const val RAD_TO_DEG = 180.0 / Math.PI
    }
}
