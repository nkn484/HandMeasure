package com.handtryon.core

import com.handtryon.domain.FingerAnchor
import com.handtryon.domain.HandPoseSnapshot
import kotlin.math.atan2
import kotlin.math.sqrt

class DefaultFingerAnchorProvider : FingerAnchorProvider {
    override fun createAnchor(pose: HandPoseSnapshot): FingerAnchor? {
        if (pose.landmarks.size <= RING_PIP_INDEX) return null
        val mcp = pose.landmarks[RING_MCP_INDEX]
        val pip = pose.landmarks[RING_PIP_INDEX]
        val dx = pip.x - mcp.x
        val dy = pip.y - mcp.y
        val length = sqrt(dx * dx + dy * dy)
        if (length < 5f) return null

        val centerX = mcp.x + dx * 0.54f
        val centerY = mcp.y + dy * 0.54f
        val angle = (atan2(dy, dx) * 180f / Math.PI).toFloat()
        val widthPx = (length * 1.34f).coerceAtLeast(18f)
        val confidence = (pose.confidence * (length / 60f).coerceIn(0.45f, 1f)).coerceIn(0f, 1f)
        if (confidence < 0.22f) return null

        return FingerAnchor(
            centerX = centerX,
            centerY = centerY,
            angleDegrees = angle,
            fingerWidthPx = widthPx,
            confidence = confidence,
            timestampMs = pose.timestampMs,
        )
    }

    private companion object {
        const val RING_MCP_INDEX = 13
        const val RING_PIP_INDEX = 14
    }
}
