package com.handtryon.ui.product

import com.google.common.truth.Truth.assertThat
import com.handtryon.domain.TryOnTrackingState
import com.handtryon.domain.TryOnUpdateAction
import org.junit.Test

class ProductTryOnStateResolverTest {
    @Test
    fun resolveRendererMode_prefersArCoreWhenAvailableAndRequested() {
        val mode =
            ProductTryOnStateResolver.resolveRendererMode(
                canUseArCoreNow = true,
                userWantsArCore = true,
                hasGlbAsset = true,
                allowLegacyOverlayFallback = true,
            )

        assertThat(mode).isEqualTo(ProductTryOnRendererMode.ARCoreCamera3D)
    }

    @Test
    fun resolveRendererMode_usesNonArWhenArUnavailable() {
        val mode =
            ProductTryOnStateResolver.resolveRendererMode(
                canUseArCoreNow = false,
                userWantsArCore = true,
                hasGlbAsset = true,
                allowLegacyOverlayFallback = true,
            )

        assertThat(mode).isEqualTo(ProductTryOnRendererMode.CameraRelative3D)
    }

    @Test
    fun resolveRendererMode_fallsBackLegacyOnlyWhenNoGlb() {
        val mode =
            ProductTryOnStateResolver.resolveRendererMode(
                canUseArCoreNow = false,
                userWantsArCore = false,
                hasGlbAsset = false,
                allowLegacyOverlayFallback = true,
            )

        assertThat(mode).isEqualTo(ProductTryOnRendererMode.Legacy2DOverlay)
    }

    @Test
    fun resolveUiState_mapsQualityAndTrackingToProductState() {
        assertThat(
            ProductTryOnStateResolver.resolveUiState(
                hasCameraPermission = true,
                hasAssetValidationError = false,
                rendererMode = ProductTryOnRendererMode.CameraRelative3D,
                trackingState = TryOnTrackingState.Candidate,
                updateAction = TryOnUpdateAction.Update,
                qualityScore = 0.45f,
                measurementApplied = false,
            ),
        ).isEqualTo(ProductTryOnUiState.PlaceHandInFrame)

        assertThat(
            ProductTryOnStateResolver.resolveUiState(
                hasCameraPermission = true,
                hasAssetValidationError = false,
                rendererMode = ProductTryOnRendererMode.CameraRelative3D,
                trackingState = TryOnTrackingState.Locked,
                updateAction = TryOnUpdateAction.FreezeScaleRotation,
                qualityScore = 0.4f,
                measurementApplied = false,
            ),
        ).isEqualTo(ProductTryOnUiState.HoldStill)
    }

    @Test
    fun resolveUiState_prioritizesAssetUnavailable() {
        val state =
            ProductTryOnStateResolver.resolveUiState(
                hasCameraPermission = true,
                hasAssetValidationError = true,
                rendererMode = ProductTryOnRendererMode.CameraRelative3D,
                trackingState = TryOnTrackingState.Locked,
                updateAction = TryOnUpdateAction.Update,
                qualityScore = 0.9f,
                measurementApplied = true,
            )

        assertThat(state).isEqualTo(ProductTryOnUiState.AssetUnavailable)
    }
}
