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
}
