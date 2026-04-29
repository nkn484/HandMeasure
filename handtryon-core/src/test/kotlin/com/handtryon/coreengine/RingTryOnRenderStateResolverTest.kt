package com.handtryon.coreengine

import com.google.common.truth.Truth.assertThat
import com.handtryon.coreengine.model.RingFingerPose
import com.handtryon.coreengine.model.RingFitSource
import com.handtryon.coreengine.model.RingFitState
import com.handtryon.coreengine.model.TryOnInputQuality
import com.handtryon.coreengine.model.TryOnRenderPass
import com.handtryon.coreengine.model.TryOnUpdateAction
import com.handtryon.coreengine.model.TryOnPoint2
import com.handtryon.coreengine.model.TryOnVec2
import org.junit.Test

class RingTryOnRenderStateResolverTest {
    @Test
    fun resolve_mapsFingerPoseToCameraRelativeTransform() {
        val state =
            RingTryOnRenderStateResolver().resolve(
                fingerPose = pose(),
                fitState = fit(),
                quality =
                    TryOnInputQuality(
                        measurementUsable = true,
                        landmarkUsable = true,
                        measurementConfidence = 0.8f,
                        landmarkConfidence = 0.86f,
                        usedLastGoodAnchor = false,
                        qualityScore = 0.78f,
                    ),
                frameWidth = 1080,
                frameHeight = 1920,
            )

        assertThat(state.ringTransform.positionMeters.x).isWithin(0.001f).of(0f)
        assertThat(state.ringTransform.positionMeters.z).isEqualTo(-0.42f)
        assertThat(state.ringTransform.rotationDegrees.x).isEqualTo(90f)
        assertThat(state.fingerOccluder).isNotNull()
        assertThat(state.fingerOccluder!!.depthMeters).isWithin(0.001f).of(0.422f)
        assertThat(state.renderPasses).containsExactly(TryOnRenderPass.FingerDepthPrepass, TryOnRenderPass.RingModel).inOrder()
        assertThat(state.visualQa!!.passesBasicGate).isTrue()
    }

    @Test
    fun resolve_removesRenderPassesWhenRuntimeRequestsHide() {
        val state =
            RingTryOnRenderStateResolver().resolve(
                fingerPose = pose(),
                fitState = fit(),
                quality =
                    TryOnInputQuality(
                        measurementUsable = true,
                        landmarkUsable = true,
                        measurementConfidence = 0.8f,
                        landmarkConfidence = 0.86f,
                        usedLastGoodAnchor = false,
                        qualityScore = 0.78f,
                        updateAction = TryOnUpdateAction.Hide,
                    ),
                frameWidth = 1080,
                frameHeight = 1920,
            )

        assertThat(state.renderPasses).isEmpty()
    }

    private fun pose(): RingFingerPose =
        RingFingerPose(
            centerPx = TryOnPoint2(540f, 960f),
            occluderStartPx = TryOnPoint2(540f, 860f),
            occluderEndPx = TryOnPoint2(540f, 1040f),
            tangentPx = TryOnVec2(0f, 1f),
            normalHintPx = TryOnVec2(1f, 0f),
            rotationDegrees = 90f,
            rollDegrees = 0f,
            fingerWidthPx = 72f,
            confidence = 0.86f,
        )

    private fun fit(): RingFitState =
        RingFitState(
            ringOuterDiameterMm = 20.4f,
            ringInnerDiameterMm = 18f,
            modelWidthMm = 20.4f,
            targetWidthPx = 72f,
            depthMeters = 0.42f,
            modelScale = 1f,
            confidence = 0.72f,
            source = RingFitSource.Measured,
        )
}
