package com.handmeasure.coordinator

import com.google.common.truth.Truth.assertThat
import com.handmeasure.api.CalibrationStatus
import com.handmeasure.api.CaptureStep
import com.handmeasure.api.HandMeasureConfig
import com.handmeasure.api.HandMeasureWarning
import com.handmeasure.measurement.FingerMeasurementFusion
import com.handmeasure.measurement.ResultReliabilityPolicy
import com.handmeasure.measurement.StepMeasurement
import com.handmeasure.measurement.TableRingSizeMapper
import com.handmeasure.measurement.WidthMeasurementSource
import org.junit.Test

class MeasurementResultAssemblerTest {
    @Test
    fun assemble_regularizesExtremeOutlierWithoutDroppingResult() {
        val config = HandMeasureConfig()
        val assembler =
            MeasurementResultAssembler(
                config = config,
                fingerMeasurementFusion = FingerMeasurementFusion(),
                reliabilityPolicy = ResultReliabilityPolicy(),
                ringSizeMapper = TableRingSizeMapper(),
            )
        val processing =
            SessionProcessingOutput(
                warnings = emptySet(),
                stepMeasurements =
                    listOf(
                        StepMeasurement(
                            step = CaptureStep.BACK_OF_HAND,
                            widthMm = 127.44,
                            confidence = 0.45f,
                            measurementConfidence = 0.25f,
                            measurementSource = WidthMeasurementSource.LANDMARK_HEURISTIC,
                        ),
                        StepMeasurement(
                            step = CaptureStep.LEFT_OBLIQUE_DORSAL,
                            widthMm = 146.53,
                            confidence = 0.45f,
                            measurementConfidence = 0.25f,
                            measurementSource = WidthMeasurementSource.LANDMARK_HEURISTIC,
                        ),
                        StepMeasurement(
                            step = CaptureStep.RIGHT_OBLIQUE_DORSAL,
                            widthMm = 146.53,
                            confidence = 0.45f,
                            measurementConfidence = 0.25f,
                            measurementSource = WidthMeasurementSource.LANDMARK_HEURISTIC,
                        ),
                        StepMeasurement(
                            step = CaptureStep.UP_TILT_DORSAL,
                            widthMm = 141.80,
                            confidence = 0.45f,
                            measurementConfidence = 0.25f,
                            measurementSource = WidthMeasurementSource.LANDMARK_HEURISTIC,
                        ),
                        StepMeasurement(
                            step = CaptureStep.DOWN_TILT_DORSAL,
                            widthMm = 141.80,
                            confidence = 0.45f,
                            measurementConfidence = 0.25f,
                            measurementSource = WidthMeasurementSource.LANDMARK_HEURISTIC,
                        ),
                    ),
                stepDiagnostics = emptyList(),
                bestScaleMmPerPxX = 0.12,
                bestScaleMmPerPxY = 0.12,
                calibrationStatus = CalibrationStatus.DEGRADED,
                frontalWidthPx = 0.0,
                thicknessSamples = emptyList(),
                debugNotes = emptyList(),
                calibrationNotes = emptyList(),
                overlays = emptyList(),
            )

        val result = assembler.assemble(completedSteps = emptyList(), processing = processing)

        assertThat(result.estimatedCircumferenceMm).isAtMost(config.sanityLimits.maxCircumferenceMm)
        assertThat(result.equivalentDiameterMm).isAtMost(config.sanityLimits.maxEquivalentDiameterMm)
        assertThat(result.confidenceScore).isLessThan(0.65f)
        assertThat(result.warnings).contains(HandMeasureWarning.BEST_EFFORT_ESTIMATE)
        assertThat(result.retryRecommended).isTrue()
    }

    @Test
    fun assemble_keepsInRangeMeasurementUnchanged() {
        val config = HandMeasureConfig()
        val assembler =
            MeasurementResultAssembler(
                config = config,
                fingerMeasurementFusion = FingerMeasurementFusion(),
                reliabilityPolicy = ResultReliabilityPolicy(),
                ringSizeMapper = TableRingSizeMapper(),
            )
        val processing =
            SessionProcessingOutput(
                warnings = emptySet(),
                stepMeasurements =
                    listOf(
                        StepMeasurement(
                            step = CaptureStep.BACK_OF_HAND,
                            widthMm = 18.2,
                            confidence = 0.9f,
                            measurementConfidence = 0.9f,
                            measurementSource = WidthMeasurementSource.EDGE_PROFILE,
                        ),
                        StepMeasurement(
                            step = CaptureStep.LEFT_OBLIQUE_DORSAL,
                            widthMm = 15.7,
                            confidence = 0.9f,
                            measurementConfidence = 0.9f,
                            measurementSource = WidthMeasurementSource.EDGE_PROFILE,
                        ),
                        StepMeasurement(
                            step = CaptureStep.RIGHT_OBLIQUE_DORSAL,
                            widthMm = 15.8,
                            confidence = 0.9f,
                            measurementConfidence = 0.9f,
                            measurementSource = WidthMeasurementSource.EDGE_PROFILE,
                        ),
                        StepMeasurement(
                            step = CaptureStep.UP_TILT_DORSAL,
                            widthMm = 15.5,
                            confidence = 0.9f,
                            measurementConfidence = 0.9f,
                            measurementSource = WidthMeasurementSource.EDGE_PROFILE,
                        ),
                        StepMeasurement(
                            step = CaptureStep.DOWN_TILT_DORSAL,
                            widthMm = 15.6,
                            confidence = 0.9f,
                            measurementConfidence = 0.9f,
                            measurementSource = WidthMeasurementSource.EDGE_PROFILE,
                        ),
                    ),
                stepDiagnostics = emptyList(),
                bestScaleMmPerPxX = 0.12,
                bestScaleMmPerPxY = 0.12,
                calibrationStatus = CalibrationStatus.CALIBRATED,
                frontalWidthPx = 0.0,
                thicknessSamples = emptyList(),
                debugNotes = emptyList(),
                calibrationNotes = emptyList(),
                overlays = emptyList(),
            )

        val result = assembler.assemble(completedSteps = emptyList(), processing = processing)

        assertThat(result.estimatedCircumferenceMm).isWithin(0.5).of(53.84)
        assertThat(result.confidenceScore).isGreaterThan(config.sanityLimits.maxConfidenceWhenAdjusted)
        assertThat(result.warnings).doesNotContain(HandMeasureWarning.LOW_RESULT_RELIABILITY)
    }
}
