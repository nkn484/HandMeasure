package com.handtryon.core

import com.handtryon.coreengine.model.TryOnRenderState3D
import com.handtryon.domain.TryOnSession
import com.handtryon.render.model.TryOnRenderState

data class TryOnSessionResolution(
    val session: TryOnSession,
    val renderState: TryOnRenderState,
    val renderState3D: TryOnRenderState3D? = null,
)
