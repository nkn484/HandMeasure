package com.handmeasure.measurement

data class QualityWeights(
    val detectionConfidence: Float = 0.30f,
    val poseConfidence: Float = 0.25f,
    val measurementConfidence: Float = 0.35f,
    val planeConfidence: Float = 0.10f,
)

data class FrameQualityInput(
    val handDetectionScore: Float,
    val handLandmarkScore: Float,
    val ringZoneScore: Float,
    val cardDetectionScore: Float,
    val cardRectangularityScore: Float,
    val cardEdgeSupportScore: Float,
    val blurScoreGlobal: Float,
    val blurScoreFingerRoi: Float,
    val motionScore: Float,
    val lightingScore: Float,
    val poseScore: Float,
    val planeScore: Float,
)

data class FrameQualitySubscores(
    val detectionConfidence: Float,
    val poseConfidence: Float,
    val measurementConfidence: Float,
    val blurScore: Float,
    val motionScore: Float,
    val lightingScore: Float,
    val cardScore: Float,
)

data class FrameQualityResult(
    val totalScore: Float,
    val subscores: FrameQualitySubscores,
    val warnings: List<String>,
    val confidencePenaltyReasons: List<String>,
)

class FrameQualityScorer(
    private val weights: QualityWeights = QualityWeights(),
) {
    fun score(input: FrameQualityInput): FrameQualityResult {
        val cardQuality =
            (
                input.cardDetectionScore * 0.5f +
                    input.cardRectangularityScore * 0.25f +
                    input.cardEdgeSupportScore * 0.25f
            ).coerceIn(0f, 1f)
        val blurScore = (input.blurScoreGlobal * 0.45f + input.blurScoreFingerRoi * 0.55f).coerceIn(0f, 1f)

        val detectionConfidence =
            (
                input.handDetectionScore * 0.34f +
                    input.handLandmarkScore * 0.18f +
                    cardQuality * 0.33f +
                    input.ringZoneScore * 0.15f
            ).coerceIn(0f, 1f)
        val poseConfidence = input.poseScore.coerceIn(0f, 1f)
        val measurementConfidence =
            (
                blurScore * 0.34f +
                    input.motionScore * 0.26f +
                    input.lightingScore * 0.20f +
                    cardQuality * 0.20f
            ).coerceIn(0f, 1f)

        val total =
            (
                detectionConfidence * weights.detectionConfidence +
                    poseConfidence * weights.poseConfidence +
                    measurementConfidence * weights.measurementConfidence +
                    input.planeScore.coerceIn(0f, 1f) * weights.planeConfidence
            ).coerceIn(0f, 1f)

        val warnings = mutableListOf<String>()
        val penalties = mutableListOf<String>()
        if (input.handDetectionScore < 0.35f) penalties += "hand_detection_low"
        if (input.cardDetectionScore < 0.35f) penalties += "card_detection_low"
        if (input.poseScore < 0.45f) penalties += "pose_low"
        if (blurScore < 0.4f) {
            warnings += "blur_high"
            penalties += "blur_high"
        }
        if (input.motionScore < 0.4f) {
            warnings += "motion_high"
            penalties += "motion_high"
        }
        if (input.lightingScore < 0.4f) {
            warnings += "lighting_poor"
            penalties += "lighting_poor"
        }
        if (cardQuality < 0.45f) warnings += "card_quality_weak"
        if (input.ringZoneScore < 0.5f) warnings += "ring_zone_weak"

        return FrameQualityResult(
            totalScore = total,
            subscores =
                FrameQualitySubscores(
                    detectionConfidence = detectionConfidence,
                    poseConfidence = poseConfidence,
                    measurementConfidence = measurementConfidence,
                    blurScore = blurScore,
                    motionScore = input.motionScore.coerceIn(0f, 1f),
                    lightingScore = input.lightingScore.coerceIn(0f, 1f),
                    cardScore = cardQuality,
                ),
            warnings = warnings.distinct(),
            confidencePenaltyReasons = penalties.distinct(),
        )
    }
}
