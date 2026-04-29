package com.handtryon.engine

import com.handtryon.coreengine.RingTryOnRenderStateResolver
import com.handtryon.coreengine.fit.RingFitSolver
import com.handtryon.coreengine.model.RingFingerPose
import com.handtryon.coreengine.model.TryOnInputQuality
import com.handtryon.coreengine.model.TryOnMode
import com.handtryon.coreengine.model.TryOnPoint2
import com.handtryon.coreengine.model.TryOnVec2
import com.handtryon.coreengine.TryOnSessionResolverPolicy
import com.handtryon.coreengine.pose.RingFingerPoseSolver
import com.handtryon.coreengine.model.TryOnEngineResult
import com.handtryon.coreengine.model.TryOnSession
import com.handtryon.coreengine.model.toEngineResult
import com.handtryon.engine.model.TryOnEngineRequest
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

internal class TryOnEngine(
    private val resolverPolicy: TryOnSessionResolverPolicy,
    private val poseSolver: RingFingerPoseSolver = RingFingerPoseSolver(),
    private val fitSolver: RingFitSolver = RingFitSolver(),
    private val renderStateResolver: RingTryOnRenderStateResolver = RingTryOnRenderStateResolver(),
) {
    fun resolve(request: TryOnEngineRequest): TryOnEngineResult {
        val session =
            resolverPolicy.resolve(
                asset = request.asset,
                handPose = request.handPose,
                measurement = request.measurement,
                manualPlacement = request.manualPlacement,
                previousSession = request.previousSession,
                frameWidth = request.frameWidth,
                frameHeight = request.frameHeight,
                nowMs = request.nowMs,
            )
        val quality = session.toCoreQualitySnapshot()
        val renderState3D =
            resolveFingerPose(request, session)
                ?.let { fingerPose ->
                    val fitState =
                        fitSolver.solve(
                            fingerPose = fingerPose,
                            measurement = request.measurement,
                            selectedDiameterMm = request.selectedDiameterMm,
                        )
                    renderStateResolver.resolve(
                        fingerPose = fingerPose,
                        fitState = fitState,
                        quality = quality,
                        frameWidth = request.frameWidth,
                        frameHeight = request.frameHeight,
                    )
                }
        return session.toEngineResult(
            generatedAtMs = request.nowMs,
            renderState3D = renderState3D,
        )
    }

    private fun resolveFingerPose(
        request: TryOnEngineRequest,
        session: TryOnSession,
    ): RingFingerPose? {
        val solvedPose = request.handPose?.let { handPose -> poseSolver.solve(handPose) }
        if (solvedPose != null) return solvedPose
        if (session.mode == TryOnMode.Manual) return null
        val anchor = session.anchor ?: return null
        val placement = session.placement
        val widthPx = anchor.fingerWidthPx.takeIf { it > 0f } ?: (placement.ringWidthPx * SYNTHETIC_FINGER_WIDTH_RATIO)
        val halfSegment = widthPx * SYNTHETIC_OCCLUDER_SEGMENT_RATIO
        val angleRadians = Math.toRadians(anchor.angleDegrees.toDouble())
        val dx = (cos(angleRadians) * halfSegment).toFloat()
        val dy = (sin(angleRadians) * halfSegment).toFloat()
        val tangentLength = max(1e-3f, kotlin.math.sqrt((dx * dx) + (dy * dy)))
        val tangentX = dx / tangentLength
        val tangentY = dy / tangentLength
        return RingFingerPose(
            centerPx = TryOnPoint2(anchor.centerX, anchor.centerY),
            occluderStartPx = TryOnPoint2(anchor.centerX - dx, anchor.centerY - dy),
            occluderEndPx = TryOnPoint2(anchor.centerX + dx, anchor.centerY + dy),
            tangentPx = TryOnVec2(tangentX, tangentY),
            normalHintPx = TryOnVec2(-tangentY, tangentX),
            rotationDegrees = placement.rotationDegrees,
            rollDegrees = 0f,
            fingerWidthPx = widthPx,
            confidence = anchor.confidence.coerceIn(0f, 1f),
        )
    }

    private fun TryOnSession.toCoreQualitySnapshot(): TryOnInputQuality =
        TryOnInputQuality(
            measurementUsable = quality.measurementUsable,
            landmarkUsable = quality.landmarkUsable,
            measurementConfidence = quality.measurementConfidence,
            landmarkConfidence = quality.landmarkConfidence,
            usedLastGoodAnchor = quality.usedLastGoodAnchor,
            trackingState = quality.trackingState,
            qualityScore = quality.qualityScore,
            updateAction = quality.updateAction,
            hints = quality.hints,
        )

    private companion object {
        const val SYNTHETIC_OCCLUDER_SEGMENT_RATIO = 0.75f
        const val SYNTHETIC_FINGER_WIDTH_RATIO = 1.2f
    }
}
