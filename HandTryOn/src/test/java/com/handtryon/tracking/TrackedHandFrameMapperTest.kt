package com.handtryon.tracking

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrackedHandFrameMapperTest {
    @Test
    fun fromCameraXSnapshot_mirrorsFrontCameraAndKeepsFrameSize() {
        val frame =
            TrackedHandFrameMapper.fromImageLandmarks(
                frameWidth = 100,
                frameHeight = 200,
                landmarks = listOf(TrackedLandmark(x = 20f, y = 40f, z = 0.1f)),
                confidence = 0.9f,
                timestampMs = 1L,
                source = FrameSource.CameraX,
                isMirrored = true,
                rotationDegrees = 0,
            )

        assertThat(frame.frameWidth).isEqualTo(100)
        assertThat(frame.frameHeight).isEqualTo(200)
        assertThat(frame.landmarks.single().x).isEqualTo(80f)
        assertThat(frame.landmarks.single().y).isEqualTo(40f)
        assertThat(frame.isMirrored).isTrue()
    }

    @Test
    fun fromArCoreCpuImageSnapshot_rotatesRightAngleAndSwapsFrameSize() {
        val frame =
            TrackedHandFrameMapper.fromImageLandmarks(
                frameWidth = 100,
                frameHeight = 200,
                landmarks = listOf(TrackedLandmark(x = 20f, y = 40f)),
                confidence = 0.9f,
                timestampMs = 1L,
                source = FrameSource.ARCoreCpuImage,
                rotationDegrees = 90,
            )

        assertThat(frame.frameWidth).isEqualTo(200)
        assertThat(frame.frameHeight).isEqualTo(100)
        assertThat(frame.landmarks.single().x).isEqualTo(160f)
        assertThat(frame.landmarks.single().y).isEqualTo(20f)
    }

    @Test
    fun fromImageLandmarks_normalizesInvalidRotationToZero() {
        val frame =
            TrackedHandFrameMapper.fromImageLandmarks(
                frameWidth = 100,
                frameHeight = 200,
                landmarks = listOf(TrackedLandmark(x = 20f, y = 40f)),
                confidence = 0.9f,
                timestampMs = 1L,
                source = FrameSource.CameraX,
                rotationDegrees = 75,
            )

        assertThat(frame.rotationDegrees).isEqualTo(0)
        assertThat(frame.frameWidth).isEqualTo(100)
        assertThat(frame.frameHeight).isEqualTo(200)
    }

    @Test
    fun fromImageLandmarks_keepsHandedness() {
        val frame =
            TrackedHandFrameMapper.fromImageLandmarks(
                frameWidth = 100,
                frameHeight = 100,
                landmarks = listOf(TrackedLandmark(x = 20f, y = 40f)),
                confidence = 0.9f,
                timestampMs = 1L,
                source = FrameSource.Replay,
                handedness = Handedness.Right,
            )

        assertThat(frame.handedness).isEqualTo(Handedness.Right)
    }

    @Test
    fun fromImageLandmarks_appliesMirrorAndRotation180() {
        val frame =
            TrackedHandFrameMapper.fromImageLandmarks(
                frameWidth = 100,
                frameHeight = 200,
                landmarks = listOf(TrackedLandmark(x = 10f, y = 30f)),
                confidence = 0.9f,
                timestampMs = 1L,
                source = FrameSource.CameraX,
                isMirrored = true,
                rotationDegrees = 180,
            )

        assertThat(frame.landmarks.single().x).isEqualTo(10f)
        assertThat(frame.landmarks.single().y).isEqualTo(170f)
        assertThat(frame.rotationDegrees).isEqualTo(180)
    }

    @Test
    fun fromArCoreSnapshot_setsSourceAndRotation() {
        val frame =
            TrackedHandFrameMapper.fromArCoreCpuImageSnapshot(
                pose =
                    com.handtryon.domain.HandPoseSnapshot(
                        frameWidth = 120,
                        frameHeight = 90,
                        landmarks = listOf(com.handtryon.domain.LandmarkPoint(20f, 30f)),
                        confidence = 0.88f,
                        timestampMs = 7L,
                    ),
                handedness = Handedness.Left,
                imageRotationDegrees = 270,
            )

        assertThat(frame.source).isEqualTo(FrameSource.ARCoreCpuImage)
        assertThat(frame.rotationDegrees).isEqualTo(270)
        assertThat(frame.handedness).isEqualTo(Handedness.Left)
    }
}
