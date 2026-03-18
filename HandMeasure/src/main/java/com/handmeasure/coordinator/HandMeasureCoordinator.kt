package com.handmeasure.coordinator

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import com.handmeasure.api.CaptureStep
import com.handmeasure.api.CapturedStepInfo
import com.handmeasure.api.DebugMetadata
import com.handmeasure.api.FusedDiagnostics
import com.handmeasure.api.HandMeasureConfig
import com.handmeasure.api.HandMeasureResult
import com.handmeasure.api.HandMeasureWarning
import com.handmeasure.api.SessionDiagnostics
import com.handmeasure.api.StepDiagnostics
import com.handmeasure.flow.CaptureUiState
import com.handmeasure.flow.HandMeasureStateMachine
import com.handmeasure.flow.StepCandidate
import com.handmeasure.measurement.FingerMeasurementEngine
import com.handmeasure.measurement.FingerMeasurementFusion
import com.handmeasure.measurement.FrameQualityInput
import com.handmeasure.measurement.FrameQualityScorer
import com.handmeasure.measurement.MetricScale
import com.handmeasure.measurement.ScaleCalibrator
import com.handmeasure.measurement.StepMeasurement
import com.handmeasure.measurement.TableRingSizeMapper
import com.handmeasure.vision.CardDetection
import com.handmeasure.vision.HandDetection
import com.handmeasure.vision.HandLandmarkEngine
import com.handmeasure.vision.OpenCvReferenceCardDetector
import com.handmeasure.vision.PoseClassifier
import com.handmeasure.vision.PoseMatchLevel
import com.handmeasure.vision.ReferenceCardDetector
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

data class LiveAnalysisState(
    val captureState: CaptureUiState,
    val qualityScore: Float,
    val detectionConfidence: Float,
    val poseConfidence: Float,
    val measurementConfidence: Float,
    val handScore: Float,
    val cardScore: Float,
    val blurScore: Float,
    val motionScore: Float,
    val lightingScore: Float,
    val handDetection: HandDetection?,
    val cardDetection: CardDetection?,
    val frameWidth: Int,
    val frameHeight: Int,
    val poseGuidanceHint: String?,
)

class HandMeasureCoordinator(
    private val config: HandMeasureConfig,
    private val handLandmarkEngine: HandLandmarkEngine,
    private val referenceCardDetector: ReferenceCardDetector = OpenCvReferenceCardDetector(),
    private val poseClassifier: PoseClassifier = PoseClassifier(),
    private val frameQualityScorer: FrameQualityScorer = FrameQualityScorer(),
    private val scaleCalibrator: ScaleCalibrator = ScaleCalibrator(),
    private val fingerMeasurementEngine: FingerMeasurementEngine = FingerMeasurementEngine(),
    private val fingerMeasurementFusion: FingerMeasurementFusion = FingerMeasurementFusion(),
    private val debugExportDirProvider: (() -> File?)? = null,
) {
    private val stateMachine = HandMeasureStateMachine(config.qualityThresholds)
    private val ringSizeMapper = TableRingSizeMapper()
    private var previousFrameLuma: FloatArray? = null
    private var previousStep: CaptureStep? = null

    fun currentState(): CaptureUiState = stateMachine.snapshot()

    fun analyzeFrame(jpegBytes: ByteArray, bitmap: Bitmap): LiveAnalysisState {
        val captureState = stateMachine.currentStep()
        if (previousStep != captureState.step) {
            poseClassifier.reset()
            previousStep = captureState.step
        }

        val hand = handLandmarkEngine.detect(bitmap)
        val card = referenceCardDetector.detect(bitmap)
        val poseEvaluation = hand?.let { poseClassifier.evaluate(captureState.step, it) }

        val handScore = hand?.confidence ?: 0f
        val cardScore = card?.confidence ?: 0f
        val ringZoneScore = if (hand?.fingerJointPair(config.targetFinger) != null) 1f else 0f
        val imageSignals = estimateImageSignals(bitmap, hand, card)
        val planeScore = estimatePlaneCloseness(hand, card, bitmap)

        val quality =
            frameQualityScorer.score(
                FrameQualityInput(
                    handDetectionScore = hand?.detectionConfidence ?: 0f,
                    handLandmarkScore = hand?.presenceConfidence ?: 0f,
                    ringZoneScore = ringZoneScore,
                    cardDetectionScore = cardScore,
                    cardRectangularityScore = card?.rectangularityScore ?: 0f,
                    cardEdgeSupportScore = card?.edgeSupportScore ?: 0f,
                    blurScoreGlobal = imageSignals.blurGlobalScore,
                    blurScoreFingerRoi = imageSignals.blurFingerRoiScore,
                    motionScore = imageSignals.motionScore,
                    lightingScore = imageSignals.lightingScore,
                    poseScore = poseEvaluation?.smoothedScore ?: 0f,
                    planeScore = planeScore,
                ),
            )

        val updatedState =
            stateMachine.onFrameEvaluated(
                StepCandidate(
                    step = captureState.step,
                    frameBytes = jpegBytes,
                    qualityScore = quality.totalScore,
                    poseScore = quality.subscores.poseConfidence,
                    cardScore = quality.subscores.cardScore,
                    handScore = quality.subscores.detectionConfidence,
                    blurScore = quality.subscores.blurScore,
                    motionScore = quality.subscores.motionScore,
                    lightingScore = quality.subscores.lightingScore,
                    confidencePenaltyReasons = quality.confidencePenaltyReasons,
                ),
            )

        return LiveAnalysisState(
            captureState = updatedState,
            qualityScore = quality.totalScore,
            detectionConfidence = quality.subscores.detectionConfidence,
            poseConfidence = quality.subscores.poseConfidence,
            measurementConfidence = quality.subscores.measurementConfidence,
            handScore = handScore,
            cardScore = cardScore,
            blurScore = quality.subscores.blurScore,
            motionScore = quality.subscores.motionScore,
            lightingScore = quality.subscores.lightingScore,
            handDetection = hand,
            cardDetection = card,
            frameWidth = bitmap.width,
            frameHeight = bitmap.height,
            poseGuidanceHint = buildPoseHint(poseEvaluation?.level, poseEvaluation?.guidanceHint, hand, card),
        )
    }

    fun advanceWithBestCandidate(): CaptureUiState = stateMachine.advanceWithBestCandidate()

    fun retryCurrentStep(): CaptureUiState = stateMachine.retryCurrentStep()

    fun isCaptureComplete(): Boolean = stateMachine.isComplete()

    fun finalizeResult(): HandMeasureResult {
        val snapshot = stateMachine.snapshot()
        val stepResults = snapshot.completedSteps.sortedBy { it.step.ordinal }
        val warnings = mutableSetOf<HandMeasureWarning>()
        val stepMeasurements = mutableListOf<StepMeasurement>()
        val debugNotes = mutableListOf<String>()
        val stepDiagnostics = mutableListOf<StepDiagnostics>()
        var bestScaleMmPerPxX = 0.12
        var bestScaleMmPerPxY = 0.12
        var frontalWidthPx = 0.0
        val thicknessSamples = mutableListOf<Double>()

        stepResults.forEach { candidate ->
            val bitmap = BitmapFactory.decodeByteArray(candidate.frameBytes, 0, candidate.frameBytes.size) ?: return@forEach
            val hand = handLandmarkEngine.detect(bitmap)
            val card = referenceCardDetector.detect(bitmap)
            val poseEvaluation = hand?.let { poseClassifier.evaluate(candidate.step, it) }
            if (candidate.cardScore < config.qualityThresholds.cardMinScore) warnings += HandMeasureWarning.LOW_CARD_CONFIDENCE
            if ((poseEvaluation?.smoothedScore ?: candidate.poseScore) < 0.45f) warnings += HandMeasureWarning.LOW_POSE_CONFIDENCE
            if (candidate.lightingScore < config.qualityThresholds.lightingMinScore) warnings += HandMeasureWarning.LOW_LIGHTING

            val scale =
                if (card != null) {
                    scaleCalibrator.calibrate(card)
                } else {
                    warnings += HandMeasureWarning.BEST_EFFORT_ESTIMATE
                    null
                }
            if (scale != null) {
                bestScaleMmPerPxX = scale.mmPerPxX
                bestScaleMmPerPxY = scale.mmPerPxY
            }

            val effectiveScale = scale ?: MetricScale(bestScaleMmPerPxX, bestScaleMmPerPxY)
            val measurement =
                if (hand != null) {
                    fingerMeasurementEngine.measureVisibleWidth(bitmap, hand, config.targetFinger, effectiveScale)
                } else {
                    warnings += HandMeasureWarning.BEST_EFFORT_ESTIMATE
                    null
                }

            val widthMm = measurement?.widthMm ?: 18.0
            val measurementConfidence =
                if (measurement == null) {
                    0.18f
                } else {
                    (
                        (if (measurement.usedFallback) 0.35f else 0.85f) * 0.35f +
                            (1f - (measurement.widthVarianceMm / 4.0).toFloat().coerceIn(0f, 1f)) * 0.35f +
                            (measurement.validSamples / 7f).coerceIn(0f, 1f) * 0.30f
                    ).coerceIn(0f, 1f)
                }
            if (measurement?.usedFallback == true || measurement == null) warnings += HandMeasureWarning.BEST_EFFORT_ESTIMATE
            if (candidate.step == CaptureStep.FRONT_PALM) {
                frontalWidthPx = measurement?.widthPx ?: frontalWidthPx
            } else {
                thicknessSamples += widthMm
            }
            stepMeasurements +=
                StepMeasurement(
                    step = candidate.step,
                    widthMm = widthMm,
                    confidence = candidate.qualityScore,
                    measurementConfidence = measurementConfidence,
                    rawWidthMm = widthMm,
                    debugNotes =
                        listOf(
                            "validSamples=${measurement?.validSamples ?: 0}",
                            "widthVarianceMm=${measurement?.widthVarianceMm ?: -1.0}",
                            "fallback=${measurement?.usedFallback ?: true}",
                        ),
                )

            stepDiagnostics +=
                StepDiagnostics(
                    step = candidate.step,
                    handScore = candidate.handScore,
                    cardScore = candidate.cardScore,
                    poseScore = poseEvaluation?.smoothedScore ?: candidate.poseScore,
                    blurScore = candidate.blurScore,
                    motionScore = candidate.motionScore,
                    lightingScore = candidate.lightingScore,
                    cardCoverageRatio = card?.coverageRatio ?: 0f,
                    cardAspectResidual = card?.aspectResidual ?: 1f,
                    cardRectangularityScore = card?.rectangularityScore ?: 0f,
                    cardEdgeSupportScore = card?.edgeSupportScore ?: 0f,
                    cardRectificationConfidence = card?.rectificationConfidence ?: 0f,
                    scaleMmPerPxX = effectiveScale.mmPerPxX,
                    scaleMmPerPxY = effectiveScale.mmPerPxY,
                    widthSamplesMm = measurement?.sampledWidthsMm ?: emptyList(),
                    widthVarianceMm = measurement?.widthVarianceMm ?: 999.0,
                    accepted = measurement != null,
                    rejectedReason = if (measurement == null || measurement.usedFallback) "fallback_or_no_edges" else null,
                    confidencePenaltyReasons = candidate.confidencePenaltyReasons,
                )
            bitmap.recycle()
        }

        if (stepMeasurements.isEmpty()) {
            warnings += HandMeasureWarning.BEST_EFFORT_ESTIMATE
            stepMeasurements += StepMeasurement(CaptureStep.FRONT_PALM, widthMm = 18.0, confidence = 0.2f, measurementConfidence = 0.2f)
        }

        val fused = fingerMeasurementFusion.fuse(stepMeasurements)
        warnings += fused.warnings
        if (snapshot.completedSteps.any { it.blurScore < config.qualityThresholds.blurMinScore }) warnings += HandMeasureWarning.HIGH_BLUR
        if (snapshot.completedSteps.any { it.motionScore < config.qualityThresholds.motionMinScore }) warnings += HandMeasureWarning.HIGH_MOTION

        val ringSize = ringSizeMapper.nearestForDiameter(config.ringSizeTable, fused.equivalentDiameterMm)
        val captured =
            snapshot.completedSteps.map {
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
                stepDiagnostics = stepDiagnostics,
                fusedDiagnostics =
                    FusedDiagnostics(
                        widthMm = fused.widthMm,
                        thicknessMm = fused.thicknessMm,
                        circumferenceMm = fused.circumferenceMm,
                        equivalentDiameterMm = fused.equivalentDiameterMm,
                        suggestedRingSizeLabel = ringSize.label,
                        finalConfidence = fused.confidenceScore,
                        warningReasons = warnings.map { it.name },
                        perStepResidualsMm = fused.perStepResidualsMm,
                    ),
            )

        val result =
            HandMeasureResult(
                targetFinger = config.targetFinger,
                fingerWidthMm = fused.widthMm,
                fingerThicknessMm = fused.thicknessMm,
                estimatedCircumferenceMm = fused.circumferenceMm,
                equivalentDiameterMm = fused.equivalentDiameterMm,
                suggestedRingSizeLabel = ringSize.label,
                confidenceScore = fused.confidenceScore.coerceIn(0f, 1f),
                warnings = warnings.toList(),
                capturedSteps = captured,
                debugMetadata =
                    DebugMetadata(
                        mmPerPxX = bestScaleMmPerPxX,
                        mmPerPxY = bestScaleMmPerPxY,
                        frontalWidthPx = frontalWidthPx,
                        thicknessSamplesMm = thicknessSamples,
                        rawNotes = debugNotes + fused.debugNotes,
                        sessionDiagnostics = sessionDiagnostics,
                    ),
            )

        exportDebugSession(result, stepResults)
        return result
    }

    private fun exportDebugSession(
        result: HandMeasureResult,
        stepResults: List<StepCandidate>,
    ) {
        if (!config.debugExportEnabled) return
        val exportDir = debugExportDirProvider?.invoke() ?: return
        if (!exportDir.exists()) exportDir.mkdirs()
        val sessionId = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        val jsonFile = File(exportDir, "handmeasure_session_$sessionId.json")
        val payload =
            JSONObject().apply {
                put("targetFinger", result.targetFinger.name)
                put("fingerWidthMm", result.fingerWidthMm)
                put("fingerThicknessMm", result.fingerThicknessMm)
                put("estimatedCircumferenceMm", result.estimatedCircumferenceMm)
                put("equivalentDiameterMm", result.equivalentDiameterMm)
                put("suggestedRingSizeLabel", result.suggestedRingSizeLabel)
                put("confidenceScore", result.confidenceScore.toDouble())
                put("warnings", JSONArray(result.warnings.map { it.name }))
                put("capturedSteps", JSONArray(result.capturedSteps.map { it.step.name }))
                put("debugMetadata", JSONObject().apply {
                    put("mmPerPxX", result.debugMetadata?.mmPerPxX)
                    put("mmPerPxY", result.debugMetadata?.mmPerPxY)
                    put("frontalWidthPx", result.debugMetadata?.frontalWidthPx)
                    put("thicknessSamplesMm", JSONArray(result.debugMetadata?.thicknessSamplesMm ?: emptyList<Double>()))
                })
            }
        jsonFile.writeText(payload.toString(2))

        if (config.debugOverlayEnabled) {
            stepResults.forEach { candidate ->
                val bitmap = BitmapFactory.decodeByteArray(candidate.frameBytes, 0, candidate.frameBytes.size) ?: return@forEach
                val hand = handLandmarkEngine.detect(bitmap)
                val card = referenceCardDetector.detect(bitmap)
                val annotated = annotateBitmap(bitmap, hand, card)
                FileOutputStream(File(exportDir, "step_${candidate.step.name.lowercase(Locale.US)}_$sessionId.jpg")).use { output ->
                    annotated.compress(Bitmap.CompressFormat.JPEG, 90, output)
                }
                annotated.recycle()
                bitmap.recycle()
            }
        }
    }

    private fun annotateBitmap(
        bitmap: Bitmap,
        hand: HandDetection?,
        card: CardDetection?,
    ): Bitmap {
        val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val handPaint =
            Paint().apply {
                color = Color.CYAN
                style = Paint.Style.FILL
                strokeWidth = 3f
            }
        hand?.imageLandmarks?.forEach { landmark ->
            canvas.drawCircle(landmark.x, landmark.y, 5f, handPaint)
        }
        val cardPaint =
            Paint().apply {
                color = Color.YELLOW
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
        val corners = card?.corners.orEmpty()
        if (corners.size == 4) {
            for (index in corners.indices) {
                val a = corners[index]
                val b = corners[(index + 1) % corners.size]
                canvas.drawLine(a.first, a.second, b.first, b.second, cardPaint)
            }
        }
        return out
    }

    private fun estimateImageSignals(
        bitmap: Bitmap,
        hand: HandDetection?,
        card: CardDetection?,
    ): ImageSignalScores {
        val lumaGrid = sampleLumaGrid(bitmap, 32, 32)
        val blurGlobal = laplacianVarianceScore(lumaGrid.values, lumaGrid.width, lumaGrid.height)
        val fingerRoi = estimateFingerRoi(bitmap, hand, card)
        val blurFinger = laplacianVarianceScore(fingerRoi.values, fingerRoi.width, fingerRoi.height)
        val motion = motionScore(fingerRoi.values)
        val lighting = lightingScore(lumaGrid.values)
        return ImageSignalScores(
            blurGlobalScore = blurGlobal,
            blurFingerRoiScore = blurFinger,
            motionScore = motion,
            lightingScore = lighting,
        )
    }

    private fun sampleLumaGrid(bitmap: Bitmap, gridX: Int, gridY: Int): GridSample {
        val w = gridX.coerceAtLeast(4)
        val h = gridY.coerceAtLeast(4)
        val values = FloatArray(w * h)
        for (j in 0 until h) {
            for (i in 0 until w) {
                val x = ((i + 0.5f) / w * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                val y = ((j + 0.5f) / h * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                val pixel = bitmap.getPixel(x, y)
                val luma = (((pixel shr 16) and 0xff) * 0.299f + ((pixel shr 8) and 0xff) * 0.587f + (pixel and 0xff) * 0.114f)
                values[j * w + i] = luma
            }
        }
        return GridSample(values, w, h)
    }

    private fun estimateFingerRoi(
        bitmap: Bitmap,
        hand: HandDetection?,
        card: CardDetection?,
    ): GridSample {
        val centerX =
            when {
                hand != null -> {
                    val joint = hand.fingerJointPair(config.targetFinger)
                    joint?.let { ((it.first.x + it.second.x) / 2f).toInt() } ?: bitmap.width / 2
                }
                card != null -> card.rectangle.centerX.toInt()
                else -> bitmap.width / 2
            }
        val centerY =
            when {
                hand != null -> {
                    val joint = hand.fingerJointPair(config.targetFinger)
                    joint?.let { ((it.first.y + it.second.y) / 2f).toInt() } ?: bitmap.height / 2
                }
                card != null -> card.rectangle.centerY.toInt()
                else -> bitmap.height / 2
            }
        val halfW = (bitmap.width * 0.18f).toInt().coerceAtLeast(80)
        val halfH = (bitmap.height * 0.18f).toInt().coerceAtLeast(80)
        val left = (centerX - halfW).coerceIn(0, bitmap.width - 1)
        val top = (centerY - halfH).coerceIn(0, bitmap.height - 1)
        val right = (centerX + halfW).coerceIn(left + 1, bitmap.width)
        val bottom = (centerY + halfH).coerceIn(top + 1, bitmap.height)
        val gridW = 24
        val gridH = 24
        val values = FloatArray(gridW * gridH)
        for (j in 0 until gridH) {
            for (i in 0 until gridW) {
                val x = (left + ((i + 0.5f) / gridW * (right - left))).toInt().coerceIn(0, bitmap.width - 1)
                val y = (top + ((j + 0.5f) / gridH * (bottom - top))).toInt().coerceIn(0, bitmap.height - 1)
                val pixel = bitmap.getPixel(x, y)
                val luma = (((pixel shr 16) and 0xff) * 0.299f + ((pixel shr 8) and 0xff) * 0.587f + (pixel and 0xff) * 0.114f)
                values[j * gridW + i] = luma
            }
        }
        return GridSample(values, gridW, gridH)
    }

    private fun laplacianVarianceScore(values: FloatArray, width: Int, height: Int): Float {
        if (width < 3 || height < 3) return 0f
        val lap = ArrayList<Float>((width - 2) * (height - 2))
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val c = values[y * width + x]
                val left = values[y * width + x - 1]
                val right = values[y * width + x + 1]
                val up = values[(y - 1) * width + x]
                val down = values[(y + 1) * width + x]
                lap += (4f * c - left - right - up - down)
            }
        }
        if (lap.isEmpty()) return 0f
        val mean = lap.average().toFloat()
        val variance = lap.map { (it - mean) * (it - mean) }.average().toFloat()
        return (variance / 420f).coerceIn(0f, 1f)
    }

    private fun motionScore(current: FloatArray): Float {
        val previous = previousFrameLuma
        previousFrameLuma = current.copyOf()
        if (previous == null || previous.size != current.size) return 1f
        var diffSum = 0f
        current.indices.forEach { idx ->
            diffSum += abs(current[idx] - previous[idx])
        }
        val meanDiff = diffSum / current.size.toFloat()
        return (1f - meanDiff / 30f).coerceIn(0f, 1f)
    }

    private fun lightingScore(values: FloatArray): Float {
        if (values.isEmpty()) return 0f
        val mean = values.average().toFloat()
        var clippedDark = 0
        var clippedBright = 0
        values.forEach {
            if (it < 15f) clippedDark++
            if (it > 240f) clippedBright++
        }
        val clippingRatio = (clippedDark + clippedBright).toFloat() / values.size.toFloat()
        val centered = (1f - abs(mean - 140f) / 140f).coerceIn(0f, 1f)
        val clippingPenalty = (1f - clippingRatio / 0.18f).coerceIn(0f, 1f)
        return (centered * 0.65f + clippingPenalty * 0.35f).coerceIn(0f, 1f)
    }

    private fun estimatePlaneCloseness(hand: HandDetection?, card: CardDetection?, bitmap: Bitmap): Float {
        if (hand == null || card == null) return 0f
        val jointPair = hand.fingerJointPair(config.targetFinger) ?: return 0f
        val ringCenter = PointF((jointPair.first.x + jointPair.second.x) / 2f, (jointPair.first.y + jointPair.second.y) / 2f)
        val dx = (ringCenter.x - card.rectangle.centerX.toFloat()) / bitmap.width.toFloat()
        val dy = (ringCenter.y - card.rectangle.centerY.toFloat()) / bitmap.height.toFloat()
        val distance = hypot(dx.toDouble(), dy.toDouble())
        return (1.0 - distance / 0.55).toFloat().coerceIn(0f, 1f)
    }

    private fun buildPoseHint(
        level: PoseMatchLevel?,
        hint: String?,
        hand: HandDetection?,
        card: CardDetection?,
    ): String? {
        if (hand == null) return "Đưa tay vào khung"
        if (card == null) return "Đưa thẻ vào gần ngón tay"
        return when (level) {
            PoseMatchLevel.CORRECT -> null
            PoseMatchLevel.ALMOST_CORRECT, PoseMatchLevel.WRONG -> hint ?: "Điều chỉnh tư thế tay"
            null -> "Giữ tay ổn định"
        }
    }

    private data class GridSample(
        val values: FloatArray,
        val width: Int,
        val height: Int,
    )

    private data class ImageSignalScores(
        val blurGlobalScore: Float,
        val blurFingerRoiScore: Float,
        val motionScore: Float,
        val lightingScore: Float,
    )
}
