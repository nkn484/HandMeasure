package com.handtryon.validation

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test
import java.io.File

class TryOnTelemetryTest {
    @Test
    fun frame_serializesStableSchema() {
        val frame =
            TryOnTelemetryFrame(
                timestampMs = 1_700L,
                frameIndex = 7,
                rendererMode = TryOnTelemetryRendererMode.CameraRelative3D,
                trackingState = "Candidate",
                updateAction = "Update",
                qualityScore = 0.82f,
                rawTransform = TryOnTelemetryTransform(centerX = 12f, centerY = 34f, rotationDegrees = 9f),
                smoothedTransform = TryOnTelemetryTransform(centerX = 13f, centerY = 35f, scale = 0.4f),
                renderStateUpdateHz = 29.5f,
                detectorLatencyMs = 18.25f,
                nodeRecreateCount = 2,
                rendererErrorStage = null,
                approxMemoryDeltaKb = 128L,
                warnings = listOf("sample_warning"),
            )

        val json = frame.toJson()

        assertThat(json.getString("rendererMode")).isEqualTo("CameraRelative3D")
        assertThat(json.getJSONObject("rawTransform").getDouble("centerX")).isEqualTo(12.0)
        assertThat(json.getJSONArray("warnings").getString(0)).isEqualTo("sample_warning")
        assertThat(json.has("rendererErrorStage")).isTrue()
    }

    @Test
    fun exporter_isSilentWhenDisabled() {
        val file = File.createTempFile("tryon-telemetry", ".jsonl").apply { delete() }
        val exporter = TryOnTelemetryJsonLinesExporter(enabled = false, outputFile = file)

        exporter.append(
            TryOnTelemetryFrame(
                timestampMs = 1L,
                frameIndex = 1,
                rendererMode = TryOnTelemetryRendererMode.Legacy2DOverlay,
                trackingState = "Searching",
                updateAction = "Hide",
                qualityScore = 0f,
            ),
        )

        assertThat(file.exists()).isFalse()
    }

    @Test
    fun exporter_writesJsonLinesWhenEnabled() {
        val file = File.createTempFile("tryon-telemetry", ".jsonl").apply { delete() }
        val exporter = TryOnTelemetryJsonLinesExporter(enabled = true, outputFile = file)

        exporter.append(
            TryOnTelemetryFrame(
                timestampMs = 2L,
                frameIndex = 3,
                rendererMode = TryOnTelemetryRendererMode.ARCoreCamera3D,
                trackingState = "Locked",
                updateAction = "Update",
                qualityScore = 0.9f,
            ),
        )

        val line = file.readLines().single()
        assertThat(JSONObject(line).getString("rendererMode")).isEqualTo("ARCoreCamera3D")
    }
}
