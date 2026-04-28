package com.handmeasure.measurement

import com.handmeasure.api.CaptureStep
import com.handmeasure.api.HandMeasureWarning
import com.handmeasure.core.measurement.CaptureStep as CoreCaptureStep
import com.handmeasure.core.measurement.FingerMeasurementFusion as CoreFingerMeasurementFusion
import com.handmeasure.core.measurement.FusedFingerMeasurement as CoreFusedFingerMeasurement
import com.handmeasure.core.measurement.HandMeasureWarning as CoreHandMeasureWarning
import com.handmeasure.core.measurement.StepMeasurement as CoreStepMeasurement
import com.handmeasure.core.measurement.ThicknessEstimationPolicy as CoreThicknessEstimationPolicy
import com.handmeasure.core.measurement.WidthMeasurementSource as CoreWidthMeasurementSource

class FingerMeasurementFusion(
    private val delegate: CoreFingerMeasurementFusion = CoreFingerMeasurementFusion(),
) {
    fun fuse(measurements: List<StepMeasurement>): FusedFingerMeasurement =
        delegate
            .fuse(measurements.map { it.toCore() })
            .toAndroidModel()
}

data class ThicknessEstimationPolicy(
    val obliqueCorrection: Double = 0.90,
    val tiltCorrection: Double = 0.93,
    val frontalFallbackThicknessRatio: Double = 0.80,
    val minThicknessCandidatesForStableEstimate: Int = 2,
) {
    fun thicknessCorrection(step: CaptureStep): Double =
        CoreThicknessEstimationPolicy(
            obliqueCorrection = obliqueCorrection,
            tiltCorrection = tiltCorrection,
            frontalFallbackThicknessRatio = frontalFallbackThicknessRatio,
            minThicknessCandidatesForStableEstimate = minThicknessCandidatesForStableEstimate,
        ).thicknessCorrection(
            step = step.toCore(),
            source = CoreWidthMeasurementSource.EDGE_PROFILE,
            measurementConfidence = 1f,
        )
}

private fun StepMeasurement.toCore(): CoreStepMeasurement =
    CoreStepMeasurement(
        step = step.toCore(),
        widthMm = widthMm,
        confidence = confidence,
        measurementConfidence = measurementConfidence,
        rawWidthMm = rawWidthMm,
        measurementSource = measurementSource.toCore(),
        usedFallback = usedFallback,
        debugNotes = debugNotes,
    )

private fun WidthMeasurementSource.toCore(): CoreWidthMeasurementSource =
    when (this) {
        WidthMeasurementSource.EDGE_PROFILE -> CoreWidthMeasurementSource.EDGE_PROFILE
        WidthMeasurementSource.LANDMARK_HEURISTIC -> CoreWidthMeasurementSource.LANDMARK_HEURISTIC
        WidthMeasurementSource.DEFAULT_HEURISTIC -> CoreWidthMeasurementSource.DEFAULT_HEURISTIC
    }

private fun CaptureStep.toCore(): CoreCaptureStep =
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

private fun CoreFusedFingerMeasurement.toAndroidModel(): FusedFingerMeasurement =
    FusedFingerMeasurement(
        widthMm = widthMm,
        thicknessMm = thicknessMm,
        circumferenceMm = circumferenceMm,
        equivalentDiameterMm = equivalentDiameterMm,
        confidenceScore = confidenceScore,
        warnings = warnings.map { it.toAndroidWarning() },
        debugNotes = debugNotes,
        perStepResidualsMm = perStepResidualsMm,
        detectionConfidence = detectionConfidence,
        poseConfidence = poseConfidence,
        measurementConfidence = measurementConfidence,
    )

private fun CoreHandMeasureWarning.toAndroidWarning(): HandMeasureWarning =
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
