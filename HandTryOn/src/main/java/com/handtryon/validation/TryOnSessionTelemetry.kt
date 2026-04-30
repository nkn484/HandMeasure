package com.handtryon.validation

import org.json.JSONObject
import java.io.File

data class TryOnSessionTelemetry(
    val sessionId: String,
    val rendererMode: TryOnTelemetryRendererMode,
    val frameSource: String,
    val rotationDegrees: Int,
    val mirrored: Boolean,
    val handedness: String,
    val trackingFps: Float,
    val detectorLatencyMs: Float,
    val renderUpdateRate: Float,
    val qualityAction: String,
    val fitSource: String,
    val assetValidationStatus: String,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("sessionId", sessionId)
            .put("rendererMode", rendererMode.name)
            .put("frameSource", frameSource)
            .put("rotationDegrees", rotationDegrees)
            .put("mirrored", mirrored)
            .put("handedness", handedness)
            .put("trackingFps", trackingFps.toDouble())
            .put("detectorLatencyMs", detectorLatencyMs.toDouble())
            .put("renderUpdateRate", renderUpdateRate.toDouble())
            .put("qualityAction", qualityAction)
            .put("fitSource", fitSource)
            .put("assetValidationStatus", assetValidationStatus)
}

class TryOnSessionTelemetryExporter(
    private val outputDir: File,
) {
    fun export(telemetry: TryOnSessionTelemetry): File {
        outputDir.mkdirs()
        val outputFile = File(outputDir, "tryon_session_${telemetry.sessionId}.json")
        outputFile.writeText(telemetry.toJson().toString(2) + "\n", Charsets.UTF_8)
        return outputFile
    }
}
