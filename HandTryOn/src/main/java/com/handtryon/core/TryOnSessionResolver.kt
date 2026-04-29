package com.handtryon.core

import com.handtryon.domain.HandPoseSnapshot
import com.handtryon.domain.MeasurementSnapshot
import com.handtryon.domain.RingAssetSource
import com.handtryon.domain.RingPlacement
import com.handtryon.domain.TryOnMeasurementInput
import com.handtryon.domain.TryOnSession
import com.handtryon.engine.TryOnEngine
import com.handtryon.engine.compat.TryOnEngineDomainMapper
import com.handtryon.engine.factory.TryOnEngineFactory

class TryOnSessionResolver internal constructor(
    private val engine: TryOnEngine,
    private val mapper: TryOnEngineDomainMapper,
) {
    constructor() : this(
        engine = TryOnEngineFactory.create(),
        mapper = TryOnEngineDomainMapper(),
    )

    fun resolve(
        asset: RingAssetSource,
        handPose: HandPoseSnapshot?,
        measurement: MeasurementSnapshot?,
        selectedDiameterMm: Float? = null,
        manualPlacement: RingPlacement?,
        previousSession: TryOnSession?,
        frameWidth: Int,
        frameHeight: Int,
        nowMs: Long = System.currentTimeMillis(),
    ): TryOnSession =
        resolveState(
            asset = asset,
            handPose = handPose,
            measurement = measurement,
            selectedDiameterMm = selectedDiameterMm,
            manualPlacement = manualPlacement,
            previousSession = previousSession,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            nowMs = nowMs,
        ).session

    fun resolve(
        asset: RingAssetSource,
        handPose: HandPoseSnapshot?,
        measurementInput: TryOnMeasurementInput?,
        selectedDiameterMm: Float? = null,
        manualPlacement: RingPlacement?,
        previousSession: TryOnSession?,
        frameWidth: Int,
        frameHeight: Int,
        nowMs: Long = System.currentTimeMillis(),
    ): TryOnSession =
        resolve(
            asset = asset,
            handPose = handPose,
            measurement = measurementInput?.toSnapshot(),
            selectedDiameterMm = selectedDiameterMm,
            manualPlacement = manualPlacement,
            previousSession = previousSession,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            nowMs = nowMs,
        )

    fun resolveState(
        asset: RingAssetSource,
        handPose: HandPoseSnapshot?,
        measurement: MeasurementSnapshot?,
        selectedDiameterMm: Float? = null,
        manualPlacement: RingPlacement?,
        previousSession: TryOnSession?,
        frameWidth: Int,
        frameHeight: Int,
        nowMs: Long = System.currentTimeMillis(),
    ): TryOnSessionResolution {
        val request =
            mapper.toEngineRequest(
                asset = asset,
                handPose = handPose,
                measurement = measurement,
                selectedDiameterMm = selectedDiameterMm,
                manualPlacement = manualPlacement,
                previousSession = previousSession,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                nowMs = nowMs,
            )
        return mapper.toSessionResolution(engine.resolve(request))
    }

    fun resolveState(
        asset: RingAssetSource,
        handPose: HandPoseSnapshot?,
        measurementInput: TryOnMeasurementInput?,
        selectedDiameterMm: Float? = null,
        manualPlacement: RingPlacement?,
        previousSession: TryOnSession?,
        frameWidth: Int,
        frameHeight: Int,
        nowMs: Long = System.currentTimeMillis(),
    ): TryOnSessionResolution =
        resolveState(
            asset = asset,
            handPose = handPose,
            measurement = measurementInput?.toSnapshot(),
            selectedDiameterMm = selectedDiameterMm,
            manualPlacement = manualPlacement,
            previousSession = previousSession,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            nowMs = nowMs,
        )

    private fun TryOnMeasurementInput.toSnapshot(): MeasurementSnapshot =
        MeasurementSnapshot(
            equivalentDiameterMm = equivalentDiameterMm,
            fingerWidthMm = fingerWidthMm ?: 0f,
            confidence = confidence,
            usable = confidence > 0f,
        )
}
