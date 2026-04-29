package com.handtryon.coreengine.fit

import com.handtryon.coreengine.model.RingFingerPose
import com.handtryon.coreengine.model.RingFitDiagnostics
import com.handtryon.coreengine.model.RingFitSource
import com.handtryon.coreengine.model.RingFitState
import com.handtryon.coreengine.model.TryOnMeasurementSnapshot

data class RingFitSolverConfig(
    val defaultRingDiameterMm: Float = 20.4f,
    val defaultDepthMeters: Float = 0.42f,
    val virtualFocalPx: Float = 930f,
    val visualRingToFingerWidthBaseRatio: Float = 0.36f,
    val visualRingReferenceFingerWidthPx: Float = 200f,
    val visualRingWidthTaperPerPx: Float = 0.001f,
    val minVisualRingToFingerWidthRatio: Float = 0.22f,
    val maxVisualRingToFingerWidthRatio: Float = 0.46f,
    val minWidthRatio: Float = 0.72f,
    val maxWidthRatio: Float = 1.45f,
    val minTargetWidthPx: Float = 18f,
    val minDepthMeters: Float = 0.14f,
    val maxDepthMeters: Float = 0.95f,
    val minModelScale: Float = 0.45f,
    val maxModelScale: Float = 1.65f,
    val selectedSizeConfidence: Float = 0.72f,
    val visualEstimateConfidence: Float = 0.55f,
    val defaultAssetConfidence: Float = 0.38f,
)

class RingFitSolver(
    private val config: RingFitSolverConfig = RingFitSolverConfig(),
) {
    fun solve(
        fingerPose: RingFingerPose,
        measurement: TryOnMeasurementSnapshot?,
        selectedDiameterMm: Float? = null,
        modelWidthMm: Float? = null,
    ): RingFitState {
        val measuredDiameter = measurement?.equivalentDiameterMm?.takeIf { measurement.usable && it > 0f }
        val measuredFingerWidth = measurement?.fingerWidthMm?.takeIf { measurement.usable && it > 0f }
        val visualDiameter = estimateVisualDiameterMm(fingerPose)
        val resolvedModelWidthMm = modelWidthMm?.takeIf { it > 0f } ?: config.defaultRingDiameterMm
        val ringOuterDiameterMm =
            measuredDiameter
                ?: selectedDiameterMm?.takeIf { it > 0f }
                ?: visualDiameter
                ?: config.defaultRingDiameterMm
        val source =
            when {
                measuredDiameter != null -> RingFitSource.Measured
                selectedDiameterMm?.takeIf { it > 0f } != null -> RingFitSource.SelectedSize
                visualDiameter != null -> RingFitSource.VisualEstimate
                else -> RingFitSource.AssetDefault
            }
        val visualRatio = visualRingToFingerWidthRatio(fingerPose.fingerWidthPx)
        val measuredWidthRatio =
            if (measuredFingerWidth != null && measuredDiameter != null) {
                measuredDiameter / measuredFingerWidth
            } else {
                null
            }
        val unclampedTargetWidthPx =
            when {
                measuredWidthRatio != null -> fingerPose.fingerWidthPx * measuredWidthRatio.coerceIn(config.minWidthRatio, config.maxWidthRatio)
                else -> fingerPose.fingerWidthPx * visualRatio
            }
        val targetWidthPx = unclampedTargetWidthPx.coerceAtLeast(config.minTargetWidthPx)
        val unclampedDepthMeters =
            config.virtualFocalPx * ringOuterDiameterMm / targetWidthPx.coerceAtLeast(1f) / MILLIMETERS_PER_METER
        val depthMeters = unclampedDepthMeters.coerceIn(config.minDepthMeters, config.maxDepthMeters)
        val unclampedModelScale = ringOuterDiameterMm / resolvedModelWidthMm
        val confidence =
            (
                fingerPose.confidence *
                    when (source) {
                        RingFitSource.Measured -> measurement?.confidence ?: 0f
                        RingFitSource.SelectedSize -> config.selectedSizeConfidence
                        RingFitSource.VisualEstimate -> config.visualEstimateConfidence
                        RingFitSource.AssetDefault -> config.defaultAssetConfidence
                    }
            ).coerceIn(0f, 1f)

        return RingFitState(
            ringOuterDiameterMm = ringOuterDiameterMm,
            ringInnerDiameterMm = measuredDiameter,
            modelWidthMm = resolvedModelWidthMm,
            targetWidthPx = targetWidthPx,
            depthMeters = depthMeters,
            modelScale = unclampedModelScale.coerceIn(config.minModelScale, config.maxModelScale),
            confidence = confidence,
            source = source,
            diagnostics =
                RingFitDiagnostics(
                    visualRingToFingerWidthRatio = visualRatio,
                    measuredWidthRatio = measuredWidthRatio,
                    unclampedTargetWidthPx = unclampedTargetWidthPx,
                    unclampedDepthMeters = unclampedDepthMeters,
                    unclampedModelScale = unclampedModelScale,
                    source = source,
                ),
        )
    }

    private fun estimateVisualDiameterMm(fingerPose: RingFingerPose): Float? =
        fingerPose.fingerWidthPx
            .takeIf { it >= config.minTargetWidthPx }
            ?.let { config.defaultRingDiameterMm }

    private fun visualRingToFingerWidthRatio(fingerWidthPx: Float): Float =
        (
            config.visualRingToFingerWidthBaseRatio -
                (fingerWidthPx - config.visualRingReferenceFingerWidthPx) * config.visualRingWidthTaperPerPx
        )
            .coerceIn(config.minVisualRingToFingerWidthRatio, config.maxVisualRingToFingerWidthRatio)

    private companion object {
        const val MILLIMETERS_PER_METER = 1000f
    }
}
