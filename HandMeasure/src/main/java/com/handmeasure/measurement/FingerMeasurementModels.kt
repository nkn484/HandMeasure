package com.handmeasure.measurement

import com.handmeasure.api.CaptureStep
import com.handmeasure.api.HandMeasureWarning

data class StepMeasurement(
    val step: CaptureStep,
    val widthMm: Double,
    val confidence: Float,
    val measurementConfidence: Float = confidence,
    val rawWidthMm: Double = widthMm,
    val debugNotes: List<String> = emptyList(),
)

data class FusedFingerMeasurement(
    val widthMm: Double,
    val thicknessMm: Double,
    val circumferenceMm: Double,
    val equivalentDiameterMm: Double,
    val confidenceScore: Float,
    val warnings: List<HandMeasureWarning>,
    val debugNotes: List<String>,
    val perStepResidualsMm: List<Double> = emptyList(),
    val detectionConfidence: Float = confidenceScore,
    val poseConfidence: Float = confidenceScore,
    val measurementConfidence: Float = confidenceScore,
)
