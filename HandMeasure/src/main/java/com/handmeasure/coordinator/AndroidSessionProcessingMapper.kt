package com.handmeasure.coordinator

import com.handmeasure.api.HandMeasureConfig
import com.handmeasure.api.StepDiagnostics
import com.handmeasure.flow.StepCandidate
import com.handmeasure.measurement.StepMeasurement
import com.handmeasure.core.measurement.StepMeasurement as CoreStepMeasurement
import com.handmeasure.core.session.MeasurementSessionProcessingRequest
import com.handmeasure.core.session.SessionProcessingResult as CoreSessionProcessingResult
import com.handmeasure.core.session.SessionQualityThresholds
import com.handmeasure.core.session.SessionStepCandidate
import com.handmeasure.core.session.SessionStepDiagnostics as CoreSessionStepDiagnostics

internal class AndroidSessionProcessingMapper {
    fun toCoreRequest(
        stepResults: List<StepCandidate>,
        config: HandMeasureConfig,
    ): MeasurementSessionProcessingRequest =
        MeasurementSessionProcessingRequest(
            stepCandidates = stepResults.map { it.toCoreCandidate() },
            thresholds =
                SessionQualityThresholds(
                    cardMinScore = config.qualityThresholds.cardMinScore,
                    lightingMinScore = config.qualityThresholds.lightingMinScore,
                    blurMinScore = config.qualityThresholds.blurMinScore,
                    motionMinScore = config.qualityThresholds.motionMinScore,
                ),
        )

    fun toAndroidOutput(coreResult: CoreSessionProcessingResult): SessionProcessingOutput =
        SessionProcessingOutput(
            warnings = coreResult.warnings.map { it.toApiWarning() }.toSet(),
            stepMeasurements = coreResult.stepMeasurements.map { it.toAndroidStepMeasurement() },
            stepDiagnostics = coreResult.stepDiagnostics.map { it.toApiStepDiagnostics() },
            bestScaleMmPerPxX = coreResult.bestScaleMmPerPxX,
            bestScaleMmPerPxY = coreResult.bestScaleMmPerPxY,
            calibrationStatus = coreResult.calibrationStatus.toApiCalibrationStatus(),
            frontalWidthPx = coreResult.frontalWidthPx,
            thicknessSamples = coreResult.thicknessSamples,
            debugNotes = coreResult.debugNotes,
            calibrationNotes = coreResult.calibrationNotes,
            overlays =
                coreResult.overlays.map { overlay ->
                    DebugOverlayFrame(
                        stepName = overlay.stepName,
                        jpegBytes = overlay.jpegBytes,
                    )
                },
        )

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
}
