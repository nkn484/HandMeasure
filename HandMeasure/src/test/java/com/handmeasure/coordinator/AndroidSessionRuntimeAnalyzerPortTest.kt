package com.handmeasure.coordinator

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import com.handmeasure.api.TargetFinger
import com.handmeasure.measurement.CardRectangle
import com.handmeasure.measurement.ScaleCalibrator
import com.handmeasure.vision.CardDetection
import com.handmeasure.vision.HandDetection
import com.handmeasure.vision.Landmark2D
import com.handmeasure.vision.Landmark3D
import com.handmeasure.core.measurement.CalibrationStatus
import com.handmeasure.core.measurement.CaptureStep
import com.handmeasure.core.measurement.WidthMeasurementSource
import com.handmeasure.core.session.SessionCardDiagnostics
import com.handmeasure.core.session.SessionFingerMeasurement
import com.handmeasure.core.session.SessionScale
import com.handmeasure.core.session.SessionScaleDiagnostics
import com.handmeasure.core.session.SessionScaleResult
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidSessionRuntimeAnalyzerPortTest {
    @Test
    fun runtimePort_delegatesToFocusedAdapters() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val hand = sampleHand()
        val card = sampleCard()
        val cardDiagnostics =
            SessionCardDiagnostics(
                coverageRatio = 0.31f,
                aspectResidual = 0.05f,
                rectangularityScore = 0.86f,
                edgeSupportScore = 0.75f,
                rectificationConfidence = 0.82f,
            )
        val scaleResult =
            SessionScaleResult(
                scale = SessionScale(mmPerPxX = 0.11, mmPerPxY = 0.12),
                diagnostics =
                    SessionScaleDiagnostics(
                        status = CalibrationStatus.CALIBRATED,
                        notes = listOf("ok"),
                    ),
            )
        val measurement =
            SessionFingerMeasurement(
                widthPx = 123.0,
                widthMm = 14.5,
                usedFallback = false,
                source = WidthMeasurementSource.EDGE_PROFILE,
                validSamples = 6,
                widthVarianceMm = 0.3,
                sampledWidthsMm = listOf(14.2, 14.8),
            )
        val overlayBytes = byteArrayOf(1, 2, 3)

        var handCalls = 0
        var cardCalls = 0
        var poseCalls = 0
        var coplanarityCalls = 0
        var scaleCalls = 0
        var measurementCalls = 0
        var overlayCalls = 0

        val port =
            AndroidSessionRuntimeAnalyzerPort(
                handRuntimeAdapter =
                    HandRuntimeAdapter {
                        handCalls += 1
                        hand
                    },
                cardRuntimeAdapter =
                    object : CardRuntimeAdapter {
                        override fun detect(frame: Bitmap): CardDetection? {
                            cardCalls += 1
                            return card
                        }

                        override fun toCardDiagnostics(card: CardDetection): SessionCardDiagnostics = cardDiagnostics
                    },
                poseRuntimeAdapter =
                    PoseRuntimeAdapter { step, detectedHand ->
                        poseCalls += 1
                        assertThat(step).isEqualTo(CaptureStep.FRONT_PALM)
                        assertThat(detectedHand).isEqualTo(hand)
                        0.8f
                    },
                coplanarityRuntimeAdapter =
                    CoplanarityRuntimeAdapter { detectedHand, detectedCard, frame, targetFinger ->
                        coplanarityCalls += 1
                        assertThat(detectedHand).isEqualTo(hand)
                        assertThat(detectedCard).isEqualTo(card)
                        assertThat(frame).isEqualTo(bitmap)
                        assertThat(targetFinger).isEqualTo(TargetFinger.RING)
                        0.61f
                    },
                scaleRuntimeAdapter =
                    ScaleRuntimeAdapter { detectedCard ->
                        scaleCalls += 1
                        assertThat(detectedCard).isEqualTo(card)
                        scaleResult
                    },
                fingerRuntimeAdapter =
                    FingerRuntimeAdapter { frame, detectedHand, targetFinger, scale ->
                        measurementCalls += 1
                        assertThat(frame).isEqualTo(bitmap)
                        assertThat(detectedHand).isEqualTo(hand)
                        assertThat(targetFinger).isEqualTo(TargetFinger.RING)
                        assertThat(scale).isEqualTo(SessionScale(mmPerPxX = 0.11, mmPerPxY = 0.12))
                        measurement
                    },
                overlayRuntimeAdapter =
                    OverlayRuntimeAdapter { step, frame, detectedHand, detectedCard ->
                        overlayCalls += 1
                        assertThat(step).isEqualTo(CaptureStep.FRONT_PALM)
                        assertThat(frame).isEqualTo(bitmap)
                        assertThat(detectedHand).isEqualTo(hand)
                        assertThat(detectedCard).isEqualTo(card)
                        overlayBytes
                    },
            )

        assertThat(port.detectHand(bitmap)).isEqualTo(hand)
        assertThat(port.detectCard(bitmap)).isEqualTo(card)
        assertThat(port.classifyPose(CaptureStep.FRONT_PALM, hand)).isEqualTo(0.8f)
        assertThat(port.estimateCoplanarity(hand, card, bitmap, TargetFinger.RING)).isEqualTo(0.61f)
        assertThat(port.extractCardDiagnostics(card)).isEqualTo(cardDiagnostics)
        assertThat(port.calibrateScale(card)).isEqualTo(scaleResult)
        assertThat(port.measureFingerWidth(bitmap, hand, TargetFinger.RING, SessionScale(0.11, 0.12))).isEqualTo(measurement)
        assertThat(
            port
                .encodeOverlay(CaptureStep.FRONT_PALM, bitmap, hand, card)
                ?.toList(),
        ).containsExactly(1.toByte(), 2.toByte(), 3.toByte()).inOrder()

        assertThat(handCalls).isEqualTo(1)
        assertThat(cardCalls).isEqualTo(1)
        assertThat(poseCalls).isEqualTo(1)
        assertThat(coplanarityCalls).isEqualTo(1)
        assertThat(scaleCalls).isEqualTo(1)
        assertThat(measurementCalls).isEqualTo(1)
        assertThat(overlayCalls).isEqualTo(1)
    }

    @Test
    fun scaleRuntimeAdapter_mapsScaleCalibrationToCoreModel() {
        val adapter = AndroidScaleRuntimeAdapter(scaleCalibrator = ScaleCalibrator())
        val card =
            CardDetection(
                rectangle =
                    CardRectangle(
                        centerX = 320.0,
                        centerY = 240.0,
                        longSidePx = 856.0,
                        shortSidePx = 539.8,
                        angleDeg = 0.0,
                    ),
                corners = listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f),
                contourAreaScore = 0.9f,
                aspectScore = 0.9f,
                confidence = 0.95f,
                coverageRatio = 0.4f,
                aspectResidual = 0.01f,
                rectangularityScore = 0.92f,
                edgeSupportScore = 0.88f,
                rectificationConfidence = 0.9f,
                perspectiveDistortion = 0.03f,
            )

        val result = adapter.calibrate(card)

        assertThat(result).isNotNull()
        assertThat(result?.scale?.mmPerPxX).isWithin(0.0001).of(0.1)
        assertThat(result?.scale?.mmPerPxY).isWithin(0.0001).of(0.1)
        assertThat(result?.diagnostics?.status).isEqualTo(CalibrationStatus.CALIBRATED)
    }

    private fun sampleHand(): HandDetection =
        HandDetection(
            imageLandmarks = List(21) { index -> Landmark2D(index.toFloat(), index.toFloat()) },
            worldLandmarks = List(21) { Landmark3D(0f, 0f, 0f) },
            handedness = "Right",
            confidence = 0.95f,
        )

    private fun sampleCard(): CardDetection =
        CardDetection(
            rectangle =
                CardRectangle(
                    centerX = 320.0,
                    centerY = 240.0,
                    longSidePx = 820.0,
                    shortSidePx = 520.0,
                    angleDeg = 0.0,
                ),
            corners = listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f),
            contourAreaScore = 0.9f,
            aspectScore = 0.9f,
            confidence = 0.95f,
            coverageRatio = 0.35f,
            aspectResidual = 0.02f,
            rectangularityScore = 0.91f,
            edgeSupportScore = 0.87f,
            rectificationConfidence = 0.9f,
            perspectiveDistortion = 0.05f,
        )
}
