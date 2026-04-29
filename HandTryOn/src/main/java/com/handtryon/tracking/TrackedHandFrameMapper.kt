package com.handtryon.tracking

import com.handtryon.coreengine.model.TryOnHandPoseSnapshot
import com.handtryon.coreengine.model.TryOnLandmarkPoint
import com.handtryon.domain.HandPoseSnapshot

object TrackedHandFrameMapper {
    fun fromCameraXSnapshot(
        pose: HandPoseSnapshot,
        handedness: Handedness = Handedness.Unknown,
        targetFinger: TargetFinger = TargetFinger.Ring,
        isFrontCamera: Boolean = false,
        sensorRotationDegrees: Int = 0,
    ): TrackedHandFrame =
        fromImageLandmarks(
            frameWidth = pose.frameWidth,
            frameHeight = pose.frameHeight,
            landmarks = pose.landmarks.map { point -> TrackedLandmark(point.x, point.y, point.z) },
            confidence = pose.confidence,
            timestampMs = pose.timestampMs,
            source = FrameSource.CameraX,
            handedness = handedness,
            targetFinger = targetFinger,
            isMirrored = isFrontCamera,
            rotationDegrees = sensorRotationDegrees,
        )

    fun fromArCoreCpuImageSnapshot(
        pose: HandPoseSnapshot,
        handedness: Handedness = Handedness.Unknown,
        targetFinger: TargetFinger = TargetFinger.Ring,
        imageRotationDegrees: Int = 0,
    ): TrackedHandFrame =
        fromImageLandmarks(
            frameWidth = pose.frameWidth,
            frameHeight = pose.frameHeight,
            landmarks = pose.landmarks.map { point -> TrackedLandmark(point.x, point.y, point.z) },
            confidence = pose.confidence,
            timestampMs = pose.timestampMs,
            source = FrameSource.ARCoreCpuImage,
            handedness = handedness,
            targetFinger = targetFinger,
            isMirrored = false,
            rotationDegrees = imageRotationDegrees,
        )

    fun fromHandPoseSnapshot(
        pose: HandPoseSnapshot,
        source: FrameSource,
        handedness: Handedness = Handedness.Unknown,
        targetFinger: TargetFinger = TargetFinger.Ring,
        isMirrored: Boolean = false,
        rotationDegrees: Int = 0,
    ): TrackedHandFrame =
        TrackedHandFrame(
            frameWidth = pose.frameWidth,
            frameHeight = pose.frameHeight,
            landmarks = pose.landmarks.map { point -> TrackedLandmark(point.x, point.y, point.z) },
            confidence = pose.confidence,
            timestampMs = pose.timestampMs,
            source = source,
            handedness = handedness,
            targetFinger = targetFinger,
            isMirrored = isMirrored,
            rotationDegrees = rotationDegrees,
        )

    fun fromImageLandmarks(
        frameWidth: Int,
        frameHeight: Int,
        landmarks: List<TrackedLandmark>,
        confidence: Float,
        timestampMs: Long,
        source: FrameSource,
        handedness: Handedness = Handedness.Unknown,
        targetFinger: TargetFinger = TargetFinger.Ring,
        isMirrored: Boolean = false,
        rotationDegrees: Int = 0,
    ): TrackedHandFrame {
        val safeWidth = frameWidth.coerceAtLeast(1)
        val safeHeight = frameHeight.coerceAtLeast(1)
        val normalizedRotation = rotationDegrees.normalizedRightAngle()
        val orientedSize =
            if (normalizedRotation == 90 || normalizedRotation == 270) {
                safeHeight to safeWidth
            } else {
                safeWidth to safeHeight
            }
        return TrackedHandFrame(
            frameWidth = orientedSize.first,
            frameHeight = orientedSize.second,
            landmarks = landmarks.map { point -> point.transform(safeWidth, safeHeight, isMirrored, normalizedRotation) },
            confidence = confidence.coerceIn(0f, 1f),
            timestampMs = timestampMs,
            source = source,
            handedness = handedness,
            targetFinger = targetFinger,
            isMirrored = isMirrored,
            rotationDegrees = normalizedRotation,
        )
    }

    fun toCorePose(frame: TrackedHandFrame): TryOnHandPoseSnapshot =
        TryOnHandPoseSnapshot(
            frameWidth = frame.frameWidth,
            frameHeight = frame.frameHeight,
            landmarks = frame.landmarks.map { point -> TryOnLandmarkPoint(point.x, point.y, point.z) },
            confidence = frame.confidence,
            timestampMs = frame.timestampMs,
        )

    private fun Int.normalizedRightAngle(): Int {
        val normalized = ((this % FULL_ROTATION_DEGREES) + FULL_ROTATION_DEGREES) % FULL_ROTATION_DEGREES
        return when (normalized) {
            0, 90, 180, 270 -> normalized
            else -> 0
        }
    }

    private fun TrackedLandmark.transform(
        frameWidth: Int,
        frameHeight: Int,
        isMirrored: Boolean,
        rotationDegrees: Int,
    ): TrackedLandmark {
        val mirroredX = if (isMirrored) frameWidth.toFloat() - x else x
        return when (rotationDegrees) {
            90 -> TrackedLandmark(x = frameHeight.toFloat() - y, y = mirroredX, z = z)
            180 -> TrackedLandmark(x = frameWidth.toFloat() - mirroredX, y = frameHeight.toFloat() - y, z = z)
            270 -> TrackedLandmark(x = y, y = frameWidth.toFloat() - mirroredX, z = z)
            else -> TrackedLandmark(x = mirroredX, y = y, z = z)
        }
    }

    private const val FULL_ROTATION_DEGREES = 360
}
