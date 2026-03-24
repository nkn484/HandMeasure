package com.handmeasure.coordinator

import com.handmeasure.api.HandMeasureConfig
import com.handmeasure.api.HandMeasureResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class DebugOverlayFrame(
    val stepName: String,
    val jpegBytes: ByteArray,
)

internal class DebugSessionExporter(
    private val config: HandMeasureConfig,
    private val debugExportDirProvider: (() -> File?)?,
) {
    fun export(
        result: HandMeasureResult,
        overlays: List<DebugOverlayFrame>,
    ) {
        if (!config.debugExportEnabled) return
        val exportDir = debugExportDirProvider?.invoke() ?: return
        if (!exportDir.exists()) exportDir.mkdirs()
        val sessionId = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        val jsonFile = File(exportDir, "handmeasure_session_$sessionId.json")
        jsonFile.writeText(buildPayload(result).toString(2))

        if (!config.debugOverlayEnabled) return
        overlays.forEach { overlay ->
            File(exportDir, "step_${overlay.stepName.lowercase(Locale.US)}_$sessionId.jpg").writeBytes(overlay.jpegBytes)
        }
    }

    private fun buildPayload(result: HandMeasureResult): JSONObject =
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
            put("resultMode", result.resultMode.name)
            put("qualityLevel", result.qualityLevel.name)
            put("retryRecommended", result.retryRecommended)
            put("calibrationStatus", result.calibrationStatus.name)
            put("measurementSources", JSONArray(result.measurementSources.map { it.name }))
            put(
                "debugMetadata",
                JSONObject().apply {
                    put("mmPerPxX", result.debugMetadata?.mmPerPxX)
                    put("mmPerPxY", result.debugMetadata?.mmPerPxY)
                    put("frontalWidthPx", result.debugMetadata?.frontalWidthPx)
                    put("thicknessSamplesMm", JSONArray(result.debugMetadata?.thicknessSamplesMm ?: emptyList<Double>()))
                },
            )
        }
}
