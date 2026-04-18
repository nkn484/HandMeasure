package com.handmeasure.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.handmeasure.R
import com.handmeasure.api.CaptureStep
import com.handmeasure.api.HandMeasureConfig
import com.handmeasure.api.HandMeasureResult
import com.handmeasure.api.HandMeasureWarning
import com.handmeasure.api.CalibrationStatus
import com.handmeasure.api.MeasurementSource
import com.handmeasure.api.QualityLevel
import com.handmeasure.api.ResultMode
import com.handmeasure.camera.CameraController
import com.handmeasure.camera.HandMeasureAnalyzer
import com.handmeasure.coordinator.HandMeasureCoordinator
import com.handmeasure.coordinator.LiveAnalysisState
import com.handmeasure.coordinator.PoseGuidanceHintKey
import com.handmeasure.coordinator.PoseGuidanceHintTextResolver
import com.handmeasure.flow.CaptureUiState
import com.handmeasure.measurement.MeasurementReplayRunner
import com.handmeasure.vision.MediaPipeHandLandmarkEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class HandMeasureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = intent.getParcelableExtra<HandMeasureConfig>(EXTRA_CONFIG) ?: HandMeasureConfig()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HandMeasureRoute(
                        config = config,
                        onCompleted = { result ->
                            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_RESULT, result))
                            finish()
                        },
                        onDismiss = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        },
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_CONFIG = "hand_measure_config"
        const val EXTRA_RESULT = "hand_measure_result"
    }
}

private enum class FlowPhase {
    CAPTURE,
    PROCESSING,
    RESULT,
}

@Composable
private fun HandMeasureRoute(
    config: HandMeasureConfig,
    onCompleted: (HandMeasureResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val cameraController = remember { CameraController(context) }
    val handLandmarkEngine = remember(config) { MediaPipeHandLandmarkEngine(context) }
    val poseHintResolver =
        remember(context) {
            PoseGuidanceHintTextResolver { hintKey -> context.getString(hintKey.toResId()) }
        }
    val coordinator =
        remember(config) {
            HandMeasureCoordinator(
                config = config,
                handLandmarkEngine = handLandmarkEngine,
                debugExportDirProvider = { File(context.filesDir, "handmeasure_debug") },
                poseGuidanceHintTextResolver = poseHintResolver,
            )
        }

    var hasPermission by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(FlowPhase.CAPTURE) }
    var captureState by remember { mutableStateOf(coordinator.currentState()) }
    var analysisState by remember { mutableStateOf<LiveAnalysisState?>(null) }
    var result by remember { mutableStateOf<HandMeasureResult?>(null) }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasPermission = granted
            if (!granted) onDismiss()
        }

    LaunchedEffect(Unit) {
        if (config.debugReplayInputPath != null) {
            phase = FlowPhase.PROCESSING
            val replayPath = config.debugReplayInputPath
            val replayOutcome =
                withContext(Dispatchers.Default) {
                    runCatching {
                        val replayEngine = MediaPipeHandLandmarkEngine(context)
                        val runner = MeasurementReplayRunner { replayConfig ->
                            HandMeasureCoordinator(
                                config = replayConfig,
                                handLandmarkEngine = replayEngine,
                                debugExportDirProvider = { File(context.filesDir, "handmeasure_debug") },
                                poseGuidanceHintTextResolver = poseHintResolver,
                            )
                        }
                        try {
                            runner.runFromDirectory(config.copy(debugReplayInputPath = null), File(replayPath))
                        } finally {
                            replayEngine.close()
                        }
                    }
                }
            result =
                replayOutcome.getOrNull()?.result
                    ?: buildBestEffortFallbackResult(
                        config = config,
                        note = replayOutcome.exceptionOrNull()?.message,
                    )
            phase = FlowPhase.RESULT
            return@LaunchedEffect
        }
        hasPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(handLandmarkEngine) {
        onDispose {
            handLandmarkEngine.close()
        }
    }

    DisposableEffect(lifecycleOwner, hasPermission, phase) {
        if (!hasPermission || phase != FlowPhase.CAPTURE) {
            onDispose { }
        } else {
            val analyzer =
                HandMeasureAnalyzer(
                    coordinator = coordinator,
                    onStateUpdated = { state ->
                        coroutineScope.launch {
                            analysisState = state
                            captureState = state.captureState
                        }
                    },
                    onFlowCompleted = {
                        coroutineScope.launch {
                            if (phase != FlowPhase.CAPTURE) return@launch
                            phase = FlowPhase.PROCESSING
                            cameraController.unbind()
                            val measurement =
                                withContext(Dispatchers.Default) {
                                    coordinator.finalizeResult()
                                }
                            result = measurement
                            phase = FlowPhase.RESULT
                        }
                    },
                )
            cameraController.bind(
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                analyzer = analyzer,
                lensFacing = config.lensFacing,
                onError = {
                    phase = FlowPhase.RESULT
                    result = buildBestEffortFallbackResult(config = config)
                },
            )
            onDispose {
                cameraController.shutdown()
            }
        }
    }

    when (phase) {
        FlowPhase.CAPTURE ->
            CaptureScreen(
                config = config,
                previewView = previewView,
                captureState = captureState,
                analysisState = analysisState,
                onUseBestFrame = { captureState = coordinator.advanceWithBestCandidate() },
                onRetry = { captureState = coordinator.retryCurrentStep() },
                onClose = onDismiss,
            )
        FlowPhase.PROCESSING -> ProcessingScreen()
        FlowPhase.RESULT -> ResultScreen(result = result, onDone = { result?.let(onCompleted) ?: onDismiss() })
    }
}

@Composable
private fun CaptureScreen(
    config: HandMeasureConfig,
    previewView: PreviewView,
    captureState: CaptureUiState,
    analysisState: LiveAnalysisState?,
    onUseBestFrame: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        GuidanceOverlay(captureState = captureState, analysisState = analysisState, debugEnabled = config.debugOverlayEnabled)

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            StatusPanel(captureState = captureState, analysisState = analysisState, onClose = onClose)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRetry, enabled = captureState.bestCurrentCandidate != null) {
                    Text("Chụp lại bước")
                }
                Button(onClick = onUseBestFrame, enabled = captureState.canAdvanceWithBest) {
                    Text("Dùng khung hình tốt nhất")
                }
            }
        }
    }
}

@Composable
private fun GuidanceOverlay(
    captureState: CaptureUiState,
    analysisState: LiveAnalysisState?,
    debugEnabled: Boolean,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(20f, 12f))
        drawRoundRect(
            color = Color(0xB3FFFFFF),
            topLeft = Offset(size.width * 0.08f, size.height * 0.16f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.84f, size.height * 0.68f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(28f, 28f),
            style = Stroke(width = 4f, cap = StrokeCap.Round, pathEffect = dash),
        )

        if (debugEnabled) {
            val frameWidth = (analysisState?.frameWidth ?: 1).coerceAtLeast(1)
            val frameHeight = (analysisState?.frameHeight ?: 1).coerceAtLeast(1)
            analysisState?.handDetection?.imageLandmarks?.forEach { landmark ->
                drawCircle(
                    color = Color(0xFF4DD0E1),
                    radius = 6f,
                    center = Offset(landmark.x / frameWidth * size.width, landmark.y / frameHeight * size.height),
                )
            }
            val corners = analysisState?.cardDetection?.corners.orEmpty()
            if (corners.size == 4) {
                for (index in corners.indices) {
                    val start = corners[index]
                    val end = corners[(index + 1) % corners.size]
                    drawLine(
                        color = Color(0xFFFFC107),
                        start = Offset(start.first / frameWidth * size.width, start.second / frameHeight * size.height),
                        end = Offset(end.first / frameWidth * size.width, end.second / frameHeight * size.height),
                        strokeWidth = 5f,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPanel(
    captureState: CaptureUiState,
    analysisState: LiveAnalysisState?,
    onClose: () -> Unit,
) {
    val step = captureState.currentStep.step
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(Color(0x7F111111), RoundedCornerShape(16.dp))
                .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(id = step.titleResId()), color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(text = stringResource(id = step.hintResId()), color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = onClose) { Text("Đóng") }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(progress = { captureState.progressFraction }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Hoàn thành ${captureState.completedSteps.size}/${captureState.totalSteps} bước",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
        )
        analysisState?.let { state ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    "chất lượng=${state.qualityScore.format2()} nhận diện=${state.detectionConfidence.format2()} tư thế=${state.poseConfidence.format2()} đo=${state.measurementConfidence.format2()} thẻ=${state.cardScore.format2()} mờ=${state.blurScore.format2()} chuyển động=${state.motionScore.format2()} sáng=${state.lightingScore.format2()}",
                color = Color(0xFFE0E0E0),
                style = MaterialTheme.typography.bodySmall,
            )
            state.poseGuidanceHint?.let {
                Text(text = "Gợi ý: $it", color = Color(0xFFFFE082), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ProcessingScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF101820)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp), color = Color(0xFFF2AA4C))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Đang tính size ngón tay", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Đang dùng các khung hình tốt nhất từ mọi góc hướng dẫn.", color = Color(0xFFD0D0D0), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ResultScreen(result: HandMeasureResult?, onDone: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF7F2EA)).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Kết quả đo", style = MaterialTheme.typography.headlineMedium)
            if (result == null) {
                Text("Không tạo được kết quả.", style = MaterialTheme.typography.bodyLarge)
            } else {
                Text("Size nhẫn gợi ý: ${result.suggestedRingSizeLabel}", style = MaterialTheme.typography.titleLarge)
                Text("Chiều rộng: ${result.fingerWidthMm.format2()} mm")
                Text("Độ dày: ${result.fingerThicknessMm.format2()} mm")
                Text("Chu vi: ${result.estimatedCircumferenceMm.format2()} mm")
                Text("Đường kính tương đương: ${result.equivalentDiameterMm.format2()} mm")
                Text("Độ tin cậy: ${result.confidenceScore.format2()}")
                Text("Số bước đã chụp: ${result.capturedSteps.size}")
                Text("Chế độ: ${result.resultMode.toVietnameseLabel()} | Chất lượng: ${result.qualityLevel.toVietnameseLabel()}")
                Text("Hiệu chuẩn: ${result.calibrationStatus.toVietnameseLabel()} | Nên đo lại: ${result.retryRecommended.toVietnameseLabel()}")
                val warningsText = if (result.warnings.isEmpty()) "không có" else result.warnings.joinToString { it.toVietnameseLabel() }
                Text(
                    "Cảnh báo: $warningsText",
                    color = Color(0xFF8A4B08),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (result.retryRecommended) {
                    Text(
                        "Kết quả hiện là ước tính tốt nhất có thể. Nên đo lại trong điều kiện ổn định hơn.",
                        color = Color(0xFF8A4B08),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onDone) { Text("Xong") }
        }
    }
}

private fun Float.format2(): String = "%.2f".format(this)

private fun Double.format2(): String = "%.2f".format(this)

private fun CaptureStep.titleResId(): Int =
    when (this) {
        CaptureStep.FRONT_PALM -> R.string.step_front_palm_title
        CaptureStep.LEFT_OBLIQUE -> R.string.step_left_oblique_title
        CaptureStep.RIGHT_OBLIQUE -> R.string.step_right_oblique_title
        CaptureStep.UP_TILT -> R.string.step_up_tilt_title
        CaptureStep.DOWN_TILT -> R.string.step_down_tilt_title
        CaptureStep.BACK_OF_HAND -> R.string.step_back_of_hand_title
        CaptureStep.LEFT_OBLIQUE_DORSAL -> R.string.step_left_oblique_dorsal_title
        CaptureStep.RIGHT_OBLIQUE_DORSAL -> R.string.step_right_oblique_dorsal_title
        CaptureStep.UP_TILT_DORSAL -> R.string.step_up_tilt_dorsal_title
        CaptureStep.DOWN_TILT_DORSAL -> R.string.step_down_tilt_dorsal_title
    }

private fun CaptureStep.hintResId(): Int =
    when (this) {
        CaptureStep.FRONT_PALM -> R.string.step_front_palm_hint
        CaptureStep.LEFT_OBLIQUE -> R.string.step_left_oblique_hint
        CaptureStep.RIGHT_OBLIQUE -> R.string.step_right_oblique_hint
        CaptureStep.UP_TILT -> R.string.step_up_tilt_hint
        CaptureStep.DOWN_TILT -> R.string.step_down_tilt_hint
        CaptureStep.BACK_OF_HAND -> R.string.step_back_of_hand_hint
        CaptureStep.LEFT_OBLIQUE_DORSAL -> R.string.step_left_oblique_dorsal_hint
        CaptureStep.RIGHT_OBLIQUE_DORSAL -> R.string.step_right_oblique_dorsal_hint
        CaptureStep.UP_TILT_DORSAL -> R.string.step_up_tilt_dorsal_hint
        CaptureStep.DOWN_TILT_DORSAL -> R.string.step_down_tilt_dorsal_hint
    }

private fun PoseGuidanceHintKey.toResId(): Int =
    when (this) {
        PoseGuidanceHintKey.PLACE_HAND_IN_FRAME -> R.string.pose_hint_place_hand_in_frame
        PoseGuidanceHintKey.PLACE_CARD_NEAR_FINGER -> R.string.pose_hint_place_card_near_finger
        PoseGuidanceHintKey.ADJUST_HAND_POSE -> R.string.pose_hint_adjust_hand_pose
        PoseGuidanceHintKey.HOLD_HAND_STEADY -> R.string.pose_hint_hold_hand_steady
        PoseGuidanceHintKey.WAIT_FOR_LOCK -> R.string.pose_hint_wait_for_lock
        PoseGuidanceHintKey.REDUCE_GLARE -> R.string.pose_hint_reduce_glare
        PoseGuidanceHintKey.KEEP_HAND_AND_CARD_CLOSER -> R.string.pose_hint_keep_hand_card_closer
        PoseGuidanceHintKey.TRACKING_UNSTABLE -> R.string.pose_hint_tracking_unstable
        PoseGuidanceHintKey.FRAME_HAND_CLEARER -> R.string.pose_hint_frame_hand_clearer
        PoseGuidanceHintKey.ROTATE_LEFT_MORE -> R.string.pose_hint_rotate_left_more
        PoseGuidanceHintKey.ROTATE_RIGHT_MORE -> R.string.pose_hint_rotate_right_more
        PoseGuidanceHintKey.TILT_UP_MORE -> R.string.pose_hint_tilt_up_more
        PoseGuidanceHintKey.TILT_DOWN_MORE -> R.string.pose_hint_tilt_down_more
        PoseGuidanceHintKey.FACE_PALM_TO_CAMERA -> R.string.pose_hint_face_palm_to_camera
    }

private fun buildBestEffortFallbackResult(
    config: HandMeasureConfig,
    note: String? = null,
): HandMeasureResult {
    val warnings =
        buildList {
            add(HandMeasureWarning.BEST_EFFORT_ESTIMATE)
            add(HandMeasureWarning.LOW_RESULT_RELIABILITY)
            if (!note.isNullOrBlank()) add(HandMeasureWarning.CALIBRATION_WEAK)
        }
    return HandMeasureResult(
        targetFinger = config.targetFinger,
        fingerWidthMm = 18.0,
        fingerThicknessMm = 14.0,
        estimatedCircumferenceMm = 50.3,
        equivalentDiameterMm = 16.0,
        suggestedRingSizeLabel = config.ringSizeTable.entries.first().label,
        confidenceScore = 0.2f,
        warnings = warnings,
        capturedSteps = emptyList(),
        resultMode = ResultMode.FALLBACK_ESTIMATE,
        qualityLevel = QualityLevel.LOW,
        retryRecommended = true,
        calibrationStatus = CalibrationStatus.MISSING_REFERENCE,
        measurementSources = listOf(MeasurementSource.DEFAULT_HEURISTIC),
        debugMetadata = null,
    )
}

private fun ResultMode.toVietnameseLabel(): String =
    when (this) {
        ResultMode.DIRECT_MEASUREMENT -> "Đo trực tiếp"
        ResultMode.HYBRID_ESTIMATE -> "Ước tính kết hợp"
        ResultMode.FALLBACK_ESTIMATE -> "Ước tính dự phòng"
    }

private fun QualityLevel.toVietnameseLabel(): String =
    when (this) {
        QualityLevel.HIGH -> "Cao"
        QualityLevel.MEDIUM -> "Trung bình"
        QualityLevel.LOW -> "Thấp"
    }

private fun CalibrationStatus.toVietnameseLabel(): String =
    when (this) {
        CalibrationStatus.CALIBRATED -> "Đã hiệu chuẩn"
        CalibrationStatus.DEGRADED -> "Suy giảm"
        CalibrationStatus.MISSING_REFERENCE -> "Thiếu tham chiếu"
    }

private fun HandMeasureWarning.toVietnameseLabel(): String =
    when (this) {
        HandMeasureWarning.BEST_EFFORT_ESTIMATE -> "Ước tính tốt nhất có thể"
        HandMeasureWarning.LOW_CARD_CONFIDENCE -> "Độ tin cậy thẻ chuẩn thấp"
        HandMeasureWarning.LOW_POSE_CONFIDENCE -> "Độ tin cậy tư thế thấp"
        HandMeasureWarning.LOW_LIGHTING -> "Ánh sáng yếu"
        HandMeasureWarning.HIGH_MOTION -> "Chuyển động cao"
        HandMeasureWarning.HIGH_BLUR -> "Hình ảnh bị mờ"
        HandMeasureWarning.THICKNESS_ESTIMATED_FROM_WEAK_ANGLES -> "Độ dày ước tính từ góc chụp yếu"
        HandMeasureWarning.CALIBRATION_WEAK -> "Hiệu chuẩn yếu"
        HandMeasureWarning.LOW_RESULT_RELIABILITY -> "Độ tin cậy kết quả thấp"
    }

private fun Boolean.toVietnameseLabel(): String = if (this) "Có" else "Không"
