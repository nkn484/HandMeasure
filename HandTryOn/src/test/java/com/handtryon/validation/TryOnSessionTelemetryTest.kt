package com.handtryon.validation

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test

class TryOnSessionTelemetryTest {
    @Test
    fun telemetry_serializesExpectedFields() {
        val telemetry =
            TryOnSessionTelemetry(
                sessionId = "abc123",
                rendererMode = TryOnTelemetryRendererMode.CameraRelative3D,
                frameSource = "CameraX",
                rotationDegrees = 90,
                mirrored = false,
                handedness = "Right",
                trackingFps = 13.4f,
                detectorLatencyMs = 28.6f,
                renderUpdateRate = 24.2f,
                qualityAction = "Update",
                fitSource = "Measured",
                assetValidationStatus = "Info",
            )

        val json = telemetry.toJson()

        assertThat(json.getString("sessionId")).isEqualTo("abc123")
        assertThat(json.getString("rendererMode")).isEqualTo("CameraRelative3D")
        assertThat(json.getInt("rotationDegrees")).isEqualTo(90)
    }

    @Test
    fun exporter_writesJsonFile() {
        val directory = createTempDir(prefix = "tryon-session-telemetry")
        val exporter = TryOnSessionTelemetryExporter(directory)
        val file =
            exporter.export(
                TryOnSessionTelemetry(
                    sessionId = "session-1",
                    rendererMode = TryOnTelemetryRendererMode.ARCoreCamera3D,
                    frameSource = "ARCoreCpuImage",
                    rotationDegrees = 0,
                    mirrored = false,
                    handedness = "Unknown",
                    trackingFps = 10f,
                    detectorLatencyMs = 35f,
                    renderUpdateRate = 15f,
                    qualityAction = "Hide",
                    fitSource = "VisualEstimate",
                    assetValidationStatus = "Warning",
                ),
            )

        val payload = JSONObject(file.readText(Charsets.UTF_8))
        assertThat(file.exists()).isTrue()
        assertThat(payload.getString("sessionId")).isEqualTo("session-1")
        assertThat(payload.getString("assetValidationStatus")).isEqualTo("Warning")
        file.delete()
        directory.deleteRecursively()
    }
}
