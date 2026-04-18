package com.handtryon.coreengine

import com.handtryon.coreengine.model.TryOnMode
import com.handtryon.coreengine.model.TryOnPlacementValidation
import com.handtryon.coreengine.model.TryOnTrackingState
import com.handtryon.coreengine.model.TryOnUpdateAction

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
)

data class TryOnQualityEvaluation(
    val score: Float,
    val updateAction: TryOnUpdateAction,
    val hints: List<String>,
)

class TryOnQualityPolicy {
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
                signals.validation.notes.size >= 2 -> 0.28f
                else -> 0.18f
            }
        val fallbackPenalty = if (signals.usedLastGoodAnchor) 0.12f else 0f
        val rawScore =
            landmarkComponent * 0.56f +
                measurementComponent * 0.2f +
                (1f - jumpPenalty).coerceIn(0f, 1f) * 0.18f +
                (if (signals.validation.isPlacementUsable) 1f else 0.5f) * 0.06f -
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
        if (score < 0.28f) hints += "tracking_lost"
        return hints.distinct()
    }
}
