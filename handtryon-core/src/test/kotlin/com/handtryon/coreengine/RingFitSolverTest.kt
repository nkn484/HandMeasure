package com.handtryon.coreengine

import com.google.common.truth.Truth.assertThat
import com.handtryon.coreengine.model.RingFingerPose
import com.handtryon.coreengine.model.RingFitSource
import com.handtryon.coreengine.model.TryOnMeasurementSnapshot
import com.handtryon.coreengine.model.TryOnPoint2
import com.handtryon.coreengine.model.TryOnVec2
import org.junit.Test

class RingFitSolverTest {
    @Test
    fun solve_prefersUsableMeasurement() {
        val fit =
            RingFitSolver().solve(
                fingerPose = pose(),
                measurement =
                    TryOnMeasurementSnapshot(
                        equivalentDiameterMm = 19.8f,
                        fingerWidthMm = 17.4f,
                        confidence = 0.82f,
                        usable = true,
                    ),
                selectedDiameterMm = 21.2f,
                modelWidthMm = 20.4f,
            )

        assertThat(fit.source).isEqualTo(RingFitSource.Measured)
        assertThat(fit.ringOuterDiameterMm).isEqualTo(19.8f)
        assertThat(fit.modelScale).isWithin(0.001f).of(19.8f / 20.4f)
        assertThat(fit.confidence).isGreaterThan(0.6f)
    }

    @Test
    fun solve_usesSelectedSizeWhenMeasurementMissing() {
        val fit =
            RingFitSolver().solve(
                fingerPose = pose(),
                measurement = null,
                selectedDiameterMm = 21.2f,
                modelWidthMm = 20.4f,
            )

        assertThat(fit.source).isEqualTo(RingFitSource.SelectedSize)
        assertThat(fit.ringOuterDiameterMm).isEqualTo(21.2f)
    }

    @Test
    fun solve_visualEstimateKeepsRingWidthNearFingerBaseBand() {
        val fit =
            RingFitSolver().solve(
                fingerPose = pose(),
                measurement = null,
                modelWidthMm = 20.4f,
            )

        assertThat(fit.source).isEqualTo(RingFitSource.VisualEstimate)
        assertThat(fit.targetWidthPx).isWithin(0.001f).of(33.12f)
    }

    @Test
    fun solve_visualEstimateTapersWideNearCameraFinger() {
        val fit =
            RingFitSolver().solve(
                fingerPose = pose(fingerWidthPx = 268f),
                measurement = null,
                modelWidthMm = 20.4f,
            )

        assertThat(fit.targetWidthPx).isWithin(0.001f).of(78.256f)
    }

    @Test
    fun solve_visualEstimateExpandsSmallFarCameraFinger() {
        val fit =
            RingFitSolver().solve(
                fingerPose = pose(fingerWidthPx = 107f),
                measurement = null,
                modelWidthMm = 20.4f,
            )

        assertThat(fit.targetWidthPx).isWithin(0.001f).of(48.471f)
    }

    private fun pose(fingerWidthPx: Float = 72f): RingFingerPose =
        RingFingerPose(
            centerPx = TryOnPoint2(540f, 720f),
            occluderStartPx = TryOnPoint2(540f, 650f),
            occluderEndPx = TryOnPoint2(540f, 820f),
            tangentPx = TryOnVec2(0f, 1f),
            normalHintPx = TryOnVec2(1f, 0f),
            rotationDegrees = 90f,
            rollDegrees = 0f,
            fingerWidthPx = fingerWidthPx,
            confidence = 0.86f,
        )
}
