package com.handmeasure.engine.factory

import com.handmeasure.api.CaptureStep
import com.handmeasure.coordinator.AndroidFingerMeasurementPort
import com.handmeasure.coordinator.DebugFrameAnnotator
import com.handmeasure.coordinator.FrameSignalEstimator
import com.handmeasure.coordinator.MeasurementResultAssembler
import com.handmeasure.coordinator.MeasurementSessionProcessor
import com.handmeasure.coordinator.PoseTarget
import com.handmeasure.engine.MeasurementEngine
import com.handmeasure.engine.MeasurementEngineProcessingPort
import com.handmeasure.engine.compat.MeasurementEngineApiMapper
import com.handmeasure.engine.model.MeasurementEngineConfig
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

internal object AndroidMeasurementEngineFactory {
    fun create(
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
        mapper: MeasurementEngineApiMapper = MeasurementEngineApiMapper(),
        processingPortOverride: MeasurementEngineProcessingPort? = null,
    ): MeasurementEngine {
        val apiConfig = mapper.toApiConfig(config)
        val poseTargets: Map<CaptureStep, PoseTarget> =
            CaptureProtocols.steps(apiConfig.protocol).associateBy { it.step }.mapValues { it.value.poseTarget }
        val processingPort =
            processingPortOverride
                ?: run {
                    val sessionProcessor =
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
                        )
                    val resultAssembler =
                        MeasurementResultAssembler(
                            config = apiConfig,
                            fingerMeasurementFusion = fingerMeasurementFusion,
                            reliabilityPolicy = reliabilityPolicy,
                            ringSizeMapper = ringSizeMapper,
                        )
                    MeasurementEngineProcessingPort { stepCandidates ->
                        val completedSteps = stepCandidates.map(mapper::toApiStepCandidate)
                        val processing = sessionProcessor.process(completedSteps)
                        val apiResult = resultAssembler.assemble(completedSteps, processing)
                        mapper.toEngineProcessingResult(
                            result = apiResult,
                            overlays = processing.overlays,
                        )
                    }
                }
        return MeasurementEngine(
            processingPort = processingPort,
        )
    }
}
