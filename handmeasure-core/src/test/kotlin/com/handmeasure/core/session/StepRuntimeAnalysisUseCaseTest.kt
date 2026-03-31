package com.handmeasure.core.session

import com.google.common.truth.Truth.assertThat
import com.handmeasure.core.measurement.CalibrationStatus
import com.handmeasure.core.measurement.CaptureStep
import com.handmeasure.core.measurement.WidthMeasurementSource
import org.junit.Test

class StepRuntimeAnalysisUseCaseTest {
    @Test
    fun analyze_buildsCoreStepAnalysisFromRuntimePort() {
        val port =
            FakeRuntimePort(
                scaleResult =
                    SessionScaleResult(
                        scale = SessionScale(mmPerPxX = 0.09, mmPerPxY = 0.1),
                        diagnostics =
                            SessionScaleDiagnostics(
                                status = CalibrationStatus.CALIBRATED,
                                notes = listOf("ok"),
                            ),
                    ),
                overlayBytes = byteArrayOf(9, 8, 7),
            )
        val useCase = StepRuntimeAnalysisUseCase(port, targetFinger = "RING")

        val result =
            useCase.analyze(
                StepRuntimeAnalysisRequest(
                    step = CaptureStep.FRONT_PALM,
                    frame = "frame-1",
                    currentScale = SessionScale(mmPerPxX = 0.12, mmPerPxY = 0.12),
                    overlayEnabled = true,
                ),
            )

        assertThat(result.poseScoreOverride).isEqualTo(0.81f)
        assertThat(result.coplanarityProxyScore).isEqualTo(0.62f)
        assertThat(result.cardDiagnostics?.coverageRatio).isEqualTo(0.3f)
        assertThat(result.scaleResult?.scale?.mmPerPxX).isEqualTo(0.09)
        assertThat(result.measurement?.widthMm).isEqualTo(17.5)
        assertThat(result.overlayFrame?.stepName).isEqualTo(CaptureStep.FRONT_PALM.name)
        assertThat(result.overlayFrame?.jpegBytes?.toList()).containsExactly(9, 8, 7).inOrder()
        assertThat(port.lastMeasuredScale).isEqualTo(SessionScale(mmPerPxX = 0.09, mmPerPxY = 0.1))
        assertThat(port.overlayCalls).isEqualTo(1)
    }

    @Test
    fun analyze_usesCurrentScaleWhenCalibrationMissing() {
        val port = FakeRuntimePort(scaleResult = null, overlayBytes = null)
        val useCase = StepRuntimeAnalysisUseCase(port, targetFinger = "RING")
        val currentScale = SessionScale(mmPerPxX = 0.13, mmPerPxY = 0.14)

        val result =
            useCase.analyze(
                StepRuntimeAnalysisRequest(
                    step = CaptureStep.RIGHT_OBLIQUE,
                    frame = "frame-2",
                    currentScale = currentScale,
                    overlayEnabled = false,
                ),
            )

        assertThat(result.scaleResult).isNull()
        assertThat(result.measurement?.widthMm).isEqualTo(17.5)
        assertThat(result.overlayFrame).isNull()
        assertThat(port.lastMeasuredScale).isEqualTo(currentScale)
        assertThat(port.overlayCalls).isEqualTo(0)
    }

    private class FakeRuntimePort(
        private val scaleResult: SessionScaleResult?,
        private val overlayBytes: ByteArray?,
    ) : SessionRuntimeAnalyzerPort<String, String, String, String> {
        var lastMeasuredScale: SessionScale? = null
        var overlayCalls: Int = 0

        override fun detectHand(frame: String): String? = "hand"

        override fun detectCard(frame: String): String? = "card"

        override fun classifyPose(
            step: CaptureStep,
            hand: String,
        ): Float? = 0.81f

        override fun estimateCoplanarity(
            hand: String?,
            card: String?,
            frame: String,
            targetFinger: String,
        ): Float = 0.62f

        override fun extractCardDiagnostics(card: String): SessionCardDiagnostics =
            SessionCardDiagnostics(
                coverageRatio = 0.3f,
                aspectResidual = 0.07f,
                rectangularityScore = 0.88f,
                edgeSupportScore = 0.79f,
                rectificationConfidence = 0.86f,
            )

        override fun calibrateScale(card: String): SessionScaleResult? = scaleResult

        override fun measureFingerWidth(
            frame: String,
            hand: String,
            targetFinger: String,
            scale: SessionScale,
        ): SessionFingerMeasurement {
            lastMeasuredScale = scale
            return SessionFingerMeasurement(
                widthPx = 122.0,
                widthMm = 17.5,
                usedFallback = false,
                source = WidthMeasurementSource.EDGE_PROFILE,
                validSamples = 6,
                widthVarianceMm = 0.3,
                sampledWidthsMm = listOf(17.4, 17.6),
            )
        }

        override fun encodeOverlay(
            step: CaptureStep,
            frame: String,
            hand: String?,
            card: String?,
        ): ByteArray? {
            overlayCalls += 1
            return overlayBytes
        }
    }
}
