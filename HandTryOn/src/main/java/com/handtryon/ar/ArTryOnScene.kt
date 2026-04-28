package com.handtryon.ar

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.ar.core.Config
import com.handtryon.domain.GlbAssetSummary
import com.handtryon.domain.HandPoseSnapshot
import com.handtryon.domain.MeasurementSnapshot
import com.handtryon.domain.RingPlacement
import com.handtryon.domain.TryOnTrackingState
import com.handtryon.domain.TryOnUpdateAction
import com.handtryon.nonar3d.DepthOnlyMaterialFactory
import com.handtryon.nonar3d.FingerOccluderMesh
import com.handtryon.nonar3d.FingerOccluderMeshFactory
import com.handtryon.nonar3d.FingerOccluderNodeFactory
import com.handtryon.nonar3d.RingFingerPose3D
import com.handtryon.nonar3d.RingFingerPoseSolver
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.rememberARCameraStream
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.node.GeometryNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes

@Composable
fun ArTryOnScene(
    modelAssetPath: String?,
    glbSummary: GlbAssetSummary?,
    placement: RingPlacement?,
    frameWidth: Int,
    frameHeight: Int,
    qualityScore: Float,
    trackingState: TryOnTrackingState,
    updateAction: TryOnUpdateAction,
    handPose: HandPoseSnapshot? = null,
    measurement: MeasurementSnapshot? = null,
    modifier: Modifier = Modifier,
    onCameraFrame: (ArCameraBitmapFrame) -> Unit = { it.bitmap.recycle() },
    onRendererError: (Throwable) -> Unit = {},
) {
    val engine = rememberEngine()
    val context = LocalContext.current
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val cameraStream = rememberARCameraStream(materialLoader)
    val childNodes = rememberNodes()
    val placementMapper = remember { ArRingPlacementMapper() }
    val frameSampler = remember { ArCoreCameraFrameSampler() }
    val fingerPoseSolver = remember { RingFingerPoseSolver() }
    val occluderMeshFactory = remember { FingerOccluderMeshFactory() }
    val depthOnlyMaterialFactory = remember { DepthOnlyMaterialFactory() }
    val occluderNodeFactory = remember { FingerOccluderNodeFactory() }
    var ringNode by remember { mutableStateOf<ModelNode?>(null) }
    var occluderNode by remember { mutableStateOf<GeometryNode?>(null) }
    var lastRenderablePlacement by remember { mutableStateOf<RingPlacement?>(null) }
    var ringFingerPose by remember { mutableStateOf<RingFingerPose3D?>(null) }
    var fingerOccluderMesh by remember { mutableStateOf<FingerOccluderMesh?>(null) }

    LaunchedEffect(cameraStream) {
        cameraStream.isDepthOcclusionEnabled = true
    }

    DisposableEffect(Unit) {
        onDispose {
            ringNode = null
            occluderNode = null
            lastRenderablePlacement = null
            ringFingerPose = null
            fingerOccluderMesh = null
        }
    }

    LaunchedEffect(modelLoader, modelAssetPath) {
        childNodes.clear()
        ringNode = null
        occluderNode = null
        if (modelAssetPath.isNullOrBlank()) return@LaunchedEffect

        val node =
            runCatching {
                createRingNode(context = context, modelLoader = modelLoader, modelAssetPath = modelAssetPath)
            }.onFailure { throwable ->
                reportRendererError(stage = "model", throwable = throwable, onRendererError = onRendererError)
            }.getOrNull()

        if (node != null) {
            ringNode = node
            childNodes += node
        }
    }

    LaunchedEffect(handPose, measurement, glbSummary) {
        ringFingerPose = fingerPoseSolver.solve(handPose = handPose, measurement = measurement, glbSummary = glbSummary)
    }

    LaunchedEffect(ringFingerPose, frameWidth, frameHeight) {
        fingerOccluderMesh =
            ringFingerPose?.let { pose ->
                occluderMeshFactory.create(
                    pose = pose,
                    frameWidth = frameWidth,
                    frameHeight = frameHeight,
                )
            }
    }

    LaunchedEffect(materialLoader, fingerOccluderMesh) {
        val mesh = fingerOccluderMesh
        if (mesh == null) {
            occluderNode?.isVisible = false
            return@LaunchedEffect
        }

        val existingNode = occluderNode
        if (existingNode == null) {
            val material = depthOnlyMaterialFactory.create(materialLoader)
            val node =
                runCatching {
                    occluderNodeFactory.create(
                        engine = engine,
                        mesh = mesh,
                        materialInstance = material,
                    )
                }.onFailure { throwable ->
                    reportRendererError(stage = "finger occluder", throwable = throwable, onRendererError = onRendererError)
                }.getOrNull()

            if (node != null) {
                occluderNode = node
                childNodes.add(0, node)
            }
        } else {
            runCatching {
                occluderNodeFactory.update(existingNode, mesh)
            }.onFailure { throwable ->
                reportRendererError(stage = "finger occluder", throwable = throwable, onRendererError = onRendererError)
            }
        }
    }

    LaunchedEffect(
        ringNode,
        placement,
        frameWidth,
        frameHeight,
        qualityScore,
        trackingState,
        updateAction,
        glbSummary,
        ringFingerPose,
        fingerOccluderMesh,
    ) {
        val node = ringNode ?: return@LaunchedEffect
        val currentPlacement = placement
        val shouldUseCurrentPlacement =
            currentPlacement != null &&
                qualityScore >= MIN_RENDER_QUALITY &&
                updateAction != TryOnUpdateAction.Hide
        val renderPlacement =
            when {
                shouldUseCurrentPlacement -> currentPlacement.also { lastRenderablePlacement = it }
                updateAction == TryOnUpdateAction.HoldLastPlacement -> lastRenderablePlacement
                updateAction == TryOnUpdateAction.FreezeScaleRotation -> lastRenderablePlacement ?: currentPlacement
                updateAction == TryOnUpdateAction.Recover -> lastRenderablePlacement ?: currentPlacement
                updateAction == TryOnUpdateAction.Hide -> lastRenderablePlacement
                else -> currentPlacement ?: lastRenderablePlacement
            }

        node.isVisible = renderPlacement != null

        if (renderPlacement != null) {
            val transform =
                placementMapper.map(
                    placement = renderPlacement,
                    frameWidth = frameWidth,
                    frameHeight = frameHeight,
                    glbSummary = glbSummary,
            )
            node.position = Float3(transform.xMeters, transform.yMeters, transform.zMeters)
            val fingerAxisDegrees = ringFingerPose?.rotationDegrees ?: transform.rollDegrees
            val fingerRollDegrees = ringFingerPose?.rollDegrees ?: 0f
            node.rotation =
                Float3(
                    RING_PLANE_PITCH_DEGREES + fingerRollDegrees,
                    0f,
                    RING_AXIS_YAW_OFFSET_DEGREES - fingerAxisDegrees,
                )
            node.scale = Float3(transform.scale, transform.scale, transform.scale)
            node.setPriority(RING_PRIORITY)
        }
    }

    ARScene(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        materialLoader = materialLoader,
        cameraStream = cameraStream,
        childNodes = childNodes,
        planeRenderer = false,
        sessionConfiguration = { session, config ->
            config.depthMode =
                if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    Config.DepthMode.AUTOMATIC
                } else if (session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY)) {
                    Config.DepthMode.RAW_DEPTH_ONLY
                } else {
                    Config.DepthMode.DISABLED
                }
            config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
        },
        onSessionFailed = { throwable ->
            reportRendererError(stage = "session", throwable = throwable, onRendererError = onRendererError)
        },
        onSessionUpdated = { _, frame ->
            runCatching {
                frameSampler.acquireBitmap(frame)?.let(onCameraFrame)
            }.onFailure { throwable ->
                reportRendererError(stage = "frame sampler", throwable = throwable, onRendererError = onRendererError)
            }
        },
    )
}

private fun reportRendererError(
    stage: String,
    throwable: Throwable,
    onRendererError: (Throwable) -> Unit,
) {
    Log.e(TAG, "AR renderer failed at $stage", throwable)
    onRendererError(ArTryOnStageException(stage = stage, source = throwable))
}

private class ArTryOnStageException(
    stage: String,
    source: Throwable,
) : RuntimeException("$stage: ${source.message ?: source::class.java.simpleName}", source)

private fun createRingNode(
    context: Context,
    modelLoader: ModelLoader,
    modelAssetPath: String,
): ModelNode {
    val assetBytes = readAssetLength(context = context, assetPath = modelAssetPath)
    val modelInstance =
        runCatching { modelLoader.createModelInstance(modelAssetPath) }
            .getOrElse { throwable ->
                throw IllegalStateException("Filament could not parse GLB asset $modelAssetPath ($assetBytes bytes)", throwable)
            }

    return ModelNode(
        modelInstance = modelInstance,
        autoAnimate = false,
        scaleToUnits = null,
        centerOrigin = Float3(0f, 0f, 0f),
    ).apply {
        isEditable = false
        isTouchable = false
        isVisible = false
    }
}

private fun readAssetLength(
    context: Context,
    assetPath: String,
): Int =
    runCatching {
        context.assets.open(assetPath).use { input ->
            input.available().takeIf { it > 0 } ?: input.readBytes().size
        }
    }.getOrElse { throwable ->
        throw IllegalStateException("Asset is not readable: $assetPath", throwable)
    }

private const val MIN_RENDER_QUALITY = 0.18f
private const val RING_PLANE_PITCH_DEGREES = 90f
private const val RING_AXIS_YAW_OFFSET_DEGREES = 90f
private const val RING_PRIORITY = 4
private const val TAG = "ArTryOnScene"
