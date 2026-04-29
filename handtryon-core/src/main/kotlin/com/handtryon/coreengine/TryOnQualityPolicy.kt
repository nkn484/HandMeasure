package com.handtryon.coreengine

import com.handtryon.coreengine.model.TryOnMode
import com.handtryon.coreengine.model.TryOnPlacementValidation
import com.handtryon.coreengine.model.TryOnTrackingState
import com.handtryon.coreengine.model.TryOnUpdateAction
import com.handtryon.coreengine.validation.TryOnTemporalJitterMetrics

data class TryOnQualitySignals(
    val mode: TryOnMode,
    val landmarkUsable: Boolean,
    val measurementUsable: Boolean,
    val landmarkConfidence: Float,
    val measurementConfidence: Float,
    val usedLastGoodAnchor: Boolean,
    val anchorJumpPx: Float,
    val placementJumpRatio: Float,
    val validation: TryOnPlacementValidation,
    val trackingState: TryOnTrackingState,
    val hasPreviousPlacement: Boolean,
    val temporalMetrics: TryOnTemporalJitterMetrics? = null,
)

data class TryOnQualityEvaluation(
    val score: Float,
    val updateAction: TryOnUpdateAction,
    val hints: List<String>,
)

data class TryOnQualityPolicyConfig(
    val landmarkWeight: Float = 0.52f,
    val measurementWeight: Float = 0.18f,
    val jumpWeight: Float = 0.16f,
    val validationWeight: Float = 0.06f,
    val temporalWeight: Float = 0.08f,
    val fallbackPenalty: Float = 0.12f,
    val multiValidationPenalty: Float = 0.28f,
    val singleValidationPenalty: Float = 0.18f,
)

class TryOnQualityPolicy(
    private val config: TryOnQualityPolicyConfig = TryOnQualityPolicyConfig(),
) {
    fun score(signals: TryOnQualitySignals): Float {
        val landmarkComponent =
            if (signals.landmarkUsable) {
                signals.landmarkConfidence.coerceIn(0f, 1f)
            } else {
                0f
            }
        val measurementComponent =
            if (signals.mode == TryOnMode.Measured && signals.measurementUsable) {
                signals.measurementConfidence.coerceIn(0f, 1f)
            } else {
                0.5f
            }
        val jumpPenalty = ((signals.anchorJumpPx / 52f) + signals.placementJumpRatio).coerceIn(0f, 1.4f)
        val validationPenalty =
            when {
                signals.validation.isPlacementUsable -> 0f
                signals.validation.notes.size >= 2 -> config.multiValidationPenalty
                else -> config.singleValidationPenalty
            }
        val fallbackPenalty = if (signals.usedLastGoodAnchor) config.fallbackPenalty else 0f
        val temporalComponent = temporalComponent(signals.temporalMetrics)
        val rawScore =
            landmarkComponent * config.landmarkWeight +
                measurementComponent * config.measurementWeight +
                (1f - jumpPenalty).coerceIn(0f, 1f) * config.jumpWeight +
                (if (signals.validation.isPlacementUsable) 1f else 0.5f) * config.validationWeight +
                temporalComponent * config.temporalWeight -
                validationPenalty -
                fallbackPenalty
        return rawScore.coerceIn(0f, 1f)
    }

    fun evaluate(signals: TryOnQualitySignals): TryOnQualityEvaluation {
        val score = score(signals)
        val action = chooseUpdateAction(signals, score)
        return TryOnQualityEvaluation(
            score = score,
            updateAction = action,
            hints = buildHints(signals, score),
        )
    }

    private fun chooseUpdateAction(
        signals: TryOnQualitySignals,
        score: Float,
    ): TryOnUpdateAction {
        if (signals.mode == TryOnMode.Manual) return TryOnUpdateAction.Update
        if (!signals.hasPreviousPlacement) return TryOnUpdateAction.Update
        if (!signals.validation.isPlacementUsable && score < 0.5f) return TryOnUpdateAction.HoldLastPlacement
        return when (signals.trackingState) {
            TryOnTrackingState.Searching -> {
                if (score >= 0.62f) TryOnUpdateAction.Update else TryOnUpdateAction.Hide
            }
            TryOnTrackingState.Candidate -> {
                when {
                    score >= 0.68f -> TryOnUpdateAction.Update
                    score >= 0.5f -> TryOnUpdateAction.FreezeScaleRotation
                    else -> TryOnUpdateAction.HoldLastPlacement
                }
            }
            TryOnTrackingState.Locked -> {
                when {
                    score >= 0.7f && signals.validation.isPlacementUsable -> TryOnUpdateAction.Update
                    score >= 0.52f -> TryOnUpdateAction.FreezeScaleRotation
                    score >= 0.36f -> TryOnUpdateAction.HoldLastPlacement
                    else -> TryOnUpdateAction.Recover
                }
            }
            TryOnTrackingState.Recovering -> {
                when {
                    score >= 0.66f && signals.validation.isPlacementUsable -> TryOnUpdateAction.Update
                    score >= 0.42f -> TryOnUpdateAction.Recover
                    else -> TryOnUpdateAction.HoldLastPlacement
                }
            }
        }
    }

    private fun buildHints(
        signals: TryOnQualitySignals,
        score: Float,
    ): List<String> {
        val hints = mutableListOf<String>()
        if (!signals.landmarkUsable || signals.landmarkConfidence < 0.45f) hints += "hand_unstable"
        if (signals.usedLastGoodAnchor) hints += "using_last_anchor"
        if (signals.trackingState == TryOnTrackingState.Recovering) hints += "tracking_recovering"
        if (signals.anchorJumpPx > 34f || signals.placementJumpRatio > 0.5f) hints += "movement_fast"
        if (signals.validation.notes.contains("far_from_anchor")) hints += "ring_finger_visibility_low"
        if (signals.validation.notes.contains("rotation_jump_high")) hints += "rotation_unstable"
        signals.temporalMetrics?.warnings.orEmpty().forEach { warning ->
            when (warning) {
                "center_jitter_high" -> hints += "center_jitter_high"
                "scale_jitter_high" -> hints += "scale_unstable"
                "rotation_jitter_high" -> hints += "rotation_unstable"
                "update_rate_low" -> hints += "tracking_update_rate_low"
            }
        }
        if (score < 0.28f) hints += "tracking_lost"
        return hints.distinct()
    }

    private fun temporalComponent(metrics: TryOnTemporalJitterMetrics?): Float {
        val temporal = metrics ?: return 0.72f
        if (temporal.measuredSampleCount < 2) return 0.5f
        val warningPenalty = (temporal.warnings.size * 0.18f).coerceAtMost(0.72f)
        val hiddenPenalty = (temporal.hiddenFrames.toFloat() / temporal.sampleCount.coerceAtLeast(1).toFloat()).coerceIn(0f, 0.5f)
        return (1f - warningPenalty - hiddenPenalty).coerceIn(0f, 1f)
    }
}
