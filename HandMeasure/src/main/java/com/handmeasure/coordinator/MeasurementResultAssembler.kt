package com.handmeasure.coordinator

import com.handmeasure.api.CapturedStepInfo
import com.handmeasure.api.DebugMetadata
import com.handmeasure.api.FusedDiagnostics
import com.handmeasure.api.HandMeasureConfig
import com.handmeasure.api.HandMeasureResult
import com.handmeasure.api.SessionDiagnostics
import com.handmeasure.flow.CaptureUiState
import com.handmeasure.flow.StepCandidate
import com.handmeasure.measurement.FingerMeasurementFusion
import com.handmeasure.measurement.ResultReliabilityPolicy
import com.handmeasure.measurement.TableRingSizeMapper

internal class MeasurementResultAssembler(
    private val config: HandMeasureConfig,
    private val fingerMeasurementFusion: FingerMeasurementFusion,
    private val reliabilityPolicy: ResultReliabilityPolicy,
    private val ringSizeMapper: TableRingSizeMapper,
) {
    fun assemble(
        snapshot: CaptureUiState,
        processing: SessionProcessingOutput,
    ): HandMeasureResult = assemble(snapshot.completedSteps, processing)

    fun assemble(
        completedSteps: List<StepCandidate>,
        processing: SessionProcessingOutput,
    ): HandMeasureResult {
        val warnings = processing.warnings.toMutableSet()
        val fused = fingerMeasurementFusion.fuse(processing.stepMeasurements)
        warnings += fused.warnings

        val reliability =
            reliabilityPolicy.assess(
                fused = fused,
                stepMeasurements = processing.stepMeasurements,
                calibrationStatus = processing.calibrationStatus,
                existingWarnings = warnings,
            )
        val ringSize = ringSizeMapper.nearestForDiameter(config.ringSizeTable, fused.equivalentDiameterMm)
        val captured =
            completedSteps.map {
                CapturedStepInfo(
                    step = it.step,
                    score = it.qualityScore,
                    poseScore = it.poseScore,
                    cardScore = it.cardScore,
                    handScore = it.handScore,
                )
            }

        val sessionDiagnostics =
            SessionDiagnostics(
                stepDiagnostics = processing.stepDiagnostics,
                fusedDiagnostics =
                    FusedDiagnostics(
                        widthMm = fused.widthMm,
                        thicknessMm = fused.thicknessMm,
                        circumferenceMm = fused.circumferenceMm,
                        equivalentDiameterMm = fused.equivalentDiameterMm,
                        suggestedRingSizeLabel = ringSize.label,
                        finalConfidence = fused.confidenceScore,
                        warningReasons = reliability.warnings.map { it.name },
                        perStepResidualsMm = fused.perStepResidualsMm,
                        resultMode = reliability.resultMode,
                        qualityLevel = reliability.qualityLevel,
                        retryRecommended = reliability.retryRecommended,
                        calibrationStatus = processing.calibrationStatus,
                        measurementSources = reliability.measurementSources,
                    ),
            )

        return HandMeasureResult(
            targetFinger = config.targetFinger,
            fingerWidthMm = fused.widthMm,
            fingerThicknessMm = fused.thicknessMm,
            estimatedCircumferenceMm = fused.circumferenceMm,
            equivalentDiameterMm = fused.equivalentDiameterMm,
            suggestedRingSizeLabel = ringSize.label,
            confidenceScore = fused.confidenceScore.coerceIn(0f, 1f),
            warnings = reliability.warnings,
            capturedSteps = captured,
            resultMode = reliability.resultMode,
            qualityLevel = reliability.qualityLevel,
            retryRecommended = reliability.retryRecommended,
            calibrationStatus = processing.calibrationStatus,
            measurementSources = reliability.measurementSources,
            debugMetadata =
                DebugMetadata(
                    mmPerPxX = processing.bestScaleMmPerPxX,
                    mmPerPxY = processing.bestScaleMmPerPxY,
                    frontalWidthPx = processing.frontalWidthPx,
                    thicknessSamplesMm = processing.thicknessSamples,
                    rawNotes = processing.debugNotes + processing.calibrationNotes + fused.debugNotes,
                    sessionDiagnostics = sessionDiagnostics,
                ),
        )
    }
}
