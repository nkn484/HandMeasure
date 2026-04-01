package com.handmeasure.engine.compat

import com.handmeasure.api.CalibrationStatus
import com.handmeasure.api.CaptureProtocol
import com.handmeasure.api.CaptureStep
import com.handmeasure.api.CapturedStepInfo
import com.handmeasure.api.DebugMetadata
import com.handmeasure.api.FusedDiagnostics
import com.handmeasure.api.HandMeasureConfig
import com.handmeasure.api.HandMeasureResult
import com.handmeasure.api.HandMeasureWarning
import com.handmeasure.api.LensFacing
import com.handmeasure.api.MeasurementSource
import com.handmeasure.api.QualityLevel
import com.handmeasure.api.QualityThresholds
import com.handmeasure.api.ResultMode
import com.handmeasure.api.RingSizeEntry
import com.handmeasure.api.RingSizeTable
import com.handmeasure.api.SessionDiagnostics
import com.handmeasure.api.StepDiagnostics
import com.handmeasure.api.TargetFinger
import com.handmeasure.coordinator.DebugOverlayFrame
import com.handmeasure.engine.model.MeasurementCaptureProtocol
import com.handmeasure.engine.model.MeasurementEngineCapturedStepInfo
import com.handmeasure.engine.model.MeasurementEngineConfig
import com.handmeasure.engine.model.MeasurementEngineDebugMetadata
import com.handmeasure.engine.model.MeasurementEngineFusedDiagnostics
import com.handmeasure.engine.model.MeasurementEngineOverlayFrame
import com.handmeasure.engine.model.MeasurementEngineProcessingResult
import com.handmeasure.engine.model.MeasurementEngineQualityThresholds
import com.handmeasure.engine.model.MeasurementEngineResult
import com.handmeasure.engine.model.MeasurementEngineSessionDiagnostics
import com.handmeasure.engine.model.MeasurementEngineStepCandidate
import com.handmeasure.engine.model.MeasurementEngineStepDiagnostics
import com.handmeasure.engine.model.MeasurementLensFacing
import com.handmeasure.engine.model.MeasurementTargetFinger
import com.handmeasure.flow.StepCandidate
import com.handmeasure.core.measurement.CalibrationStatus as CoreCalibrationStatus
import com.handmeasure.core.measurement.CaptureStep as CoreCaptureStep
import com.handmeasure.core.measurement.HandMeasureWarning as CoreHandMeasureWarning
import com.handmeasure.core.measurement.MeasurementSource as CoreMeasurementSource
import com.handmeasure.core.measurement.QualityLevel as CoreQualityLevel
import com.handmeasure.core.measurement.ResultMode as CoreResultMode
import com.handmeasure.core.measurement.RingSizeEntry as CoreRingSizeEntry
import com.handmeasure.core.measurement.RingSizeTable as CoreRingSizeTable

internal class MeasurementEngineApiMapper {
    fun toEngineConfig(config: HandMeasureConfig): MeasurementEngineConfig =
        MeasurementEngineConfig(
            targetFinger = config.targetFinger.toEngineTargetFinger(),
            debugOverlayEnabled = config.debugOverlayEnabled,
            debugExportEnabled = config.debugExportEnabled,
            debugReplayInputPath = config.debugReplayInputPath,
            qualityThresholds = config.qualityThresholds.toEngineThresholds(),
            ringSizeTable = config.ringSizeTable.toCoreRingTable(),
            lensFacing = config.lensFacing.toEngineLensFacing(),
            protocol = config.protocol.toEngineProtocol(),
        )

    fun toApiConfig(config: MeasurementEngineConfig): HandMeasureConfig =
        HandMeasureConfig(
            targetFinger = config.targetFinger.toApiTargetFinger(),
            debugOverlayEnabled = config.debugOverlayEnabled,
            debugExportEnabled = config.debugExportEnabled,
            debugReplayInputPath = config.debugReplayInputPath,
            qualityThresholds = config.qualityThresholds.toApiThresholds(),
            ringSizeTable = config.ringSizeTable.toApiRingTable(),
            lensFacing = config.lensFacing.toApiLensFacing(),
            protocol = config.protocol.toApiProtocol(),
        )

    fun toEngineStepCandidate(stepCandidate: StepCandidate): MeasurementEngineStepCandidate =
        MeasurementEngineStepCandidate(
            step = stepCandidate.step.toCoreStep(),
            frameBytes = stepCandidate.frameBytes,
            qualityScore = stepCandidate.qualityScore,
            poseScore = stepCandidate.poseScore,
            cardScore = stepCandidate.cardScore,
            handScore = stepCandidate.handScore,
            blurScore = stepCandidate.blurScore,
            motionScore = stepCandidate.motionScore,
            lightingScore = stepCandidate.lightingScore,
            confidencePenaltyReasons = stepCandidate.confidencePenaltyReasons,
        )

    fun toApiStepCandidate(stepCandidate: MeasurementEngineStepCandidate): StepCandidate =
        StepCandidate(
            step = stepCandidate.step.toApiStep(),
            frameBytes = stepCandidate.frameBytes,
            qualityScore = stepCandidate.qualityScore,
            poseScore = stepCandidate.poseScore,
            cardScore = stepCandidate.cardScore,
            handScore = stepCandidate.handScore,
            blurScore = stepCandidate.blurScore,
            motionScore = stepCandidate.motionScore,
            lightingScore = stepCandidate.lightingScore,
            confidencePenaltyReasons = stepCandidate.confidencePenaltyReasons,
        )

    fun toEngineResult(result: HandMeasureResult): MeasurementEngineResult =
        MeasurementEngineResult(
            targetFinger = result.targetFinger.toEngineTargetFinger(),
            fingerWidthMm = result.fingerWidthMm,
            fingerThicknessMm = result.fingerThicknessMm,
            estimatedCircumferenceMm = result.estimatedCircumferenceMm,
            equivalentDiameterMm = result.equivalentDiameterMm,
            suggestedRingSizeLabel = result.suggestedRingSizeLabel,
            confidenceScore = result.confidenceScore,
            warnings = result.warnings.map { it.toCoreWarning() },
            capturedSteps = result.capturedSteps.map { it.toEngineCapturedStepInfo() },
            resultMode = result.resultMode.toCoreResultMode(),
            qualityLevel = result.qualityLevel.toCoreQualityLevel(),
            retryRecommended = result.retryRecommended,
            calibrationStatus = result.calibrationStatus.toCoreCalibrationStatus(),
            measurementSources = result.measurementSources.map { it.toCoreMeasurementSource() },
            debugMetadata = result.debugMetadata?.toEngineDebugMetadata(),
        )

    fun toApiResult(result: MeasurementEngineResult): HandMeasureResult =
        HandMeasureResult(
            targetFinger = result.targetFinger.toApiTargetFinger(),
            fingerWidthMm = result.fingerWidthMm,
            fingerThicknessMm = result.fingerThicknessMm,
            estimatedCircumferenceMm = result.estimatedCircumferenceMm,
            equivalentDiameterMm = result.equivalentDiameterMm,
            suggestedRingSizeLabel = result.suggestedRingSizeLabel,
            confidenceScore = result.confidenceScore,
            warnings = result.warnings.map { it.toApiWarning() },
            capturedSteps = result.capturedSteps.map { it.toApiCapturedStepInfo() },
            resultMode = result.resultMode.toApiResultMode(),
            qualityLevel = result.qualityLevel.toApiQualityLevel(),
            retryRecommended = result.retryRecommended,
            calibrationStatus = result.calibrationStatus.toApiCalibrationStatus(),
            measurementSources = result.measurementSources.map { it.toApiMeasurementSource() },
            debugMetadata = result.debugMetadata?.toApiDebugMetadata(),
        )

    fun toEngineOverlay(overlay: DebugOverlayFrame): MeasurementEngineOverlayFrame =
        MeasurementEngineOverlayFrame(
            stepName = overlay.stepName,
            jpegBytes = overlay.jpegBytes,
        )

    fun toApiOverlay(overlay: MeasurementEngineOverlayFrame): DebugOverlayFrame =
        DebugOverlayFrame(
            stepName = overlay.stepName,
            jpegBytes = overlay.jpegBytes,
        )

    fun toEngineProcessingResult(
        result: HandMeasureResult,
        overlays: List<DebugOverlayFrame>,
    ): MeasurementEngineProcessingResult =
        MeasurementEngineProcessingResult(
            result = toEngineResult(result),
            overlays = overlays.map(::toEngineOverlay),
        )

    private fun QualityThresholds.toEngineThresholds(): MeasurementEngineQualityThresholds =
        MeasurementEngineQualityThresholds(
            autoCaptureScore = autoCaptureScore,
            bestCandidateProgressScore = bestCandidateProgressScore,
            handMinScore = handMinScore,
            cardMinScore = cardMinScore,
            lightingMinScore = lightingMinScore,
            blurMinScore = blurMinScore,
            motionMinScore = motionMinScore,
        )

    private fun MeasurementEngineQualityThresholds.toApiThresholds(): QualityThresholds =
        QualityThresholds(
            autoCaptureScore = autoCaptureScore,
            bestCandidateProgressScore = bestCandidateProgressScore,
            handMinScore = handMinScore,
            cardMinScore = cardMinScore,
            lightingMinScore = lightingMinScore,
            blurMinScore = blurMinScore,
            motionMinScore = motionMinScore,
        )

    private fun RingSizeTable.toCoreRingTable(): CoreRingSizeTable =
        CoreRingSizeTable(
            name = name,
            entries = entries.map { entry -> CoreRingSizeEntry(label = entry.label, diameterMm = entry.diameterMm) },
        )

    private fun CoreRingSizeTable.toApiRingTable(): RingSizeTable =
        RingSizeTable(
            name = name,
            entries = entries.map { entry -> RingSizeEntry(label = entry.label, diameterMm = entry.diameterMm) },
        )

    private fun TargetFinger.toEngineTargetFinger(): MeasurementTargetFinger =
        when (this) {
            TargetFinger.THUMB -> MeasurementTargetFinger.THUMB
            TargetFinger.INDEX -> MeasurementTargetFinger.INDEX
            TargetFinger.MIDDLE -> MeasurementTargetFinger.MIDDLE
            TargetFinger.RING -> MeasurementTargetFinger.RING
            TargetFinger.LITTLE -> MeasurementTargetFinger.LITTLE
        }

    private fun MeasurementTargetFinger.toApiTargetFinger(): TargetFinger =
        when (this) {
            MeasurementTargetFinger.THUMB -> TargetFinger.THUMB
            MeasurementTargetFinger.INDEX -> TargetFinger.INDEX
            MeasurementTargetFinger.MIDDLE -> TargetFinger.MIDDLE
            MeasurementTargetFinger.RING -> TargetFinger.RING
            MeasurementTargetFinger.LITTLE -> TargetFinger.LITTLE
        }

    private fun LensFacing.toEngineLensFacing(): MeasurementLensFacing =
        when (this) {
            LensFacing.BACK -> MeasurementLensFacing.BACK
            LensFacing.FRONT -> MeasurementLensFacing.FRONT
        }

    private fun MeasurementLensFacing.toApiLensFacing(): LensFacing =
        when (this) {
            MeasurementLensFacing.BACK -> LensFacing.BACK
            MeasurementLensFacing.FRONT -> LensFacing.FRONT
        }

    private fun CaptureProtocol.toEngineProtocol(): MeasurementCaptureProtocol =
        when (this) {
            CaptureProtocol.DORSAL_V1 -> MeasurementCaptureProtocol.DORSAL_V1
            CaptureProtocol.PALMAR_V1 -> MeasurementCaptureProtocol.PALMAR_V1
        }

    private fun MeasurementCaptureProtocol.toApiProtocol(): CaptureProtocol =
        when (this) {
            MeasurementCaptureProtocol.DORSAL_V1 -> CaptureProtocol.DORSAL_V1
            MeasurementCaptureProtocol.PALMAR_V1 -> CaptureProtocol.PALMAR_V1
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

    private fun HandMeasureWarning.toCoreWarning(): CoreHandMeasureWarning =
        when (this) {
            HandMeasureWarning.BEST_EFFORT_ESTIMATE -> CoreHandMeasureWarning.BEST_EFFORT_ESTIMATE
            HandMeasureWarning.LOW_CARD_CONFIDENCE -> CoreHandMeasureWarning.LOW_CARD_CONFIDENCE
            HandMeasureWarning.LOW_POSE_CONFIDENCE -> CoreHandMeasureWarning.LOW_POSE_CONFIDENCE
            HandMeasureWarning.LOW_LIGHTING -> CoreHandMeasureWarning.LOW_LIGHTING
            HandMeasureWarning.HIGH_MOTION -> CoreHandMeasureWarning.HIGH_MOTION
            HandMeasureWarning.HIGH_BLUR -> CoreHandMeasureWarning.HIGH_BLUR
            HandMeasureWarning.THICKNESS_ESTIMATED_FROM_WEAK_ANGLES -> CoreHandMeasureWarning.THICKNESS_ESTIMATED_FROM_WEAK_ANGLES
            HandMeasureWarning.CALIBRATION_WEAK -> CoreHandMeasureWarning.CALIBRATION_WEAK
            HandMeasureWarning.LOW_RESULT_RELIABILITY -> CoreHandMeasureWarning.LOW_RESULT_RELIABILITY
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

    private fun ResultMode.toCoreResultMode(): CoreResultMode =
        when (this) {
            ResultMode.DIRECT_MEASUREMENT -> CoreResultMode.DIRECT_MEASUREMENT
            ResultMode.HYBRID_ESTIMATE -> CoreResultMode.HYBRID_ESTIMATE
            ResultMode.FALLBACK_ESTIMATE -> CoreResultMode.FALLBACK_ESTIMATE
        }

    private fun CoreResultMode.toApiResultMode(): ResultMode =
        when (this) {
            CoreResultMode.DIRECT_MEASUREMENT -> ResultMode.DIRECT_MEASUREMENT
            CoreResultMode.HYBRID_ESTIMATE -> ResultMode.HYBRID_ESTIMATE
            CoreResultMode.FALLBACK_ESTIMATE -> ResultMode.FALLBACK_ESTIMATE
        }

    private fun QualityLevel.toCoreQualityLevel(): CoreQualityLevel =
        when (this) {
            QualityLevel.HIGH -> CoreQualityLevel.HIGH
            QualityLevel.MEDIUM -> CoreQualityLevel.MEDIUM
            QualityLevel.LOW -> CoreQualityLevel.LOW
        }

    private fun CoreQualityLevel.toApiQualityLevel(): QualityLevel =
        when (this) {
            CoreQualityLevel.HIGH -> QualityLevel.HIGH
            CoreQualityLevel.MEDIUM -> QualityLevel.MEDIUM
            CoreQualityLevel.LOW -> QualityLevel.LOW
        }

    private fun CalibrationStatus.toCoreCalibrationStatus(): CoreCalibrationStatus =
        when (this) {
            CalibrationStatus.CALIBRATED -> CoreCalibrationStatus.CALIBRATED
            CalibrationStatus.DEGRADED -> CoreCalibrationStatus.DEGRADED
            CalibrationStatus.MISSING_REFERENCE -> CoreCalibrationStatus.MISSING_REFERENCE
        }

    private fun CoreCalibrationStatus.toApiCalibrationStatus(): CalibrationStatus =
        when (this) {
            CoreCalibrationStatus.CALIBRATED -> CalibrationStatus.CALIBRATED
            CoreCalibrationStatus.DEGRADED -> CalibrationStatus.DEGRADED
            CoreCalibrationStatus.MISSING_REFERENCE -> CalibrationStatus.MISSING_REFERENCE
        }

    private fun MeasurementSource.toCoreMeasurementSource(): CoreMeasurementSource =
        when (this) {
            MeasurementSource.EDGE_PROFILE -> CoreMeasurementSource.EDGE_PROFILE
            MeasurementSource.LANDMARK_HEURISTIC -> CoreMeasurementSource.LANDMARK_HEURISTIC
            MeasurementSource.DEFAULT_HEURISTIC -> CoreMeasurementSource.DEFAULT_HEURISTIC
            MeasurementSource.FUSION_ESTIMATE -> CoreMeasurementSource.FUSION_ESTIMATE
        }

    private fun CoreMeasurementSource.toApiMeasurementSource(): MeasurementSource =
        when (this) {
            CoreMeasurementSource.EDGE_PROFILE -> MeasurementSource.EDGE_PROFILE
            CoreMeasurementSource.LANDMARK_HEURISTIC -> MeasurementSource.LANDMARK_HEURISTIC
            CoreMeasurementSource.DEFAULT_HEURISTIC -> MeasurementSource.DEFAULT_HEURISTIC
            CoreMeasurementSource.FUSION_ESTIMATE -> MeasurementSource.FUSION_ESTIMATE
        }

    private fun CapturedStepInfo.toEngineCapturedStepInfo(): MeasurementEngineCapturedStepInfo =
        MeasurementEngineCapturedStepInfo(
            step = step.toCoreStep(),
            score = score,
            poseScore = poseScore,
            cardScore = cardScore,
            handScore = handScore,
        )

    private fun MeasurementEngineCapturedStepInfo.toApiCapturedStepInfo(): CapturedStepInfo =
        CapturedStepInfo(
            step = step.toApiStep(),
            score = score,
            poseScore = poseScore,
            cardScore = cardScore,
            handScore = handScore,
        )

    private fun DebugMetadata.toEngineDebugMetadata(): MeasurementEngineDebugMetadata =
        MeasurementEngineDebugMetadata(
            mmPerPxX = mmPerPxX,
            mmPerPxY = mmPerPxY,
            frontalWidthPx = frontalWidthPx,
            thicknessSamplesMm = thicknessSamplesMm,
            rawNotes = rawNotes,
            sessionDiagnostics = sessionDiagnostics?.toEngineSessionDiagnostics(),
        )

    private fun MeasurementEngineDebugMetadata.toApiDebugMetadata(): DebugMetadata =
        DebugMetadata(
            mmPerPxX = mmPerPxX,
            mmPerPxY = mmPerPxY,
            frontalWidthPx = frontalWidthPx,
            thicknessSamplesMm = thicknessSamplesMm,
            rawNotes = rawNotes,
            sessionDiagnostics = sessionDiagnostics?.toApiSessionDiagnostics(),
        )

    private fun SessionDiagnostics.toEngineSessionDiagnostics(): MeasurementEngineSessionDiagnostics =
        MeasurementEngineSessionDiagnostics(
            stepDiagnostics = stepDiagnostics.map { it.toEngineStepDiagnostics() },
            fusedDiagnostics = fusedDiagnostics.toEngineFusedDiagnostics(),
        )

    private fun MeasurementEngineSessionDiagnostics.toApiSessionDiagnostics(): SessionDiagnostics =
        SessionDiagnostics(
            stepDiagnostics = stepDiagnostics.map { it.toApiStepDiagnostics() },
            fusedDiagnostics = fusedDiagnostics.toApiFusedDiagnostics(),
        )

    private fun StepDiagnostics.toEngineStepDiagnostics(): MeasurementEngineStepDiagnostics =
        MeasurementEngineStepDiagnostics(
            step = step.toCoreStep(),
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
            measurementSource = measurementSource.toCoreMeasurementSource(),
            usedFallback = usedFallback,
            coplanarityProxyScore = coplanarityProxyScore,
            coplanarityProxyKind = coplanarityProxyKind,
        )

    private fun MeasurementEngineStepDiagnostics.toApiStepDiagnostics(): StepDiagnostics =
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

    private fun FusedDiagnostics.toEngineFusedDiagnostics(): MeasurementEngineFusedDiagnostics =
        MeasurementEngineFusedDiagnostics(
            widthMm = widthMm,
            thicknessMm = thicknessMm,
            circumferenceMm = circumferenceMm,
            equivalentDiameterMm = equivalentDiameterMm,
            suggestedRingSizeLabel = suggestedRingSizeLabel,
            finalConfidence = finalConfidence,
            warningReasons = warningReasons,
            perStepResidualsMm = perStepResidualsMm,
            resultMode = resultMode.toCoreResultMode(),
            qualityLevel = qualityLevel.toCoreQualityLevel(),
            retryRecommended = retryRecommended,
            calibrationStatus = calibrationStatus.toCoreCalibrationStatus(),
            measurementSources = measurementSources.map { it.toCoreMeasurementSource() },
        )

    private fun MeasurementEngineFusedDiagnostics.toApiFusedDiagnostics(): FusedDiagnostics =
        FusedDiagnostics(
            widthMm = widthMm,
            thicknessMm = thicknessMm,
            circumferenceMm = circumferenceMm,
            equivalentDiameterMm = equivalentDiameterMm,
            suggestedRingSizeLabel = suggestedRingSizeLabel,
            finalConfidence = finalConfidence,
            warningReasons = warningReasons,
            perStepResidualsMm = perStepResidualsMm,
            resultMode = resultMode.toApiResultMode(),
            qualityLevel = qualityLevel.toApiQualityLevel(),
            retryRecommended = retryRecommended,
            calibrationStatus = calibrationStatus.toApiCalibrationStatus(),
            measurementSources = measurementSources.map { it.toApiMeasurementSource() },
        )
}
