package com.handmeasure.coordinator

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import com.handmeasure.api.TargetFinger
import com.handmeasure.measurement.FingerMeasurementEngine
import com.handmeasure.vision.HandDetection
import com.handmeasure.vision.Landmark2D
import com.handmeasure.vision.Landmark3D
import com.handmeasure.core.measurement.WidthMeasurementSource
import com.handmeasure.core.session.SessionFingerMeasurement
import com.handmeasure.core.session.SessionFingerMeasurementPort
import com.handmeasure.core.session.SessionFingerMeasurementRequest
import com.handmeasure.core.session.SessionScale
import org.junit.Test

class AndroidFingerRuntimeAdapterTest {
    @Test
    fun measure_buildsCoreRequestAndDelegatesToPort() {
        val frame = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val hand = sampleHand()
        val scale = SessionScale(mmPerPxX = 0.13, mmPerPxY = 0.14)
        var capturedRequest: SessionFingerMeasurementRequest<Bitmap, HandDetection, TargetFinger>? = null
        val expected =
            SessionFingerMeasurement(
                widthPx = 120.0,
                widthMm = 16.8,
                usedFallback = false,
                source = WidthMeasurementSource.EDGE_PROFILE,
                validSamples = 5,
                widthVarianceMm = 0.3,
                sampledWidthsMm = listOf(16.7, 16.9),
            )
        val adapter =
            AndroidFingerRuntimeAdapter(
                fingerMeasurementPort =
                    AndroidFingerMeasurementPort { request ->
                        capturedRequest = request
                        expected
                    },
            )

        val result = adapter.measure(frame, hand, TargetFinger.RING, scale)

        assertThat(result).isEqualTo(expected)
        assertThat(capturedRequest).isNotNull()
        assertThat(capturedRequest?.frame).isEqualTo(frame)
        assertThat(capturedRequest?.hand).isEqualTo(hand)
        assertThat(capturedRequest?.targetFinger).isEqualTo(TargetFinger.RING)
        assertThat(capturedRequest?.scale).isEqualTo(scale)
    }

    @Test
    fun coordinator_constructorDependsOnPortNotEngine() {
        val constructors = HandMeasureCoordinator::class.java.declaredConstructors

        val dependsOnEngine =
            constructors.any { constructor ->
                constructor.parameterTypes.any { parameterType ->
                    parameterType == FingerMeasurementEngine::class.java
                }
            }
        val dependsOnPort =
            constructors.any { constructor ->
                constructor.parameterTypes.any { parameterType ->
                    parameterType == SessionFingerMeasurementPort::class.java
                }
            }

        assertThat(dependsOnEngine).isFalse()
        assertThat(dependsOnPort).isTrue()
    }

    private fun sampleHand(): HandDetection =
        HandDetection(
            imageLandmarks = List(21) { index -> Landmark2D(index.toFloat(), index.toFloat()) },
            worldLandmarks = List(21) { Landmark3D(0f, 0f, 0f) },
            handedness = "Right",
            confidence = 0.95f,
        )
}
