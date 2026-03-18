package com.handmeasure.measurement

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.handmeasure.api.CaptureStep
import com.handmeasure.api.HandMeasureConfig
import com.handmeasure.coordinator.HandMeasureCoordinator
import com.handmeasure.vision.MediaPipeHandLandmarkEngine
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class MeasurementReplayRunnerInstrumentedTest {
    @Test
    fun replayRunner_producesJsonReport() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val inputDir =
            (InstrumentationRegistry.getArguments().getString("replayInputDir")?.let { File(it) }
                ?: File(context.cacheDir, "replay_runner_input"))
                .apply { mkdirs() }
        if (inputDir.listFiles().isNullOrEmpty()) {
            CaptureStep.entries.forEach { step ->
                val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(0xFF777777.toInt())
                FileOutputStream(File(inputDir, "${step.name.lowercase()}.jpg")).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                bitmap.recycle()
            }
        }
        val reportFile = File(context.cacheDir, "replay_runner_report.json")
        val runner =
            MeasurementReplayRunner { cfg ->
                HandMeasureCoordinator(
                    config = cfg,
                    handLandmarkEngine = MediaPipeHandLandmarkEngine(context),
                    debugExportDirProvider = { File(context.cacheDir, "handmeasure_debug") },
                )
            }

        val output = runner.runFromDirectory(HandMeasureConfig(), inputDir, reportFile)

        assertTrue(output.predictedDiameterMm > 0.0)
        assertTrue(reportFile.exists())
    }
}
