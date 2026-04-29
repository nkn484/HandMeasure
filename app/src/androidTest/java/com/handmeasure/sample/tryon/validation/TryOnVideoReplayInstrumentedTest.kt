package com.handmeasure.sample.tryon.validation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.handmeasure.vision.HandDetection
import com.handmeasure.vision.MediaPipeHandLandmarkEngine
import com.handtryon.domain.HandPoseSnapshot
import com.handtryon.domain.LandmarkPoint
import com.handtryon.domain.TryOnTrackingState
import com.handtryon.domain.TryOnUpdateAction
import com.handtryon.render3d.TryOnRenderState3DFactory
import com.handtryon.tracking.FrameSource
import com.handtryon.tracking.TrackedHandFrameMapper
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TryOnVideoReplayInstrumentedTest {
    @Test
    fun videoFixture_runsThroughMediaPipeAndRenderStatePipeline() {
        val targetContext = ApplicationProvider.getApplicationContext<Context>()
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        val reportDir = File(targetContext.getExternalFilesDir(null), "tryon_replay").apply { mkdirs() }
        val renderer = TryOnRenderState3DFactory()
        val handEngine = MediaPipeHandLandmarkEngine(targetContext)

        try {
            for (annotationAsset in ANNOTATION_ASSETS) {
                runFixture(
                    annotationAsset = annotationAsset,
                    testAssets = testAssets,
                    reportDir = reportDir,
                    handEngine = handEngine,
                    renderer = renderer,
                )
            }
        } finally {
            handEngine.close()
        }
    }

    private fun runFixture(
        annotationAsset: String,
        testAssets: android.content.res.AssetManager,
        reportDir: File,
        handEngine: MediaPipeHandLandmarkEngine,
        renderer: TryOnRenderState3DFactory,
    ) {
        val annotation = JSONObject(testAssets.open(annotationAsset).bufferedReader().use { it.readText() })
        val media = annotation.getJSONObject("media")
        val videoAsset = media.getString("file")
        val fixtureName = annotationAsset.removeSuffix(".json")
        val screenshotDir = File(reportDir, "$fixtureName-screenshots").apply { mkdirs() }
        val reportFile = File(reportDir, "$fixtureName-android-report.json")
        val frameRows = JSONArray()
        val retriever = MediaMetadataRetriever()

        try {
            testAssets.openFd(videoAsset).use { descriptor ->
                retriever.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
            }
            val frames = annotation.getJSONArray("frames")
            for (index in 0 until frames.length()) {
                val expectedFrame = frames.getJSONObject(index)
                val bitmap = retriever.frameForAnnotation(expectedFrame)
                frameRows.put(
                    runFrame(
                        expectedFrame = expectedFrame,
                        bitmap = bitmap,
                        handEngine = handEngine,
                        renderer = renderer,
                        screenshotDir = screenshotDir,
                    ),
                )
                bitmap?.recycle()
            }
        } finally {
            retriever.release()
        }

        val summary = summarize(frameRows)
        val report =
            JSONObject()
                .put("schemaVersion", 1)
                .put("source", "android_instrumented_mediapipe_replay")
                .put("annotationAsset", annotationAsset)
                .put("media", media)
                .put("screenshotDir", screenshotDir.absolutePath)
                .put("summary", summary)
                .put("frames", frameRows)
        reportFile.writeText(report.toString(2), Charsets.UTF_8)

        assertThat(summary.getInt("totalFrames")).isEqualTo(annotation.getJSONArray("frames").length())
        assertThat(summary.getInt("unreadableFrames")).isEqualTo(0)
        assertThat(reportFile.exists()).isTrue()
    }

    private fun runFrame(
        expectedFrame: JSONObject,
        bitmap: Bitmap?,
        handEngine: MediaPipeHandLandmarkEngine,
        renderer: TryOnRenderState3DFactory,
        screenshotDir: File,
    ): JSONObject {
        val visibleFinger = expectedFrame.getBoolean("visibleFinger")
        if (bitmap == null) {
            return baseRow(expectedFrame, visibleFinger)
                .put("status", "unreadable_frame")
                .put("pass", false)
                .put("reason", "MediaMetadataRetriever returned null frame")
        }

        val detection = handEngine.detect(bitmap)
        val renderState =
            detection
                ?.toTrackedFrame(bitmap)
                ?.let { trackedFrame ->
                    renderer.create(
                        trackedHandFrame = trackedFrame,
                        measurement = null,
                        glbSummary = null,
                        frameWidth = trackedFrame.frameWidth,
                        frameHeight = trackedFrame.frameHeight,
                        qualityScore = trackedFrame.confidence,
                        trackingState = TryOnTrackingState.Candidate,
                        updateAction = TryOnUpdateAction.Update,
                    )
                }

        if (renderState == null) {
            saveReplayOverlay(
                source = bitmap,
                expectedFrame = expectedFrame,
                predicted = null,
                output = File(screenshotDir, "frame_${expectedFrame.getInt("frameIndex").toString().padStart(6, '0')}.png"),
            )
            return baseRow(expectedFrame, visibleFinger)
                .put("status", "no_render_state")
                .put("pass", !visibleFinger)
                .put("confidence", detection?.confidence ?: 0.0)
                .put("debugLandmarks", detection?.debugRingFingerLandmarks() ?: JSONObject())
                .put("reason", if (visibleFinger) "No render state for visible ring finger" else "Correctly hidden")
        }

        val expected = expectedFrame.getJSONObject("ringFingerZone")
        val centerError =
            hypot(
                renderState.fingerPose.centerPx.x - expected.getDouble("centerX"),
                renderState.fingerPose.centerPx.y - expected.getDouble("centerY"),
            )
        val widthError = abs(renderState.fitState.targetWidthPx - expected.getDouble("widthPx"))
        val rotationError = normalizeAxisDegrees(renderState.fingerPose.rotationDegrees - expected.getDouble("rotationDeg"))
        val pass =
            if (!visibleFinger) {
                false
            } else {
                centerError <= CENTER_THRESHOLD_PX &&
                    widthError <= WIDTH_THRESHOLD_PX &&
                    rotationError <= ROTATION_THRESHOLD_DEGREES
            }

        saveReplayOverlay(
            source = bitmap,
            expectedFrame = expectedFrame,
            predicted =
                PredictedZone(
                    centerX = renderState.fingerPose.centerPx.x,
                    centerY = renderState.fingerPose.centerPx.y,
                    widthPx = renderState.fitState.targetWidthPx,
                ),
            output = File(screenshotDir, "frame_${expectedFrame.getInt("frameIndex").toString().padStart(6, '0')}.png"),
        )

        return baseRow(expectedFrame, visibleFinger)
            .put("status", "measured")
            .put("pass", pass)
            .put("centerErrorPx", centerError)
            .put("widthErrorPx", widthError)
            .put("rotationErrorDeg", rotationError)
            .put("confidence", renderState.fingerPose.confidence.toDouble())
            .put("predictedRingFingerZone", JSONObject()
                .put("centerX", renderState.fingerPose.centerPx.x.toDouble())
                .put("centerY", renderState.fingerPose.centerPx.y.toDouble())
                .put("widthPx", renderState.fitState.targetWidthPx.toDouble())
                .put("rotationDeg", renderState.fingerPose.rotationDegrees.toDouble())
                .put("fingerWidthPx", renderState.fingerPose.fingerWidthPx.toDouble())
                .put("normalHintX", renderState.fingerPose.normalHintPx.x.toDouble())
                .put("normalHintY", renderState.fingerPose.normalHintPx.y.toDouble()))
            .put("debugLandmarks", detection.debugRingFingerLandmarks())
            .put("reason", if (pass) "" else if (!visibleFinger) "Unexpected render state for hidden ring finger" else "Metric exceeded threshold")
    }

    private fun HandDetection.debugRingFingerLandmarks(): JSONObject {
        if (imageLandmarks.size <= RING_DIP_INDEX) return JSONObject()
        val mcp = imageLandmarks[RING_MCP_INDEX]
        val pip = imageLandmarks[RING_PIP_INDEX]
        val dip = imageLandmarks[RING_DIP_INDEX]
        val mcpPipLength = hypot((pip.x - mcp.x).toDouble(), (pip.y - mcp.y).toDouble())
        val pipDipLength = hypot((dip.x - pip.x).toDouble(), (dip.y - pip.y).toDouble())
        val mcpDipLength = hypot((dip.x - mcp.x).toDouble(), (dip.y - mcp.y).toDouble())
        return JSONObject()
            .put("handedness", handedness)
            .put("ringMcp", mcp.toJson())
            .put("ringPip", pip.toJson())
            .put("ringDip", dip.toJson())
            .put("mcpPipLengthPx", mcpPipLength)
            .put("pipDipLengthPx", pipDipLength)
            .put("mcpDipLengthPx", mcpDipLength)
            .put("extensionRatio", mcpDipLength / (mcpPipLength + pipDipLength).coerceAtLeast(1e-4))
    }

    private fun com.handmeasure.vision.Landmark2D.toJson(): JSONObject =
        JSONObject()
            .put("x", x.toDouble())
            .put("y", y.toDouble())
            .put("z", z.toDouble())

    private fun saveReplayOverlay(
        source: Bitmap,
        expectedFrame: JSONObject,
        predicted: PredictedZone?,
        output: File,
    ) {
        val bitmap = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bitmap)
        val expected = expectedFrame.getJSONObject("ringFingerZone")
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 5f
                textSize = 28f
            }
        val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 28f
                style = Paint.Style.FILL
            }
        if (expectedFrame.getBoolean("visibleFinger")) {
            paint.color = Color.GREEN
            val expectedRadius = expected.getDouble("widthPx").toFloat() * 0.5f
            val expectedX = expected.getDouble("centerX").toFloat()
            val expectedY = expected.getDouble("centerY").toFloat()
            canvas.drawCircle(expectedX, expectedY, expectedRadius, paint)
            canvas.drawText("expected", expectedX + expectedRadius + 8f, expectedY, textPaint)
        } else {
            paint.color = Color.YELLOW
            canvas.drawRect(16f, 16f, 260f, 74f, paint)
            canvas.drawText("expected hide", 26f, 54f, textPaint)
        }
        if (predicted != null) {
            paint.color = Color.RED
            val predictedRadius = predicted.widthPx * 0.5f
            canvas.drawCircle(predicted.centerX, predicted.centerY, predictedRadius, paint)
            canvas.drawText("predicted", predicted.centerX + predictedRadius + 8f, predicted.centerY, textPaint)
        }
        output.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        bitmap.recycle()
    }

    private fun baseRow(
        expectedFrame: JSONObject,
        visibleFinger: Boolean,
    ): JSONObject =
        JSONObject()
            .put("file", "video_fixture.mp4")
            .put("frameIndex", expectedFrame.getInt("frameIndex"))
            .put("timeSec", expectedFrame.getDouble("timeSec"))
            .put("visibleFinger", visibleFinger)
            .put("declaredAnnotationQuality", expectedFrame.optString("annotationQuality"))

    private fun MediaMetadataRetriever.frameForAnnotation(expectedFrame: JSONObject): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getFrameAtIndex(expectedFrame.getInt("frameIndex"))
        } else {
            getFrameAtTime(
                (expectedFrame.getDouble("timeSec") * MICROSECONDS_PER_SECOND).toLong(),
                MediaMetadataRetriever.OPTION_CLOSEST,
            )
        }

    private fun HandDetection.toTrackedFrame(bitmap: Bitmap) =
        TrackedHandFrameMapper.fromHandPoseSnapshot(
            pose =
                HandPoseSnapshot(
                    frameWidth = bitmap.width,
                    frameHeight = bitmap.height,
                    landmarks = imageLandmarks.map { point -> LandmarkPoint(point.x, point.y, point.z) },
                    confidence = confidence.coerceIn(0f, 1f),
                    timestampMs = System.currentTimeMillis(),
                ),
            source = FrameSource.Replay,
            handedness = when (handedness.lowercase()) {
                "left" -> com.handtryon.tracking.Handedness.Left
                "right" -> com.handtryon.tracking.Handedness.Right
                else -> com.handtryon.tracking.Handedness.Unknown
            },
        )

    private fun summarize(rows: JSONArray): JSONObject {
        var unreadable = 0
        var measured = 0
        var passed = 0
        var falsePositiveHidden = 0
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            if (row.getString("status") == "unreadable_frame") unreadable += 1
            if (row.getString("status") == "measured") measured += 1
            if (row.getBoolean("pass")) passed += 1
            if (!row.getBoolean("visibleFinger") && row.getString("status") == "measured") falsePositiveHidden += 1
        }
        return JSONObject()
            .put("totalFrames", rows.length())
            .put("unreadableFrames", unreadable)
            .put("measuredFrames", measured)
            .put("passedFrames", passed)
            .put("falsePositiveHiddenFrames", falsePositiveHidden)
    }

    private data class PredictedZone(
        val centerX: Float,
        val centerY: Float,
        val widthPx: Float,
    )

    private fun normalizeDegrees(value: Double): Double {
        val normalized = (value + 180.0) % 360.0 - 180.0
        return abs(normalized)
    }

    private fun normalizeAxisDegrees(value: Double): Double =
        minOf(normalizeDegrees(value), normalizeDegrees(value + 180.0))

    private companion object {
        val ANNOTATION_ASSETS =
            listOf(
                "video-fixture-2026-04-29.json",
                "video-fixture2-2026-04-29.json",
                "video-fixture3-2026-04-29.json",
            )
        const val MICROSECONDS_PER_SECOND = 1_000_000.0
        const val CENTER_THRESHOLD_PX = 24.0
        const val WIDTH_THRESHOLD_PX = 14.0
        const val ROTATION_THRESHOLD_DEGREES = 18.0
        const val RING_MCP_INDEX = 13
        const val RING_PIP_INDEX = 14
        const val RING_DIP_INDEX = 15
    }
}
