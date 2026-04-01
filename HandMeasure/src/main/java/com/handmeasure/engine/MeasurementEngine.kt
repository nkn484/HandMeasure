package com.handmeasure.engine

import com.handmeasure.engine.model.MeasurementEngineProcessingResult
import com.handmeasure.engine.model.MeasurementEngineStepCandidate

internal class MeasurementEngine(
    private val processingPort: MeasurementEngineProcessingPort,
) {
    fun process(stepCandidates: List<MeasurementEngineStepCandidate>): MeasurementEngineProcessingResult =
        processingPort.process(stepCandidates)
}
