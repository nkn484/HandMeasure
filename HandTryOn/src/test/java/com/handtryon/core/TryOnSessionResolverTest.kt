package com.handtryon.core

import com.google.common.truth.Truth.assertThat
import com.handtryon.domain.HandPoseSnapshot
import com.handtryon.domain.LandmarkPoint
import com.handtryon.domain.MeasurementSnapshot
import com.handtryon.domain.RingAssetSource
import com.handtryon.domain.TryOnMode
import org.junit.Test

class TryOnSessionResolverTest {
    private val resolver = TryOnSessionResolver()
    private val asset = RingAssetSource(id = "ring", name = "ring", overlayAssetPath = "ring.png")

    @Test
    fun measured_mode_when_landmark_and_measurement_usable() {
        val session =
            resolver.resolve(
                asset = asset,
                handPose = ringPose(),
                measurement = MeasurementSnapshot(17.6f, 19f, confidence = 0.8f),
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
    fun fallback_to_landmark_only_when_measurement_not_usable() {
        val session =
            resolver.resolve(
                asset = asset,
                handPose = ringPose(),
                measurement = MeasurementSnapshot(17.6f, 19f, confidence = 0.2f),
                manualPlacement = null,
                previousSession = null,
                frameWidth = 1080,
                frameHeight = 1920,
            )

        assertThat(session.mode).isEqualTo(TryOnMode.LandmarkOnly)
        assertThat(session.quality.measurementUsable).isFalse()
        assertThat(session.quality.landmarkUsable).isTrue()
    }

    @Test
    fun fallback_to_manual_when_landmark_missing() {
        val session =
            resolver.resolve(
                asset = asset,
                handPose = null,
                measurement = MeasurementSnapshot(17.6f, 19f, confidence = 0.8f),
                manualPlacement = null,
                previousSession = null,
                frameWidth = 1080,
                frameHeight = 1920,
            )

        assertThat(session.mode).isEqualTo(TryOnMode.Manual)
        assertThat(session.quality.landmarkUsable).isFalse()
    }

    @Test
    fun uses_last_good_anchor_when_detection_drops_temporarily() {
        val first =
            resolver.resolve(
                asset = asset,
                handPose = ringPose(timestampMs = 1000L),
                measurement = null,
                manualPlacement = null,
                previousSession = null,
                frameWidth = 1080,
                frameHeight = 1920,
                nowMs = 1000L,
            )
        val second =
            resolver.resolve(
                asset = asset,
                handPose = null,
                measurement = null,
                manualPlacement = null,
                previousSession = first,
                frameWidth = 1080,
                frameHeight = 1920,
                nowMs = 1400L,
            )

        assertThat(second.mode).isEqualTo(TryOnMode.LandmarkOnly)
        assertThat(second.quality.usedLastGoodAnchor).isTrue()
    }

    private fun ringPose(timestampMs: Long = 1000L): HandPoseSnapshot {
        val points = MutableList(21) { LandmarkPoint(400f, 900f) }
        points[13] = LandmarkPoint(540f, 960f)
        points[14] = LandmarkPoint(600f, 980f)
        return HandPoseSnapshot(
            frameWidth = 1080,
            frameHeight = 1920,
            landmarks = points,
            confidence = 0.92f,
            timestampMs = timestampMs,
        )
    }
}
