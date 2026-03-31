package com.handmeasure.core.session

class StepRuntimeAnalysisUseCase<FrameT, HandT, CardT, TargetFingerT>(
    private val runtimeAnalyzerPort: SessionRuntimeAnalyzerPort<FrameT, HandT, CardT, TargetFingerT>,
    private val targetFinger: TargetFingerT,
) {
    fun analyze(request: StepRuntimeAnalysisRequest<FrameT>): SessionStepAnalysis {
        val hand = runtimeAnalyzerPort.detectHand(request.frame)
        val card = runtimeAnalyzerPort.detectCard(request.frame)
        val poseScore = hand?.let { runtimeAnalyzerPort.classifyPose(request.step, it) }
        val coplanarityProxyScore =
            runtimeAnalyzerPort.estimateCoplanarity(
                hand = hand,
                card = card,
                frame = request.frame,
                targetFinger = targetFinger,
            )
        val cardDiagnostics = card?.let(runtimeAnalyzerPort::extractCardDiagnostics)
        val scaleResult = card?.let(runtimeAnalyzerPort::calibrateScale)
        val effectiveScale = scaleResult?.scale ?: request.currentScale
        val measurement =
            hand?.let { detectedHand ->
                runtimeAnalyzerPort.measureFingerWidth(
                    frame = request.frame,
                    hand = detectedHand,
                    targetFinger = targetFinger,
                    scale = effectiveScale,
                )
            }
        val overlayFrame =
            if (request.overlayEnabled) {
                runtimeAnalyzerPort
                    .encodeOverlay(
                        step = request.step,
                        frame = request.frame,
                        hand = hand,
                        card = card,
                    )?.let { jpegBytes ->
                        SessionOverlayFrame(
                            stepName = request.step.name,
                            jpegBytes = jpegBytes,
                        )
                    }
            } else {
                null
            }

        return SessionStepAnalysis(
            poseScoreOverride = poseScore,
            coplanarityProxyScore = coplanarityProxyScore,
            cardDiagnostics = cardDiagnostics,
            scaleResult = scaleResult,
            measurement = measurement,
            overlayFrame = overlayFrame,
        )
    }
}
