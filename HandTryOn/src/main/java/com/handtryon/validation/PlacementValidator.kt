package com.handtryon.validation

import com.handtryon.coreengine.PlacementValidationPolicy
import com.handtryon.domain.FingerAnchor
import com.handtryon.domain.PlacementValidation
import com.handtryon.domain.RingPlacement
import com.handtryon.engine.compat.TryOnEngineDomainMapper

internal class PlacementValidator(
    private val policy: PlacementValidationPolicy = PlacementValidationPolicy(),
    private val mapper: TryOnEngineDomainMapper = TryOnEngineDomainMapper(),
) {
    fun validate(
        placement: RingPlacement,
        anchor: FingerAnchor?,
        previousPlacement: RingPlacement?,
        frameWidth: Int,
    ): PlacementValidation =
        mapper.toDomainPlacementValidation(
            policy.validate(
                placement = mapper.toCorePlacement(placement),
                anchor = anchor?.let(mapper::toCoreAnchor),
                previousPlacement = previousPlacement?.let(mapper::toCorePlacement),
                frameWidth = frameWidth,
            ),
        )
}
