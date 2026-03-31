package com.handmeasure.core.session

data class MeasurementSessionProcessingRequest(
    val stepCandidates: List<SessionStepCandidate>,
    val thresholds: SessionQualityThresholds,
)

class MeasurementSessionProcessingUseCase(
    private val finalizationUseCase: MeasurementSessionFinalizationUseCase,
) {
    constructor(stepAnalyzer: SessionStepAnalyzer) : this(MeasurementSessionFinalizationUseCase(stepAnalyzer))

    fun process(request: MeasurementSessionProcessingRequest): SessionProcessingResult =
        finalizationUseCase.process(
            stepCandidates = request.stepCandidates,
            thresholds = request.thresholds,
        )
}
