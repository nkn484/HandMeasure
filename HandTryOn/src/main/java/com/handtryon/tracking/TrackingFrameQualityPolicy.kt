package com.handtryon.tracking

import kotlin.math.sqrt

class TrackingFrameQualityPolicy(
    private val minConfidence: Float = MIN_CONFIDENCE,
    private val minFingerAxisPx: Float = MIN_FINGER_AXIS_PX,
) {
    fun rejectReason(frame: TrackedHandFrame?): TrackingRejectReason? {
        val hand = frame ?: return TrackingRejectReason.HandMissing
        val indices = hand.targetFinger.landmarkIndices()
        val maxIndex = maxOf(indices.mcp, indices.pip, indices.dip)
        if (hand.landmarks.size <= maxIndex) return TrackingRejectReason.MissingLandmarks
        if (hand.confidence < minConfidence) return TrackingRejectReason.LowConfidence

        val mcp = hand.landmarks[indices.mcp]
        val pip = hand.landmarks[indices.pip]
        val dip = hand.landmarks[indices.dip]
        if (!mcp.inside(hand) || !pip.inside(hand) || !dip.inside(hand)) {
            return TrackingRejectReason.FingerOutsideFrame
        }

        val dx = dip.x - mcp.x
        val dy = dip.y - mcp.y
        val axisLength = sqrt(dx * dx + dy * dy)
        if (axisLength < minFingerAxisPx) return TrackingRejectReason.UnstableGeometry
        return null
    }

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

    private data class FingerLandmarkIndices(
        val mcp: Int,
        val pip: Int,
        val dip: Int,
    )

    private companion object {
        const val MIN_CONFIDENCE = 0.22f
        const val MIN_FINGER_AXIS_PX = 9f
    }
}
