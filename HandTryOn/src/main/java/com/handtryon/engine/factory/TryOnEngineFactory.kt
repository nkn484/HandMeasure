package com.handtryon.engine.factory

import com.handtryon.coreengine.DefaultFingerAnchorFactory
import com.handtryon.coreengine.FingerAnchorFactory
import com.handtryon.coreengine.TryOnSessionResolverPolicy
import com.handtryon.engine.TryOnEngine

internal object TryOnEngineFactory {
    fun create(
        fingerAnchorFactory: FingerAnchorFactory = DefaultFingerAnchorFactory(),
    ): TryOnEngine =
        TryOnEngine(
            resolverPolicy = TryOnSessionResolverPolicy(fingerAnchorFactory),
        )
}
