package com.handtryon.coreengine

import com.google.common.truth.Truth.assertThat
import com.handtryon.coreengine.model.TryOnAssetSource
import com.handtryon.coreengine.model.TryOnFingerAnchor
import com.handtryon.coreengine.model.TryOnHandPoseSnapshot
import com.handtryon.coreengine.model.TryOnLandmarkPoint
import com.handtryon.coreengine.model.TryOnMeasurementSnapshot
import com.handtryon.coreengine.model.TryOnMode
import com.handtryon.coreengine.model.TryOnPlacement
import com.handtryon.coreengine.model.TryOnSession
import com.handtryon.coreengine.model.TryOnTrackingState
import com.handtryon.coreengine.model.TryOnUpdateAction
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

    @Test
    fun reuses_last_good_anchor_within_grace_window() {
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
                nowMs = 1800L,
            )

        assertThat(second.mode).isEqualTo(TryOnMode.LandmarkOnly)
        assertThat(second.quality.usedLastGoodAnchor).isTrue()
        assertThat(second.anchor).isNotNull()
    }

    @Test
    fun does_not_reuse_last_good_anchor_after_grace_window() {
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
                previousSession = null,
                frameWidth = 1080,
                frameHeight = 1920,
                nowMs = 2001L,
            )

        assertThat(second.mode).isEqualTo(TryOnMode.Manual)
        assertThat(second.quality.usedLastGoodAnchor).isFalse()
        assertThat(second.anchor).isNull()
    }

    @Test
    fun uses_manual_placement_in_manual_mode() {
        val manualPlacement = TryOnPlacement(centerX = 321f, centerY = 654f, ringWidthPx = 77f, rotationDegrees = 12f)
        val session =
            resolver.resolve(
                asset = asset,
                handPose = null,
                measurement = null,
                manualPlacement = manualPlacement,
                previousSession = null,
                frameWidth = 1080,
                frameHeight = 1920,
                nowMs = 3000L,
            )

        assertThat(session.mode).isEqualTo(TryOnMode.Manual)
        assertThat(session.placement).isEqualTo(manualPlacement)
    }

    @Test
    fun transitions_to_locked_after_consecutive_stable_frames() {
        var previous: TryOnSession? = null
        var latest: TryOnSession? = null

        repeat(3) { index ->
            latest =
                resolver.resolve(
                    asset = asset,
                    handPose = ringPose(timestampMs = 1000L + index * 33L),
                    measurement = null,
                    manualPlacement = null,
                    previousSession = previous,
                    frameWidth = 1080,
                    frameHeight = 1920,
                    nowMs = 1000L + index * 33L,
                )
            previous = latest
        }

        assertThat(latest?.quality?.trackingState).isEqualTo(TryOnTrackingState.Locked)
    }

    @Test
    fun low_quality_jump_enters_recovering_and_avoids_large_visual_jump() {
        val fixedAnchorFactory =
            object : FingerAnchorFactory {
                private var callCount = 0

                override fun createAnchor(pose: TryOnHandPoseSnapshot): TryOnFingerAnchor {
                    callCount += 1
                    return if (callCount <= 3) {
                        TryOnFingerAnchor(
                            centerX = 300f,
                            centerY = 420f,
                            angleDegrees = 10f,
                            fingerWidthPx = 95f,
                            confidence = 0.92f,
                            timestampMs = pose.timestampMs,
                        )
                    } else {
                        TryOnFingerAnchor(
                            centerX = 880f,
                            centerY = 1180f,
                            angleDegrees = 80f,
                            fingerWidthPx = 130f,
                            confidence = 0.45f,
                            timestampMs = pose.timestampMs,
                        )
                    }
                }
            }
        val policy = TryOnSessionResolverPolicy(fingerAnchorFactory = fixedAnchorFactory)

        var previous: TryOnSession? = null
        repeat(3) { index ->
            previous =
                policy.resolve(
                    asset = asset,
                    handPose = ringPose(timestampMs = 1000L + index * 33L),
                    measurement = null,
                    manualPlacement = null,
                    previousSession = previous,
                    frameWidth = 1080,
                    frameHeight = 1920,
                    nowMs = 1000L + index * 33L,
                )
        }
        val locked = requireNotNull(previous)
        val recovering =
            policy.resolve(
                asset = asset,
                handPose = ringPose(timestampMs = 1200L),
                measurement = null,
                manualPlacement = null,
                previousSession = locked,
                frameWidth = 1080,
                frameHeight = 1920,
                nowMs = 1200L,
            )

        assertThat(locked.quality.trackingState).isEqualTo(TryOnTrackingState.Locked)
        assertThat(recovering.quality.trackingState).isEqualTo(TryOnTrackingState.Recovering)
        assertThat(recovering.quality.updateAction).isAnyOf(
            TryOnUpdateAction.HoldLastPlacement,
            TryOnUpdateAction.Recover,
            TryOnUpdateAction.FreezeScaleRotation,
        )
        val centerJump = recovering.placement.centerX - locked.placement.centerX
        assertThat(kotlin.math.abs(centerJump)).isLessThan(50f)
    }

    @Test
    fun exposes_quality_hints_when_tracking_is_unstable() {
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
                nowMs = 1200L,
            )

        assertThat(second.quality.hints).isNotEmpty()
        assertThat(second.quality.hints).containsAnyOf("using_last_anchor", "hand_unstable", "tracking_recovering")
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
