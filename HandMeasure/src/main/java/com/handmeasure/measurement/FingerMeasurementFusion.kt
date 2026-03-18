package com.handmeasure.measurement

import com.handmeasure.api.CaptureStep
import com.handmeasure.api.HandMeasureWarning
import kotlin.math.abs

class FingerMeasurementFusion {
    fun fuse(measurements: List<StepMeasurement>): FusedFingerMeasurement {
        val frontal =
            measurements.firstOrNull { it.step == CaptureStep.FRONT_PALM }
                ?: measurements.maxByOrNull { it.confidence }
                ?: StepMeasurement(CaptureStep.FRONT_PALM, widthMm = 18.0, confidence = 0.2f)

        val sideSteps =
            measurements
                .filter { it.step != CaptureStep.FRONT_PALM }
                .associateBy { it.step }

        val sideCandidates =
            sideSteps.values.map { step ->
                val correction =
                    when (step.step) {
                        CaptureStep.LEFT_OBLIQUE, CaptureStep.RIGHT_OBLIQUE -> 0.90
                        CaptureStep.UP_TILT, CaptureStep.DOWN_TILT -> 0.93
                        CaptureStep.FRONT_PALM -> 1.0
                    }
                WeightedThickness(
                    source = step.step,
                    thicknessMm = step.widthMm * correction,
                    weight = (step.confidence * 0.6f + step.measurementConfidence * 0.4f).coerceAtLeast(0.05f),
                )
            }

        val robustCandidates = rejectOutlierThickness(sideCandidates)
        val thicknessMm =
            if (robustCandidates.isEmpty()) {
                frontal.widthMm * 0.80
            } else {
                weightedMedian(robustCandidates)
            }

        val symmetryPenalties = buildSymmetryPenalties(sideSteps)
        val residuals = robustCandidates.map { it.thicknessMm - thicknessMm }
        val circumferenceMm = EllipseMath.circumferenceFromWidthThickness(frontal.widthMm, thicknessMm)
        val diameterMm = EllipseMath.equivalentDiameterFromCircumference(circumferenceMm)

        val detectionConfidence = measurements.map { it.confidence }.average().toFloat().coerceIn(0f, 1f)
        val poseConfidence = measurements.map { (it.confidence * 0.7f + it.measurementConfidence * 0.3f) }.average().toFloat().coerceIn(0f, 1f)
        val measurementConfidence =
            (
                robustCandidates.map { it.weight.toDouble() }.average().toFloat().coerceAtLeast(0f) *
                    (1f - symmetryPenalties.totalPenalty).coerceIn(0.4f, 1f)
            ).coerceIn(0f, 1f)
        val finalConfidence =
            (
                detectionConfidence * 0.30f +
                    poseConfidence * 0.25f +
                    measurementConfidence * 0.45f
            ).coerceIn(0f, 1f)

        val warnings = buildList {
            if (finalConfidence < 0.65f) add(HandMeasureWarning.BEST_EFFORT_ESTIMATE)
            if (sideCandidates.size < 2) add(HandMeasureWarning.THICKNESS_ESTIMATED_FROM_WEAK_ANGLES)
            if (symmetryPenalties.leftRightPenalty > 0.10f || symmetryPenalties.upDownPenalty > 0.10f) {
                add(HandMeasureWarning.THICKNESS_ESTIMATED_FROM_WEAK_ANGLES)
            }
        }.distinct()

        val debugNotes =
            buildList {
                add("thicknessCandidates=${sideCandidates.joinToString { "${it.source}:${"%.2f".format(it.thicknessMm)}@${"%.2f".format(it.weight)}" }}")
                add("leftRightPenalty=${"%.3f".format(symmetryPenalties.leftRightPenalty)}")
                add("upDownPenalty=${"%.3f".format(symmetryPenalties.upDownPenalty)}")
                addAll(symmetryPenalties.notes)
            }

        return FusedFingerMeasurement(
            widthMm = frontal.widthMm,
            thicknessMm = thicknessMm,
            circumferenceMm = circumferenceMm,
            equivalentDiameterMm = diameterMm,
            confidenceScore = finalConfidence,
            warnings = warnings,
            debugNotes = debugNotes,
            perStepResidualsMm = residuals,
            detectionConfidence = detectionConfidence,
            poseConfidence = poseConfidence,
            measurementConfidence = measurementConfidence,
        )
    }

    private fun rejectOutlierThickness(candidates: List<WeightedThickness>): List<WeightedThickness> {
        if (candidates.size <= 2) return candidates
        val med = weightedMedian(candidates)
        val allowed = maxOf(0.55, med * 0.2)
        return candidates.filter { abs(it.thicknessMm - med) <= allowed }
    }

    private fun weightedMedian(candidates: List<WeightedThickness>): Double {
        if (candidates.isEmpty()) return 0.0
        val sorted = candidates.sortedBy { it.thicknessMm }
        val totalWeight = sorted.sumOf { it.weight.toDouble() }.coerceAtLeast(1e-6)
        var cumulative = 0.0
        for (entry in sorted) {
            cumulative += entry.weight
            if (cumulative >= totalWeight / 2.0) return entry.thicknessMm
        }
        return sorted.last().thicknessMm
    }

    private fun buildSymmetryPenalties(
        sideSteps: Map<CaptureStep, StepMeasurement>,
    ): SymmetryPenalties {
        val left = sideSteps[CaptureStep.LEFT_OBLIQUE]?.widthMm
        val right = sideSteps[CaptureStep.RIGHT_OBLIQUE]?.widthMm
        val up = sideSteps[CaptureStep.UP_TILT]?.widthMm
        val down = sideSteps[CaptureStep.DOWN_TILT]?.widthMm
        val leftRightPenalty = pairPenalty(left, right)
        val upDownPenalty = pairPenalty(up, down)
        return SymmetryPenalties(
            leftRightPenalty = leftRightPenalty,
            upDownPenalty = upDownPenalty,
            totalPenalty = (leftRightPenalty + upDownPenalty).coerceIn(0f, 0.35f),
            notes =
                listOfNotNull(
                    if (left != null && right != null) "leftRightResidual=${"%.3f".format(abs(left - right))}" else null,
                    if (up != null && down != null) "upDownResidual=${"%.3f".format(abs(up - down))}" else null,
                ),
        )
    }

    private fun pairPenalty(a: Double?, b: Double?): Float {
        if (a == null || b == null) return 0.06f
        val denom = maxOf((a + b) / 2.0, 1.0)
        return (abs(a - b) / denom).toFloat().coerceIn(0f, 0.18f)
    }

    private data class WeightedThickness(
        val source: CaptureStep,
        val thicknessMm: Double,
        val weight: Float,
    )

    private data class SymmetryPenalties(
        val leftRightPenalty: Float,
        val upDownPenalty: Float,
        val totalPenalty: Float,
        val notes: List<String>,
    )
}
