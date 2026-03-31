package com.handmeasure.core.session

import com.handmeasure.core.measurement.CaptureStep

data class StepRuntimeAnalysisRequest<FrameT>(
    val step: CaptureStep,
    val frame: FrameT,
    val currentScale: SessionScale,
    val overlayEnabled: Boolean = false,
)

interface SessionRuntimeAnalyzerPort<FrameT, HandT, CardT, TargetFingerT> {
    fun detectHand(frame: FrameT): HandT?

    fun detectCard(frame: FrameT): CardT?

    fun classifyPose(
        step: CaptureStep,
        hand: HandT,
    ): Float?

    fun estimateCoplanarity(
        hand: HandT?,
        card: CardT?,
        frame: FrameT,
        targetFinger: TargetFingerT,
    ): Float

    fun extractCardDiagnostics(card: CardT): SessionCardDiagnostics

    fun calibrateScale(card: CardT): SessionScaleResult?

    fun measureFingerWidth(
        frame: FrameT,
        hand: HandT,
        targetFinger: TargetFingerT,
        scale: SessionScale,
    ): SessionFingerMeasurement?

    fun encodeOverlay(
        step: CaptureStep,
        frame: FrameT,
        hand: HandT?,
        card: CardT?,
    ): ByteArray?
}
