package com.handtryon.coreengine

import com.handtryon.coreengine.model.RingFingerPose
import com.handtryon.coreengine.model.RingFingerPoseRejectReason
import com.handtryon.coreengine.model.RingFingerPoseSolveResult
import com.handtryon.coreengine.model.TryOnHandPoseSnapshot
import com.handtryon.coreengine.pose.RingFingerPoseSolverConfig
import com.handtryon.coreengine.pose.RingFingerPoseSolver as PoseRingFingerPoseSolver

class RingFingerPoseSolver(
    config: RingFingerPoseSolverConfig = RingFingerPoseSolverConfig(),
) {
    private val delegate = PoseRingFingerPoseSolver(config)

    fun solve(handPose: TryOnHandPoseSnapshot?): RingFingerPose? = delegate.solve(handPose)

    fun rejectReason(handPose: TryOnHandPoseSnapshot?): RingFingerPoseRejectReason? = delegate.rejectReason(handPose)

    fun evaluate(handPose: TryOnHandPoseSnapshot?): RingFingerPoseSolveResult = delegate.evaluate(handPose)
}
