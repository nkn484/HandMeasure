package com.handtryon.coreengine.validation

import com.handtryon.coreengine.model.TryOnPlacement
import com.handtryon.coreengine.model.TryOnTrackingState
import com.handtryon.coreengine.model.TryOnUpdateAction

data class TryOnImageAugmentation(
    val id: String,
    val rotationDegrees: Float = 0f,
    val brightnessDelta: Float = 0f,
    val contrastScale: Float = 1f,
) {
    init {
        require(id.isNotBlank()) { "Augmentation id must not be blank." }
        require(rotationDegrees in -15f..15f) { "Validation rotation should stay within a small realism band." }
        require(brightnessDelta in -0.35f..0.35f) { "Brightness delta must stay within validation bounds." }
        require(contrastScale in 0.65f..1.45f) { "Contrast scale must stay within validation bounds." }
    }

    val isIdentity: Boolean
        get() = rotationDegrees == 0f && brightnessDelta == 0f && contrastScale == 1f

    companion object {
        val Identity = TryOnImageAugmentation(id = "identity")

        val DefaultRobustnessSet =
            listOf(
                Identity,
                TryOnImageAugmentation(id = "rotate_left_3", rotationDegrees = -3f),
                TryOnImageAugmentation(id = "rotate_right_3", rotationDegrees = 3f),
                TryOnImageAugmentation(id = "brightness_down", brightnessDelta = -0.08f, contrastScale = 0.96f),
                TryOnImageAugmentation(id = "brightness_up", brightnessDelta = 0.08f, contrastScale = 1.04f),
                TryOnImageAugmentation(id = "contrast_down", contrastScale = 0.88f),
                TryOnImageAugmentation(id = "contrast_up", contrastScale = 1.12f),
            )
    }
}

data class TryOnValidationFrameId(
    val fixtureId: String,
    val frameIndex: Int,
    val augmentationId: String = TryOnImageAugmentation.Identity.id,
)

data class TryOnExpectedRingZone(
    val centerX: Float,
    val centerY: Float,
    val widthPx: Float,
    val rotationDegrees: Float,
)

data class TryOnValidationFrame(
    val id: TryOnValidationFrameId,
    val timeSec: Double,
    val visibleFinger: Boolean,
    val expectedZone: TryOnExpectedRingZone,
    val annotationQuality: String = "",
    val augmentation: TryOnImageAugmentation = TryOnImageAugmentation.Identity,
)

data class TryOnFramePlacementMetrics(
    val centerErrorPx: Float,
    val centerErrorRatio: Float,
    val widthErrorPx: Float,
    val widthErrorRatio: Float,
    val rotationErrorDeg: Float,
    val pass: Boolean,
    val notes: List<String> = emptyList(),
)

data class TryOnTemporalSample(
    val timestampMs: Long,
    val placement: TryOnPlacement?,
    val qualityScore: Float,
    val trackingState: TryOnTrackingState,
    val updateAction: TryOnUpdateAction,
)

data class TryOnTemporalJitterConfig(
    val maxCenterJitterRatio: Float = 0.12f,
    val maxScaleJitterRatio: Float = 0.12f,
    val maxRotationJitterDegrees: Float = 8f,
    val minEffectiveUpdateHz: Float = 12f,
    val lowQualityThreshold: Float = 0.38f,
)

data class TryOnTemporalJitterMetrics(
    val sampleCount: Int,
    val measuredSampleCount: Int,
    val durationMs: Long,
    val effectiveUpdateHz: Float,
    val avgCenterStepRatio: Float,
    val maxCenterStepRatio: Float,
    val avgScaleStepRatio: Float,
    val maxScaleStepRatio: Float,
    val avgRotationStepDeg: Float,
    val maxRotationStepDeg: Float,
    val hiddenFrames: Int,
    val frozenFrames: Int,
    val lowQualityFrames: Int,
    val warnings: List<String>,
) {
    val stableEnough: Boolean
        get() = warnings.isEmpty()
}
