package com.handmeasure.measurement

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import com.handmeasure.api.TargetFinger
import com.handmeasure.vision.HandDetection
import com.handmeasure.vision.Landmark2D
import com.handmeasure.vision.Landmark3D
import com.handmeasure.core.measurement.WidthMeasurementSource
import com.handmeasure.core.session.SessionFingerMeasurementRequest
import com.handmeasure.core.session.SessionScale
import org.junit.Test

class OpenCvSessionFingerMeasurementPortTest {
    @Test
    fun measureVisibleWidth_delegatesAndMapsRequestAndResult() {
        val frame = Bitmap.createBitmap(6, 6, Bitmap.Config.ARGB_8888)
        val hand = sampleHand()
        val request =
            SessionFingerMeasurementRequest(
                frame = frame,
                hand = hand,
                targetFinger = TargetFinger.RING,
                scale = SessionScale(mmPerPxX = 0.11, mmPerPxY = 0.12),
            )
        var capturedRequest: OpenCvFingerMeasurementRequest? = null
        val port =
            OpenCvSessionFingerMeasurementPort(
                executor =
                    OpenCvFingerMeasurementExecutor { executionRequest ->
                        capturedRequest = executionRequest
                        FingerWidthMeasurement(
                            widthPx = 140.0,
                            widthMm = 16.1,
                            usedFallback = false,
                            source = WidthMeasurementSource.EDGE_PROFILE.toAndroidSource(),
                            validSamples = 5,
                            widthVarianceMm = 0.4,
                            sampledWidthsMm = listOf(16.0, 16.2),
                        )
                    },
            )

        val result = port.measureVisibleWidth(request)

        assertThat(capturedRequest?.frame).isEqualTo(frame)
        assertThat(capturedRequest?.hand).isEqualTo(hand)
        assertThat(capturedRequest?.targetFinger).isEqualTo(TargetFinger.RING)
        assertThat(capturedRequest?.scale?.mmPerPxX).isEqualTo(0.11)
        assertThat(capturedRequest?.scale?.mmPerPxY).isEqualTo(0.12)
        assertThat(result.widthMm).isEqualTo(16.1)
        assertThat(result.source).isEqualTo(WidthMeasurementSource.EDGE_PROFILE)
        assertThat(result.usedFallback).isFalse()
        assertThat(result.validSamples).isEqualTo(5)
    }

    @Test
    fun mapper_mapsScaleAndMeasurementSource() {
        val mapper = OpenCvFingerMeasurementMapper()

        val metricScale = mapper.toMetricScale(SessionScale(mmPerPxX = 0.13, mmPerPxY = 0.14))
        val mappedMeasurement =
            mapper.toCoreMeasurement(
                FingerWidthMeasurement(
                    widthPx = 120.0,
                    widthMm = 14.0,
                    usedFallback = true,
                    source = com.handmeasure.measurement.WidthMeasurementSource.LANDMARK_HEURISTIC,
                    validSamples = 0,
                    widthVarianceMm = 999.0,
                    sampledWidthsMm = emptyList(),
                ),
            )

        assertThat(metricScale.mmPerPxX).isEqualTo(0.13)
        assertThat(metricScale.mmPerPxY).isEqualTo(0.14)
        assertThat(mappedMeasurement.source).isEqualTo(WidthMeasurementSource.LANDMARK_HEURISTIC)
        assertThat(mappedMeasurement.usedFallback).isTrue()
    }

    private fun sampleHand(): HandDetection =
        HandDetection(
            imageLandmarks = List(21) { index -> Landmark2D(index.toFloat(), index.toFloat()) },
            worldLandmarks = List(21) { Landmark3D(0f, 0f, 0f) },
            handedness = "Right",
            confidence = 0.95f,
        )

    private fun WidthMeasurementSource.toAndroidSource(): com.handmeasure.measurement.WidthMeasurementSource =
        when (this) {
            WidthMeasurementSource.EDGE_PROFILE -> com.handmeasure.measurement.WidthMeasurementSource.EDGE_PROFILE
            WidthMeasurementSource.LANDMARK_HEURISTIC -> com.handmeasure.measurement.WidthMeasurementSource.LANDMARK_HEURISTIC
            WidthMeasurementSource.DEFAULT_HEURISTIC -> com.handmeasure.measurement.WidthMeasurementSource.DEFAULT_HEURISTIC
        }
}
