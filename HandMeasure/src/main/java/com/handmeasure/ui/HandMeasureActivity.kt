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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.handmeasure.api.HandMeasureConfig
import com.handmeasure.api.HandMeasureResult
import com.handmeasure.camera.CameraController
import com.handmeasure.camera.HandMeasureAnalyzer
import com.handmeasure.coordinator.HandMeasureCoordinator
import com.handmeasure.coordinator.LiveAnalysisState
import com.handmeasure.flow.CaptureUiState
import com.handmeasure.flow.GuidedSteps
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
    val coordinator =
        remember(config) {
            HandMeasureCoordinator(
                config = config,
                handLandmarkEngine = MediaPipeHandLandmarkEngine(context),
                debugExportDirProvider = { File(context.filesDir, "handmeasure_debug") },
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
            val replayResult =
                withContext(Dispatchers.Default) {
                    runCatching {
                        val runner = MeasurementReplayRunner { replayConfig ->
                            HandMeasureCoordinator(
                                config = replayConfig,
                                handLandmarkEngine = MediaPipeHandLandmarkEngine(context),
                                debugExportDirProvider = { File(context.filesDir, "handmeasure_debug") },
                            )
                        }
                        runner.runFromDirectory(config.copy(debugReplayInputPath = null), File(replayPath))
                    }.getOrNull()
                }
            result = replayResult?.result
            phase = FlowPhase.RESULT
            return@LaunchedEffect
        }
        hasPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
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
                    result =
                        HandMeasureResult(
                            targetFinger = config.targetFinger,
                            fingerWidthMm = 18.0,
                            fingerThicknessMm = 14.0,
                            estimatedCircumferenceMm = 50.3,
                            equivalentDiameterMm = 16.0,
                            suggestedRingSizeLabel = config.ringSizeTable.entries.first().label,
                            confidenceScore = 0.2f,
                            warnings = listOf(com.handmeasure.api.HandMeasureWarning.BEST_EFFORT_ESTIMATE),
                            capturedSteps = emptyList(),
                        )
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
                    Text("Retake step")
                }
                Button(onClick = onUseBestFrame, enabled = captureState.canAdvanceWithBest) {
                    Text("Use best frame")
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
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(Color(0x7F111111), RoundedCornerShape(16.dp))
                .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = captureState.currentStep.title, color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(text = captureState.currentStep.hint, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = onClose) { Text("Close") }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(progress = { captureState.progressFraction }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Completed ${captureState.completedSteps.size}/${GuidedSteps.all.size} steps",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
        )
        analysisState?.let { state ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    "quality=${state.qualityScore.format2()} detect=${state.detectionConfidence.format2()} pose=${state.poseConfidence.format2()} measure=${state.measurementConfidence.format2()} card=${state.cardScore.format2()} blur=${state.blurScore.format2()} motion=${state.motionScore.format2()} light=${state.lightingScore.format2()}",
                color = Color(0xFFE0E0E0),
                style = MaterialTheme.typography.bodySmall,
            )
            state.poseGuidanceHint?.let {
                Text(text = "Hint: $it", color = Color(0xFFFFE082), style = MaterialTheme.typography.bodySmall)
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
            Text("Calculating finger size", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Using the best frames from all guided angles.", color = Color(0xFFD0D0D0), style = MaterialTheme.typography.bodyMedium)
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
            Text("Measurement result", style = MaterialTheme.typography.headlineMedium)
            if (result == null) {
                Text("No result produced.", style = MaterialTheme.typography.bodyLarge)
            } else {
                Text("Suggested ring size: ${result.suggestedRingSizeLabel}", style = MaterialTheme.typography.titleLarge)
                Text("Width: ${result.fingerWidthMm.format2()} mm")
                Text("Thickness: ${result.fingerThicknessMm.format2()} mm")
                Text("Circumference: ${result.estimatedCircumferenceMm.format2()} mm")
                Text("Equivalent diameter: ${result.equivalentDiameterMm.format2()} mm")
                Text("Confidence: ${result.confidenceScore.format2()}")
                if (result.warnings.isNotEmpty()) {
                    Text(
                        "Warnings: ${result.warnings.joinToString()}",
                        color = Color(0xFF8A4B08),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onDone) { Text("Done") }
        }
    }
}

private fun Float.format2(): String = "%.2f".format(this)

private fun Double.format2(): String = "%.2f".format(this)
