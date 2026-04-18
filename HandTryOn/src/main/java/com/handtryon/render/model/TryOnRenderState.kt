package com.handtryon.render.model

import com.handtryon.domain.FingerAnchor
import com.handtryon.domain.RingPlacement
import com.handtryon.domain.TryOnMode
import com.handtryon.domain.TryOnTrackingState
import com.handtryon.domain.TryOnUpdateAction

data class TryOnRenderState(
    val mode: TryOnMode,
    val anchor: FingerAnchor?,
    val placement: RingPlacement,
    val trackingState: TryOnTrackingState = TryOnTrackingState.Searching,
    val qualityScore: Float = 0f,
    val updateAction: TryOnUpdateAction = TryOnUpdateAction.Update,
    val hints: List<String> = emptyList(),
    val shouldRenderOverlay: Boolean = true,
    val generatedAtMs: Long,
)
