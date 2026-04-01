package com.handmeasure.engine

import com.handmeasure.api.CaptureStep
import com.handmeasure.api.HandMeasureResult
import com.handmeasure.coordinator.AndroidFingerMeasurementPort
import com.handmeasure.coordinator.DebugFrameAnnotator
import com.handmeasure.coordinator.FrameSignalEstimator
import com.handmeasure.coordinator.MeasurementResultAssembler
import com.handmeasure.coordinator.MeasurementSessionProcessor
import com.handmeasure.coordinator.PoseTarget
import com.handmeasure.coordinator.SessionProcessingOutput
import com.handmeasure.engine.compat.MeasurementEngineApiMapper
import com.handmeasure.engine.model.MeasurementEngineConfig
import com.handmeasure.engine.model.MeasurementEngineProcessingResult
import com.handmeasure.engine.model.MeasurementEngineStepCandidate
import com.handmeasure.flow.StepCandidate
import com.handmeasure.measurement.FingerMeasurementFusion
import com.handmeasure.measurement.OpenCvSessionFingerMeasurementPort
import com.handmeasure.measurement.ResultReliabilityPolicy
import com.handmeasure.measurement.ScaleCalibrator
import com.handmeasure.measurement.TableRingSizeMapper
import com.handmeasure.protocol.CaptureProtocols
import com.handmeasure.vision.HandLandmarkEngine
import com.handmeasure.vision.OpenCvReferenceCardDetector
import com.handmeasure.vision.PoseClassifier
import com.handmeasure.vision.ReferenceCardDetector

internal fun interface MeasurementEngineSessionProcessorPort {
    fun process(stepResults: List<StepCandidate>): SessionProcessingOutput
}

internal fun interface MeasurementEngineResultAssemblerPort {
    fun assemble(
        completedSteps: List<StepCandidate>,
        processing: SessionProcessingOutput,
    ): HandMeasureResult
}

internal class MeasurementEngine(
    config: MeasurementEngineConfig,
    handLandmarkEngine: HandLandmarkEngine,
    referenceCardDetector: ReferenceCardDetector = OpenCvReferenceCardDetector(),
    poseClassifier: PoseClassifier = PoseClassifier(),
    scaleCalibrator: ScaleCalibrator = ScaleCalibrator(),
    fingerMeasurementPort: AndroidFingerMeasurementPort = OpenCvSessionFingerMeasurementPort(),
    fingerMeasurementFusion: FingerMeasurementFusion = FingerMeasurementFusion(),
    reliabilityPolicy: ResultReliabilityPolicy = ResultReliabilityPolicy(),
    ringSizeMapper: TableRingSizeMapper = TableRingSizeMapper(),
    frameSignalEstimator: FrameSignalEstimator = FrameSignalEstimator(),
    frameAnnotator: DebugFrameAnnotator = DebugFrameAnnotator(),
    private val mapper: MeasurementEngineApiMapper = MeasurementEngineApiMapper(),
    sessionProcessorOverride: MeasurementEngineSessionProcessorPort? = null,
    resultAssemblerOverride: MeasurementEngineResultAssemblerPort? = null,
) {
    private val apiConfig = mapper.toApiConfig(config)
    private val poseTargets: Map<CaptureStep, PoseTarget> =
        CaptureProtocols.steps(apiConfig.protocol).associateBy { it.step }.mapValues { it.value.poseTarget }
    private val sessionProcessor: MeasurementEngineSessionProcessorPort =
        sessionProcessorOverride
            ?: MeasurementEngineSessionProcessorPort { steps ->
                MeasurementSessionProcessor(
                    config = apiConfig,
                    handLandmarkEngine = handLandmarkEngine,
                    referenceCardDetector = referenceCardDetector,
                    poseClassifier = poseClassifier,
                    scaleCalibrator = scaleCalibrator,
                    fingerMeasurementPort = fingerMeasurementPort,
                    frameSignalEstimator = frameSignalEstimator,
                    frameAnnotator = frameAnnotator,
                    poseTargets = poseTargets,
                ).process(steps)
            }
    private val resultAssembler: MeasurementEngineResultAssemblerPort =
        resultAssemblerOverride
            ?: MeasurementEngineResultAssemblerPort { completedSteps, processing ->
                MeasurementResultAssembler(
                    config = apiConfig,
                    fingerMeasurementFusion = fingerMeasurementFusion,
                    reliabilityPolicy = reliabilityPolicy,
                    ringSizeMapper = ringSizeMapper,
                ).assemble(completedSteps, processing)
            }

    fun process(stepCandidates: List<MeasurementEngineStepCandidate>): MeasurementEngineProcessingResult {
        val completedSteps = stepCandidates.map(mapper::toApiStepCandidate)
        val processing = sessionProcessor.process(completedSteps)
        val apiResult = resultAssembler.assemble(completedSteps, processing)
        return MeasurementEngineProcessingResult(
            result = mapper.toEngineResult(apiResult),
            overlays = processing.overlays.map(mapper::toEngineOverlay),
        )
    }
}
