package com.handtryon.core

import com.handtryon.coreengine.TemporalPlacementSmootherPolicy
import com.handtryon.coreengine.TryOnSmoothingContext
import com.handtryon.coreengine.model.TryOnTrackingState
import com.handtryon.coreengine.model.TryOnUpdateAction
import com.handtryon.domain.RingPlacement
import com.handtryon.domain.TryOnTrackingState as DomainTrackingState
import com.handtryon.domain.TryOnUpdateAction as DomainUpdateAction
import com.handtryon.engine.compat.TryOnEngineDomainMapper

internal class TemporalPlacementSmoother(
    private val policy: TemporalPlacementSmootherPolicy = TemporalPlacementSmootherPolicy(),
    private val mapper: TryOnEngineDomainMapper = TryOnEngineDomainMapper(),
) {
    fun smooth(
        raw: RingPlacement,
        previous: RingPlacement?,
        deltaMs: Long,
        qualityScore: Float = 1f,
        trackingState: DomainTrackingState = DomainTrackingState.Locked,
        updateAction: DomainUpdateAction = DomainUpdateAction.Update,
    ): RingPlacement =
        mapper.toDomainPlacement(
            policy.smooth(
                raw = mapper.toCorePlacement(raw),
                previous = previous?.let(mapper::toCorePlacement),
                deltaMs = deltaMs,
                context =
                    TryOnSmoothingContext(
                        qualityScore = qualityScore,
                        trackingState = trackingState.toCoreTrackingState(),
                        updateAction = updateAction.toCoreUpdateAction(),
                    ),
            ),
        )

    private fun DomainTrackingState.toCoreTrackingState(): TryOnTrackingState =
        when (this) {
            DomainTrackingState.Searching -> TryOnTrackingState.Searching
            DomainTrackingState.Candidate -> TryOnTrackingState.Candidate
            DomainTrackingState.Locked -> TryOnTrackingState.Locked
            DomainTrackingState.Recovering -> TryOnTrackingState.Recovering
        }

    private fun DomainUpdateAction.toCoreUpdateAction(): TryOnUpdateAction =
        when (this) {
            DomainUpdateAction.Update -> TryOnUpdateAction.Update
            DomainUpdateAction.FreezeScaleRotation -> TryOnUpdateAction.FreezeScaleRotation
            DomainUpdateAction.HoldLastPlacement -> TryOnUpdateAction.HoldLastPlacement
            DomainUpdateAction.Recover -> TryOnUpdateAction.Recover
            DomainUpdateAction.Hide -> TryOnUpdateAction.Hide
        }
}
