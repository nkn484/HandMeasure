package com.handmeasure.measurement

import android.graphics.BitmapFactory
import com.handmeasure.api.CaptureStep
import com.handmeasure.api.HandMeasureConfig
import com.handmeasure.api.HandMeasureResult
import com.handmeasure.coordinator.HandMeasureCoordinator
import org.json.JSONObject
import java.io.File

data class ReplayOutput(
    val result: HandMeasureResult,
    val predictedWidthMm: Double,
    val predictedThicknessMm: Double,
    val predictedCircumferenceMm: Double,
    val predictedDiameterMm: Double,
    val predictedRingSize: String,
    val confidence: Float,
    val warnings: List<String>,
    val diameterErrorMm: Double? = null,
)

class MeasurementReplayRunner(
    private val coordinatorFactory: (HandMeasureConfig) -> HandMeasureCoordinator,
) {
    fun runFromDirectory(
        config: HandMeasureConfig,
        inputDir: File,
        outputReportFile: File? = null,
    ): ReplayOutput {
        val coordinator = coordinatorFactory(config)
        CaptureStep.entries.forEach { step ->
            val frameFile = resolveStepFile(inputDir, step) ?: return@forEach
            val bytes = frameFile.readBytes()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@forEach
            coordinator.analyzeFrame(bytes, bitmap)
            bitmap.recycle()
            coordinator.advanceWithBestCandidate()
        }
        val result = coordinator.finalizeResult()
        val groundTruthDiameter = parseGroundTruthDiameter(inputDir)
        val output =
            ReplayOutput(
                result = result,
                predictedWidthMm = result.fingerWidthMm,
                predictedThicknessMm = result.fingerThicknessMm,
                predictedCircumferenceMm = result.estimatedCircumferenceMm,
                predictedDiameterMm = result.equivalentDiameterMm,
                predictedRingSize = result.suggestedRingSizeLabel,
                confidence = result.confidenceScore,
                warnings = result.warnings.map { it.name },
                diameterErrorMm = groundTruthDiameter?.let { kotlin.math.abs(result.equivalentDiameterMm - it) },
            )
        if (outputReportFile != null) {
            outputReportFile.parentFile?.mkdirs()
            outputReportFile.writeText(output.toJson().toString(2))
        }
        return output
    }

    private fun parseGroundTruthDiameter(inputDir: File): Double? {
        val file = File(inputDir, "ground_truth.json")
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText())
            if (json.has("diameterMm")) json.getDouble("diameterMm") else null
        }.getOrNull()
    }

    private fun resolveStepFile(inputDir: File, step: CaptureStep): File? {
        val names =
            listOf(
                "${step.name.lowercase()}.jpg",
                "${step.name.lowercase()}.jpeg",
                "${step.name.lowercase()}.png",
            )
        return names.map { File(inputDir, it) }.firstOrNull { it.exists() }
    }
}

fun ReplayOutput.toJson(): JSONObject =
    JSONObject().apply {
        put("predictedWidthMm", predictedWidthMm)
        put("predictedThicknessMm", predictedThicknessMm)
        put("predictedCircumferenceMm", predictedCircumferenceMm)
        put("predictedDiameterMm", predictedDiameterMm)
        put("predictedRingSize", predictedRingSize)
        put("confidence", confidence.toDouble())
        put("warnings", warnings)
        if (diameterErrorMm != null) put("diameterErrorMm", diameterErrorMm)
    }
