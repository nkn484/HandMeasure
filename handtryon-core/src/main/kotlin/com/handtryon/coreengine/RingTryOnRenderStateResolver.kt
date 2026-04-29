package com.handtryon.coreengine

import com.handtryon.coreengine.model.FingerOccluderState
import com.handtryon.coreengine.model.RingFingerPose
import com.handtryon.coreengine.model.RingFitState
import com.handtryon.coreengine.model.RingTransform3D
import com.handtryon.coreengine.model.TryOnRenderPass
import com.handtryon.coreengine.model.TryOnRenderQuality
import com.handtryon.coreengine.model.TryOnInputQuality
import com.handtryon.coreengine.model.TryOnRenderState3D
import com.handtryon.coreengine.model.TryOnUpdateAction
import com.handtryon.coreengine.model.TryOnVisualQaSnapshot
import com.handtryon.coreengine.model.TryOnVec3
import kotlin.math.abs

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
        val occluderRadiusPx = (fingerPose.fingerWidthPx * FINGER_OCCLUDER_RADIUS_RATIO).coerceAtLeast(MIN_OCCLUDER_RADIUS_PX)
        val occluderDepthMeters = fitState.depthMeters + OCCLUDER_DEPTH_BIAS_METERS
        val visualQa =
            visualQa(
                fingerPose = fingerPose,
                fitState = fitState,
                occluderRadiusPx = occluderRadiusPx,
                occluderDepthMeters = occluderDepthMeters,
                renderScale = renderScale,
            )

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
                    radiusPx = occluderRadiusPx,
                    normalHintPx = fingerPose.normalHintPx,
                depthMeters = occluderDepthMeters,
                confidence = fingerPose.confidence,
            ),
            quality = quality,
            renderPasses = renderPasses(fingerPose, quality, visualQa),
            renderQuality = renderQuality(quality, visualQa),
            visualQa = visualQa,
        )
    }

    private fun renderPasses(
        fingerPose: RingFingerPose,
        quality: TryOnInputQuality,
        visualQa: TryOnVisualQaSnapshot,
    ): List<TryOnRenderPass> =
        if (
            quality.updateAction == TryOnUpdateAction.Hide ||
            !quality.landmarkUsable ||
            fingerPose.confidence < MIN_RENDER_CONFIDENCE ||
            !visualQa.passesBasicGate
        ) {
            emptyList()
        } else {
            listOf(TryOnRenderPass.FingerDepthPrepass, TryOnRenderPass.RingModel)
        }

    private fun visualQa(
        fingerPose: RingFingerPose,
        fitState: RingFitState,
        occluderRadiusPx: Float,
        occluderDepthMeters: Float,
        renderScale: Float,
    ): TryOnVisualQaSnapshot {
        val ringWidth = fitState.targetWidthPx.coerceAtLeast(1f)
        val attachmentRatio =
            abs(fingerPose.centerPx.x - midpoint(fingerPose.occluderStartPx.x, fingerPose.occluderEndPx.x)) / ringWidth
        val occluderRadiusRatio = occluderRadiusPx / ringWidth
        val warnings = mutableListOf<String>()
        if (occluderRadiusRatio !in MIN_OCCLUDER_RADIUS_RATIO..MAX_OCCLUDER_RADIUS_RATIO) {
            warnings += "occluder_radius_ratio_out_of_range"
        }
        if (abs(occluderDepthMeters - fitState.depthMeters) > MAX_OCCLUDER_DEPTH_DELTA_METERS) {
            warnings += "occluder_depth_not_aligned"
        }
        if (renderScale == MIN_RENDER_SCALE || renderScale == MAX_RENDER_SCALE) warnings += "render_scale_clamped"
        return TryOnVisualQaSnapshot(
            attachmentRatio = attachmentRatio,
            occluderRadiusToRingWidthRatio = occluderRadiusRatio,
            occluderDepthMeters = occluderDepthMeters,
            ringDepthMeters = fitState.depthMeters,
            renderScale = renderScale,
            warnings = warnings,
        )
    }

    private fun midpoint(
        a: Float,
        b: Float,
    ): Float = a + (b - a) * 0.5f

    private fun renderQuality(
        quality: TryOnInputQuality,
        visualQa: TryOnVisualQaSnapshot,
    ): TryOnRenderQuality =
        when {
            quality.updateAction == TryOnUpdateAction.Hide || !quality.landmarkUsable -> TryOnRenderQuality.Hidden
            quality.qualityScore >= 0.66f && visualQa.passesBasicGate -> TryOnRenderQuality.Stable
            else -> TryOnRenderQuality.Degraded
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
        const val OCCLUDER_DEPTH_BIAS_METERS = 0.002f
        const val MAX_OCCLUDER_DEPTH_DELTA_METERS = 0.01f
        const val MIN_OCCLUDER_RADIUS_RATIO = 0.45f
        const val MAX_OCCLUDER_RADIUS_RATIO = 2.40f
        const val MIN_RENDER_CONFIDENCE = 0.18f
    }
}
