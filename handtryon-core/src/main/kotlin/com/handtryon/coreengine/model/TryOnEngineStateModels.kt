package com.handtryon.coreengine.model

data class TryOnEngineResult(
    val session: TryOnEngineSessionState,
    val renderState: TryOnEngineRenderState,
)

data class TryOnEngineSessionState(
    val asset: TryOnAssetSource,
    val mode: TryOnMode,
    val quality: TryOnInputQuality,
    val anchor: TryOnFingerAnchor?,
    val placement: TryOnPlacement,
    val updatedAtMs: Long,
)

data class TryOnEngineRenderState(
    val mode: TryOnMode,
    val anchor: TryOnFingerAnchor?,
    val placement: TryOnPlacement,
    val trackingState: TryOnTrackingState = TryOnTrackingState.Searching,
    val qualityScore: Float = 0f,
    val updateAction: TryOnUpdateAction = TryOnUpdateAction.Update,
    val hints: List<String> = emptyList(),
    val shouldRenderOverlay: Boolean = true,
    val generatedAtMs: Long,
)

fun TryOnSession.toEngineResult(generatedAtMs: Long = updatedAtMs): TryOnEngineResult =
    TryOnEngineResult(
        session =
            TryOnEngineSessionState(
                asset = asset,
                mode = mode,
                quality = quality,
                anchor = anchor,
                placement = placement,
                updatedAtMs = updatedAtMs,
            ),
        renderState =
            TryOnEngineRenderState(
                mode = mode,
                anchor = anchor,
                placement = placement,
                trackingState = quality.trackingState,
                qualityScore = quality.qualityScore,
                updateAction = quality.updateAction,
                hints = quality.hints,
                shouldRenderOverlay = quality.updateAction != TryOnUpdateAction.Hide,
                generatedAtMs = generatedAtMs,
            ),
    )
