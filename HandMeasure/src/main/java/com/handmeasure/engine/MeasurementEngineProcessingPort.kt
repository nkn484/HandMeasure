package com.handmeasure.engine

import com.handmeasure.engine.model.MeasurementEngineProcessingResult
import com.handmeasure.engine.model.MeasurementEngineStepCandidate

internal fun interface MeasurementEngineProcessingPort {
    fun process(stepCandidates: List<MeasurementEngineStepCandidate>): MeasurementEngineProcessingResult
}
