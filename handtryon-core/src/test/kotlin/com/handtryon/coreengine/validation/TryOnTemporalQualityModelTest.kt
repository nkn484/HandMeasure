package com.handtryon.coreengine.validation

import com.google.common.truth.Truth.assertThat
import com.handtryon.coreengine.model.TryOnPlacement
import com.handtryon.coreengine.model.TryOnTrackingState
import com.handtryon.coreengine.model.TryOnUpdateAction
import org.junit.Test

class TryOnTemporalQualityModelTest {
    @Test
    fun evaluate_marksSteadySequenceStable() {
        val metrics =
            TryOnTemporalQualityModel().evaluate(
                listOf(
                    sample(0L, placement(100f, 100f, 80f, 4f)),
                    sample(50L, placement(102f, 101f, 80.8f, 5f)),
                    sample(100L, placement(103f, 101.5f, 81.2f, 5.5f)),
                    sample(150L, placement(104f, 102f, 81.4f, 6f)),
                ),
            )

        assertThat(metrics.stableEnough).isTrue()
        assertThat(metrics.effectiveUpdateHz).isGreaterThan(12f)
        assertThat(metrics.maxRotationStepDeg).isLessThan(8f)
    }

    @Test
    fun evaluate_flagsCenterScaleAndRotationJitter() {
        val metrics =
            TryOnTemporalQualityModel().evaluate(
                listOf(
                    sample(0L, placement(100f, 100f, 80f, 4f)),
                    sample(50L, placement(140f, 105f, 96f, 22f)),
                    sample(100L, placement(92f, 98f, 70f, -4f)),
                ),
            )

        assertThat(metrics.warnings).contains("center_jitter_high")
        assertThat(metrics.warnings).contains("scale_jitter_high")
        assertThat(metrics.warnings).contains("rotation_jitter_high")
    }

    @Test
    fun imageAugmentation_defaultSetIncludesSmallPhotometricAndRotationVariants() {
        val ids = TryOnImageAugmentation.DefaultRobustnessSet.map { it.id }

        assertThat(ids).containsAtLeast("identity", "rotate_left_3", "rotate_right_3", "brightness_down", "contrast_up")
    }

    private fun sample(
        timestampMs: Long,
        placement: TryOnPlacement?,
        qualityScore: Float = 0.8f,
    ): TryOnTemporalSample =
        TryOnTemporalSample(
            timestampMs = timestampMs,
            placement = placement,
            qualityScore = qualityScore,
            trackingState = TryOnTrackingState.Locked,
            updateAction = TryOnUpdateAction.Update,
        )

    private fun placement(
        x: Float,
        y: Float,
        width: Float,
        rotation: Float,
    ): TryOnPlacement =
        TryOnPlacement(
            centerX = x,
            centerY = y,
            ringWidthPx = width,
            rotationDegrees = rotation,
        )
}
