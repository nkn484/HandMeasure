package com.handtryon.core

import com.handtryon.coreengine.DefaultFingerAnchorFactory
import com.handtryon.coreengine.FingerAnchorFactory
import com.handtryon.domain.FingerAnchor
import com.handtryon.domain.HandPoseSnapshot
import com.handtryon.engine.compat.TryOnEngineDomainMapper

class DefaultFingerAnchorProvider(
    private val anchorFactory: FingerAnchorFactory = DefaultFingerAnchorFactory(),
    private val mapper: TryOnEngineDomainMapper = TryOnEngineDomainMapper(),
) : FingerAnchorProvider {
    override fun createAnchor(pose: HandPoseSnapshot): FingerAnchor? =
        anchorFactory
            .createAnchor(mapper.toCoreHandPose(pose))
            ?.let(mapper::toDomainAnchor)
}
