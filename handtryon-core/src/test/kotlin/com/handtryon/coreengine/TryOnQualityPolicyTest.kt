package com.handtryon.coreengine

import com.google.common.truth.Truth.assertThat
import com.handtryon.coreengine.model.TryOnMode
import com.handtryon.coreengine.model.TryOnPlacementValidation
import com.handtryon.coreengine.model.TryOnTrackingState
import com.handtryon.coreengine.model.TryOnUpdateAction
import org.junit.Test

class TryOnQualityPolicyTest {
    private val policy = TryOnQualityPolicy()

    @Test
    fun locked_high_quality_prefers_normal_updates() {
        val result =
            policy.evaluate(
                baseSignals(
                    trackingState = TryOnTrackingState.Locked,
                    landmarkConfidence = 0.9f,
                    anchorJumpPx = 2f,
                    placementJumpRatio = 0.05f,
                ),
            )

        assertThat(result.score).isGreaterThan(0.7f)
        assertThat(result.updateAction).isEqualTo(TryOnUpdateAction.Update)
    }

    @Test
    fun locked_medium_quality_freezes_scale_and_rotation() {
        val result =
            policy.evaluate(
                baseSignals(
                    trackingState = TryOnTrackingState.Locked,
                    landmarkConfidence = 0.58f,
                    anchorJumpPx = 18f,
                    placementJumpRatio = 0.22f,
                ),
            )

        assertThat(result.score).isAtLeast(0.5f)
        assertThat(result.updateAction).isEqualTo(TryOnUpdateAction.FreezeScaleRotation)
    }

    @Test
    fun recovering_low_quality_prefers_hold_or_recover() {
        val result =
            policy.evaluate(
                baseSignals(
                    trackingState = TryOnTrackingState.Recovering,
                    landmarkConfidence = 0.28f,
                    anchorJumpPx = 44f,
                    placementJumpRatio = 0.7f,
                    validation = invalidValidation(),
                ),
            )

        assertThat(result.updateAction).isAnyOf(TryOnUpdateAction.HoldLastPlacement, TryOnUpdateAction.Recover)
        assertThat(result.hints).contains("tracking_recovering")
    }

    @Test
    fun searching_poor_quality_hides_overlay_when_previous_exists() {
        val result =
            policy.evaluate(
                baseSignals(
                    trackingState = TryOnTrackingState.Searching,
                    landmarkConfidence = 0.12f,
                    anchorJumpPx = 60f,
                    placementJumpRatio = 0.8f,
                    validation = invalidValidation(),
                ),
            )

        assertThat(result.updateAction).isAnyOf(TryOnUpdateAction.Hide, TryOnUpdateAction.HoldLastPlacement)
    }

    @Test
    fun manual_mode_keeps_update_action_to_preserve_manual_semantics() {
        val result =
            policy.evaluate(
                baseSignals(
                    mode = TryOnMode.Manual,
                    trackingState = TryOnTrackingState.Searching,
                    landmarkUsable = false,
                    measurementUsable = false,
                    hasPreviousPlacement = true,
                ),
            )

        assertThat(result.updateAction).isEqualTo(TryOnUpdateAction.Update)
    }

    private fun baseSignals(
        mode: TryOnMode = TryOnMode.LandmarkOnly,
        trackingState: TryOnTrackingState,
        landmarkUsable: Boolean = true,
        measurementUsable: Boolean = false,
        landmarkConfidence: Float = 0.8f,
        measurementConfidence: Float = 0.5f,
        usedLastGoodAnchor: Boolean = false,
        anchorJumpPx: Float = 3f,
        placementJumpRatio: Float = 0.08f,
        validation: TryOnPlacementValidation = validValidation(),
        hasPreviousPlacement: Boolean = true,
    ): TryOnQualitySignals =
        TryOnQualitySignals(
            mode = mode,
            landmarkUsable = landmarkUsable,
            measurementUsable = measurementUsable,
            landmarkConfidence = landmarkConfidence,
            measurementConfidence = measurementConfidence,
            usedLastGoodAnchor = usedLastGoodAnchor,
            anchorJumpPx = anchorJumpPx,
            placementJumpRatio = placementJumpRatio,
            validation = validation,
            trackingState = trackingState,
            hasPreviousPlacement = hasPreviousPlacement,
        )

    private fun validValidation(): TryOnPlacementValidation =
        TryOnPlacementValidation(
            widthRatio = 0.13f,
            anchorDistancePx = 6f,
            rotationJumpDeg = 3f,
            isPlacementUsable = true,
            notes = emptyList(),
        )

    private fun invalidValidation(): TryOnPlacementValidation =
        TryOnPlacementValidation(
            widthRatio = 0.6f,
            anchorDistancePx = 80f,
            rotationJumpDeg = 34f,
            isPlacementUsable = false,
            notes = listOf("far_from_anchor", "rotation_jump_high"),
        )
}
