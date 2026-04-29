package com.handmeasure.sample.tryon.validation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.handmeasure.vision.HandDetection
import com.handmeasure.vision.MediaPipeHandLandmarkEngine
import com.handtryon.coreengine.model.TryOnPlacement
import com.handtryon.coreengine.validation.TryOnImageAugmentation
import com.handtryon.coreengine.validation.TryOnTemporalQualityModel
import com.handtryon.coreengine.validation.TryOnTemporalSample
import com.handtryon.coreengine.validation.VisualDiffPolicy
import com.handtryon.coreengine.validation.VisualDiffThresholds
import com.handtryon.domain.HandPoseSnapshot
import com.handtryon.domain.LandmarkPoint
import com.handtryon.domain.TryOnTrackingState
import com.handtryon.domain.TryOnUpdateAction
import com.handtryon.render3d.TryOnRenderState3DFactory
import com.handtryon.tracking.FrameSource
import com.handtryon.tracking.TrackedHandFrameMapper
import com.handtryon.validation.TryOnTelemetryFrame
import com.handtryon.validation.TryOnTelemetryJsonLinesExporter
import com.handtryon.validation.TryOnTelemetryRendererMode
import com.handtryon.validation.TryOnTelemetryTransform
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class TryOnVideoReplayInstrumentedTest {
    @Test
    fun videoFixture_runsThroughMediaPipeAndRenderStatePipeline() {
        val targetContext = ApplicationProvider.getApplicationContext<Context>()
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        val outputRoot =
            InstrumentationRegistry.getArguments()
                .getString("additionalTestOutputDir")
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?: targetContext.getExternalFilesDir(null)
        val reportDir = File(outputRoot, "tryon_replay").apply { mkdirs() }
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
        val telemetryFile = File(reportDir, "$fixtureName-telemetry.jsonl").apply { delete() }
        val telemetryExporter = TryOnTelemetryJsonLinesExporter(enabled = true, outputFile = telemetryFile)
        val fixtureMetadata = fixtureMetadata(annotationAsset, media, testAssets)
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
                for (augmentation in AUGMENTATIONS) {
                    val augmentedBitmap = bitmap?.augment(augmentation)
                    val augmentedExpected =
                        if (bitmap == null) {
                            expectedFrame
                        } else {
                            expectedFrame.augmentGeometry(
                                augmentation = augmentation,
                                frameWidth = bitmap.width,
                                frameHeight = bitmap.height,
                            )
                        }
                    val row =
                        runFrame(
                            expectedFrame = augmentedExpected,
                            bitmap = augmentedBitmap,
                            handEngine = handEngine,
                            renderer = renderer,
                            screenshotDir = screenshotDir,
                            testAssets = testAssets,
                            fixtureName = fixtureName,
                        )
                    frameRows.put(row)
                    telemetryExporter.append(row.toTelemetryFrame())
                    augmentedBitmap?.recycle()
                }
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
                .put("fixtureMetadata", fixtureMetadata)
                .put("screenshotDir", screenshotDir.absolutePath)
                .put("telemetryFile", telemetryFile.absolutePath)
                .put("summary", summary)
                .put("augmentationSummary", summarizeAugmentations(frameRows))
                .put("temporalQuality", temporalQuality(frameRows))
                .put("visualDiff", summarizeVisualDiff(frameRows))
                .put("baselineRegression", baselineRegression(annotationAsset, summary))
                .put("frames", frameRows)
        reportFile.writeText(report.toString(2), Charsets.UTF_8)

        assertThat(summary.getInt("totalFrames")).isEqualTo(annotation.getJSONArray("frames").length() * AUGMENTATIONS.size)
        assertThat(summary.getInt("unreadableFrames")).isEqualTo(0)
        assertThat(reportFile.exists()).isTrue()
    }

    private fun runFrame(
        expectedFrame: JSONObject,
        bitmap: Bitmap?,
        handEngine: MediaPipeHandLandmarkEngine,
        renderer: TryOnRenderState3DFactory,
        screenshotDir: File,
        testAssets: android.content.res.AssetManager,
        fixtureName: String,
    ): JSONObject {
        val visibleFinger = expectedFrame.getBoolean("visibleFinger")
        if (bitmap == null) {
            return baseRow(expectedFrame, visibleFinger)
                .put("status", "unreadable_frame")
                .put("pass", false)
                .put("visualDiff", JSONObject().put("status", "not_run").put("reason", "unreadable_frame"))
                .put("reason", "MediaMetadataRetriever returned null frame")
        }

        val detectorStartNs = System.nanoTime()
        val detection = handEngine.detect(bitmap)
        val detectorLatencyMs = (System.nanoTime() - detectorStartNs) / 1_000_000.0
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
            val overlayFile = File(screenshotDir, screenshotName(expectedFrame))
            saveReplayOverlay(
                source = bitmap,
                expectedFrame = expectedFrame,
                predicted = null,
                output = overlayFile,
            )
            val visualDiff = compareReplayGolden(testAssets, fixtureName, overlayFile)
            return baseRow(expectedFrame, visibleFinger)
                .put("status", "no_render_state")
                .put("pass", !visibleFinger)
                .put("confidence", detection?.confidence ?: 0.0)
                .put("detectorLatencyMs", detectorLatencyMs)
                .put("visualDiff", visualDiff)
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
        val centerErrorRatio = centerError / renderState.fitState.targetWidthPx.coerceAtLeast(1f)
        val widthErrorRatio = widthError / expected.getDouble("widthPx").coerceAtLeast(1.0)
        val metricPass =
            if (!visibleFinger) {
                false
            } else {
                centerError <= CENTER_THRESHOLD_PX &&
                    widthError <= WIDTH_THRESHOLD_PX &&
                    rotationError <= ROTATION_THRESHOLD_DEGREES &&
                    renderState.visualQa?.passesBasicGate != false
            }

        val overlayFile = File(screenshotDir, screenshotName(expectedFrame))
        saveReplayOverlay(
            source = bitmap,
            expectedFrame = expectedFrame,
            predicted =
                PredictedZone(
                    centerX = renderState.fingerPose.centerPx.x,
                    centerY = renderState.fingerPose.centerPx.y,
                    widthPx = renderState.fitState.targetWidthPx,
                ),
            output = overlayFile,
        )
        val visualDiff = compareReplayGolden(testAssets, fixtureName, overlayFile)
        val pass = metricPass && visualDiff.optBoolean("pass", true)

        return baseRow(expectedFrame, visibleFinger)
            .put("status", "measured")
            .put("pass", pass)
            .put("centerErrorPx", centerError)
            .put("centerErrorRatio", centerErrorRatio)
            .put("widthErrorPx", widthError)
            .put("widthErrorRatio", widthErrorRatio)
            .put("rotationErrorDeg", rotationError)
            .put("confidence", renderState.fingerPose.confidence.toDouble())
            .put("detectorLatencyMs", detectorLatencyMs)
            .put(
                "predictedRingFingerZone",
                JSONObject()
                    .put("centerX", renderState.fingerPose.centerPx.x.toDouble())
                    .put("centerY", renderState.fingerPose.centerPx.y.toDouble())
                    .put("widthPx", renderState.fitState.targetWidthPx.toDouble())
                    .put("rotationDeg", renderState.fingerPose.rotationDegrees.toDouble())
                    .put("fingerWidthPx", renderState.fingerPose.fingerWidthPx.toDouble())
                    .put("normalHintX", renderState.fingerPose.normalHintPx.x.toDouble())
                    .put("normalHintY", renderState.fingerPose.normalHintPx.y.toDouble()),
            )
            .put("poseDiagnostics", renderState.fingerPose.diagnostics?.toJson() ?: JSONObject())
            .put("fitDiagnostics", renderState.fitState.diagnostics?.toJson() ?: JSONObject())
            .put("visualQa", renderState.visualQa?.toJson() ?: JSONObject())
            .put("visualDiff", visualDiff)
            .put("renderPasses", JSONArray(renderState.renderPasses.map { it.name }))
            .put("debugLandmarks", detection.debugRingFingerLandmarks())
            .put(
                "reason",
                if (pass) {
                    ""
                } else if (!visibleFinger) {
                    "Unexpected render state for hidden ring finger"
                } else {
                    "Metric exceeded threshold"
                },
            )
    }

    private fun com.handtryon.coreengine.model.RingFingerPoseDiagnostics.toJson(): JSONObject =
        JSONObject()
            .put("extensionRatio", extensionRatio.toDouble())
            .put("bendCosine", bendCosine.toDouble())
            .put("distalToProximalRatio", distalToProximalRatio.toDouble())
            .put("forwardExtensionCosine", forwardExtensionCosine.toDouble())
            .put("centerOnMcpToPip", centerOnMcpToPip.toDouble())
            .put("lateralOffsetPx", lateralOffsetPx.toDouble())
            .put("axisLengthPx", axisLengthPx.toDouble())
            .put("rawRotationDegrees", rawRotationDegrees.toDouble())
            .put("rotationCorrectionDegrees", rotationCorrectionDegrees.toDouble())
            .put("rotationCorrectionBucket", rotationCorrectionBucket)
            .put("finalRotationDegrees", finalRotationDegrees.toDouble())
            .put("confidence", confidence.toDouble())
            .put("centerPolicy", centerPolicy)
            .put("rejectReason", rejectReason?.name ?: "")

    private fun com.handtryon.coreengine.model.RingFitDiagnostics.toJson(): JSONObject =
        JSONObject()
            .put("visualRingToFingerWidthRatio", visualRingToFingerWidthRatio.toDouble())
            .put("measuredWidthRatio", measuredWidthRatio?.toDouble() ?: JSONObject.NULL)
            .put("unclampedTargetWidthPx", unclampedTargetWidthPx.toDouble())
            .put("unclampedDepthMeters", unclampedDepthMeters.toDouble())
            .put("unclampedModelScale", unclampedModelScale.toDouble())
            .put("source", source.name)

    private fun com.handtryon.coreengine.model.TryOnVisualQaSnapshot.toJson(): JSONObject =
        JSONObject()
            .put("attachmentRatio", attachmentRatio.toDouble())
            .put("occluderRadiusToRingWidthRatio", occluderRadiusToRingWidthRatio.toDouble())
            .put("occluderDepthMeters", occluderDepthMeters.toDouble())
            .put("ringDepthMeters", ringDepthMeters.toDouble())
            .put("renderScale", renderScale.toDouble())
            .put("passesBasicGate", passesBasicGate)
            .put("warnings", JSONArray(warnings))

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

    private fun compareReplayGolden(
        testAssets: android.content.res.AssetManager,
        fixtureName: String,
        actualFile: File,
    ): JSONObject {
        val goldenAsset = "goldens/$fixtureName/${actualFile.name}"
        val golden =
            try {
                testAssets.open(goldenAsset).use { stream -> BitmapFactory.decodeStream(stream) }
            } catch (_: IOException) {
                return JSONObject()
                    .put("goldenAsset", goldenAsset)
                    .put("actualPng", actualFile.absolutePath)
                    .put("goldenMissing", true)
                    .put("pass", true)
                    .put("status", "golden_missing")
            }
        val actual =
            BitmapFactory.decodeFile(actualFile.absolutePath)
                ?: run {
                    golden.recycle()
                    return JSONObject()
                        .put("goldenAsset", goldenAsset)
                        .put("actualPng", actualFile.absolutePath)
                        .put("pass", false)
                        .put("status", "actual_unreadable")
                }
        if (golden.width != actual.width || golden.height != actual.height) {
            val diffFile = File(actualFile.parentFile, actualFile.nameWithoutExtension + "-diff.png")
            actual.recycle()
            golden.recycle()
            return JSONObject()
                .put("goldenAsset", goldenAsset)
                .put("actualPng", actualFile.absolutePath)
                .put("diffPng", diffFile.absolutePath)
                .put("pass", false)
                .put("status", "dimension_mismatch")
        }

        val actualPixels = actual.toArgbArray()
        val goldenPixels = golden.toArgbArray()
        val result =
            VisualDiffPolicy(
                VisualDiffThresholds(
                    maxMeanAbsoluteError = 4.0,
                    maxRmsError = 9.0,
                    maxLumaMeanAbsoluteError = 5.0,
                ),
            ).compare(
                actualArgb = actualPixels,
                expectedArgb = goldenPixels,
                width = actual.width,
                height = actual.height,
            )
        val diffFile = File(actualFile.parentFile, actualFile.nameWithoutExtension + "-diff.png")
        if (!result.pass) {
            writeDiffBitmap(actualPixels, goldenPixels, actual.width, actual.height, diffFile)
        }
        actual.recycle()
        golden.recycle()
        return result.toJson()
            .put("goldenAsset", goldenAsset)
            .put("actualPng", actualFile.absolutePath)
            .put("diffPng", if (result.pass) JSONObject.NULL else diffFile.absolutePath)
            .put("goldenMissing", false)
            .put("status", if (result.pass) "passed" else "failed")
    }

    private fun Bitmap.toArgbArray(): IntArray =
        IntArray(width * height).also { pixels ->
            getPixels(pixels, 0, width, 0, 0, width, height)
        }

    private fun com.handtryon.coreengine.validation.VisualDiffResult.toJson(): JSONObject =
        JSONObject()
            .put("width", width)
            .put("height", height)
            .put("comparedPixels", comparedPixels)
            .put("meanAbsoluteError", meanAbsoluteError)
            .put("rmsError", rmsError)
            .put("lumaMeanAbsoluteError", lumaMeanAbsoluteError)
            .put("pass", pass)
            .put("warnings", JSONArray(warnings))

    private fun writeDiffBitmap(
        actualPixels: IntArray,
        goldenPixels: IntArray,
        width: Int,
        height: Int,
        output: File,
    ) {
        val diffPixels =
            IntArray(width * height) { index ->
                val actual = actualPixels[index]
                val golden = goldenPixels[index]
                val dr = kotlin.math.abs(((actual shr 16) and 0xff) - ((golden shr 16) and 0xff))
                val dg = kotlin.math.abs(((actual shr 8) and 0xff) - ((golden shr 8) and 0xff))
                val db = kotlin.math.abs((actual and 0xff) - (golden and 0xff))
                val intensity = maxOf(dr, dg, db).coerceIn(0, 255)
                Color.argb(255, intensity, 0, 0)
            }
        val diff = Bitmap.createBitmap(diffPixels, width, height, Bitmap.Config.ARGB_8888)
        output.outputStream().use { stream -> diff.compress(Bitmap.CompressFormat.PNG, 100, stream) }
        diff.recycle()
    }

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

    private fun screenshotName(expectedFrame: JSONObject): String =
        "frame_${expectedFrame.getInt(
            "frameIndex",
        ).toString().padStart(6, '0')}_${expectedFrame.optString("augmentationId", "identity")}.png"

    private fun Bitmap.augment(augmentation: TryOnImageAugmentation): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val matrix =
            Matrix().apply {
                setRotate(augmentation.rotationDegrees, width * 0.5f, height * 0.5f)
            }
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = augmentation.colorFilter()
            }
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(this, matrix, paint)
        return output
    }

    private fun TryOnImageAugmentation.colorFilter(): ColorMatrixColorFilter? {
        if (brightnessDelta == 0f && contrastScale == 1f) return null
        val brightness = brightnessDelta * 255f
        val matrix =
            ColorMatrix(
                floatArrayOf(
                    contrastScale, 0f, 0f, 0f, brightness,
                    0f, contrastScale, 0f, 0f, brightness,
                    0f, 0f, contrastScale, 0f, brightness,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
        return ColorMatrixColorFilter(matrix)
    }

    private fun JSONObject.augmentGeometry(
        augmentation: TryOnImageAugmentation,
        frameWidth: Int,
        frameHeight: Int,
    ): JSONObject {
        val copy = JSONObject(toString())
        val zone = copy.getJSONObject("ringFingerZone")
        val rotated =
            rotatePoint(
                x = zone.getDouble("centerX"),
                y = zone.getDouble("centerY"),
                degrees = augmentation.rotationDegrees.toDouble(),
                frameWidth = frameWidth.toDouble(),
                frameHeight = frameHeight.toDouble(),
            )
        zone
            .put("centerX", rotated.first)
            .put("centerY", rotated.second)
            .put("rotationDeg", zone.getDouble("rotationDeg") + augmentation.rotationDegrees)
        copy
            .put("augmentationId", augmentation.id)
            .put(
                "augmentation",
                JSONObject()
                    .put("rotationDegrees", augmentation.rotationDegrees.toDouble())
                    .put("brightnessDelta", augmentation.brightnessDelta.toDouble())
                    .put("contrastScale", augmentation.contrastScale.toDouble()),
            )
        return copy
    }

    private fun rotatePoint(
        x: Double,
        y: Double,
        degrees: Double,
        frameWidth: Double,
        frameHeight: Double,
    ): Pair<Double, Double> {
        val radians = Math.toRadians(degrees)
        val cx = frameWidth * 0.5
        val cy = frameHeight * 0.5
        val dx = x - cx
        val dy = y - cy
        return Pair(
            cx + dx * cos(radians) - dy * sin(radians),
            cy + dx * sin(radians) + dy * cos(radians),
        )
    }

    private fun baseRow(
        expectedFrame: JSONObject,
        visibleFinger: Boolean,
    ): JSONObject =
        JSONObject()
            .put("file", "video_fixture.mp4")
            .put("frameIndex", expectedFrame.getInt("frameIndex"))
            .put("timeSec", expectedFrame.getDouble("timeSec"))
            .put("augmentationId", expectedFrame.optString("augmentationId", "identity"))
            .put("visibleFinger", visibleFinger)
            .put("declaredAnnotationQuality", expectedFrame.optString("annotationQuality"))
            .put("expectedRingFingerZone", JSONObject(expectedFrame.getJSONObject("ringFingerZone").toString()))

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
            handedness =
                when (handedness.lowercase()) {
                    "left" -> com.handtryon.tracking.Handedness.Left
                    "right" -> com.handtryon.tracking.Handedness.Right
                    else -> com.handtryon.tracking.Handedness.Unknown
                },
        )

    private fun fixtureMetadata(
        annotationAsset: String,
        media: JSONObject,
        testAssets: android.content.res.AssetManager,
    ): JSONObject {
        val manifest =
            try {
                JSONObject(testAssets.open("fixture-manifest.json").bufferedReader().use { it.readText() })
            } catch (_: IOException) {
                return JSONObject()
            }
        val fixtures = manifest.optJSONArray("fixtures") ?: return JSONObject()
        val mediaFile = media.optString("file")
        for (index in 0 until fixtures.length()) {
            val fixture = fixtures.getJSONObject(index)
            if (fixture.optString("annotationFile") == annotationAsset || fixture.optString("mediaFile") == mediaFile) {
                return fixture
            }
        }
        return JSONObject()
    }

    private fun JSONObject.toTelemetryFrame(): TryOnTelemetryFrame {
        val predicted = optJSONObject("predictedRingFingerZone")
        val poseDiagnostics = optJSONObject("poseDiagnostics")
        val warnings = mutableListOf<String>()
        optJSONObject("visualQa")?.optJSONArray("warnings")?.let { array ->
            for (index in 0 until array.length()) warnings += array.getString(index)
        }
        optJSONObject("visualDiff")?.let { diff ->
            if (diff.optBoolean("goldenMissing", false)) warnings += "visual_golden_missing"
            diff.optJSONArray("warnings")?.let { array ->
                for (index in 0 until array.length()) warnings += "visual_${array.getString(index)}"
            }
        }
        val measured = optString("status") == "measured"
        return TryOnTelemetryFrame(
            timestampMs = (optDouble("timeSec", 0.0) * 1000.0).toLong(),
            frameIndex = getInt("frameIndex"),
            rendererMode = TryOnTelemetryRendererMode.CameraRelative3D,
            trackingState = "Candidate",
            updateAction = if (measured) "Update" else "Hide",
            qualityScore = optDouble("confidence", 0.0).toFloat(),
            rawTransform =
                TryOnTelemetryTransform(
                    centerX = predicted?.optDouble("centerX")?.toFloat(),
                    centerY = predicted?.optDouble("centerY")?.toFloat(),
                    scale = predicted?.optDouble("widthPx")?.toFloat(),
                    rotationDegrees = poseDiagnostics?.optDouble("rawRotationDegrees")?.toFloat(),
                ),
            smoothedTransform =
                TryOnTelemetryTransform(
                    centerX = predicted?.optDouble("centerX")?.toFloat(),
                    centerY = predicted?.optDouble("centerY")?.toFloat(),
                    scale = predicted?.optDouble("widthPx")?.toFloat(),
                    rotationDegrees = predicted?.optDouble("rotationDeg")?.toFloat(),
                ),
            renderStateUpdateHz = null,
            detectorLatencyMs = optDouble("detectorLatencyMs", Double.NaN).takeUnless { it.isNaN() }?.toFloat(),
            nodeRecreateCount = 0,
            rendererErrorStage = if (measured) null else optString("status"),
            rendererErrorMessage = optString("reason").takeIf { it.isNotBlank() },
            approxMemoryDeltaKb = null,
            warnings = warnings.distinct(),
        )
    }

    private fun summarize(rows: JSONArray): JSONObject {
        var unreadable = 0
        var measured = 0
        var passed = 0
        var falsePositiveHidden = 0
        var maxCenterError = 0.0
        var maxCenterErrorRatio = 0.0
        var maxWidthError = 0.0
        var maxRotationError = 0.0
        var measuredCenterErrorSum = 0.0
        var measuredAttachmentRatioSum = 0.0
        var visualQaWarnings = 0
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            if (row.getString("status") == "unreadable_frame") unreadable += 1
            if (row.getString("status") == "measured") measured += 1
            if (row.getBoolean("pass")) passed += 1
            if (!row.getBoolean("visibleFinger") && row.getString("status") == "measured") falsePositiveHidden += 1
            if (row.getString("status") == "measured") {
                val centerError = row.getDouble("centerErrorPx")
                val centerErrorRatio = row.getDouble("centerErrorRatio")
                val widthError = row.getDouble("widthErrorPx")
                val rotationError = row.getDouble("rotationErrorDeg")
                val visualQa = row.optJSONObject("visualQa")
                maxCenterError = maxOf(maxCenterError, centerError)
                maxCenterErrorRatio = maxOf(maxCenterErrorRatio, centerErrorRatio)
                maxWidthError = maxOf(maxWidthError, widthError)
                maxRotationError = maxOf(maxRotationError, rotationError)
                measuredCenterErrorSum += centerError
                measuredAttachmentRatioSum += visualQa?.optDouble("attachmentRatio", 0.0) ?: 0.0
                if (visualQa?.optBoolean("passesBasicGate", true) == false) visualQaWarnings += 1
            }
        }
        val safeMeasured = measured.coerceAtLeast(1)
        return JSONObject()
            .put("totalFrames", rows.length())
            .put("unreadableFrames", unreadable)
            .put("measuredFrames", measured)
            .put("passedFrames", passed)
            .put("falsePositiveHiddenFrames", falsePositiveHidden)
            .put("maxCenterErrorPx", maxCenterError)
            .put("maxCenterErrorRatio", maxCenterErrorRatio)
            .put("maxWidthErrorPx", maxWidthError)
            .put("maxRotationErrorDeg", maxRotationError)
            .put("avgCenterErrorPx", measuredCenterErrorSum / safeMeasured)
            .put("avgVisualQaAttachmentRatio", measuredAttachmentRatioSum / safeMeasured)
            .put("visualQaWarningFrames", visualQaWarnings)
    }

    private fun summarizeAugmentations(rows: JSONArray): JSONArray {
        val ids = mutableListOf<String>()
        for (index in 0 until rows.length()) {
            val id = rows.getJSONObject(index).optString("augmentationId", "identity")
            if (!ids.contains(id)) ids += id
        }
        val summaries = JSONArray()
        for (id in ids) {
            var total = 0
            var measured = 0
            var passed = 0
            var centerSum = 0.0
            var maxCenterRatio = 0.0
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                if (row.optString("augmentationId", "identity") != id) continue
                total += 1
                if (row.getBoolean("pass")) passed += 1
                if (row.getString("status") == "measured") {
                    measured += 1
                    centerSum += row.optDouble("centerErrorPx", 0.0)
                    maxCenterRatio = maxOf(maxCenterRatio, row.optDouble("centerErrorRatio", 0.0))
                }
            }
            summaries.put(
                JSONObject()
                    .put("augmentationId", id)
                    .put("totalFrames", total)
                    .put("measuredFrames", measured)
                    .put("passedFrames", passed)
                    .put("avgCenterErrorPx", centerSum / measured.coerceAtLeast(1))
                    .put("maxCenterErrorRatio", maxCenterRatio),
            )
        }
        return summaries
    }

    private fun summarizeVisualDiff(rows: JSONArray): JSONObject {
        var compared = 0
        var passed = 0
        var failed = 0
        var missing = 0
        for (index in 0 until rows.length()) {
            val visualDiff = rows.getJSONObject(index).optJSONObject("visualDiff") ?: continue
            if (visualDiff.optBoolean("goldenMissing", false)) {
                missing += 1
                continue
            }
            if (visualDiff.optString("status") == "not_run") continue
            compared += 1
            if (visualDiff.optBoolean("pass", false)) {
                passed += 1
            } else {
                failed += 1
            }
        }
        return JSONObject()
            .put("comparedFrames", compared)
            .put("passedFrames", passed)
            .put("failedFrames", failed)
            .put("goldenMissingFrames", missing)
            .put("strict", compared > 0)
    }

    private fun temporalQuality(rows: JSONArray): JSONObject {
        val samples = mutableListOf<TryOnTemporalSample>()
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            if (row.optString("augmentationId", "identity") != TryOnImageAugmentation.Identity.id) continue
            val predicted = row.optJSONObject("predictedRingFingerZone")
            val placement =
                if (predicted != null && row.getString("status") == "measured") {
                    TryOnPlacement(
                        centerX = predicted.getDouble("centerX").toFloat(),
                        centerY = predicted.getDouble("centerY").toFloat(),
                        ringWidthPx = predicted.getDouble("widthPx").toFloat(),
                        rotationDegrees = predicted.getDouble("rotationDeg").toFloat(),
                    )
                } else {
                    null
                }
            samples +=
                TryOnTemporalSample(
                    timestampMs = (row.getDouble("timeSec") * 1000.0).toLong(),
                    placement = placement,
                    qualityScore = row.optDouble("confidence", 0.0).toFloat(),
                    trackingState = TryOnTrackingState.Candidate.toCoreTrackingState(),
                    updateAction =
                        if (placement == null) {
                            TryOnUpdateAction.Hide.toCoreUpdateAction()
                        } else {
                            TryOnUpdateAction.Update.toCoreUpdateAction()
                        },
                )
        }
        val metrics = TryOnTemporalQualityModel().evaluate(samples)
        return JSONObject()
            .put("source", "sampled_identity_replay_frames")
            .put("sampleCount", metrics.sampleCount)
            .put("measuredSampleCount", metrics.measuredSampleCount)
            .put("durationMs", metrics.durationMs)
            .put("effectiveUpdateHz", metrics.effectiveUpdateHz.toDouble())
            .put("avgCenterStepRatio", metrics.avgCenterStepRatio.toDouble())
            .put("maxCenterStepRatio", metrics.maxCenterStepRatio.toDouble())
            .put("avgScaleStepRatio", metrics.avgScaleStepRatio.toDouble())
            .put("maxScaleStepRatio", metrics.maxScaleStepRatio.toDouble())
            .put("avgRotationStepDeg", metrics.avgRotationStepDeg.toDouble())
            .put("maxRotationStepDeg", metrics.maxRotationStepDeg.toDouble())
            .put("hiddenFrames", metrics.hiddenFrames)
            .put("frozenFrames", metrics.frozenFrames)
            .put("lowQualityFrames", metrics.lowQualityFrames)
            .put("stableEnough", metrics.stableEnough)
            .put("warnings", JSONArray(metrics.warnings))
    }

    private fun baselineRegression(
        annotationAsset: String,
        summary: JSONObject,
    ): JSONObject {
        val baseline =
            BASELINE_REGRESSION_BY_ASSET[annotationAsset]
                ?: return JSONObject()
                    .put("baselineName", BASELINE_NAME)
                    .put("status", "missing_baseline")
        val passedDelta = summary.getInt("passedFrames") - baseline.passedFrames
        val passRateDelta = passRate(summary.getInt("passedFrames"), summary.getInt("totalFrames")) - baseline.passRate
        val maxCenterRatioDelta = summary.getDouble("maxCenterErrorRatio") - baseline.maxCenterErrorRatio
        val maxRotationDelta = summary.getDouble("maxRotationErrorDeg") - baseline.maxRotationErrorDeg
        val hiddenFpDelta = summary.getInt("falsePositiveHiddenFrames") - baseline.falsePositiveHiddenFrames
        val visualQaWarningDelta = summary.getInt("visualQaWarningFrames") - baseline.visualQaWarningFrames
        val status =
            when {
                passedDelta < 0 || hiddenFpDelta > 0 || visualQaWarningDelta > 0 -> "regressed"
                passedDelta > 0 || maxCenterRatioDelta < -0.02 -> "improved"
                else -> "unchanged"
            }
        return JSONObject()
            .put("baselineName", BASELINE_NAME)
            .put("status", status)
            .put("baseline", baseline.toJson())
            .put(
                "delta",
                JSONObject()
                    .put("passedFrames", passedDelta)
                    .put("passRate", passRateDelta)
                    .put("maxCenterErrorRatio", maxCenterRatioDelta)
                    .put("maxRotationErrorDeg", maxRotationDelta)
                    .put("falsePositiveHiddenFrames", hiddenFpDelta)
                    .put("visualQaWarningFrames", visualQaWarningDelta),
            )
    }

    private fun passRate(
        passedFrames: Int,
        totalFrames: Int,
    ): Double = passedFrames.toDouble() / totalFrames.coerceAtLeast(1).toDouble()

    private fun TryOnTrackingState.toCoreTrackingState(): com.handtryon.coreengine.model.TryOnTrackingState =
        when (this) {
            TryOnTrackingState.Searching -> com.handtryon.coreengine.model.TryOnTrackingState.Searching
            TryOnTrackingState.Candidate -> com.handtryon.coreengine.model.TryOnTrackingState.Candidate
            TryOnTrackingState.Locked -> com.handtryon.coreengine.model.TryOnTrackingState.Locked
            TryOnTrackingState.Recovering -> com.handtryon.coreengine.model.TryOnTrackingState.Recovering
        }

    private fun TryOnUpdateAction.toCoreUpdateAction(): com.handtryon.coreengine.model.TryOnUpdateAction =
        when (this) {
            TryOnUpdateAction.Update -> com.handtryon.coreengine.model.TryOnUpdateAction.Update
            TryOnUpdateAction.FreezeScaleRotation -> com.handtryon.coreengine.model.TryOnUpdateAction.FreezeScaleRotation
            TryOnUpdateAction.HoldLastPlacement -> com.handtryon.coreengine.model.TryOnUpdateAction.HoldLastPlacement
            TryOnUpdateAction.Recover -> com.handtryon.coreengine.model.TryOnUpdateAction.Recover
            TryOnUpdateAction.Hide -> com.handtryon.coreengine.model.TryOnUpdateAction.Hide
        }

    private data class PredictedZone(
        val centerX: Float,
        val centerY: Float,
        val widthPx: Float,
    )

    private data class ReplayRegressionBaseline(
        val totalFrames: Int,
        val passedFrames: Int,
        val falsePositiveHiddenFrames: Int,
        val visualQaWarningFrames: Int,
        val maxCenterErrorRatio: Double,
        val maxRotationErrorDeg: Double,
    ) {
        val passRate: Double = passedFrames.toDouble() / totalFrames.coerceAtLeast(1).toDouble()

        fun toJson(): JSONObject =
            JSONObject()
                .put("totalFrames", totalFrames)
                .put("passedFrames", passedFrames)
                .put("passRate", passRate)
                .put("falsePositiveHiddenFrames", falsePositiveHiddenFrames)
                .put("visualQaWarningFrames", visualQaWarningFrames)
                .put("maxCenterErrorRatio", maxCenterErrorRatio)
                .put("maxRotationErrorDeg", maxRotationErrorDeg)
    }

    private fun normalizeDegrees(value: Double): Double {
        val normalized = (value + 180.0) % 360.0 - 180.0
        return abs(normalized)
    }

    private fun normalizeAxisDegrees(value: Double): Double = minOf(normalizeDegrees(value), normalizeDegrees(value + 180.0))

    private companion object {
        val ANNOTATION_ASSETS =
            listOf(
                "video-fixture-2026-04-29.json",
                "video-fixture2-2026-04-29.json",
                "video-fixture3-2026-04-29.json",
            )
        val AUGMENTATIONS = TryOnImageAugmentation.DefaultRobustnessSet
        const val BASELINE_NAME = "android_augmented_rotation_tuned_v2"
        val BASELINE_REGRESSION_BY_ASSET =
            mapOf(
                "video-fixture-2026-04-29.json" to
                    ReplayRegressionBaseline(
                        totalFrames = 42,
                        passedFrames = 39,
                        falsePositiveHiddenFrames = 0,
                        visualQaWarningFrames = 0,
                        maxCenterErrorRatio = 1.0078,
                        maxRotationErrorDeg = 14.615,
                    ),
                "video-fixture2-2026-04-29.json" to
                    ReplayRegressionBaseline(
                        totalFrames = 42,
                        passedFrames = 38,
                        falsePositiveHiddenFrames = 0,
                        visualQaWarningFrames = 0,
                        maxCenterErrorRatio = 0.8704,
                        maxRotationErrorDeg = 9.828,
                    ),
                "video-fixture3-2026-04-29.json" to
                    ReplayRegressionBaseline(
                        totalFrames = 77,
                        passedFrames = 77,
                        falsePositiveHiddenFrames = 0,
                        visualQaWarningFrames = 0,
                        maxCenterErrorRatio = 0.1195,
                        maxRotationErrorDeg = 5.105,
                    ),
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
