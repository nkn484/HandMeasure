package com.handtryon.engine.model

import com.handtryon.coreengine.model.TryOnAssetSource
import com.handtryon.coreengine.model.TryOnHandPoseSnapshot
import com.handtryon.coreengine.model.TryOnMeasurementSnapshot
import com.handtryon.coreengine.model.TryOnPlacement
import com.handtryon.coreengine.model.TryOnSession

internal data class TryOnEngineRequest(
    val asset: TryOnAssetSource,
    val handPose: TryOnHandPoseSnapshot?,
    val measurement: TryOnMeasurementSnapshot?,
    val selectedDiameterMm: Float? = null,
    val manualPlacement: TryOnPlacement?,
    val previousSession: TryOnSession?,
    val frameWidth: Int,
    val frameHeight: Int,
    val nowMs: Long,
)
