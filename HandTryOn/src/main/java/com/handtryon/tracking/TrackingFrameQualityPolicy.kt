package com.handtryon.tracking

import kotlin.math.sqrt

class TrackingFrameQualityPolicy(
    private val minConfidence: Float = MIN_CONFIDENCE,
    private val minFingerAxisPx: Float = MIN_FINGER_AXIS_PX,
    private val edgeMarginRatio: Float = EDGE_MARGIN_RATIO,
) {
    fun assess(frame: TrackedHandFrame?): TrackingFrameQualityAssessment {
        val hand = frame ?: return TrackingRejectReason.HandMissing
            .let { TrackingFrameQualityAssessment(rejectReason = it) }
        val indices = hand.targetFinger.landmarkIndices()
        val maxIndex = maxOf(indices.mcp, indices.pip, indices.dip)
        if (hand.landmarks.size <= maxIndex) return TrackingFrameQualityAssessment(rejectReason = TrackingRejectReason.MissingLandmarks)
        if (hand.confidence < minConfidence) return TrackingFrameQualityAssessment(rejectReason = TrackingRejectReason.LowConfidence)

        val mcp = hand.landmarks[indices.mcp]
        val pip = hand.landmarks[indices.pip]
        val dip = hand.landmarks[indices.dip]
        if (!mcp.inside(hand) || !pip.inside(hand) || !dip.inside(hand)) {
            return TrackingFrameQualityAssessment(rejectReason = TrackingRejectReason.FingerOutsideFrame)
        }

        val dx = dip.x - mcp.x
        val dy = dip.y - mcp.y
        val axisLength = sqrt(dx * dx + dy * dy)
        if (axisLength < minFingerAxisPx) return TrackingFrameQualityAssessment(rejectReason = TrackingRejectReason.UnstableGeometry)
        if (isImpossibleGeometry(mcp = mcp, pip = pip, dip = dip)) {
            return TrackingFrameQualityAssessment(rejectReason = TrackingRejectReason.ImpossibleGeometry)
        }

        var penalty = 0f
        val notes = mutableListOf<String>()
        if (isNearFrameEdge(hand, mcp, pip, dip)) {
            penalty += EDGE_PENALTY
            notes += "finger_near_edge"
        }
        if (isRingFingerCrossing(hand, indices)) {
            penalty += CROSSING_PENALTY
            notes += "ring_finger_crossing"
        }
        if (isExtremeHandScale(hand)) {
            penalty += SCALE_PENALTY
            notes += "hand_scale_outlier"
        }
        return TrackingFrameQualityAssessment(
            rejectReason = null,
            penaltyScore = penalty.coerceIn(0f, 0.9f),
            notes = notes,
        )
    }

    fun rejectReason(frame: TrackedHandFrame?): TrackingRejectReason? = assess(frame).rejectReason

    private fun TrackedLandmark.inside(frame: TrackedHandFrame): Boolean =
        x in 0f..frame.frameWidth.coerceAtLeast(1).toFloat() &&
            y in 0f..frame.frameHeight.coerceAtLeast(1).toFloat()

    private fun TargetFinger.landmarkIndices(): FingerLandmarkIndices =
        when (this) {
            TargetFinger.Thumb -> FingerLandmarkIndices(mcp = 2, pip = 3, dip = 4)
            TargetFinger.Index -> FingerLandmarkIndices(mcp = 5, pip = 6, dip = 7)
            TargetFinger.Middle -> FingerLandmarkIndices(mcp = 9, pip = 10, dip = 11)
            TargetFinger.Ring -> FingerLandmarkIndices(mcp = 13, pip = 14, dip = 15)
            TargetFinger.Little -> FingerLandmarkIndices(mcp = 17, pip = 18, dip = 19)
        }

    private fun isImpossibleGeometry(
        mcp: TrackedLandmark,
        pip: TrackedLandmark,
        dip: TrackedLandmark,
    ): Boolean {
        val forwardX = pip.x - mcp.x
        val forwardY = pip.y - mcp.y
        val distalX = dip.x - pip.x
        val distalY = dip.y - pip.y
        val dot = forwardX * distalX + forwardY * distalY
        return dot <= 0f
    }

    private fun isNearFrameEdge(
        frame: TrackedHandFrame,
        mcp: TrackedLandmark,
        pip: TrackedLandmark,
        dip: TrackedLandmark,
    ): Boolean {
        val margin = frame.frameWidth.coerceAtLeast(1).coerceAtMost(frame.frameHeight.coerceAtLeast(1)) * edgeMarginRatio
        return listOf(mcp, pip, dip).any { point ->
            point.x <= margin ||
                point.y <= margin ||
                point.x >= frame.frameWidth - margin ||
                point.y >= frame.frameHeight - margin
        }
    }

    private fun isRingFingerCrossing(
        frame: TrackedHandFrame,
        ring: FingerLandmarkIndices,
    ): Boolean {
        if (frame.landmarks.size <= 18) return false
        val middlePip = frame.landmarks[10]
        val littlePip = frame.landmarks[18]
        val ringPip = frame.landmarks[ring.pip]
        val ringDip = frame.landmarks[ring.dip]
        val ringWidth = sqrt((ringDip.x - ringPip.x) * (ringDip.x - ringPip.x) + (ringDip.y - ringPip.y) * (ringDip.y - ringPip.y))
        val middleDistance = sqrt((middlePip.x - ringPip.x) * (middlePip.x - ringPip.x) + (middlePip.y - ringPip.y) * (middlePip.y - ringPip.y))
        val littleDistance = sqrt((littlePip.x - ringPip.x) * (littlePip.x - ringPip.x) + (littlePip.y - ringPip.y) * (littlePip.y - ringPip.y))
        return middleDistance < ringWidth * 0.28f || littleDistance < ringWidth * 0.28f
    }

    private fun isExtremeHandScale(frame: TrackedHandFrame): Boolean {
        if (frame.landmarks.size <= 17) return false
        val indexMcp = frame.landmarks[5]
        val littleMcp = frame.landmarks[17]
        val palmSpan = sqrt((littleMcp.x - indexMcp.x) * (littleMcp.x - indexMcp.x) + (littleMcp.y - indexMcp.y) * (littleMcp.y - indexMcp.y))
        val minDimension = frame.frameWidth.coerceAtLeast(1).coerceAtMost(frame.frameHeight.coerceAtLeast(1)).toFloat()
        val ratio = palmSpan / minDimension
        return ratio < 0.08f || ratio > 0.72f
    }

    private data class FingerLandmarkIndices(
        val mcp: Int,
        val pip: Int,
        val dip: Int,
    )

    private companion object {
        const val MIN_CONFIDENCE = 0.22f
        const val MIN_FINGER_AXIS_PX = 9f
        const val EDGE_MARGIN_RATIO = 0.08f
        const val EDGE_PENALTY = 0.22f
        const val CROSSING_PENALTY = 0.26f
        const val SCALE_PENALTY = 0.25f
    }
}
