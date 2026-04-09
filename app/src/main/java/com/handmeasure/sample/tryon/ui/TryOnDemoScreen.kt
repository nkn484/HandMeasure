package com.handmeasure.sample.tryon.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.handmeasure.api.HandMeasureContract
import com.handmeasure.camera.CameraController
import com.handmeasure.sample.measure.ui.defaultDemoMeasureConfig
import com.handmeasure.sample.tryon.model.TryOnDemoHandoff
import com.handmeasure.sample.tryon.model.resolveDemoHandoff
import com.handmeasure.sample.tryon.model.sampleTryOnDemoHandoff
import com.handmeasure.vision.HandDetection
import com.handmeasure.vision.MediaPipeHandLandmarkEngine
import com.handtryon.core.HandPoseProvider
import com.handtryon.core.OptionalMeasurementProvider
import com.handtryon.core.TryOnSessionResolver
import com.handtryon.data.RingAssetLoader
import com.handtryon.domain.HandPoseSnapshot
import com.handtryon.domain.LandmarkPoint
import com.handtryon.domain.MeasurementSnapshot
import com.handtryon.domain.RingAssetSource
import com.handtryon.domain.RingPlacement
import com.handtryon.domain.TryOnMode
import com.handtryon.domain.TryOnSession
import com.handtryon.realtime.TryOnRealtimeAnalyzer
import com.handtryon.render.StableRingOverlayRenderer
import com.handtryon.ui.TryOnOverlay
import com.handtryon.validation.RuntimeMetrics
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

@Composable
fun TryOnDemoScreen(
    onBack: (() -> Unit)? = null,
    autoLaunchMeasureOnStart: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val cameraController = remember { CameraController(context) }
    val handLandmarkEngine = remember { MediaPipeHandLandmarkEngine(context) }
    val sessionResolver = remember { TryOnSessionResolver() }
    val previewRenderer = remember { StableRingOverlayRenderer() }
    val exportRenderer = remember { StableRingOverlayRenderer() }
    val handPoseProvider = remember { MutableHandPoseProvider() }
    val measurementProvider = remember { MutableMeasurementProvider() }

    var hasCameraPermission by remember { mutableStateOf(false) }
    var latestFrame by remember { mutableStateOf<Bitmap?>(null) }
    var session by remember { mutableStateOf<TryOnSession?>(null) }
    var manualPlacement by remember { mutableStateOf<RingPlacement?>(null) }
    var manualAdjustEnabled by remember { mutableStateOf(false) }
    var runtimeMetrics by remember { mutableStateOf<RuntimeMetrics?>(null) }
    var exportedPath by remember { mutableStateOf<String?>(null) }
    var latestMeasurementHandoff by remember { mutableStateOf<TryOnDemoHandoff?>(null) }
    var hasTriggeredAutoMeasure by rememberSaveable { mutableStateOf(false) }

    val baseAsset =
        remember {
            RingAssetSource(
                id = "single_ring_demo_v2",
                name = "Demo Ring",
                overlayAssetPath = "tryon/ring_overlay_v2.png",
                metadataAssetPath = "tryon/normalization_report_v2.json",
                defaultWidthRatio = 0.16f,
            )
        }
    val ringAssetLoader = remember { RingAssetLoader(context.assets) }
    val ringBitmap = remember { runCatching { ringAssetLoader.loadOverlayBitmap(baseAsset) }.getOrNull() }
    val metadata = remember { runCatching { ringAssetLoader.loadMetadata(baseAsset) }.getOrNull() }
    val activeAsset =
        remember(baseAsset, metadata) {
            baseAsset.copy(
                defaultWidthRatio = metadata?.recommendedWidthRatio ?: baseAsset.defaultWidthRatio,
                rotationBiasDeg = metadata?.rotationBiasDeg ?: 0f,
            )
        }

    fun resolveSessionState(
        handPose: HandPoseSnapshot? = handPoseProvider.latestPose(),
        measurement: MeasurementSnapshot? = measurementProvider.latestMeasurement(),
        manualPlacementOverride: RingPlacement? = if (manualAdjustEnabled) manualPlacement else null,
        previousSessionOverride: TryOnSession? = session,
        frameWidth: Int = latestFrame?.width ?: DEMO_FALLBACK_FRAME_WIDTH,
        frameHeight: Int = latestFrame?.height ?: DEMO_FALLBACK_FRAME_HEIGHT,
        nowMs: Long = System.currentTimeMillis(),
    ): TryOnSession =
        sessionResolver.resolve(
            asset = activeAsset,
            handPose = handPose,
            measurement = measurement,
            manualPlacement = manualPlacementOverride,
            previousSession = previousSessionOverride,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            nowMs = nowMs,
        )

    fun applyMeasurementHandoff(handoff: TryOnDemoHandoff) {
        latestMeasurementHandoff = handoff
        measurementProvider.snapshot = handoff.snapshot
        session = resolveSessionState()
    }

    fun clearMeasurementHandoff() {
        latestMeasurementHandoff = null
        measurementProvider.snapshot = null
        manualAdjustEnabled = false
        session = resolveSessionState(measurement = null, manualPlacementOverride = null)
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCameraPermission = granted
        }
    val measureLauncher =
        rememberLauncherForActivityResult(HandMeasureContract()) { result ->
            val handoff =
                resolveDemoHandoff(
                    result = result,
                    allowSimulatedFallback = autoLaunchMeasureOnStart,
                )
            if (handoff != null) {
                applyMeasurementHandoff(handoff)
                if (result == null && autoLaunchMeasureOnStart) {
                    Toast.makeText(
                        context,
                        "Đã hủy đo tay. Đã áp dụng handoff mô phỏng để demo không bị gián đoạn.",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }

    LaunchedEffect(autoLaunchMeasureOnStart, hasTriggeredAutoMeasure) {
        if (autoLaunchMeasureOnStart && !hasTriggeredAutoMeasure) {
            hasTriggeredAutoMeasure = true
            measureLauncher.launch(defaultDemoMeasureConfig())
        }
    }

    LaunchedEffect(Unit) {
        hasCameraPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(lifecycleOwner, hasCameraPermission, handLandmarkEngine, activeAsset) {
        if (!hasCameraPermission) {
            onDispose { }
        } else {
            val analyzer =
                TryOnRealtimeAnalyzer(
                    minDetectionIntervalMs = 110L,
                    onDetectionFrame = { frame, timestampMs ->
                        val detection = handLandmarkEngine.detect(frame)
                        val pose = detection?.toPoseSnapshot(frame.width, frame.height, timestampMs)
                        ContextCompat.getMainExecutor(context).execute {
                            handPoseProvider.snapshot = pose
                            latestFrame = upsertSnapshot(latestFrame, frame)
                            if (!manualAdjustEnabled || session == null) {
                                session =
                                    resolveSessionState(
                                        frameWidth = frame.width,
                                        frameHeight = frame.height,
                                        nowMs = timestampMs,
                                    )
                                if (session?.mode != TryOnMode.Manual) {
                                    manualPlacement = null
                                    manualAdjustEnabled = false
                                }
                            }
                        }
                    },
                    onMetricsUpdated = { metrics ->
                        ContextCompat.getMainExecutor(context).execute {
                            runtimeMetrics = metrics
                        }
                    },
                )
            cameraController.bind(
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                analyzer = analyzer,
                lensFacing = com.handmeasure.api.LensFacing.BACK,
                analysisOutputImageFormat = androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888,
                analysisTargetResolution = android.util.Size(960, 720),
                onError = { throwable ->
                    Toast.makeText(context, "Không thể khởi tạo camera: ${throwable.message}", Toast.LENGTH_SHORT).show()
                },
            )
            onDispose {
                analyzer.close()
                cameraController.shutdown()
                handLandmarkEngine.close()
                previewRenderer.reset()
                exportRenderer.reset()
                latestFrame?.recycle()
                latestFrame = null
                ringBitmap?.recycle()
            }
        }
    }

    val currentSession = session
    val frameWidth = latestFrame?.width ?: 0
    val frameHeight = latestFrame?.height ?: 0
    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Column(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            onBack?.let { goBack ->
                Button(onClick = goBack) {
                    Text("Quay lại màn hình demo")
                }
            }
            if (!hasCameraPermission) {
                Text(
                    text = "Cần cấp quyền camera để thử nhẫn theo thời gian thực.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier =
                        Modifier
                            .background(Color(0xAA000000), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                )
            }
            if (ringBitmap == null) {
                Text(
                    text = "Không tải được asset nhẫn. Không thể hiển thị overlay hoặc xuất ảnh.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier =
                        Modifier
                            .background(Color(0xAA000000), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                )
            }
        }
        TryOnOverlay(
            ringBitmap = ringBitmap,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            placement = currentSession?.placement,
            anchor = currentSession?.anchor,
            renderer = previewRenderer,
            manualAdjustEnabled = manualAdjustEnabled || currentSession?.mode == TryOnMode.Manual,
            onManualTransform = { panXFrame, panYFrame, zoom, rotationDeg ->
                val oldPlacement = manualPlacement ?: currentSession?.placement ?: return@TryOnOverlay
                val baseSession =
                    currentSession
                        ?: sessionResolver.resolve(
                            asset = activeAsset,
                            handPose = handPoseProvider.latestPose(),
                            measurement = measurementProvider.latestMeasurement(),
                            manualPlacement = oldPlacement,
                            previousSession = null,
                            frameWidth = frameWidth.coerceAtLeast(1080),
                            frameHeight = frameHeight.coerceAtLeast(1920),
                        )
                val safeFrameWidth = frameWidth.coerceAtLeast(1)
                val next =
                    oldPlacement.copy(
                        centerX = oldPlacement.centerX + panXFrame,
                        centerY = oldPlacement.centerY + panYFrame,
                        ringWidthPx = (oldPlacement.ringWidthPx * zoom).coerceIn(20f, safeFrameWidth * 0.6f),
                        rotationDegrees = oldPlacement.rotationDegrees + rotationDeg,
                    )
                manualPlacement = next
                manualAdjustEnabled = true
                session = baseSession.copy(mode = TryOnMode.Manual, placement = next)
            },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xAA000000), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Chế độ: ${currentSession?.mode.toModeLabel()}",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = runtimeMetrics.toRuntimeText(),
                color = Color(0xFFE0E0E0),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text =
                    latestMeasurementHandoff?.summary
                        ?: "Handoff: chưa có. Hãy dùng Đo tay hoặc Dùng handoff mẫu để hoàn tất luồng demo.",
                color = Color(0xFFB2FF59),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        manualAdjustEnabled = false
                        session = resolveSessionState(manualPlacementOverride = null)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = ringBitmap != null,
                ) {
                    Text("Nhận diện tay")
                }
                Button(
                    onClick = {
                        val basePlacement =
                            manualPlacement ?: currentSession?.placement ?: run {
                                resolveSessionState(
                                    handPose = null,
                                    measurement = null,
                                    manualPlacementOverride = null,
                                    previousSessionOverride = null,
                                ).placement
                            }
                        manualPlacement = basePlacement
                        manualAdjustEnabled = true
                        session =
                            (currentSession
                                ?: resolveSessionState(
                                    manualPlacementOverride = basePlacement,
                                    previousSessionOverride = null,
                                )).copy(
                                mode = TryOnMode.Manual,
                                placement = basePlacement,
                            )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = ringBitmap != null,
                ) {
                    Text("Chỉnh tay")
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        measureLauncher.launch(defaultDemoMeasureConfig())
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Đo tay")
                }
                Button(
                    onClick = {
                        applyMeasurementHandoff(sampleTryOnDemoHandoff())
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Dùng handoff mẫu")
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val frame = latestFrame ?: return@Button
                        val active = currentSession ?: return@Button
                        val overlay = ringBitmap ?: return@Button
                        val rendered =
                            exportRenderer.renderToBitmap(
                                baseFrame = frame,
                                ringBitmap = overlay,
                                rawPlacement = active.placement,
                                anchor = active.anchor,
                                mode = active.mode,
                            )
                        val file = saveRenderedBitmap(context, rendered.bitmap)
                        exportedPath = file.absolutePath
                        val note =
                            if (rendered.validation.notes.isEmpty()) {
                                "ổn"
                            } else {
                                rendered.validation.notes.joinToString()
                            }
                        Toast.makeText(context, "Đã xuất: ${file.name} ($note)", Toast.LENGTH_SHORT).show()
                        rendered.bitmap.recycle()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Xuất ảnh")
                }
                Button(
                    onClick = {
                        clearMeasurementHandoff()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Xóa handoff")
                }
            }
            exportedPath?.let {
                Text(
                    text = "Đã lưu: $it",
                    color = Color(0xFFB2FF59),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            latestMeasurementHandoff?.let { handoff ->
                Text(
                    text = "Nguồn: ${handoff.sourceLabel}",
                    color = Color(0xFFE0E0E0),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun upsertSnapshot(
    existing: Bitmap?,
    source: Bitmap,
): Bitmap {
    if (existing != null && isReusableSnapshot(existing, source)) {
        val canvas = android.graphics.Canvas(existing)
        canvas.drawBitmap(source, 0f, 0f, null)
        return existing
    }
    existing?.recycle()
    return source.copy(Bitmap.Config.ARGB_8888, true)
}

private fun isReusableSnapshot(
    existing: Bitmap,
    source: Bitmap,
): Boolean = existing.width == source.width && existing.height == source.height && existing.isMutable

private class MutableHandPoseProvider : HandPoseProvider {
    var snapshot: HandPoseSnapshot? = null

    override fun latestPose(): HandPoseSnapshot? = snapshot
}

private class MutableMeasurementProvider : OptionalMeasurementProvider {
    var snapshot: MeasurementSnapshot? = null

    override fun latestMeasurement(): MeasurementSnapshot? = snapshot
}

private fun RuntimeMetrics?.toRuntimeText(): String {
    val metrics = this ?: return "Realtime: đang chờ..."
    val hz =
        if (metrics.avgUpdateIntervalMs <= 0.0) {
            0
        } else {
            (1000.0 / metrics.avgUpdateIntervalMs).roundToInt()
        }
    return "Realtime nhận diện=${"%.1f".format(metrics.avgDetectionMs)} ms, cập nhật=${hz} Hz, chênh bộ nhớ=${metrics.approxMemoryDeltaKb} KB"
}

private fun HandDetection.toPoseSnapshot(
    width: Int,
    height: Int,
    timestampMs: Long,
): HandPoseSnapshot =
    HandPoseSnapshot(
        frameWidth = width,
        frameHeight = height,
        landmarks = imageLandmarks.map { point -> LandmarkPoint(x = point.x, y = point.y, z = point.z) },
        confidence = confidence.coerceIn(0f, 1f),
        timestampMs = timestampMs,
    )

private fun saveRenderedBitmap(
    context: Context,
    bitmap: Bitmap,
): File {
    val exportDir = File(context.filesDir, "tryon_exports").apply { mkdirs() }
    val file = File(exportDir, "ring_tryon_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { stream ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    }
    return file
}

private fun TryOnMode?.toModeLabel(): String =
    when (this) {
        TryOnMode.Measured -> "Đã đo"
        TryOnMode.LandmarkOnly -> "Theo landmark"
        TryOnMode.Manual -> "Thủ công"
        null -> "Thủ công"
    }

private const val DEMO_FALLBACK_FRAME_WIDTH = 1080
private const val DEMO_FALLBACK_FRAME_HEIGHT = 1920
