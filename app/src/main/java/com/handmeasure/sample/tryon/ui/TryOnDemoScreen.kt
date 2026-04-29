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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.handtryon.ar.ArTryOnAvailability
import com.handtryon.ar.ArTryOnAvailabilityStatus
import com.handtryon.ar.ArTryOnScene
import com.handtryon.core.HandPoseProvider
import com.handtryon.core.OptionalMeasurementProvider
import com.handtryon.core.TryOnSessionResolution
import com.handtryon.core.TryOnSessionResolver
import com.handtryon.data.RingAssetLoader
import com.handtryon.domain.GlbAssetSummary
import com.handtryon.domain.HandPoseSnapshot
import com.handtryon.domain.LandmarkPoint
import com.handtryon.domain.MeasurementSnapshot
import com.handtryon.domain.RingAssetSource
import com.handtryon.domain.RingPlacement
import com.handtryon.domain.TryOnMode
import com.handtryon.domain.TryOnSession
import com.handtryon.domain.TryOnTrackingState
import com.handtryon.domain.TryOnUpdateAction
import com.handtryon.realtime.TryOnRealtimeAnalyzer
import com.handtryon.render.StableRingOverlayRenderer
import com.handtryon.tracking.FrameSource
import com.handtryon.tracking.Handedness
import com.handtryon.tracking.TargetFinger
import com.handtryon.tracking.TrackedHandFrame
import com.handtryon.tracking.TrackedHandFrameMapper
import com.handtryon.ui.TryOnOverlay
import com.handtryon.validation.RuntimeMetrics
import com.handtryon.validation.RuntimeMetricsTracker
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun TryOnDemoScreen(
    onBack: (() -> Unit)? = null,
    autoLaunchMeasureOnStart: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView =
        remember {
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        }
    val cameraController = remember { CameraController(context) }
    val handLandmarkEngine = remember { MediaPipeHandLandmarkEngine(context) }
    val sessionResolver = remember { TryOnSessionResolver() }
    val previewRenderer = remember { StableRingOverlayRenderer() }
    val exportRenderer = remember { StableRingOverlayRenderer() }
    val handPoseProvider = remember { MutableHandPoseProvider() }
    val measurementProvider = remember { MutableMeasurementProvider() }
    val arFrameExecutor = remember { Executors.newSingleThreadExecutor() }
    val arFrameDetectionInFlight = remember { AtomicBoolean(false) }
    val arRuntimeMetricsTracker = remember { RuntimeMetricsTracker() }
    val handDetectionLock = remember { Any() }

    var hasCameraPermission by remember { mutableStateOf(false) }
    var latestFrame by remember { mutableStateOf<Bitmap?>(null) }
    var latestHandPose by remember { mutableStateOf<HandPoseSnapshot?>(null) }
    var latestTrackedHandFrame by remember { mutableStateOf<TrackedHandFrame?>(null) }
    var session by remember { mutableStateOf<TryOnSession?>(null) }
    var latestResolution by remember { mutableStateOf<TryOnSessionResolution?>(null) }
    var manualPlacement by remember { mutableStateOf<RingPlacement?>(null) }
    var manualAdjustEnabled by remember { mutableStateOf(false) }
    var runtimeMetrics by remember { mutableStateOf<RuntimeMetrics?>(null) }
    var exportedPath by remember { mutableStateOf<String?>(null) }
    var latestMeasurementHandoff by remember { mutableStateOf<TryOnDemoHandoff?>(null) }
    var arPreviewEnabled by rememberSaveable { mutableStateOf(false) }
    var hasSelectedPreviewMode by rememberSaveable { mutableStateOf(false) }
    var debugFingerOccluderVisible by rememberSaveable { mutableStateOf(false) }
    var rendererError by remember { mutableStateOf<String?>(null) }
    var hasTriggeredAutoMeasure by rememberSaveable { mutableStateOf(false) }

    val baseAsset =
        remember {
            RingAssetSource(
                id = "ring_ar_glb_v1",
                name = "AR GLB Ring",
                overlayAssetPath = "tryon/ring_overlay_v2.png",
                modelAssetPath = "tryon/ring_AR.glb",
                metadataAssetPath = "tryon/normalization_report_v2.json",
                defaultWidthRatio = 0.16f,
            )
        }
    val ringAssetLoader = remember { RingAssetLoader(context.assets) }
    var arAvailability by remember { mutableStateOf(ArTryOnAvailability.from(context)) }
    val glbSummary = remember { runCatching { ringAssetLoader.loadGlbSummary(baseAsset) }.getOrNull() }
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
    ): TryOnSession {
        val resolution =
            sessionResolver.resolveState(
                asset = activeAsset,
                handPose = handPose,
                measurement = measurement,
                manualPlacement = manualPlacementOverride,
                previousSession = previousSessionOverride,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                nowMs = nowMs,
            )
        latestResolution = resolution
        return resolution.session
    }
    
    fun applyMeasurementHandoff(handoff: TryOnDemoHandoff) {
        latestMeasurementHandoff = handoff
        measurementProvider.snapshot = handoff.snapshot
        val resolved = resolveSessionState(measurement = handoff.snapshot)
        manualPlacement = resolved.placement
        manualAdjustEnabled = true
        session =
            resolved.copy(
                mode = TryOnMode.Manual,
                quality =
                    resolved.quality.copy(
                        qualityScore = maxOf(resolved.quality.qualityScore, 0.72f),
                        updateAction = TryOnUpdateAction.Update,
                    ),
            )
    }

    fun clearMeasurementHandoff() {
        latestMeasurementHandoff = null
        measurementProvider.snapshot = null
        manualAdjustEnabled = false
        session = resolveSessionState(measurement = null, manualPlacementOverride = null)
    }

    val canUseArPreview = arAvailability.isUsableNow && !activeAsset.modelAssetPath.isNullOrBlank()
    val enableLegacy2dTryOn = false
    val isArPreviewActive = arPreviewEnabled && canUseArPreview
    val isModel3dPreviewActive = isArPreviewActive

    LaunchedEffect(canUseArPreview, hasSelectedPreviewMode) {
        when {
            canUseArPreview && !hasSelectedPreviewMode -> arPreviewEnabled = true
            !canUseArPreview -> {
                arPreviewEnabled = false
                hasSelectedPreviewMode = false
            }
        }
    }

    LaunchedEffect(activeAsset) {
        if (session == null) {
            session =
                resolveSessionState(
                    handPose = null,
                    measurement = null,
                    previousSessionOverride = null,
                )
        }
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

    LaunchedEffect(context) {
        repeat(AR_AVAILABILITY_RETRY_COUNT) {
            arAvailability = ArTryOnAvailability.from(context)
            if (arAvailability.status != com.handtryon.ar.ArTryOnAvailabilityStatus.Unknown) {
                return@LaunchedEffect
            }
            delay(AR_AVAILABILITY_RETRY_DELAY_MS)
        }
    }

    DisposableEffect(handLandmarkEngine) {
        onDispose {
            handLandmarkEngine.close()
        }
    }

    DisposableEffect(ringBitmap) {
        onDispose {
            ringBitmap?.recycle()
        }
    }

    DisposableEffect(arFrameExecutor) {
        onDispose {
            arFrameExecutor.shutdownNow()
        }
    }

    DisposableEffect(lifecycleOwner, hasCameraPermission, handLandmarkEngine, activeAsset, isArPreviewActive) {
        if (!hasCameraPermission || isArPreviewActive) {
            onDispose { }
        } else {
            val analyzer =
                TryOnRealtimeAnalyzer(
                    minDetectionIntervalMs = 110L,
                    onDetectionFrame = { frame, timestampMs ->
                        val detection = synchronized(handDetectionLock) { handLandmarkEngine.detect(frame) }
                            val pose = detection?.toPoseSnapshot(frame.width, frame.height, timestampMs)
                            val trackedFrame =
                                if (pose != null) {
                                    detection.toTrackedHandFrame(
                                        pose = pose,
                                        source = FrameSource.CameraX,
                                        isFrontCamera = false,
                                        rotationDegrees = previewView.display?.rotation.toDegrees(),
                                    )
                                } else {
                                    null
                                }
                            ContextCompat.getMainExecutor(context).execute {
                                handPoseProvider.snapshot = pose
                                latestHandPose = pose
                                latestTrackedHandFrame = trackedFrame
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
                previewRenderer.reset()
                exportRenderer.reset()
                latestFrame?.recycle()
                latestFrame = null
                latestTrackedHandFrame = null
            }
        }
    }

    val currentSession = session
    val frameWidth = latestFrame?.width ?: 0
    val frameHeight = latestFrame?.height ?: 0
    val renderFrameWidth = frameWidth.takeIf { it > 0 } ?: DEMO_FALLBACK_FRAME_WIDTH
    val renderFrameHeight = frameHeight.takeIf { it > 0 } ?: DEMO_FALLBACK_FRAME_HEIGHT
    val currentRenderState3D = latestResolution?.renderState3D
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        if (isArPreviewActive) {
            ArTryOnScene(
                modelAssetPath = activeAsset.modelAssetPath,
                renderState3D = currentRenderState3D,
                frameWidth = renderFrameWidth,
                frameHeight = renderFrameHeight,
                glbSummary = glbSummary,
                debugFingerOccluderVisible = debugFingerOccluderVisible,
                onRendererError = { throwable ->
                    rendererError = "ARCore 3D: ${throwable.message ?: throwable::class.java.simpleName}"
                },
                onTelemetryUpdated = { metrics ->
                    runtimeMetrics = metrics
                },
                onCameraFrame = { cameraFrame ->
                    val bitmap = cameraFrame.bitmap
                    if (!arFrameDetectionInFlight.compareAndSet(false, true)) {
                        bitmap.recycle()
                        return@ArTryOnScene
                    }
                    try {
                        arFrameExecutor.execute {
                            try {
                                arRuntimeMetricsTracker.onFrameAnalyzed()
                                val startNs = System.nanoTime()
                                val detection = synchronized(handDetectionLock) { handLandmarkEngine.detect(bitmap) }
                                val detectionDurationNs = System.nanoTime() - startNs
                                arRuntimeMetricsTracker.onDetectionUpdate(
                                    durationNs = detectionDurationNs,
                                    timestampMs = cameraFrame.timestampMs,
                                )
                                val metrics = arRuntimeMetricsTracker.snapshot()
                                val pose = detection?.toPoseSnapshot(bitmap.width, bitmap.height, cameraFrame.timestampMs)
                                val trackedFrame =
                                    if (pose != null) {
                                        detection.toTrackedHandFrame(
                                            pose = pose,
                                            source = FrameSource.ARCoreCpuImage,
                                            isFrontCamera = false,
                                            rotationDegrees = 0,
                                        )
                                    } else {
                                        null
                                    }
                                ContextCompat.getMainExecutor(context).execute {
                                    try {
                                        runtimeMetrics = metrics
                                        handPoseProvider.snapshot = pose
                                        latestHandPose = pose
                                        latestTrackedHandFrame = trackedFrame
                                        latestFrame = upsertSnapshot(latestFrame, bitmap)
                                        if (!manualAdjustEnabled || session == null) {
                                            session =
                                                resolveSessionState(
                                                    frameWidth = bitmap.width,
                                                    frameHeight = bitmap.height,
                                                    nowMs = cameraFrame.timestampMs,
                                                )
                                            if (session?.mode != TryOnMode.Manual) {
                                                manualPlacement = null
                                                manualAdjustEnabled = false
                                            }
                                        }
                                    } finally {
                                        bitmap.recycle()
                                        arFrameDetectionInFlight.set(false)
                                    }
                                }
                            } catch (_: Throwable) {
                                bitmap.recycle()
                                arFrameDetectionInFlight.set(false)
                            }
                        }
                    } catch (_: RejectedExecutionException) {
                        bitmap.recycle()
                        arFrameDetectionInFlight.set(false)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        }
        Column(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
            if (glbSummary != null && ringBitmap == null) {
                Text(
                    text = "GLB loaded, but 2D fallback overlay is missing.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier =
                        Modifier
                            .background(Color(0xAA000000), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                )
            }
            rendererError?.let { error ->
                Text(
                    text = "3D renderer error: $error",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier =
                        Modifier
                            .background(Color(0xAA000000), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                )
            }
        }
        if (enableLegacy2dTryOn && !isModel3dPreviewActive) {
            TryOnOverlay(
                ringBitmap = ringBitmap,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                placement = currentSession?.placement,
                anchor = currentSession?.anchor,
                renderer = previewRenderer,
                mode = currentSession?.mode ?: TryOnMode.LandmarkOnly,
                qualityScore = currentSession?.quality?.qualityScore ?: (currentSession?.anchor?.confidence ?: 0.62f),
                trackingState = currentSession?.quality?.trackingState ?: TryOnTrackingState.Searching,
                updateAction = currentSession?.quality?.updateAction ?: TryOnUpdateAction.Update,
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
        }

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Chế độ: ${currentSession?.mode.toModeLabel()}",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = runtimeMetrics.toRuntimeTelemetryText(),
                color = Color(0xFFE0E0E0),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text =
                    when {
                        isArPreviewActive -> "Renderer: ARCore 3D"
                        else -> "Renderer: CameraX preview (AR inactive)"
                    },
                color = Color(0xFFE0E0E0),
                style = MaterialTheme.typography.bodySmall,
            )
            glbSummary.toReadableText()?.let { modelText ->
                Text(
                    text = modelText,
                    color = Color(0xFFE0E0E0),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
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
                        rendererError = null
                        hasSelectedPreviewMode = true
                        arPreviewEnabled = !arPreviewEnabled
                    },
                    modifier = Modifier.weight(1f),
                    enabled = canUseArPreview,
                ) {
                    Text(if (isArPreviewActive) "Tat AR" else "Bat AR")
                }
                Text(
                    text =
                        if (canUseArPreview) {
                            "ARCore ready"
                        } else {
                            arAvailability.toUserReadableText()
                        },
                    color = Color(0xFFE0E0E0),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { debugFingerOccluderVisible = !debugFingerOccluderVisible },
                    modifier = Modifier.weight(1f),
                    enabled = isModel3dPreviewActive,
                ) {
                    Text(if (debugFingerOccluderVisible) "Ẩn occluder debug" else "Hiện occluder debug")
                }
                Text(
                    text = "Occluder Phase A: ${if (debugFingerOccluderVisible) "debug mesh" else "depth-only"}",
                    color = Color(0xFFE0E0E0),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        manualAdjustEnabled = false
                        session = resolveSessionState(manualPlacementOverride = null)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = ringBitmap != null && !isArPreviewActive,
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
                    enabled = ringBitmap != null && enableLegacy2dTryOn && !isModel3dPreviewActive,
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
                                qualityScore = active.quality.qualityScore,
                                trackingState = active.quality.trackingState,
                                updateAction = active.quality.updateAction,
                                shouldRenderOverlay = active.quality.updateAction != TryOnUpdateAction.Hide,
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
                    enabled =
                        ringBitmap != null &&
                            latestFrame != null &&
                            currentSession != null &&
                            enableLegacy2dTryOn &&
                            !isModel3dPreviewActive,
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

private fun RuntimeMetrics?.toRuntimeTelemetryText(): String {
    val metrics = this ?: return "Realtime: waiting..."
    val updateHz =
        if (metrics.avgUpdateIntervalMs <= 0.0) {
            0
        } else {
            (1000.0 / metrics.avgUpdateIntervalMs).roundToInt()
        }
    val renderHz = metrics.renderStateUpdateHz.roundToInt()
    val rendererError = metrics.rendererErrorStage?.let { stage -> ", rendererError=$stage" }.orEmpty()
    return "Realtime detector=${"%.1f".format(metrics.detectorLatencyMs)} ms, update=${updateHz} Hz, renderState=${renderHz} Hz, nodeRecreate=${metrics.nodeRecreateCount}$rendererError, memory=${metrics.approxMemoryDeltaKb} KB"
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

private fun GlbAssetSummary?.toReadableText(): String? {
    val summary = this ?: return null
    val bounds = summary.estimatedBoundsMm
    val boundsText =
        if (bounds == null) {
            "unknown bounds"
        } else {
            "bounds ${"%.2f".format(bounds.x)} x ${"%.2f".format(bounds.y)} x ${"%.2f".format(bounds.z)} mm"
        }
    return "GLB: v${summary.glbVersion}, mesh=${summary.meshCount}, material=${summary.materialCount}, $boundsText"
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

private fun HandDetection.toTrackedHandFrame(
    pose: HandPoseSnapshot,
    source: FrameSource,
    isFrontCamera: Boolean,
    rotationDegrees: Int,
): TrackedHandFrame =
    when (source) {
        FrameSource.CameraX ->
            TrackedHandFrameMapper.fromCameraXSnapshot(
                pose = pose,
                handedness = handedness.toDomainHandedness(),
                targetFinger = TargetFinger.Ring,
                isFrontCamera = isFrontCamera,
                sensorRotationDegrees = rotationDegrees,
            )
        FrameSource.ARCoreCpuImage ->
            TrackedHandFrameMapper.fromArCoreCpuImageSnapshot(
                pose = pose,
                handedness = handedness.toDomainHandedness(),
                targetFinger = TargetFinger.Ring,
                imageRotationDegrees = rotationDegrees,
            )
        FrameSource.Replay ->
            TrackedHandFrameMapper.fromHandPoseSnapshot(
                pose = pose,
                source = FrameSource.Replay,
                handedness = handedness.toDomainHandedness(),
                targetFinger = TargetFinger.Ring,
                isMirrored = isFrontCamera,
                rotationDegrees = rotationDegrees,
            )
    }

private fun String.toDomainHandedness(): Handedness =
    when (lowercase()) {
        "left" -> Handedness.Left
        "right" -> Handedness.Right
        else -> Handedness.Unknown
    }

private fun Int?.toDegrees(): Int =
    when (this) {
        0 -> 0
        1 -> 90
        2 -> 180
        3 -> 270
        else -> 0
    }

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

private fun ArTryOnAvailability.toUserReadableText(): String =
    when (status) {
        ArTryOnAvailabilityStatus.SupportedNeedsInstall ->
            "Cần cài Google Play Services for AR (ARCore / com.google.ar.core)."
        ArTryOnAvailabilityStatus.Unsupported ->
            "Máy không được ARCore hỗ trợ."
        ArTryOnAvailabilityStatus.Unknown ->
            "Đang kiểm tra ARCore. Nếu giữ nguyên, hãy cài/cập nhật Google Play Services for AR."
        ArTryOnAvailabilityStatus.SupportedInstalled ->
            "ARCore ready"
    }

private const val DEMO_FALLBACK_FRAME_WIDTH = 1080
private const val DEMO_FALLBACK_FRAME_HEIGHT = 1920
private const val AR_AVAILABILITY_RETRY_COUNT = 8
private const val AR_AVAILABILITY_RETRY_DELAY_MS = 500L
