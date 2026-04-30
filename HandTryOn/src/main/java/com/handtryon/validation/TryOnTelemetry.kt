package com.handtryon.validation

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class TryOnTelemetryRendererMode {
    ARCoreCamera3D,
    CameraRelative3D,
    Static3DPreview,
    Legacy2DOverlay,
}

data class TryOnTelemetryTransform(
    val centerX: Float? = null,
    val centerY: Float? = null,
    val scale: Float? = null,
    val rotationDegrees: Float? = null,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .putNullable("centerX", centerX)
            .putNullable("centerY", centerY)
            .putNullable("scale", scale)
            .putNullable("rotationDegrees", rotationDegrees)
}

data class TryOnTelemetryFrame(
    val timestampMs: Long,
    val frameIndex: Int,
    val rendererMode: TryOnTelemetryRendererMode,
    val trackingState: String,
    val updateAction: String,
    val qualityScore: Float,
    val rawTransform: TryOnTelemetryTransform? = null,
    val smoothedTransform: TryOnTelemetryTransform? = null,
    val renderStateUpdateHz: Float? = null,
    val detectorLatencyMs: Float? = null,
    val frameSource: String? = null,
    val rotationDegrees: Int? = null,
    val mirrored: Boolean? = null,
    val handedness: String? = null,
    val fitSource: String? = null,
    val assetValidationStatus: String? = null,
    val nodeRecreateCount: Int = 0,
    val rendererErrorStage: String? = null,
    val rendererErrorMessage: String? = null,
    val approxMemoryDeltaKb: Long? = null,
    val warnings: List<String> = emptyList(),
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("timestampMs", timestampMs)
            .put("frameIndex", frameIndex)
            .put("rendererMode", rendererMode.name)
            .put("trackingState", trackingState)
            .put("updateAction", updateAction)
            .put("qualityScore", qualityScore.toDouble())
            .putNullable("rawTransform", rawTransform?.toJson())
            .putNullable("smoothedTransform", smoothedTransform?.toJson())
            .putNullable("renderStateUpdateHz", renderStateUpdateHz)
            .putNullable("detectorLatencyMs", detectorLatencyMs)
            .putNullable("frameSource", frameSource)
            .putNullable("rotationDegrees", rotationDegrees)
            .putNullable("mirrored", mirrored)
            .putNullable("handedness", handedness)
            .putNullable("fitSource", fitSource)
            .putNullable("assetValidationStatus", assetValidationStatus)
            .put("nodeRecreateCount", nodeRecreateCount)
            .putNullable("rendererErrorStage", rendererErrorStage)
            .putNullable("rendererErrorMessage", rendererErrorMessage)
            .putNullable("approxMemoryDeltaKb", approxMemoryDeltaKb)
            .put("warnings", JSONArray(warnings))

    fun toJsonLine(): String = toJson().toString()
}

class TryOnTelemetryJsonLinesExporter(
    private val enabled: Boolean,
    private val outputFile: File?,
) {
    fun append(frame: TryOnTelemetryFrame) {
        if (!enabled || outputFile == null) return
        outputFile.parentFile?.mkdirs()
        outputFile.appendText(frame.toJsonLine() + "\n", Charsets.UTF_8)
    }
}

private fun JSONObject.putNullable(
    name: String,
    value: Any?,
): JSONObject = put(name, value ?: JSONObject.NULL)
