package com.handtryon.engine

import com.handtryon.coreengine.TryOnSessionResolverPolicy
import com.handtryon.engine.model.TryOnEngineRequest
import com.handtryon.engine.model.TryOnEngineResult

internal class TryOnEngine(
    private val resolverPolicy: TryOnSessionResolverPolicy,
) {
    fun resolve(request: TryOnEngineRequest): TryOnEngineResult =
        TryOnEngineResult(
            session =
                resolverPolicy.resolve(
                    asset = request.asset,
                    handPose = request.handPose,
                    measurement = request.measurement,
                    manualPlacement = request.manualPlacement,
                    previousSession = request.previousSession,
                    frameWidth = request.frameWidth,
                    frameHeight = request.frameHeight,
                    nowMs = request.nowMs,
                ),
        )
}
