package com.handmeasure.coordinator

import com.handmeasure.api.CalibrationStatus
import com.handmeasure.api.CaptureStep
import com.handmeasure.api.HandMeasureConfig
import com.handmeasure.api.HandMeasureWarning
import com.handmeasure.api.MeasurementSource
import com.handmeasure.api.StepDiagnostics
import com.handmeasure.flow.StepCandidate
import com.handmeasure.measurement.FingerMeasurementEngine
import com.handmeasure.measurement.ScaleCalibrator
import com.handmeasure.measurement.StepMeasurement
import com.handmeasure.measurement.WidthMeasurementSource
import com.handmeasure.vision.HandLandmarkEngine
import com.handmeasure.vision.PoseClassifier
import com.handmeasure.vision.ReferenceCardDetector
import com.handmeasure.core.measurement.CalibrationStatus as CoreCalibrationStatus
import com.handmeasure.core.measurement.CaptureStep as CoreCaptureStep
import com.handmeasure.core.measurement.HandMeasureWarning as CoreHandMeasureWarning
import com.handmeasure.core.measurement.MeasurementSource as CoreMeasurementSource
import com.handmeasure.core.measurement.StepMeasurement as CoreStepMeasurement
import com.handmeasure.core.measurement.WidthMeasurementSource as CoreWidthMeasurementSource
import com.handmeasure.core.session.MeasurementSessionFinalizationUseCase
import com.handmeasure.core.session.SessionProcessingResult as CoreSessionProcessingResult
import com.handmeasure.core.session.SessionQualityThresholds
import com.handmeasure.core.session.SessionStepCandidate
import com.handmeasure.core.session.SessionStepDiagnostics as CoreSessionStepDiagnostics

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
    private val fingerMeasurementEngine: FingerMeasurementEngine,
    private val frameSignalEstimator: FrameSignalEstimator,
    private val frameAnnotator: DebugFrameAnnotator,
    private val poseTargets: Map<CaptureStep, PoseTarget>,
) {
    private val finalizationUseCase =
        MeasurementSessionFinalizationUseCase(
            stepAnalyzer =
                AndroidSessionStepAnalyzer(
                    config = config,
                    handLandmarkEngine = handLandmarkEngine,
                    referenceCardDetector = referenceCardDetector,
                    poseClassifier = poseClassifier,
                    scaleCalibrator = scaleCalibrator,
                    fingerMeasurementEngine = fingerMeasurementEngine,
                    frameSignalEstimator = frameSignalEstimator,
                    frameAnnotator = frameAnnotator,
                    poseTargets = poseTargets,
                ),
        )

    fun process(stepResults: List<StepCandidate>): SessionProcessingOutput {
        val coreResult =
            finalizationUseCase.process(
                stepCandidates = stepResults.map { it.toCoreCandidate() },
                thresholds =
                    SessionQualityThresholds(
                        cardMinScore = config.qualityThresholds.cardMinScore,
                        lightingMinScore = config.qualityThresholds.lightingMinScore,
                        blurMinScore = config.qualityThresholds.blurMinScore,
                        motionMinScore = config.qualityThresholds.motionMinScore,
                    ),
            )
        return coreResult.toAndroidOutput()
    }

    private fun StepCandidate.toCoreCandidate(): SessionStepCandidate =
        SessionStepCandidate(
            step = step.toCoreStep(),
            frameBytes = frameBytes,
            qualityScore = qualityScore,
            poseScore = poseScore,
            cardScore = cardScore,
            handScore = handScore,
            blurScore = blurScore,
            motionScore = motionScore,
            lightingScore = lightingScore,
            confidencePenaltyReasons = confidencePenaltyReasons,
        )

    private fun CoreSessionProcessingResult.toAndroidOutput(): SessionProcessingOutput =
        SessionProcessingOutput(
            warnings = warnings.map { it.toApiWarning() }.toSet(),
            stepMeasurements = stepMeasurements.map { it.toAndroidStepMeasurement() },
            stepDiagnostics = stepDiagnostics.map { it.toApiStepDiagnostics() },
            bestScaleMmPerPxX = bestScaleMmPerPxX,
            bestScaleMmPerPxY = bestScaleMmPerPxY,
            calibrationStatus = calibrationStatus.toApiCalibrationStatus(),
            frontalWidthPx = frontalWidthPx,
            thicknessSamples = thicknessSamples,
            debugNotes = debugNotes,
            calibrationNotes = calibrationNotes,
            overlays =
                overlays.map { overlay ->
                    DebugOverlayFrame(
                        stepName = overlay.stepName,
                        jpegBytes = overlay.jpegBytes,
                    )
                },
        )

    private fun CoreSessionStepDiagnostics.toApiStepDiagnostics(): StepDiagnostics =
        StepDiagnostics(
            step = step.toApiStep(),
            handScore = handScore,
            cardScore = cardScore,
            poseScore = poseScore,
            blurScore = blurScore,
            motionScore = motionScore,
            lightingScore = lightingScore,
            cardCoverageRatio = cardCoverageRatio,
            cardAspectResidual = cardAspectResidual,
            cardRectangularityScore = cardRectangularityScore,
            cardEdgeSupportScore = cardEdgeSupportScore,
            cardRectificationConfidence = cardRectificationConfidence,
            scaleMmPerPxX = scaleMmPerPxX,
            scaleMmPerPxY = scaleMmPerPxY,
            widthSamplesMm = widthSamplesMm,
            widthVarianceMm = widthVarianceMm,
            accepted = accepted,
            rejectedReason = rejectedReason,
            confidencePenaltyReasons = confidencePenaltyReasons,
            measurementSource = measurementSource.toApiMeasurementSource(),
            usedFallback = usedFallback,
            coplanarityProxyScore = coplanarityProxyScore,
            coplanarityProxyKind = coplanarityProxyKind,
        )

    private fun CoreStepMeasurement.toAndroidStepMeasurement(): StepMeasurement =
        StepMeasurement(
            step = step.toApiStep(),
            widthMm = widthMm,
            confidence = confidence,
            measurementConfidence = measurementConfidence,
            rawWidthMm = rawWidthMm,
            measurementSource = measurementSource.toAndroidWidthSource(),
            usedFallback = usedFallback,
            debugNotes = debugNotes,
        )

    private fun CoreCaptureStep.toApiStep(): CaptureStep =
        when (this) {
            CoreCaptureStep.FRONT_PALM -> CaptureStep.FRONT_PALM
            CoreCaptureStep.LEFT_OBLIQUE -> CaptureStep.LEFT_OBLIQUE
            CoreCaptureStep.RIGHT_OBLIQUE -> CaptureStep.RIGHT_OBLIQUE
            CoreCaptureStep.UP_TILT -> CaptureStep.UP_TILT
            CoreCaptureStep.DOWN_TILT -> CaptureStep.DOWN_TILT
            CoreCaptureStep.BACK_OF_HAND -> CaptureStep.BACK_OF_HAND
            CoreCaptureStep.LEFT_OBLIQUE_DORSAL -> CaptureStep.LEFT_OBLIQUE_DORSAL
            CoreCaptureStep.RIGHT_OBLIQUE_DORSAL -> CaptureStep.RIGHT_OBLIQUE_DORSAL
            CoreCaptureStep.UP_TILT_DORSAL -> CaptureStep.UP_TILT_DORSAL
            CoreCaptureStep.DOWN_TILT_DORSAL -> CaptureStep.DOWN_TILT_DORSAL
        }

    private fun CaptureStep.toCoreStep(): CoreCaptureStep =
        when (this) {
            CaptureStep.FRONT_PALM -> CoreCaptureStep.FRONT_PALM
            CaptureStep.LEFT_OBLIQUE -> CoreCaptureStep.LEFT_OBLIQUE
            CaptureStep.RIGHT_OBLIQUE -> CoreCaptureStep.RIGHT_OBLIQUE
            CaptureStep.UP_TILT -> CoreCaptureStep.UP_TILT
            CaptureStep.DOWN_TILT -> CoreCaptureStep.DOWN_TILT
            CaptureStep.BACK_OF_HAND -> CoreCaptureStep.BACK_OF_HAND
            CaptureStep.LEFT_OBLIQUE_DORSAL -> CoreCaptureStep.LEFT_OBLIQUE_DORSAL
            CaptureStep.RIGHT_OBLIQUE_DORSAL -> CoreCaptureStep.RIGHT_OBLIQUE_DORSAL
            CaptureStep.UP_TILT_DORSAL -> CoreCaptureStep.UP_TILT_DORSAL
            CaptureStep.DOWN_TILT_DORSAL -> CoreCaptureStep.DOWN_TILT_DORSAL
        }

    private fun CoreHandMeasureWarning.toApiWarning(): HandMeasureWarning =
        when (this) {
            CoreHandMeasureWarning.BEST_EFFORT_ESTIMATE -> HandMeasureWarning.BEST_EFFORT_ESTIMATE
            CoreHandMeasureWarning.LOW_CARD_CONFIDENCE -> HandMeasureWarning.LOW_CARD_CONFIDENCE
            CoreHandMeasureWarning.LOW_POSE_CONFIDENCE -> HandMeasureWarning.LOW_POSE_CONFIDENCE
            CoreHandMeasureWarning.LOW_LIGHTING -> HandMeasureWarning.LOW_LIGHTING
            CoreHandMeasureWarning.HIGH_MOTION -> HandMeasureWarning.HIGH_MOTION
            CoreHandMeasureWarning.HIGH_BLUR -> HandMeasureWarning.HIGH_BLUR
            CoreHandMeasureWarning.THICKNESS_ESTIMATED_FROM_WEAK_ANGLES -> HandMeasureWarning.THICKNESS_ESTIMATED_FROM_WEAK_ANGLES
            CoreHandMeasureWarning.CALIBRATION_WEAK -> HandMeasureWarning.CALIBRATION_WEAK
            CoreHandMeasureWarning.LOW_RESULT_RELIABILITY -> HandMeasureWarning.LOW_RESULT_RELIABILITY
        }

    private fun CoreCalibrationStatus.toApiCalibrationStatus(): CalibrationStatus =
        when (this) {
            CoreCalibrationStatus.CALIBRATED -> CalibrationStatus.CALIBRATED
            CoreCalibrationStatus.DEGRADED -> CalibrationStatus.DEGRADED
            CoreCalibrationStatus.MISSING_REFERENCE -> CalibrationStatus.MISSING_REFERENCE
        }

    private fun CoreMeasurementSource.toApiMeasurementSource(): MeasurementSource =
        when (this) {
            CoreMeasurementSource.EDGE_PROFILE -> MeasurementSource.EDGE_PROFILE
            CoreMeasurementSource.LANDMARK_HEURISTIC -> MeasurementSource.LANDMARK_HEURISTIC
            CoreMeasurementSource.DEFAULT_HEURISTIC -> MeasurementSource.DEFAULT_HEURISTIC
            CoreMeasurementSource.FUSION_ESTIMATE -> MeasurementSource.FUSION_ESTIMATE
        }

    private fun CoreWidthMeasurementSource.toAndroidWidthSource(): WidthMeasurementSource =
        when (this) {
            CoreWidthMeasurementSource.EDGE_PROFILE -> WidthMeasurementSource.EDGE_PROFILE
            CoreWidthMeasurementSource.LANDMARK_HEURISTIC -> WidthMeasurementSource.LANDMARK_HEURISTIC
            CoreWidthMeasurementSource.DEFAULT_HEURISTIC -> WidthMeasurementSource.DEFAULT_HEURISTIC
        }
}
