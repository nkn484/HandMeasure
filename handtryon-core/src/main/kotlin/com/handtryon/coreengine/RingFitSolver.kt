package com.handtryon.coreengine

import com.handtryon.coreengine.fit.RingFitSolver as FitRingFitSolver
import com.handtryon.coreengine.fit.RingFitSolverConfig
import com.handtryon.coreengine.model.RingFingerPose
import com.handtryon.coreengine.model.RingFitState
import com.handtryon.coreengine.model.TryOnMeasurementSnapshot

class RingFitSolver(
    defaultRingDiameterMm: Float = RingFitSolverConfig().defaultRingDiameterMm,
    defaultDepthMeters: Float = RingFitSolverConfig().defaultDepthMeters,
    virtualFocalPx: Float = RingFitSolverConfig().virtualFocalPx,
) {
    private val delegate =
        FitRingFitSolver(
            RingFitSolverConfig(
                defaultRingDiameterMm = defaultRingDiameterMm,
                defaultDepthMeters = defaultDepthMeters,
                virtualFocalPx = virtualFocalPx,
            ),
        )

    fun solve(
        fingerPose: RingFingerPose,
        measurement: TryOnMeasurementSnapshot?,
        selectedDiameterMm: Float? = null,
        modelWidthMm: Float? = null,
    ): RingFitState =
        delegate.solve(
            fingerPose = fingerPose,
            measurement = measurement,
            selectedDiameterMm = selectedDiameterMm,
            modelWidthMm = modelWidthMm,
        )
}
