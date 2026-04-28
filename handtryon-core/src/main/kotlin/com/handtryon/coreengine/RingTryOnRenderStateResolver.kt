package com.handtryon.coreengine

import com.handtryon.coreengine.model.FingerOccluderState
import com.handtryon.coreengine.model.RingFingerPose
import com.handtryon.coreengine.model.RingFitState
import com.handtryon.coreengine.model.RingTransform3D
import com.handtryon.coreengine.model.TryOnInputQuality
import com.handtryon.coreengine.model.TryOnRenderState3D
import com.handtryon.coreengine.model.TryOnVec3

class RingTryOnRenderStateResolver(
    private val viewportWidthAtDepthMeters: Float = DEFAULT_VIEWPORT_WIDTH_AT_DEPTH_METERS,
) {
    fun resolve(
        fingerPose: RingFingerPose,
        fitState: RingFitState,
        quality: TryOnInputQuality,
        frameWidth: Int,
        frameHeight: Int,
    ): TryOnRenderState3D {
        val safeWidth = frameWidth.coerceAtLeast(1).toFloat()
        val safeHeight = frameHeight.coerceAtLeast(1).toFloat()
        val normalizedX = (fingerPose.centerPx.x / safeWidth - 0.5f).coerceIn(-0.5f, 0.5f)
        val normalizedY = (fingerPose.centerPx.y / safeHeight - 0.5f).coerceIn(-0.5f, 0.5f)
        val modelWidthMeters = (fitState.modelWidthMm / MILLIMETERS_PER_METER).coerceAtLeast(1e-4f)
        val targetWidthMeters = (fitState.targetWidthPx / safeWidth) * viewportWidthAtDepthMeters
        val renderScale = (targetWidthMeters / modelWidthMeters).coerceIn(MIN_RENDER_SCALE, MAX_RENDER_SCALE)

        return TryOnRenderState3D(
            ringTransform =
                RingTransform3D(
                    positionMeters =
                        TryOnVec3(
                            x = normalizedX * viewportWidthAtDepthMeters,
                            y = -normalizedY * viewportWidthAtDepthMeters * (safeHeight / safeWidth),
                            z = -fitState.depthMeters,
                        ),
                    rotationDegrees =
                        TryOnVec3(
                            x = RING_PLANE_PITCH_DEGREES + fingerPose.rollDegrees,
                            y = 0f,
                            z = RING_AXIS_YAW_OFFSET_DEGREES - fingerPose.rotationDegrees,
                        ),
                    scale = TryOnVec3(renderScale, renderScale, renderScale),
                ),
            fingerPose = fingerPose,
            fitState = fitState,
            fingerOccluder =
                FingerOccluderState(
                    startPx = fingerPose.occluderStartPx,
                    endPx = fingerPose.occluderEndPx,
                    radiusPx = (fingerPose.fingerWidthPx * FINGER_OCCLUDER_RADIUS_RATIO).coerceAtLeast(MIN_OCCLUDER_RADIUS_PX),
                    normalHintPx = fingerPose.normalHintPx,
                    confidence = fingerPose.confidence,
                ),
            quality = quality,
        )
    }

    private companion object {
        const val DEFAULT_VIEWPORT_WIDTH_AT_DEPTH_METERS = 0.32f
        const val MILLIMETERS_PER_METER = 1000f
        const val MIN_RENDER_SCALE = 0.2f
        const val MAX_RENDER_SCALE = 4.0f
        const val RING_PLANE_PITCH_DEGREES = 90f
        const val RING_AXIS_YAW_OFFSET_DEGREES = 90f
        const val FINGER_OCCLUDER_RADIUS_RATIO = 0.5f
        const val MIN_OCCLUDER_RADIUS_PX = 8f
    }
}
