package com.handtryon.engine

import com.google.common.truth.Truth.assertThat
import com.handtryon.coreengine.TryOnSessionResolverPolicy
import com.handtryon.coreengine.model.TryOnAssetSource
import com.handtryon.coreengine.model.TryOnHandPoseSnapshot
import com.handtryon.coreengine.model.TryOnLandmarkPoint
import com.handtryon.coreengine.model.TryOnMeasurementSnapshot
import com.handtryon.coreengine.model.TryOnMode
import com.handtryon.engine.model.TryOnEngineRequest
import org.junit.Test

class TryOnEngineTest {
    @Test
    fun resolve_routesThroughCorePolicyAndReturnsMeasuredSession() {
        val engine = TryOnEngine(resolverPolicy = TryOnSessionResolverPolicy())
        val request =
            TryOnEngineRequest(
                asset = TryOnAssetSource(id = "ring", name = "ring", overlayAssetPath = "ring.png"),
                handPose = ringPose(),
                measurement = TryOnMeasurementSnapshot(17.6f, 19f, confidence = 0.8f),
                manualPlacement = null,
                previousSession = null,
                frameWidth = 1080,
                frameHeight = 1920,
                nowMs = 1234L,
            )

        val result = engine.resolve(request)

        assertThat(result.session.mode).isEqualTo(TryOnMode.Measured)
        assertThat(result.session.updatedAtMs).isEqualTo(1234L)
    }

    private fun ringPose(): TryOnHandPoseSnapshot {
        val points = MutableList(21) { TryOnLandmarkPoint(400f, 900f) }
        points[13] = TryOnLandmarkPoint(540f, 960f)
        points[14] = TryOnLandmarkPoint(600f, 980f)
        return TryOnHandPoseSnapshot(
            frameWidth = 1080,
            frameHeight = 1920,
            landmarks = points,
            confidence = 0.92f,
            timestampMs = 1000L,
        )
    }
}
