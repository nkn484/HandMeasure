package com.handtryon.nonar3d
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.handtryon.domain.GlbAssetSummary
import com.handtryon.domain.HandPoseSnapshot
import com.handtryon.domain.MeasurementSnapshot
import com.handtryon.domain.RingPlacement
import com.handtryon.domain.TryOnTrackingState
import com.handtryon.domain.TryOnUpdateAction
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
    glbSummary: GlbAssetSummary?,
    placement: RingPlacement?,
    frameWidth: Int,
    frameHeight: Int,
    qualityScore: Float,
    trackingState: TryOnTrackingState,
    updateAction: TryOnUpdateAction,
    handPose: HandPoseSnapshot? = null,
    measurement: MeasurementSnapshot? = null,
    enableFingerOccluder: Boolean = false,
    modifier: Modifier = Modifier,
    onRendererError: (Throwable) -> Unit = {},
) {
    val engine = rememberEngine()
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
    val placementMapper = remember { NonAr3dPlacementMapper() }
    val fingerPoseSolver = remember { RingFingerPoseSolver() }
    val occluderMeshFactory = remember { FingerOccluderMeshFactory() }
    val depthOnlyMaterialFactory = remember { DepthOnlyMaterialFactory() }
    val occluderNodeFactory = remember { FingerOccluderNodeFactory() }
    var ringNode by remember { mutableStateOf<ModelNode?>(null) }
    var occluderNode by remember { mutableStateOf<GeometryNode?>(null) }
    var ringFingerPose by remember { mutableStateOf<RingFingerPose3D?>(null) }
    var fingerOccluderMesh by remember { mutableStateOf<FingerOccluderMesh?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            ringNode = null
            occluderNode = null
        }
    }

    LaunchedEffect(modelLoader, modelAssetPath) {
        childNodes.clear()
        ringNode = null
        if (modelAssetPath.isNullOrBlank()) return@LaunchedEffect

        val node =
            runCatching {
                createRingNode(modelLoader = modelLoader, modelAssetPath = modelAssetPath)
            }.onFailure(onRendererError).getOrNull()

        if (node != null) {
            ringNode = node
            childNodes += node
        }
    }

    LaunchedEffect(enableFingerOccluder, handPose, measurement, glbSummary, frameWidth, frameHeight) {
        if (!enableFingerOccluder) {
            ringFingerPose = null
            fingerOccluderMesh = null
            occluderNode?.isVisible = false
            return@LaunchedEffect
        }

        val pose = fingerPoseSolver.solve(handPose = handPose, measurement = measurement, glbSummary = glbSummary)
        ringFingerPose = pose
        fingerOccluderMesh =
            pose?.let {
                occluderMeshFactory.create(
                    pose = it,
                    frameWidth = frameWidth,
                    frameHeight = frameHeight,
                )
            }
    }

    LaunchedEffect(enableFingerOccluder, materialLoader, fingerOccluderMesh) {
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
        if (existingNode == null) {
            val material = depthOnlyMaterialFactory.create(materialLoader)
            val node =
                runCatching {
                    occluderNodeFactory.create(
                        engine = engine,
                        mesh = mesh,
                        materialInstance = material,
                    )
                }.onFailure(onRendererError).getOrNull()

            if (node != null) {
                occluderNode = node
                childNodes.add(0, node)
            }
        } else {
            runCatching {
                occluderNodeFactory.update(existingNode, mesh)
            }.onFailure(onRendererError)
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
        val poseConfidence = ringFingerPose?.confidence ?: qualityScore
        node.isVisible =
            currentPlacement != null &&
            poseConfidence >= MIN_RENDER_QUALITY &&
            updateAction != TryOnUpdateAction.Hide

        if (currentPlacement != null) {
            val transform =
                placementMapper.map(
                    placement = currentPlacement,
                    frameWidth = frameWidth,
                    frameHeight = frameHeight,
                    glbSummary = glbSummary,
                )
            node.position = Float3(transform.xMeters, transform.yMeters, transform.zMeters)
            node.rotation = Float3(ringFingerPose?.rollDegrees ?: 0f, 0f, -transform.rollDegrees)
            node.scale = Float3(transform.scale, transform.scale, transform.scale)
            node.setPriority(RING_PRIORITY)
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

private suspend fun createRingNode(
    modelLoader: ModelLoader,
    modelAssetPath: String,
): ModelNode {
    val modelInstance =
        modelLoader.loadModelInstance(modelAssetPath)
            ?: error("ModelLoader returned null for $modelAssetPath")

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

private data class NonAr3dRingTransform(
    val xMeters: Float,
    val yMeters: Float,
    val zMeters: Float,
    val scale: Float,
    val rollDegrees: Float,
)

private class NonAr3dPlacementMapper(
    private val defaultDepthMeters: Float = 0.42f,
    private val viewportWidthAtDepthMeters: Float = 0.32f,
) {
    fun map(
        placement: RingPlacement,
        frameWidth: Int,
        frameHeight: Int,
        glbSummary: GlbAssetSummary?,
    ): NonAr3dRingTransform {
        val safeWidth = frameWidth.coerceAtLeast(1).toFloat()
        val safeHeight = frameHeight.coerceAtLeast(1).toFloat()
        val normalizedX = (placement.centerX / safeWidth - 0.5f).coerceIn(-0.5f, 0.5f)
        val normalizedY = (placement.centerY / safeHeight - 0.5f).coerceIn(-0.5f, 0.5f)
        val modelWidthMeters = glbSummary?.estimatedBoundsMm?.x?.takeIf { it > 0f }?.div(1000f) ?: DEFAULT_RING_WIDTH_METERS
        val targetWidthMeters = (placement.ringWidthPx / safeWidth) * viewportWidthAtDepthMeters

        return NonAr3dRingTransform(
            xMeters = normalizedX * viewportWidthAtDepthMeters,
            yMeters = -normalizedY * viewportWidthAtDepthMeters * (safeHeight / safeWidth),
            zMeters = -defaultDepthMeters,
            scale = (targetWidthMeters / modelWidthMeters).coerceIn(0.2f, 4.0f),
            rollDegrees = placement.rotationDegrees,
        )
    }

    private companion object {
        const val DEFAULT_RING_WIDTH_METERS = 0.0204f
    }
}

private const val MIN_RENDER_QUALITY = 0.18f
private const val RING_PRIORITY = 4
