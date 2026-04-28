package com.handmeasure.core.measurement

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.tanh

class FingerMeasurementFusion(
    private val policy: ThicknessEstimationPolicy = ThicknessEstimationPolicy(),
) {
    fun fuse(measurements: List<StepMeasurement>): FusedFingerMeasurement {
        if (measurements.isEmpty()) return fallbackMeasurement()

        val frontal =
            measurements.firstOrNull { it.step.role() == CaptureStepRole.FRONTAL }
                ?: measurements.maxByOrNull { it.confidence }!!
        val regularizedWidthMm = regularizeFrontalWidth(frontal)

        val sideCandidates =
            measurements
                .filter { it.step.role() != CaptureStepRole.FRONTAL }
                .map { step ->
                    val correction = policy.thicknessCorrection(step.step, step.measurementSource, step.measurementConfidence)
                    val rawThickness = step.widthMm * correction
                    val boundedThickness =
                        softBound(
                            value = rawThickness,
                            min = policy.sideSoftMinMm,
                            max = policy.sideSoftMaxMm,
                            softness = policy.sideSoftBoundSharpness,
                        )
                    WeightedThickness(
                        source = step.step,
                        thicknessMm = boundedThickness,
                        rawThicknessMm = rawThickness,
                        weight = baseWeight(step),
                        measurementSource = step.measurementSource,
                    )
                }

        val robustCandidates = rejectOutlierThickness(sideCandidates)
        val fallbackThicknessMm = regularizedWidthMm * policy.frontalFallbackThicknessRatio
        val optimizedThicknessMm = solveRobustThickness(robustCandidates, fallbackThicknessMm)
        val supportBlend = supportBlend(robustCandidates)
        val thicknessMm =
            enforceAspectBounds(
                widthMm = regularizedWidthMm,
                thicknessMm = optimizedThicknessMm * supportBlend + fallbackThicknessMm * (1.0 - supportBlend),
            )

        val symmetryPenalties = buildSymmetryPenalties(robustCandidates)
        val residuals = robustCandidates.map { it.thicknessMm - thicknessMm }
        val circumferenceMm = EllipseMath.circumferenceFromWidthThickness(regularizedWidthMm, thicknessMm)
        val diameterMm = EllipseMath.equivalentDiameterFromCircumference(circumferenceMm)

        val detectionConfidence = measurements.map { it.confidence }.average().toFloat().coerceIn(0f, 1f)
        val poseConfidence =
            measurements
                .map { (it.confidence * 0.65f + it.measurementConfidence * 0.35f) }
                .average()
                .toFloat()
                .coerceIn(0f, 1f)
        val measurementConfidence = measurementConfidence(robustCandidates, residuals, symmetryPenalties)
        val finalConfidence =
            (
                detectionConfidence * 0.28f +
                    poseConfidence * 0.22f +
                    measurementConfidence * 0.50f
            ).coerceIn(0f, 1f)

        val warnings =
            buildList {
                if (finalConfidence < 0.65f) add(HandMeasureWarning.BEST_EFFORT_ESTIMATE)
                if (robustCandidates.size < policy.minThicknessCandidatesForStableEstimate) {
                    add(HandMeasureWarning.THICKNESS_ESTIMATED_FROM_WEAK_ANGLES)
                }
                if (symmetryPenalties.leftRightPenalty > 0.10f || symmetryPenalties.upDownPenalty > 0.10f) {
                    add(HandMeasureWarning.THICKNESS_ESTIMATED_FROM_WEAK_ANGLES)
                }
            }.distinct()

        val debugNotes =
            buildList {
                add(
                    "thicknessCandidates=${
                        sideCandidates.joinToString {
                            "${it.source}:${"%.2f".format(it.thicknessMm)}(raw=${"%.2f".format(it.rawThicknessMm)})@${"%.2f".format(it.weight)}"
                        }
                    }",
                )
                add("regularizedWidthMm=${"%.2f".format(regularizedWidthMm)}")
                add("supportBlend=${"%.3f".format(supportBlend)}")
                add("leftRightPenalty=${"%.3f".format(symmetryPenalties.leftRightPenalty)}")
                add("upDownPenalty=${"%.3f".format(symmetryPenalties.upDownPenalty)}")
                addAll(symmetryPenalties.notes)
            }

        return FusedFingerMeasurement(
            widthMm = regularizedWidthMm,
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

    private fun regularizeFrontalWidth(frontal: StepMeasurement): Double {
        val sourceBias =
            when (frontal.measurementSource) {
                WidthMeasurementSource.EDGE_PROFILE -> 1.0
                WidthMeasurementSource.LANDMARK_HEURISTIC -> policy.landmarkWidthScale
                WidthMeasurementSource.DEFAULT_HEURISTIC -> policy.defaultWidthScale
            }
        val confidenceBias = (policy.widthConfidenceBase + policy.widthConfidenceGain * frontal.measurementConfidence).coerceIn(0.85f, 1.08f)
        val scaled = frontal.widthMm * sourceBias * confidenceBias
        return softBound(
            value = scaled,
            min = policy.frontalSoftMinMm,
            max = policy.frontalSoftMaxMm,
            softness = policy.frontalSoftBoundSharpness,
        )
    }

    private fun fallbackMeasurement(): FusedFingerMeasurement =
        FusedFingerMeasurement(
            widthMm = 18.0,
            thicknessMm = 14.0,
            circumferenceMm = EllipseMath.circumferenceFromWidthThickness(18.0, 14.0),
            equivalentDiameterMm = EllipseMath.equivalentDiameterFromCircumference(
                EllipseMath.circumferenceFromWidthThickness(18.0, 14.0),
            ),
            confidenceScore = 0.2f,
            warnings = listOf(HandMeasureWarning.BEST_EFFORT_ESTIMATE),
            debugNotes = listOf("fallback=no_steps"),
        )

    private fun baseWeight(step: StepMeasurement): Double {
        val sourceWeight =
            when (step.measurementSource) {
                WidthMeasurementSource.EDGE_PROFILE -> 1.0
                WidthMeasurementSource.LANDMARK_HEURISTIC -> 0.74
                WidthMeasurementSource.DEFAULT_HEURISTIC -> 0.58
            }
        val confidenceWeight = (step.confidence * 0.58f + step.measurementConfidence * 0.42f).toDouble().coerceIn(0.04, 1.0)
        return sourceWeight * confidenceWeight
    }

    private fun rejectOutlierThickness(candidates: List<WeightedThickness>): List<WeightedThickness> {
        if (candidates.size <= 2) return candidates
        val med = weightedMedian(candidates) { it.thicknessMm }
        val deviations = candidates.map { abs(it.thicknessMm - med) }
        val mad = median(deviations).coerceAtLeast(maxOf(policy.outlierFloorMm * 0.35, med * 0.02))
        val allowed = maxOf(policy.outlierFloorMm, mad * policy.outlierMadMultiplier)
        val filtered = candidates.filter { abs(it.thicknessMm - med) <= allowed }
        return if (filtered.isEmpty()) listOf(candidates.minByOrNull { abs(it.thicknessMm - med) }!!) else filtered
    }

    private fun solveRobustThickness(
        candidates: List<WeightedThickness>,
        fallbackThicknessMm: Double,
    ): Double {
        if (candidates.isEmpty()) return fallbackThicknessMm
        var center = weightedMedian(candidates) { it.thicknessMm }
        repeat(policy.irlsIterations) {
            val scale =
                median(candidates.map { abs(it.thicknessMm - center) })
                    .coerceAtLeast(maxOf(policy.irlsMinScaleMm, center * policy.irlsScaleRatioFloor))
            var numerator = 0.0
            var denominator = 0.0
            candidates.forEach { candidate ->
                val normalizedResidual = (candidate.thicknessMm - center) / scale
                val robustWeight = 1.0 / (1.0 + (normalizedResidual / policy.irlsCauchyK).pow(2.0))
                val weight = candidate.weight * robustWeight
                numerator += weight * candidate.thicknessMm
                denominator += weight
            }
            val priorWeight = policy.priorWeightBase / (1.0 + candidates.size * 0.35 + denominator * 0.22)
            center = (numerator + fallbackThicknessMm * priorWeight) / (denominator + priorWeight).coerceAtLeast(1e-6)
        }
        return center
    }

    private fun supportBlend(candidates: List<WeightedThickness>): Double {
        if (candidates.isEmpty()) return 0.0
        val total = candidates.sumOf { it.weight }
        val edgeWeight = candidates.filter { it.measurementSource == WidthMeasurementSource.EDGE_PROFILE }.sumOf { it.weight }
        val support = (total / policy.nominalThicknessSupportWeight).coerceIn(0.0, 1.0)
        val edgeRatio = (edgeWeight / total.coerceAtLeast(1e-6)).coerceIn(0.0, 1.0)
        return (support * 0.75 + edgeRatio * 0.25).coerceIn(0.15, 0.98)
    }

    private fun enforceAspectBounds(
        widthMm: Double,
        thicknessMm: Double,
    ): Double {
        val minAllowed = widthMm * policy.minThicknessAspectRatio
        val maxAllowed = widthMm * policy.maxThicknessAspectRatio
        return softBound(
            value = thicknessMm,
            min = minAllowed,
            max = maxAllowed,
            softness = policy.aspectSoftBoundSharpness,
        )
    }

    private fun measurementConfidence(
        candidates: List<WeightedThickness>,
        residuals: List<Double>,
        symmetry: SymmetryPenalties,
    ): Float {
        if (candidates.isEmpty()) return 0.2f
        val supportScore = (candidates.sumOf { it.weight } / policy.nominalThicknessSupportWeight).coerceIn(0.0, 1.0)
        val sourceScore =
            candidates
                .map {
                    when (it.measurementSource) {
                        WidthMeasurementSource.EDGE_PROFILE -> 1.0
                        WidthMeasurementSource.LANDMARK_HEURISTIC -> 0.72
                        WidthMeasurementSource.DEFAULT_HEURISTIC -> 0.56
                    }
                }.average()
        val residualMad = median(residuals.map { abs(it) })
        val stabilityScore = (1.0 - residualMad / maxOf(policy.stabilityResidualFloorMm, candidates.map { it.thicknessMm }.average() * 0.09)).coerceIn(0.0, 1.0)
        val symmetryScore = (1f - symmetry.totalPenalty).coerceIn(0f, 1f)
        return (
            supportScore * 0.50 +
                sourceScore * 0.22 +
                stabilityScore * 0.18 +
                symmetryScore * 0.10
        ).toFloat().coerceIn(0f, 1f)
    }

    private fun buildSymmetryPenalties(candidates: List<WeightedThickness>): SymmetryPenalties {
        val bySource = candidates.associateBy { it.source.role() }
        val left = bySource[CaptureStepRole.LEFT_OBLIQUE]?.thicknessMm
        val right = bySource[CaptureStepRole.RIGHT_OBLIQUE]?.thicknessMm
        val up = bySource[CaptureStepRole.TILT_UP]?.thicknessMm
        val down = bySource[CaptureStepRole.TILT_DOWN]?.thicknessMm
        val leftRightPenalty = pairPenalty(left, right)
        val upDownPenalty = pairPenalty(up, down)
        return SymmetryPenalties(
            leftRightPenalty = leftRightPenalty,
            upDownPenalty = upDownPenalty,
            totalPenalty = (leftRightPenalty + upDownPenalty).coerceIn(0f, 0.34f),
            notes =
                listOfNotNull(
                    if (left != null && right != null) "leftRightResidual=${"%.3f".format(abs(left - right))}" else null,
                    if (up != null && down != null) "upDownResidual=${"%.3f".format(abs(up - down))}" else null,
                ),
        )
    }

    private fun pairPenalty(
        a: Double?,
        b: Double?,
    ): Float {
        if (a == null || b == null) return policy.missingPairPenalty
        val denom = maxOf((a + b) / 2.0, 1.0)
        return (abs(a - b) / denom).toFloat().coerceIn(0f, policy.maxPairPenalty)
    }

    private fun softBound(
        value: Double,
        min: Double,
        max: Double,
        softness: Double,
    ): Double {
        if (min >= max) return value
        val mid = (min + max) / 2.0
        val half = (max - min) / 2.0
        val normalized = ((value - mid) / half).coerceIn(-5.0, 5.0)
        return mid + half * tanh(normalized * softness)
    }

    private fun weightedMedian(
        values: List<WeightedThickness>,
        selector: (WeightedThickness) -> Double,
    ): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sortedBy(selector)
        val totalWeight = sorted.sumOf { it.weight }.coerceAtLeast(1e-6)
        var cumulative = 0.0
        sorted.forEach { entry ->
            cumulative += entry.weight
            if (cumulative >= totalWeight / 2.0) return selector(entry)
        }
        return selector(sorted.last())
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
    }

    private data class WeightedThickness(
        val source: CaptureStep,
        val thicknessMm: Double,
        val rawThicknessMm: Double,
        val weight: Double,
        val measurementSource: WidthMeasurementSource,
    )

    private data class SymmetryPenalties(
        val leftRightPenalty: Float,
        val upDownPenalty: Float,
        val totalPenalty: Float,
        val notes: List<String>,
    )
}

data class ThicknessEstimationPolicy(
    val obliqueCorrection: Double = 0.90,
    val tiltCorrection: Double = 0.93,
    val frontalFallbackThicknessRatio: Double = 0.80,
    val minThicknessCandidatesForStableEstimate: Int = 2,
    val frontalSoftMinMm: Double = 12.0,
    val frontalSoftMaxMm: Double = 24.0,
    val frontalSoftBoundSharpness: Double = 1.35,
    val sideSoftMinMm: Double = 10.0,
    val sideSoftMaxMm: Double = 22.0,
    val sideSoftBoundSharpness: Double = 1.28,
    val minThicknessAspectRatio: Double = 0.55,
    val maxThicknessAspectRatio: Double = 0.96,
    val aspectSoftBoundSharpness: Double = 1.25,
    val landmarkSourceScale: Double = 0.95,
    val defaultSourceScale: Double = 0.90,
    val landmarkWidthScale: Double = 0.95,
    val defaultWidthScale: Double = 0.90,
    val widthConfidenceBase: Float = 0.94f,
    val widthConfidenceGain: Float = 0.10f,
    val outlierFloorMm: Double = 0.65,
    val outlierMadMultiplier: Double = 2.8,
    val irlsIterations: Int = 6,
    val irlsCauchyK: Double = 1.45,
    val irlsMinScaleMm: Double = 0.35,
    val irlsScaleRatioFloor: Double = 0.035,
    val priorWeightBase: Double = 0.95,
    val nominalThicknessSupportWeight: Double = 2.5,
    val stabilityResidualFloorMm: Double = 0.75,
    val missingPairPenalty: Float = 0.05f,
    val maxPairPenalty: Float = 0.18f,
) {
    fun thicknessCorrection(
        step: CaptureStep,
        source: WidthMeasurementSource,
        measurementConfidence: Float,
    ): Double {
        val roleBase =
            when (step.role()) {
                CaptureStepRole.LEFT_OBLIQUE, CaptureStepRole.RIGHT_OBLIQUE -> obliqueCorrection
                CaptureStepRole.TILT_UP, CaptureStepRole.TILT_DOWN -> tiltCorrection
                CaptureStepRole.FRONTAL -> 1.0
            }
        val sourceScale =
            when (source) {
                WidthMeasurementSource.EDGE_PROFILE -> 1.0
                WidthMeasurementSource.LANDMARK_HEURISTIC -> landmarkSourceScale
                WidthMeasurementSource.DEFAULT_HEURISTIC -> defaultSourceScale
            }
        val confidenceScale = (0.90 + measurementConfidence.coerceIn(0f, 1f) * 0.18).coerceIn(0.85, 1.08)
        return roleBase * sourceScale * confidenceScale
    }
}
