package com.handtryon.ar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.google.ar.core.Config
import com.handtryon.domain.GlbAssetSummary
import com.handtryon.domain.RingPlacement
import com.handtryon.domain.TryOnTrackingState
import com.handtryon.domain.TryOnUpdateAction
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ARScene
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
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
    modifier: Modifier = Modifier,
    onCameraFrame: (ArCameraBitmapFrame) -> Unit = { it.bitmap.recycle() },
    onRendererError: (Throwable) -> Unit = {},
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val childNodes = rememberNodes()
    val placementMapper = remember { ArRingPlacementMapper() }
    val frameSampler = remember { ArCoreCameraFrameSampler() }
    var ringNode by remember { mutableStateOf<ModelNode?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            ringNode = null
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

    LaunchedEffect(ringNode, placement, frameWidth, frameHeight, qualityScore, trackingState, updateAction, glbSummary) {
        val node = ringNode ?: return@LaunchedEffect
        val currentPlacement = placement
        node.isVisible =
            currentPlacement != null &&
            qualityScore >= MIN_RENDER_QUALITY &&
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
            node.rotation = Float3(0f, 0f, -transform.rollDegrees)
            node.scale = Float3(transform.scale, transform.scale, transform.scale)
        }
    }

    ARScene(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        childNodes = childNodes,
        planeRenderer = false,
        sessionConfiguration = { session, config ->
            config.depthMode =
                if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    Config.DepthMode.AUTOMATIC
                } else {
                    Config.DepthMode.DISABLED
                }
            config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
        },
        onSessionFailed = onRendererError,
        onSessionUpdated = { _, frame ->
            frameSampler.acquireBitmap(frame)?.let(onCameraFrame)
        },
    )
}

private fun createRingNode(
    modelLoader: ModelLoader,
    modelAssetPath: String,
): ModelNode =
    ModelNode(
        modelInstance = modelLoader.createModelInstance(modelAssetPath),
        autoAnimate = false,
        scaleToUnits = null,
        centerOrigin = Float3(0f, 0f, 0f),
    ).apply {
        isEditable = false
        isTouchable = false
        isVisible = false
    }

private const val MIN_RENDER_QUALITY = 0.18f
