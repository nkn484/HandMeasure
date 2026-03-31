package com.handmeasure.coordinator

import com.handmeasure.api.CalibrationStatus
import com.handmeasure.api.CaptureStep
import com.handmeasure.api.HandMeasureWarning
import com.handmeasure.api.MeasurementSource
import com.handmeasure.measurement.WidthMeasurementSource
import com.handmeasure.core.measurement.CalibrationStatus as CoreCalibrationStatus
import com.handmeasure.core.measurement.CaptureStep as CoreCaptureStep
import com.handmeasure.core.measurement.HandMeasureWarning as CoreHandMeasureWarning
import com.handmeasure.core.measurement.MeasurementSource as CoreMeasurementSource
import com.handmeasure.core.measurement.WidthMeasurementSource as CoreWidthMeasurementSource

internal fun CoreCaptureStep.toApiStep(): CaptureStep =
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

internal fun CaptureStep.toCoreStep(): CoreCaptureStep =
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

internal fun CoreHandMeasureWarning.toApiWarning(): HandMeasureWarning =
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

internal fun CoreCalibrationStatus.toApiCalibrationStatus(): CalibrationStatus =
    when (this) {
        CoreCalibrationStatus.CALIBRATED -> CalibrationStatus.CALIBRATED
        CoreCalibrationStatus.DEGRADED -> CalibrationStatus.DEGRADED
        CoreCalibrationStatus.MISSING_REFERENCE -> CalibrationStatus.MISSING_REFERENCE
    }

internal fun CalibrationStatus.toCoreCalibrationStatus(): CoreCalibrationStatus =
    when (this) {
        CalibrationStatus.CALIBRATED -> CoreCalibrationStatus.CALIBRATED
        CalibrationStatus.DEGRADED -> CoreCalibrationStatus.DEGRADED
        CalibrationStatus.MISSING_REFERENCE -> CoreCalibrationStatus.MISSING_REFERENCE
    }

internal fun CoreMeasurementSource.toApiMeasurementSource(): MeasurementSource =
    when (this) {
        CoreMeasurementSource.EDGE_PROFILE -> MeasurementSource.EDGE_PROFILE
        CoreMeasurementSource.LANDMARK_HEURISTIC -> MeasurementSource.LANDMARK_HEURISTIC
        CoreMeasurementSource.DEFAULT_HEURISTIC -> MeasurementSource.DEFAULT_HEURISTIC
        CoreMeasurementSource.FUSION_ESTIMATE -> MeasurementSource.FUSION_ESTIMATE
    }

internal fun CoreWidthMeasurementSource.toAndroidWidthSource(): WidthMeasurementSource =
    when (this) {
        CoreWidthMeasurementSource.EDGE_PROFILE -> WidthMeasurementSource.EDGE_PROFILE
        CoreWidthMeasurementSource.LANDMARK_HEURISTIC -> WidthMeasurementSource.LANDMARK_HEURISTIC
        CoreWidthMeasurementSource.DEFAULT_HEURISTIC -> WidthMeasurementSource.DEFAULT_HEURISTIC
    }

internal fun WidthMeasurementSource.toCoreWidthMeasurementSource(): CoreWidthMeasurementSource =
    when (this) {
        WidthMeasurementSource.EDGE_PROFILE -> CoreWidthMeasurementSource.EDGE_PROFILE
        WidthMeasurementSource.LANDMARK_HEURISTIC -> CoreWidthMeasurementSource.LANDMARK_HEURISTIC
        WidthMeasurementSource.DEFAULT_HEURISTIC -> CoreWidthMeasurementSource.DEFAULT_HEURISTIC
    }
