package com.handtryon.coreengine.validation

import com.handtryon.coreengine.model.TryOnPlacement
import com.handtryon.coreengine.model.TryOnUpdateAction
import kotlin.math.abs
import kotlin.math.sqrt

class TryOnTemporalQualityModel(
    private val config: TryOnTemporalJitterConfig = TryOnTemporalJitterConfig(),
) {
    fun evaluate(samples: List<TryOnTemporalSample>): TryOnTemporalJitterMetrics {
        if (samples.isEmpty()) {
            return TryOnTemporalJitterMetrics(
                sampleCount = 0,
                measuredSampleCount = 0,
                durationMs = 0L,
                effectiveUpdateHz = 0f,
                avgCenterStepRatio = 0f,
                maxCenterStepRatio = 0f,
                avgScaleStepRatio = 0f,
                maxScaleStepRatio = 0f,
                avgRotationStepDeg = 0f,
                maxRotationStepDeg = 0f,
                hiddenFrames = 0,
                frozenFrames = 0,
                lowQualityFrames = 0,
                warnings = listOf("no_samples"),
            )
        }

        val ordered = samples.sortedBy { it.timestampMs }
        val measured = ordered.filter { it.placement != null }
        var previousPlacement: TryOnPlacement? = null
        var previousTimestamp: Long? = null
        var updateCount = 0
        var centerSum = 0f
        var centerMax = 0f
        var scaleSum = 0f
        var scaleMax = 0f
        var rotationSum = 0f
        var rotationMax = 0f

        for (sample in measured) {
            val placement = sample.placement ?: continue
            val previous = previousPlacement
            if (previous != null) {
                val centerRatio = centerStepRatio(previous = previous, current = placement)
                val scaleRatio = abs(placement.ringWidthPx - previous.ringWidthPx) / previous.ringWidthPx.coerceAtLeast(1f)
                val rotationStep = abs(normalizeRotationDelta(placement.rotationDegrees - previous.rotationDegrees))
                centerSum += centerRatio
                centerMax = maxOf(centerMax, centerRatio)
                scaleSum += scaleRatio
                scaleMax = maxOf(scaleMax, scaleRatio)
                rotationSum += rotationStep
                rotationMax = maxOf(rotationMax, rotationStep)
                updateCount += 1
            }
            previousPlacement = placement
            previousTimestamp = sample.timestampMs
        }

        val firstTimestamp = measured.firstOrNull()?.timestampMs ?: ordered.first().timestampMs
        val lastTimestamp = previousTimestamp ?: ordered.last().timestampMs
        val durationMs = (lastTimestamp - firstTimestamp).coerceAtLeast(0L)
        val effectiveUpdateHz =
            if (durationMs > 0L && measured.size > 1) {
                ((measured.size - 1).toFloat() * 1000f) / durationMs.toFloat()
            } else {
                0f
            }
        val safeUpdateCount = updateCount.coerceAtLeast(1)
        val avgCenter = centerSum / safeUpdateCount
        val avgScale = scaleSum / safeUpdateCount
        val avgRotation = rotationSum / safeUpdateCount
        val warnings = mutableListOf<String>()
        if (measured.size < 2) warnings += "not_enough_measured_samples"
        if (centerMax > config.maxCenterJitterRatio) warnings += "center_jitter_high"
        if (scaleMax > config.maxScaleJitterRatio) warnings += "scale_jitter_high"
        if (rotationMax > config.maxRotationJitterDegrees) warnings += "rotation_jitter_high"
        if (effectiveUpdateHz > 0f && effectiveUpdateHz < config.minEffectiveUpdateHz) warnings += "update_rate_low"

        return TryOnTemporalJitterMetrics(
            sampleCount = ordered.size,
            measuredSampleCount = measured.size,
            durationMs = durationMs,
            effectiveUpdateHz = effectiveUpdateHz,
            avgCenterStepRatio = avgCenter,
            maxCenterStepRatio = centerMax,
            avgScaleStepRatio = avgScale,
            maxScaleStepRatio = scaleMax,
            avgRotationStepDeg = avgRotation,
            maxRotationStepDeg = rotationMax,
            hiddenFrames = ordered.count { it.updateAction == TryOnUpdateAction.Hide || it.placement == null },
            frozenFrames = ordered.count {
                it.updateAction == TryOnUpdateAction.FreezeScaleRotation ||
                    it.updateAction == TryOnUpdateAction.HoldLastPlacement
            },
            lowQualityFrames = ordered.count { it.qualityScore < config.lowQualityThreshold },
            warnings = warnings,
        )
    }

    private fun centerStepRatio(
        previous: TryOnPlacement,
        current: TryOnPlacement,
    ): Float {
        val dx = current.centerX - previous.centerX
        val dy = current.centerY - previous.centerY
        return sqrt(dx * dx + dy * dy) / previous.ringWidthPx.coerceAtLeast(1f)
    }

    private fun normalizeRotationDelta(delta: Float): Float {
        var adjusted = delta
        while (adjusted > 180f) adjusted -= 360f
        while (adjusted < -180f) adjusted += 360f
        return adjusted
    }
}
