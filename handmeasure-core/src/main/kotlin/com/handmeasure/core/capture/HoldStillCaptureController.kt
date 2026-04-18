package com.handmeasure.core.capture

enum class HoldStillState {
    SEARCHING,
    CANDIDATE,
    LOCKED,
    CAPTURED,
}

data class HoldStillInput<KeyT>(
    val key: KeyT?,
    val qualityScore: Float,
    val motionScore: Float,
    val isBucketStable: Boolean,
    val timestampMs: Long,
)

data class HoldStillDecision<KeyT>(
    val state: HoldStillState,
    val commitKey: KeyT? = null,
    val stableFrames: Int = 0,
)

class HoldStillCaptureController<KeyT>(
    private val candidateMinScore: Float,
    private val lockMinScore: Float,
    private val stableMotionMinScore: Float,
    private val minStableFrames: Int = 3,
    private val minStableDurationMs: Long = 320L,
) {
    private var activeKey: KeyT? = null
    private var state: HoldStillState = HoldStillState.SEARCHING
    private var stableFrames: Int = 0
    private var candidateStartAtMs: Long = 0L
    private val capturedKeys = mutableSetOf<KeyT>()

    fun reset() {
        activeKey = null
        state = HoldStillState.SEARCHING
        stableFrames = 0
        candidateStartAtMs = 0L
        capturedKeys.clear()
    }

    fun clearCapturedKey(key: KeyT) {
        capturedKeys.remove(key)
        if (activeKey == key) {
            activeKey = null
            state = HoldStillState.SEARCHING
            stableFrames = 0
            candidateStartAtMs = 0L
        }
    }

    fun markCaptured(key: KeyT) {
        capturedKeys += key
        if (activeKey == key) {
            state = HoldStillState.CAPTURED
        }
    }

    fun evaluate(input: HoldStillInput<KeyT>): HoldStillDecision<KeyT> {
        val key = input.key
        if (key == null) {
            resetActiveCandidate()
            return HoldStillDecision(state = HoldStillState.SEARCHING)
        }

        if (capturedKeys.contains(key)) {
            activeKey = key
            state = HoldStillState.CAPTURED
            return HoldStillDecision(state = state, stableFrames = stableFrames)
        }

        if (activeKey != key) {
            activeKey = key
            state = HoldStillState.SEARCHING
            stableFrames = 0
            candidateStartAtMs = 0L
        }

        val stableFrame =
            input.isBucketStable &&
                input.motionScore >= stableMotionMinScore &&
                input.qualityScore >= candidateMinScore
        if (!stableFrame) {
            resetActiveCandidate(keepKey = true)
            return HoldStillDecision(state = state, stableFrames = stableFrames)
        }

        if (state == HoldStillState.SEARCHING) {
            state = HoldStillState.CANDIDATE
            candidateStartAtMs = input.timestampMs
            stableFrames = 0
        }
        stableFrames += 1

        val stableDurationMs = input.timestampMs - candidateStartAtMs
        if (stableFrames >= minStableFrames &&
            stableDurationMs >= minStableDurationMs &&
            input.qualityScore >= lockMinScore
        ) {
            state = HoldStillState.LOCKED
            return HoldStillDecision(
                state = state,
                commitKey = key,
                stableFrames = stableFrames,
            )
        }

        return HoldStillDecision(
            state = state,
            stableFrames = stableFrames,
        )
    }

    private fun resetActiveCandidate(keepKey: Boolean = false) {
        if (!keepKey) {
            activeKey = null
        }
        state = HoldStillState.SEARCHING
        stableFrames = 0
        candidateStartAtMs = 0L
    }
}
