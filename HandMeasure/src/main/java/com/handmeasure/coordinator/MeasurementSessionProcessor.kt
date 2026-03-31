package com.handmeasure.coordinator

import com.handmeasure.api.CalibrationStatus
import com.handmeasure.api.CaptureStep
import com.handmeasure.api.HandMeasureConfig
import com.handmeasure.api.HandMeasureWarning
import com.handmeasure.api.StepDiagnostics
import com.handmeasure.flow.StepCandidate
import com.handmeasure.measurement.ScaleCalibrator
import com.handmeasure.measurement.StepMeasurement
import com.handmeasure.vision.HandLandmarkEngine
import com.handmeasure.vision.PoseClassifier
import com.handmeasure.vision.ReferenceCardDetector
import com.handmeasure.core.session.MeasurementSessionFinalizationUseCase
import com.handmeasure.core.session.MeasurementSessionProcessingUseCase

internal data class SessionProcessingOutput(
    val warnings: Set<HandMeasureWarning>,
    val stepMeasurements: List<StepMeasurement>,
    val stepDiagnostics: List<StepDiagnostics>,
    val bestScaleMmPerPxX: Double,
    val bestScaleMmPerPxY: Double,
    val calibrationStatus: CalibrationStatus,
    val frontalWidthPx: Double,
    val thicknessSamples: List<Double>,
    val debugNotes: List<String>,
    val calibrationNotes: List<String>,
    val overlays: List<DebugOverlayFrame>,
)

internal class MeasurementSessionProcessor(
    private val config: HandMeasureConfig,
    private val handLandmarkEngine: HandLandmarkEngine,
    private val referenceCardDetector: ReferenceCardDetector,
    private val poseClassifier: PoseClassifier,
    private val scaleCalibrator: ScaleCalibrator,
    private val fingerMeasurementPort: AndroidFingerMeasurementPort,
    private val frameSignalEstimator: FrameSignalEstimator,
    private val frameAnnotator: DebugFrameAnnotator,
    private val poseTargets: Map<CaptureStep, PoseTarget>,
) {
    private val mapper = AndroidSessionProcessingMapper()
    private val processingUseCase =
        MeasurementSessionProcessingUseCase(
            finalizationUseCase =
                MeasurementSessionFinalizationUseCase(
                    stepAnalyzer =
                        AndroidSessionStepAnalyzer(
                            config = config,
                            handLandmarkEngine = handLandmarkEngine,
                            referenceCardDetector = referenceCardDetector,
                            poseClassifier = poseClassifier,
                            scaleCalibrator = scaleCalibrator,
                            fingerMeasurementPort = fingerMeasurementPort,
                            frameSignalEstimator = frameSignalEstimator,
                            frameAnnotator = frameAnnotator,
                            poseTargets = poseTargets,
                        ),
                ),
        )

    fun process(stepResults: List<StepCandidate>): SessionProcessingOutput {
        val request = mapper.toCoreRequest(stepResults, config)
        val coreResult = processingUseCase.process(request)
        return mapper.toAndroidOutput(coreResult)
    }
}
