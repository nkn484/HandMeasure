package com.handtryon.coreengine

import com.google.common.truth.Truth.assertThat
import com.handtryon.coreengine.model.TryOnAssetSource
import com.handtryon.coreengine.model.TryOnHandPoseSnapshot
import com.handtryon.coreengine.model.TryOnLandmarkPoint
import com.handtryon.coreengine.model.TryOnMeasurementSnapshot
import com.handtryon.coreengine.model.TryOnMode
import org.junit.Test

class TryOnSessionResolverPolicyTest {
    private val resolver = TryOnSessionResolverPolicy()
    private val asset = TryOnAssetSource(id = "ring", name = "ring", overlayAssetPath = "ring.png")

    @Test
    fun measured_mode_when_landmark_and_measurement_usable() {
        val session =
            resolver.resolve(
                asset = asset,
                handPose = ringPose(),
                measurement = TryOnMeasurementSnapshot(17.6f, 19f, confidence = 0.8f),
                manualPlacement = null,
                previousSession = null,
                frameWidth = 1080,
                frameHeight = 1920,
            )

        assertThat(session.mode).isEqualTo(TryOnMode.Measured)
        assertThat(session.quality.landmarkUsable).isTrue()
        assertThat(session.quality.measurementUsable).isTrue()
    }

    @Test
    fun fallback_to_manual_when_landmark_missing() {
        val session =
            resolver.resolve(
                asset = asset,
                handPose = null,
                measurement = TryOnMeasurementSnapshot(17.6f, 19f, confidence = 0.8f),
                manualPlacement = null,
                previousSession = null,
                frameWidth = 1080,
                frameHeight = 1920,
            )

        assertThat(session.mode).isEqualTo(TryOnMode.Manual)
        assertThat(session.quality.landmarkUsable).isFalse()
    }

    private fun ringPose(timestampMs: Long = 1000L): TryOnHandPoseSnapshot {
        val points = MutableList(21) { TryOnLandmarkPoint(400f, 900f) }
        points[13] = TryOnLandmarkPoint(540f, 960f)
        points[14] = TryOnLandmarkPoint(600f, 980f)
        return TryOnHandPoseSnapshot(
            frameWidth = 1080,
            frameHeight = 1920,
            landmarks = points,
            confidence = 0.92f,
            timestampMs = timestampMs,
        )
    }
}
