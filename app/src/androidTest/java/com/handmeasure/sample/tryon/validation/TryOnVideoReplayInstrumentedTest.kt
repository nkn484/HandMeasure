package com.handmeasure.sample.tryon.validation

import android.content.Context
import android.graphics.Bitmap
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
        val annotation = JSONObject(testAssets.open(ANNOTATION_ASSET).bufferedReader().use { it.readText() })
        val media = annotation.getJSONObject("media")
        val videoAsset = media.getString("file")
        val reportDir = File(targetContext.getExternalFilesDir(null), "tryon_replay").apply { mkdirs() }
        val reportFile = File(reportDir, "video-fixture-2026-04-29-android-report.json")
        val frameRows = JSONArray()
        val renderer = TryOnRenderState3DFactory()
        val handEngine = MediaPipeHandLandmarkEngine(targetContext)
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
                    ),
                )
                bitmap?.recycle()
            }
        } finally {
            retriever.release()
            handEngine.close()
        }

        val summary = summarize(frameRows)
        val report =
            JSONObject()
                .put("schemaVersion", 1)
                .put("source", "android_instrumented_mediapipe_replay")
                .put("media", media)
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
            return baseRow(expectedFrame, visibleFinger)
                .put("status", "no_render_state")
                .put("pass", !visibleFinger)
                .put("confidence", detection?.confidence ?: 0.0)
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
                .put("rotationDeg", renderState.fingerPose.rotationDegrees.toDouble()))
            .put("reason", if (pass) "" else if (!visibleFinger) "Unexpected render state for hidden ring finger" else "Metric exceeded threshold")
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

    private fun normalizeDegrees(value: Double): Double {
        val normalized = (value + 180.0) % 360.0 - 180.0
        return abs(normalized)
    }

    private fun normalizeAxisDegrees(value: Double): Double =
        minOf(normalizeDegrees(value), normalizeDegrees(value + 180.0))

    private companion object {
        const val ANNOTATION_ASSET = "video-fixture-2026-04-29.json"
        const val MICROSECONDS_PER_SECOND = 1_000_000.0
        const val CENTER_THRESHOLD_PX = 24.0
        const val WIDTH_THRESHOLD_PX = 14.0
        const val ROTATION_THRESHOLD_DEGREES = 18.0
    }
}
