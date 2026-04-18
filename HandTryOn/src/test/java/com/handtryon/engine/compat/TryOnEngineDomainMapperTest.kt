package com.handtryon.engine.compat

import com.google.common.truth.Truth.assertThat
import com.handtryon.domain.FingerAnchor
import com.handtryon.domain.HandPoseSnapshot
import com.handtryon.domain.LandmarkPoint
import com.handtryon.domain.MeasurementSnapshot
import com.handtryon.domain.RingAssetSource
import com.handtryon.domain.RingPlacement
import com.handtryon.domain.TryOnInputQuality
import com.handtryon.domain.TryOnMode
import com.handtryon.domain.TryOnSession
import com.handtryon.domain.TryOnTrackingState
import com.handtryon.domain.TryOnUpdateAction
import com.handtryon.coreengine.model.TryOnEngineRenderState
import com.handtryon.coreengine.model.TryOnEngineResult
import com.handtryon.coreengine.model.TryOnEngineSessionState
import org.junit.Test

class TryOnEngineDomainMapperTest {
    private val mapper = TryOnEngineDomainMapper()

    @Test
    fun requestMapping_mapsDomainModelsToEngineRequest() {
        val request =
            mapper.toEngineRequest(
                asset = RingAssetSource(id = "ring", name = "ring", overlayAssetPath = "ring.png"),
                handPose =
                    HandPoseSnapshot(
                        frameWidth = 1080,
                        frameHeight = 1920,
                        landmarks = List(21) { index -> LandmarkPoint(index.toFloat(), index.toFloat()) },
                        confidence = 0.9f,
                        timestampMs = 2000L,
                    ),
                measurement = MeasurementSnapshot(17.6f, 19f, confidence = 0.8f),
                manualPlacement = RingPlacement(centerX = 300f, centerY = 400f, ringWidthPx = 80f, rotationDegrees = 10f),
                previousSession = null,
                frameWidth = 1080,
                frameHeight = 1920,
                nowMs = 2100L,
            )

        assertThat(request.asset.id).isEqualTo("ring")
        assertThat(request.handPose?.landmarks).hasSize(21)
        assertThat(request.measurement?.equivalentDiameterMm).isEqualTo(17.6f)
        assertThat(request.manualPlacement?.ringWidthPx).isEqualTo(80f)
        assertThat(request.nowMs).isEqualTo(2100L)
    }

    @Test
    fun sessionMapping_roundTripsEngineResultToDomainModel() {
        val domainSession =
            TryOnSession(
                asset = RingAssetSource(id = "ring", name = "ring", overlayAssetPath = "ring.png"),
                mode = TryOnMode.LandmarkOnly,
                quality =
                    TryOnInputQuality(
                        measurementUsable = false,
                        landmarkUsable = true,
                        measurementConfidence = 0.1f,
                        landmarkConfidence = 0.9f,
                        usedLastGoodAnchor = false,
                    ),
                anchor =
                    FingerAnchor(
                        centerX = 310f,
                        centerY = 510f,
                        angleDegrees = 8f,
                        fingerWidthPx = 90f,
                        confidence = 0.9f,
                        timestampMs = 1000L,
                    ),
                placement = RingPlacement(centerX = 300f, centerY = 500f, ringWidthPx = 92f, rotationDegrees = 9f),
                updatedAtMs = 1200L,
            )

        val request =
            mapper.toEngineRequest(
                asset = domainSession.asset,
                handPose = null,
                measurement = null,
                manualPlacement = null,
                previousSession = domainSession,
                frameWidth = 1080,
                frameHeight = 1920,
                nowMs = 1300L,
            )
        val previousSession = request.previousSession!!
        val engineResult =
            TryOnEngineResult(
                session =
                    TryOnEngineSessionState(
                        asset = previousSession.asset,
                        mode = previousSession.mode,
                        quality = previousSession.quality,
                        anchor = previousSession.anchor,
                        placement = previousSession.placement,
                        updatedAtMs = previousSession.updatedAtMs,
                    ),
                renderState =
                    TryOnEngineRenderState(
                        mode = previousSession.mode,
                        anchor = previousSession.anchor,
                        placement = previousSession.placement,
                        generatedAtMs = 1300L,
                    ),
            )
        val mappedBack = mapper.toDomainSession(engineResult)

        assertThat(mappedBack).isEqualTo(domainSession)
    }

    @Test
    fun renderStateMapping_mapsEngineResultToAndroidCompatibilityRenderState() {
        val request =
            mapper.toEngineRequest(
                asset = RingAssetSource(id = "ring", name = "ring", overlayAssetPath = "ring.png"),
                handPose = null,
                measurement = null,
                manualPlacement = RingPlacement(centerX = 120f, centerY = 240f, ringWidthPx = 66f, rotationDegrees = 7f),
                previousSession = null,
                frameWidth = 1080,
                frameHeight = 1920,
                nowMs = 3000L,
            )
        val corePlacement = requireNotNull(request.manualPlacement)
        val engineResult =
            TryOnEngineResult(
                session =
                    TryOnEngineSessionState(
                        asset = request.asset,
                        mode = com.handtryon.coreengine.model.TryOnMode.Manual,
                        quality =
                            com.handtryon.coreengine.model.TryOnInputQuality(
                                measurementUsable = false,
                                landmarkUsable = false,
                                measurementConfidence = 0f,
                                landmarkConfidence = 0f,
                                usedLastGoodAnchor = false,
                            ),
                        anchor = null,
                        placement = corePlacement,
                        updatedAtMs = 3000L,
                    ),
                renderState =
                    TryOnEngineRenderState(
                        mode = com.handtryon.coreengine.model.TryOnMode.Manual,
                        anchor = null,
                        placement = corePlacement,
                        generatedAtMs = 3000L,
                    ),
            )

        val renderState = mapper.toRenderState(engineResult)
        val resolution = mapper.toSessionResolution(engineResult)

        assertThat(renderState.mode).isEqualTo(TryOnMode.Manual)
        assertThat(renderState.placement.ringWidthPx).isEqualTo(66f)
        assertThat(renderState.generatedAtMs).isEqualTo(3000L)
        assertThat(renderState.trackingState).isEqualTo(TryOnTrackingState.Searching)
        assertThat(renderState.updateAction).isEqualTo(TryOnUpdateAction.Update)
        assertThat(renderState.shouldRenderOverlay).isTrue()
        assertThat(resolution.session.mode).isEqualTo(TryOnMode.Manual)
        assertThat(resolution.renderState).isEqualTo(renderState)
    }

    @Test
    fun placementValidationMapping_preserves_validation_contract_fields() {
        val domainValidation =
            mapper.toDomainPlacementValidation(
                com.handtryon.coreengine.model.TryOnPlacementValidation(
                    widthRatio = 0.14f,
                    anchorDistancePx = 9f,
                    rotationJumpDeg = 3f,
                    isPlacementUsable = true,
                    notes = emptyList(),
                ),
            )

        assertThat(domainValidation.widthRatio).isEqualTo(0.14f)
        assertThat(domainValidation.anchorDistancePx).isEqualTo(9f)
        assertThat(domainValidation.rotationJumpDeg).isEqualTo(3f)
        assertThat(domainValidation.isPlacementUsable).isTrue()
        assertThat(domainValidation.notes).isEmpty()
    }
}
