package com.handtryon.coreengine

import com.handtryon.coreengine.model.RingFingerPose
import com.handtryon.coreengine.model.RingFitSource
import com.handtryon.coreengine.model.RingFitState
import com.handtryon.coreengine.model.TryOnMeasurementSnapshot

class RingFitSolver(
    private val defaultRingDiameterMm: Float = DEFAULT_RING_DIAMETER_MM,
    private val defaultDepthMeters: Float = DEFAULT_DEPTH_METERS,
    private val virtualFocalPx: Float = DEFAULT_VIRTUAL_FOCAL_PX,
) {
    fun solve(
        fingerPose: RingFingerPose,
        measurement: TryOnMeasurementSnapshot?,
        selectedDiameterMm: Float? = null,
        modelWidthMm: Float? = null,
    ): RingFitState {
        val measuredDiameter = measurement?.equivalentDiameterMm?.takeIf { measurement.usable && it > 0f }
        val measuredFingerWidth = measurement?.fingerWidthMm?.takeIf { measurement.usable && it > 0f }
        val resolvedModelWidthMm = modelWidthMm?.takeIf { it > 0f } ?: defaultRingDiameterMm
        val ringOuterDiameterMm =
            measuredDiameter
                ?: selectedDiameterMm?.takeIf { it > 0f }
                ?: estimateVisualDiameterMm(fingerPose)
                ?: defaultRingDiameterMm
        val source =
            when {
                measuredDiameter != null -> RingFitSource.Measured
                selectedDiameterMm?.takeIf { it > 0f } != null -> RingFitSource.SelectedSize
                estimateVisualDiameterMm(fingerPose) != null -> RingFitSource.VisualEstimate
                else -> RingFitSource.AssetDefault
            }
        val targetWidthPx =
            when {
                measuredFingerWidth != null && measuredDiameter != null ->
                    fingerPose.fingerWidthPx * (measuredDiameter / measuredFingerWidth).coerceIn(MIN_WIDTH_RATIO, MAX_WIDTH_RATIO)
                else -> fingerPose.fingerWidthPx * VISUAL_RING_TO_FINGER_WIDTH_RATIO
            }.coerceAtLeast(MIN_TARGET_WIDTH_PX)
        val depthMeters =
            (virtualFocalPx * ringOuterDiameterMm / targetWidthPx.coerceAtLeast(1f) / MILLIMETERS_PER_METER)
                .coerceIn(MIN_DEPTH_METERS, MAX_DEPTH_METERS)
        val confidence =
            (
                fingerPose.confidence *
                    when (source) {
                        RingFitSource.Measured -> measurement?.confidence ?: 0f
                        RingFitSource.SelectedSize -> SELECTED_SIZE_CONFIDENCE
                        RingFitSource.VisualEstimate -> VISUAL_ESTIMATE_CONFIDENCE
                        RingFitSource.AssetDefault -> DEFAULT_ASSET_CONFIDENCE
                    }
            ).coerceIn(0f, 1f)

        return RingFitState(
            ringOuterDiameterMm = ringOuterDiameterMm,
            ringInnerDiameterMm = measuredDiameter,
            modelWidthMm = resolvedModelWidthMm,
            targetWidthPx = targetWidthPx,
            depthMeters = depthMeters,
            modelScale = (ringOuterDiameterMm / resolvedModelWidthMm).coerceIn(MIN_MODEL_SCALE, MAX_MODEL_SCALE),
            confidence = confidence,
            source = source,
        )
    }

    private fun estimateVisualDiameterMm(fingerPose: RingFingerPose): Float? =
        fingerPose.fingerWidthPx
            .takeIf { it >= MIN_TARGET_WIDTH_PX }
            ?.let { defaultRingDiameterMm }

    private companion object {
        const val DEFAULT_RING_DIAMETER_MM = 20.4f
        const val DEFAULT_DEPTH_METERS = 0.42f
        const val DEFAULT_VIRTUAL_FOCAL_PX = 930f
        const val MILLIMETERS_PER_METER = 1000f
        const val VISUAL_RING_TO_FINGER_WIDTH_RATIO = 1.18f
        const val MIN_WIDTH_RATIO = 0.72f
        const val MAX_WIDTH_RATIO = 1.45f
        const val MIN_TARGET_WIDTH_PX = 18f
        const val MIN_DEPTH_METERS = 0.14f
        const val MAX_DEPTH_METERS = 0.95f
        const val MIN_MODEL_SCALE = 0.45f
        const val MAX_MODEL_SCALE = 1.65f
        const val SELECTED_SIZE_CONFIDENCE = 0.72f
        const val VISUAL_ESTIMATE_CONFIDENCE = 0.55f
        const val DEFAULT_ASSET_CONFIDENCE = 0.38f
    }
}
