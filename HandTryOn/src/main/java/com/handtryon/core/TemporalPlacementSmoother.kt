package com.handtryon.core

import com.handtryon.coreengine.TemporalPlacementSmootherPolicy
import com.handtryon.domain.RingPlacement
import com.handtryon.engine.compat.TryOnEngineDomainMapper

internal class TemporalPlacementSmoother(
    private val policy: TemporalPlacementSmootherPolicy = TemporalPlacementSmootherPolicy(),
    private val mapper: TryOnEngineDomainMapper = TryOnEngineDomainMapper(),
) {
    fun smooth(
        raw: RingPlacement,
        previous: RingPlacement?,
        deltaMs: Long,
    ): RingPlacement =
        mapper.toDomainPlacement(
            policy.smooth(
                raw = mapper.toCorePlacement(raw),
                previous = previous?.let(mapper::toCorePlacement),
                deltaMs = deltaMs,
            ),
        )
}
