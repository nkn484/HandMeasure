package com.handtryon.core

import com.handtryon.domain.FingerAnchor
import com.handtryon.domain.HandPoseSnapshot
import com.handtryon.domain.MeasurementSnapshot

interface HandPoseProvider {
    fun latestPose(): HandPoseSnapshot?
}

interface FingerAnchorProvider {
    fun createAnchor(pose: HandPoseSnapshot): FingerAnchor?
}

interface OptionalMeasurementProvider {
    fun latestMeasurement(): MeasurementSnapshot?
}
