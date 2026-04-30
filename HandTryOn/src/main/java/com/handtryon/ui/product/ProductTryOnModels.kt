package com.handtryon.ui.product

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.handtryon.domain.TryOnTrackingState
import com.handtryon.domain.TryOnUpdateAction

enum class ProductTryOnRendererMode {
    ARCoreCamera3D,
    CameraRelative3D,
    Static3DPreview,
    Legacy2DOverlay,
}

enum class ProductTryOnFitSource {
    Measured,
    SelectedSize,
    VisualEstimate,
    Default,
}

enum class ProductTryOnUiState {
    PreparingCamera,
    SearchingHand,
    PlaceHandInFrame,
    Tracking,
    HoldStill,
    LowConfidence,
    MeasurementApplied,
    Fallback3D,
    UnsupportedDevice,
    AssetUnavailable,
}

data class ProductTryOnState(
    val uiState: ProductTryOnUiState,
    val rendererMode: ProductTryOnRendererMode,
    val fitSource: ProductTryOnFitSource,
    val developerMode: Boolean = false,
)

object ProductTryOnStateResolver {
    fun resolveRendererMode(
        canUseArCoreNow: Boolean,
        userWantsArCore: Boolean,
        hasGlbAsset: Boolean,
        allowLegacyOverlayFallback: Boolean,
    ): ProductTryOnRendererMode =
        when {
            hasGlbAsset && canUseArCoreNow && userWantsArCore -> ProductTryOnRendererMode.ARCoreCamera3D
            hasGlbAsset -> ProductTryOnRendererMode.CameraRelative3D
            allowLegacyOverlayFallback -> ProductTryOnRendererMode.Legacy2DOverlay
            else -> ProductTryOnRendererMode.Static3DPreview
        }

    fun resolveUiState(
        hasCameraPermission: Boolean,
        hasAssetValidationError: Boolean,
        rendererMode: ProductTryOnRendererMode,
        trackingState: TryOnTrackingState?,
        updateAction: TryOnUpdateAction?,
        qualityScore: Float,
        measurementApplied: Boolean,
    ): ProductTryOnUiState {
        if (hasAssetValidationError) return ProductTryOnUiState.AssetUnavailable
        if (!hasCameraPermission && rendererMode == ProductTryOnRendererMode.Static3DPreview) {
            return ProductTryOnUiState.Fallback3D
        }
        if (!hasCameraPermission) return ProductTryOnUiState.PreparingCamera
        if (trackingState == null) return ProductTryOnUiState.SearchingHand
        if (measurementApplied && qualityScore >= 0.5f) return ProductTryOnUiState.MeasurementApplied
        if (updateAction == TryOnUpdateAction.Hide || qualityScore < 0.22f) return ProductTryOnUiState.LowConfidence
        if (updateAction == TryOnUpdateAction.FreezeScaleRotation || updateAction == TryOnUpdateAction.HoldLastPlacement) {
            return ProductTryOnUiState.HoldStill
        }
        return when (trackingState) {
            TryOnTrackingState.Searching -> ProductTryOnUiState.SearchingHand
            TryOnTrackingState.Candidate -> ProductTryOnUiState.PlaceHandInFrame
            TryOnTrackingState.Locked -> ProductTryOnUiState.Tracking
            TryOnTrackingState.Recovering -> ProductTryOnUiState.HoldStill
        }
    }
}

@Composable
fun ProductTryOnScreen(
    state: ProductTryOnState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    debugContent: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier) {
        content()
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xAA000000))
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = state.uiState.name,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
            controls()
            if (state.developerMode && debugContent != null) {
                debugContent()
            }
        }
    }
}

@Composable
fun ProductTryOnControls(
    fitSource: ProductTryOnFitSource,
    onSelectMeasuredFit: () -> Unit,
    onSelectSelectedSizeFit: () -> Unit,
    onSelectVisualEstimateFit: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onSelectMeasuredFit, modifier = Modifier.weight(1f)) {
            Text(if (fitSource == ProductTryOnFitSource.Measured) "Measured ✓" else "Measured")
        }
        Button(onClick = onSelectSelectedSizeFit, modifier = Modifier.weight(1f)) {
            Text(if (fitSource == ProductTryOnFitSource.SelectedSize) "Size ✓" else "Size")
        }
        Button(onClick = onSelectVisualEstimateFit, modifier = Modifier.weight(1f)) {
            Text(if (fitSource == ProductTryOnFitSource.VisualEstimate) "Visual ✓" else "Visual")
        }
    }
}
