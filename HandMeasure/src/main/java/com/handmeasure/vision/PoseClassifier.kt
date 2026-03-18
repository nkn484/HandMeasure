package com.handmeasure.vision

import com.handmeasure.api.CaptureStep
import kotlin.math.abs
import kotlin.math.sqrt

enum class PoseMatchLevel {
    CORRECT,
    ALMOST_CORRECT,
    WRONG,
}

data class PoseEvaluation(
    val rawScore: Float,
    val smoothedScore: Float,
    val level: PoseMatchLevel,
    val guidanceHint: String?,
)

data class PoseSnapshot(
    val normalX: Float,
    val normalY: Float,
    val normalZ: Float,
)

class PoseClassifier {
    private val recentScores = ArrayDeque<Float>()
    private val maxHistory = 6
    private var lastStableScore: Float = 0f

    fun reset() {
        recentScores.clear()
        lastStableScore = 0f
    }

    fun classify(step: CaptureStep, handDetection: HandDetection): Float {
        val snapshot = extractPalmNormal(handDetection) ?: return 0f
        return classify(step, snapshot)
    }

    fun classify(step: CaptureStep, snapshot: PoseSnapshot): Float {
        val nx = snapshot.normalX
        val ny = snapshot.normalY
        val nz = snapshot.normalZ
        return when (step) {
            CaptureStep.FRONT_PALM -> axisDominanceScore(abs(nz), abs(nx), abs(ny))
            CaptureStep.LEFT_OBLIQUE -> signedAxisScore(-nx, abs(ny), abs(nz))
            CaptureStep.RIGHT_OBLIQUE -> signedAxisScore(nx, abs(ny), abs(nz))
            CaptureStep.UP_TILT -> signedAxisScore(-ny, abs(nx), abs(nz))
            CaptureStep.DOWN_TILT -> signedAxisScore(ny, abs(nx), abs(nz))
        }
    }

    fun evaluate(step: CaptureStep, handDetection: HandDetection): PoseEvaluation {
        val snapshot = extractPalmNormal(handDetection)
        val rawScore = if (snapshot == null) 0f else classify(step, snapshot)
        val smoothed = updateSmoothedScore(rawScore)
        val level =
            when {
                smoothed >= 0.72f -> PoseMatchLevel.CORRECT
                smoothed >= 0.5f -> PoseMatchLevel.ALMOST_CORRECT
                else -> PoseMatchLevel.WRONG
            }
        return PoseEvaluation(
            rawScore = rawScore,
            smoothedScore = smoothed,
            level = level,
            guidanceHint = buildGuidanceHint(step, snapshot, level),
        )
    }

    fun extractPalmNormal(handDetection: HandDetection): PoseSnapshot? {
        val world = handDetection.worldLandmarks
        if (world.size < 18) return null
        val wrist = world[0]
        val indexMcp = world[5]
        val pinkyMcp = world[17]
        val ax = indexMcp.x - wrist.x
        val ay = indexMcp.y - wrist.y
        val az = indexMcp.z - wrist.z
        val bx = pinkyMcp.x - wrist.x
        val by = pinkyMcp.y - wrist.y
        val bz = pinkyMcp.z - wrist.z
        val nx = ay * bz - az * by
        val ny = az * bx - ax * bz
        val nz = ax * by - ay * bx
        val mag = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
        return PoseSnapshot(nx / mag, ny / mag, nz / mag)
    }

    private fun axisDominanceScore(primary: Float, secondaryA: Float, secondaryB: Float): Float {
        val dominance = (primary - maxOf(secondaryA, secondaryB)).coerceAtLeast(0f)
        return (primary * 0.75f + dominance * 0.25f).coerceIn(0f, 1f)
    }

    private fun signedAxisScore(primarySigned: Float, secondaryA: Float, secondaryB: Float): Float {
        val primary = primarySigned.coerceAtLeast(0f)
        val dominance = (primary - maxOf(secondaryA, secondaryB)).coerceAtLeast(0f)
        return (primary * 0.75f + dominance * 0.25f).coerceIn(0f, 1f)
    }

    private fun updateSmoothedScore(rawScore: Float): Float {
        recentScores.addLast(rawScore)
        while (recentScores.size > maxHistory) {
            recentScores.removeFirst()
        }
        val average = recentScores.average().toFloat()
        val hysteresis = if (average < lastStableScore) 0.85f else 0.65f
        lastStableScore = (lastStableScore * hysteresis + average * (1f - hysteresis)).coerceIn(0f, 1f)
        return lastStableScore
    }

    private fun buildGuidanceHint(
        step: CaptureStep,
        snapshot: PoseSnapshot?,
        level: PoseMatchLevel,
    ): String? {
        if (level == PoseMatchLevel.CORRECT) return null
        if (snapshot == null) return "Đưa bàn tay vào khung rõ hơn"
        val nx = snapshot.normalX
        val ny = snapshot.normalY
        return when (step) {
            CaptureStep.LEFT_OBLIQUE -> if (nx > -0.35f) "Xoay tay sang trái thêm" else "Giữ tay ổn định"
            CaptureStep.RIGHT_OBLIQUE -> if (nx < 0.35f) "Xoay tay sang phải thêm" else "Giữ tay ổn định"
            CaptureStep.UP_TILT -> if (ny > -0.35f) "Nghiêng tay lên thêm" else "Giữ tay ổn định"
            CaptureStep.DOWN_TILT -> if (ny < 0.35f) "Nghiêng tay xuống thêm" else "Giữ tay ổn định"
            CaptureStep.FRONT_PALM -> "Hướng lòng bàn tay trực diện camera"
        }
    }
}
