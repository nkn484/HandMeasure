package com.handtryon.tracking

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrackingFrameQualityPolicyTest {
    private val policy = TrackingFrameQualityPolicy()

    @Test
    fun rejectReason_returnsNullForUsableRingFingerFrame() {
        val frame = frame()

        assertThat(policy.rejectReason(frame)).isNull()
    }

    @Test
    fun rejectReason_rejectsMissingHand() {
        assertThat(policy.rejectReason(null)).isEqualTo(TrackingRejectReason.HandMissing)
    }

    @Test
    fun rejectReason_rejectsLowConfidence() {
        assertThat(policy.rejectReason(frame(confidence = 0.1f))).isEqualTo(TrackingRejectReason.LowConfidence)
    }

    @Test
    fun rejectReason_rejectsFingerOutsideFrame() {
        val frame =
            frame(
                landmarks =
                    MutableList(21) { TrackedLandmark(100f, 100f) }.apply {
                        this[13] = TrackedLandmark(100f, 100f)
                        this[14] = TrackedLandmark(100f, 140f)
                        this[15] = TrackedLandmark(100f, 260f)
                    },
            )

        assertThat(policy.rejectReason(frame)).isEqualTo(TrackingRejectReason.FingerOutsideFrame)
    }

    @Test
    fun rejectReason_rejectsUnstableGeometry() {
        val frame =
            frame(
                landmarks =
                    MutableList(21) { TrackedLandmark(100f, 100f) }.apply {
                        this[13] = TrackedLandmark(100f, 100f)
                        this[14] = TrackedLandmark(102f, 102f)
                        this[15] = TrackedLandmark(104f, 104f)
                    },
            )

        assertThat(policy.rejectReason(frame)).isEqualTo(TrackingRejectReason.UnstableGeometry)
    }

    @Test
    fun assess_addsPenaltyForNearEdgeAndCrossing() {
        val frame =
            frame(
                landmarks =
                    MutableList(21) { TrackedLandmark(100f, 100f) }.apply {
                        this[13] = TrackedLandmark(5f, 5f)
                        this[14] = TrackedLandmark(12f, 18f)
                        this[15] = TrackedLandmark(20f, 30f)
                        this[10] = TrackedLandmark(14f, 20f)
                        this[18] = TrackedLandmark(16f, 22f)
                    },
            )

        val assessment = policy.assess(frame)

        assertThat(assessment.rejectReason).isNull()
        assertThat(assessment.penaltyScore).isGreaterThan(0f)
        assertThat(assessment.notes).containsAtLeast("finger_near_edge", "ring_finger_crossing")
    }

    @Test
    fun rejectReason_rejectsImpossibleGeometry() {
        val frame =
            frame(
                landmarks =
                    MutableList(21) { TrackedLandmark(100f, 100f) }.apply {
                        this[13] = TrackedLandmark(100f, 120f)
                        this[14] = TrackedLandmark(100f, 140f)
                        this[15] = TrackedLandmark(100f, 130f)
                    },
            )

        assertThat(policy.rejectReason(frame)).isEqualTo(TrackingRejectReason.ImpossibleGeometry)
    }

    private fun frame(
        confidence: Float = 0.9f,
        landmarks: List<TrackedLandmark> =
            MutableList(21) { TrackedLandmark(100f, 100f) }.apply {
                this[13] = TrackedLandmark(100f, 100f)
                this[14] = TrackedLandmark(100f, 140f)
                this[15] = TrackedLandmark(100f, 180f)
            },
    ): TrackedHandFrame =
        TrackedHandFrame(
            frameWidth = 200,
            frameHeight = 240,
            landmarks = landmarks,
            confidence = confidence,
            timestampMs = 1L,
            source = FrameSource.CameraX,
            handedness = Handedness.Unknown,
            targetFinger = TargetFinger.Ring,
        )
}
