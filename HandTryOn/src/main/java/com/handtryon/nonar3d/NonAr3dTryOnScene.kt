package com.handtryon.nonar3d
import android.content.Context
import android.os.SystemClock
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
import com.handtryon.coreengine.model.TryOnRenderPass
import com.handtryon.coreengine.model.TryOnRenderState3D
import com.handtryon.domain.GlbAssetSummary
import com.handtryon.validation.RuntimeMetrics
import com.handtryon.validation.RuntimeMetricsTracker
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.Scene
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.node.GeometryNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes

@Composable
fun NonAr3dTryOnScene(
    modelAssetPath: String?,
    renderState3D: TryOnRenderState3D?,
    frameWidth: Int,
    frameHeight: Int,
    glbSummary: GlbAssetSummary? = null,
    enableFingerOccluder: Boolean = true,
    debugFingerOccluderVisible: Boolean = false,
    modifier: Modifier = Modifier,
    onRendererError: (Throwable) -> Unit = {},
    onTelemetryUpdated: (RuntimeMetrics) -> Unit = {},
) {
    val engine = rememberEngine()
    val context = LocalContext.current
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val childNodes = rememberNodes()
    val cameraNode =
        rememberCameraNode(engine) {
            position = Float3(0f, 0f, 0f)
            setProjection(52.0, 0.05f, 10.0f, com.google.android.filament.Camera.Fov.VERTICAL, 1.0)
        }
    val mainLightNode =
        rememberMainLightNode(engine) {
            intensity = 45_000f
            position = Float3(0.2f, 0.6f, 1.0f)
        }
    val metrics = remember { RuntimeMetricsTracker() }
    val occluderMeshFactory = remember { FingerOccluderMeshFactory() }
    val depthOnlyMaterialFactory = remember { DepthOnlyMaterialFactory() }
    val occluderNodeFactory = remember { FingerOccluderNodeFactory() }
    var ringNode by remember { mutableStateOf<ModelNode?>(null) }
    var occluderNode by remember { mutableStateOf<GeometryNode?>(null) }
    var fingerOccluderMesh by remember { mutableStateOf<FingerOccluderMesh?>(null) }
    var occluderDebugVisible by remember { mutableStateOf<Boolean?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            ringNode = null
            occluderNode = null
            occluderDebugVisible = null
        }
    }

    LaunchedEffect(modelLoader, modelAssetPath, glbSummary) {
        childNodes.clear()
        ringNode = null
        occluderNode = null
        occluderDebugVisible = null
        if (modelAssetPath.isNullOrBlank()) return@LaunchedEffect

        val node =
            runCatching {
                createRingNode(context = context, modelLoader = modelLoader, modelAssetPath = modelAssetPath, glbSummary = glbSummary)
            }.onFailure { throwable ->
                reportRendererError(
                    stage = "model",
                    throwable = throwable,
                    metrics = metrics,
                    onRendererError = onRendererError,
                    onTelemetryUpdated = onTelemetryUpdated,
                )
            }.getOrNull()

        if (node != null) {
            metrics.onNodeRecreated()
            onTelemetryUpdated(metrics.snapshot())
            ringNode = node
            childNodes += node
        }
    }

    LaunchedEffect(renderState3D) {
        metrics.onRenderStateUpdated(SystemClock.elapsedRealtime())
        onTelemetryUpdated(metrics.snapshot())
    }

    LaunchedEffect(enableFingerOccluder, renderState3D, frameWidth, frameHeight) {
        if (!enableFingerOccluder) {
            fingerOccluderMesh = null
            occluderNode?.isVisible = false
            return@LaunchedEffect
        }

        fingerOccluderMesh =
            renderState3D
                ?.takeIf { TryOnRenderPass.FingerDepthPrepass in it.renderPasses }
                ?.fingerOccluder
                ?.let { occluder ->
                    occluderMeshFactory.create(
                        state = occluder,
                        frameWidth = frameWidth,
                        frameHeight = frameHeight,
                    )
                }
    }

    LaunchedEffect(enableFingerOccluder, materialLoader, fingerOccluderMesh, debugFingerOccluderVisible) {
        if (!enableFingerOccluder) {
            occluderNode?.isVisible = false
            return@LaunchedEffect
        }

        val mesh = fingerOccluderMesh
        if (mesh == null) {
            occluderNode?.isVisible = false
            return@LaunchedEffect
        }

        val existingNode = occluderNode
        val shouldCreateNode = existingNode == null || occluderDebugVisible != debugFingerOccluderVisible
        if (shouldCreateNode) {
            existingNode?.let {
                childNodes.remove(it)
                occluderNode = null
            }
            val material =
                if (debugFingerOccluderVisible) {
                    depthOnlyMaterialFactory.createDebugVisible(materialLoader)
                } else {
                    depthOnlyMaterialFactory.create(materialLoader)
                }
            val node =
                runCatching {
                    occluderNodeFactory.create(
                        engine = engine,
                        mesh = mesh,
                        materialInstance = material,
                    )
                }.onFailure { throwable ->
                    reportRendererError(
                        stage = "finger occluder",
                        throwable = throwable,
                        metrics = metrics,
                        onRendererError = onRendererError,
                        onTelemetryUpdated = onTelemetryUpdated,
                    )
                }.getOrNull()

            if (node != null) {
                metrics.onNodeRecreated()
                onTelemetryUpdated(metrics.snapshot())
                occluderNode = node
                occluderDebugVisible = debugFingerOccluderVisible
                childNodes.add(0, node)
            }
        } else {
            runCatching {
                occluderNodeFactory.update(existingNode, mesh)
            }.onFailure { throwable ->
                reportRendererError(
                    stage = "finger occluder",
                    throwable = throwable,
                    metrics = metrics,
                    onRendererError = onRendererError,
                    onTelemetryUpdated = onTelemetryUpdated,
                )
            }
        }
    }

    LaunchedEffect(
        ringNode,
        renderState3D,
    ) {
        val node = ringNode ?: return@LaunchedEffect
        val renderState = renderState3D
        if (renderState != null) {
            node.isVisible = TryOnRenderPass.RingModel in renderState.renderPasses
            val transform = renderState.ringTransform
            node.position = Float3(transform.positionMeters.x, transform.positionMeters.y, transform.positionMeters.z)
            node.rotation = Float3(transform.rotationDegrees.x, transform.rotationDegrees.y, transform.rotationDegrees.z)
            node.scale = Float3(transform.scale.x, transform.scale.y, transform.scale.z)
            node.setPriority(RING_PRIORITY)
        } else {
            node.isVisible = false
        }
    }

    Scene(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        materialLoader = materialLoader,
        isOpaque = false,
        mainLightNode = mainLightNode,
        cameraNode = cameraNode,
        cameraManipulator = null,
        childNodes = childNodes,
    )
}

private fun reportRendererError(
    stage: String,
    throwable: Throwable,
    metrics: RuntimeMetricsTracker,
    onRendererError: (Throwable) -> Unit,
    onTelemetryUpdated: (RuntimeMetrics) -> Unit,
) {
    Log.e(TAG, "Non-AR renderer failed at $stage", throwable)
    metrics.onRendererError(stage = stage, throwable = throwable)
    onTelemetryUpdated(metrics.snapshot())
    onRendererError(NonArTryOnStageException(stage = stage, source = throwable))
}

private class NonArTryOnStageException(
    stage: String,
    source: Throwable,
) : RuntimeException("$stage: ${source.message ?: source::class.java.simpleName}", source)

private fun createRingNode(
    context: Context,
    modelLoader: ModelLoader,
    modelAssetPath: String,
    glbSummary: GlbAssetSummary?,
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
        centerOrigin = glbSummary?.pivot?.let { Float3(it.x, it.y, it.z) } ?: Float3(0f, 0f, 0f),
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

private const val RING_PRIORITY = 4
private const val TAG = "NonAr3dTryOnScene"
