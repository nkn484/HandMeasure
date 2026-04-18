package com.handtryon.core

import com.handtryon.coreengine.TemporalPlacementSmootherPolicy
import com.handtryon.coreengine.TryOnSmoothingContext
import com.handtryon.domain.RingPlacement
import com.handtryon.domain.TryOnTrackingState as DomainTrackingState
import com.handtryon.domain.TryOnUpdateAction as DomainUpdateAction
import com.handtryon.engine.compat.TryOnEngineDomainMapper
import com.handtryon.engine.compat.TryOnRuntimeStateMapper

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
                        trackingState = TryOnRuntimeStateMapper.toCoreTrackingState(trackingState),
                        updateAction = TryOnRuntimeStateMapper.toCoreUpdateAction(updateAction),
                    ),
            ),
        )
}
