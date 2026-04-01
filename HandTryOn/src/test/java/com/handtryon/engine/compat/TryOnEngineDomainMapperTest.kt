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
        val mappedBack = mapper.toDomainSession(com.handtryon.engine.model.TryOnEngineResult(request.previousSession!!))

        assertThat(mappedBack).isEqualTo(domainSession)
    }
}
