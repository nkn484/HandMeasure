package com.handtryon.coreengine

import com.handtryon.coreengine.model.TryOnMode
import com.handtryon.coreengine.model.TryOnTrackingState
import com.handtryon.coreengine.model.TryOnUpdateAction

data class TryOnOcclusionDecision(
    val enabled: Boolean,
    val maskOpacity: Float,
    val bandThicknessRatio: Float,
    val verticalOffsetRatio: Float,
)

class TryOnOcclusionPolicy {
    fun evaluate(
        mode: TryOnMode,
        trackingState: TryOnTrackingState,
        updateAction: TryOnUpdateAction,
        qualityScore: Float,
    ): TryOnOcclusionDecision {
        val quality = qualityScore.coerceIn(0f, 1f)
        if (updateAction == TryOnUpdateAction.Hide) {
            return TryOnOcclusionDecision(
                enabled = false,
                maskOpacity = 0f,
                bandThicknessRatio = 0f,
                verticalOffsetRatio = 0f,
            )
        }
        if (trackingState == TryOnTrackingState.Searching && quality < 0.55f) {
            return disabled()
        }
        if (quality < 0.24f) {
            return disabled()
        }
        if (mode == TryOnMode.Manual && quality < 0.38f) {
            return disabled()
        }

        val baseOpacity =
            when (trackingState) {
                TryOnTrackingState.Locked -> 0.82f
                TryOnTrackingState.Candidate -> 0.62f
                TryOnTrackingState.Recovering -> 0.45f
                TryOnTrackingState.Searching -> 0.38f
            }
        val actionFactor =
            when (updateAction) {
                TryOnUpdateAction.Update -> 1f
                TryOnUpdateAction.FreezeScaleRotation -> 0.85f
                TryOnUpdateAction.Recover -> 0.74f
                TryOnUpdateAction.HoldLastPlacement -> 0.58f
                TryOnUpdateAction.Hide -> 0f
            }
        val confidenceFactor = (0.4f + quality * 0.6f).coerceIn(0.4f, 1f)
        val maskOpacity = (baseOpacity * actionFactor * confidenceFactor).coerceIn(0f, 0.9f)
        if (maskOpacity < 0.14f) {
            return disabled()
        }

        val thickness =
            when (trackingState) {
                TryOnTrackingState.Locked -> 0.24f
                TryOnTrackingState.Candidate -> 0.21f
                TryOnTrackingState.Recovering -> 0.18f
                TryOnTrackingState.Searching -> 0.16f
            }
        return TryOnOcclusionDecision(
            enabled = true,
            maskOpacity = maskOpacity,
            bandThicknessRatio = thickness,
            verticalOffsetRatio = 0.06f,
        )
    }

    private fun disabled(): TryOnOcclusionDecision =
        TryOnOcclusionDecision(
            enabled = false,
            maskOpacity = 0f,
            bandThicknessRatio = 0f,
            verticalOffsetRatio = 0f,
        )
}
