package com.handtryon.tracking

import com.handtryon.domain.HandPoseSnapshot
import com.handtryon.domain.LandmarkPoint

data class TrackedLandmark(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
)

enum class FrameSource {
    CameraX,
    ARCoreCpuImage,
    Replay,
}

enum class Handedness {
    Left,
    Right,
    Unknown,
}

enum class TargetFinger {
    Thumb,
    Index,
    Middle,
    Ring,
    Little,
}

enum class TrackingRejectReason {
    HandMissing,
    MissingLandmarks,
    FingerOutsideFrame,
    LowConfidence,
    UnstableGeometry,
    ImpossibleGeometry,
}

data class TrackingFrameQualityAssessment(
    val rejectReason: TrackingRejectReason?,
    val penaltyScore: Float = 0f,
    val notes: List<String> = emptyList(),
)

data class TrackedHandFrame(
    val frameWidth: Int,
    val frameHeight: Int,
    val landmarks: List<TrackedLandmark>,
    val confidence: Float,
    val timestampMs: Long,
    val source: FrameSource,
    val handedness: Handedness = Handedness.Unknown,
    val targetFinger: TargetFinger = TargetFinger.Ring,
    val isMirrored: Boolean = false,
    val rotationDegrees: Int = 0,
) {
    fun toHandPoseSnapshot(): HandPoseSnapshot =
        HandPoseSnapshot(
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            landmarks = landmarks.map { point -> LandmarkPoint(point.x, point.y, point.z) },
            confidence = confidence,
            timestampMs = timestampMs,
        )
}
