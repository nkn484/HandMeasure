package com.handtryon.validation

data class RuntimeMetrics(
    val analyzedFrames: Long,
    val detectionUpdates: Long,
    val avgDetectionMs: Double,
    val detectorLatencyMs: Double = avgDetectionMs,
    val avgUpdateIntervalMs: Double,
    val renderStateUpdates: Long = 0L,
    val renderStateUpdateHz: Double = 0.0,
    val nodeRecreateCount: Long = 0L,
    val rendererErrorStage: String? = null,
    val rendererErrorMessage: String? = null,
    val approxMemoryDeltaKb: Long,
)

class RuntimeMetricsTracker {
    private var analyzedFrames = 0L
    private var detectionUpdates = 0L
    private var totalDetectionNs = 0L
    private var totalUpdateIntervalMs = 0L
    private var lastUpdateTs = 0L
    private var renderStateUpdates = 0L
    private var firstRenderStateUpdateTs = 0L
    private var lastRenderStateUpdateTs = 0L
    private var nodeRecreateCount = 0L
    private var rendererErrorStage: String? = null
    private var rendererErrorMessage: String? = null
    private var baselineMemory = usedMemoryKb()

    fun onFrameAnalyzed() {
        analyzedFrames += 1
    }

    fun onDetectionUpdate(durationNs: Long, timestampMs: Long) {
        detectionUpdates += 1
        totalDetectionNs += durationNs
        if (lastUpdateTs != 0L) {
            totalUpdateIntervalMs += (timestampMs - lastUpdateTs).coerceAtLeast(0L)
        }
        lastUpdateTs = timestampMs
    }

    fun onRenderStateUpdated(timestampMs: Long) {
        renderStateUpdates += 1
        if (firstRenderStateUpdateTs == 0L) firstRenderStateUpdateTs = timestampMs
        lastRenderStateUpdateTs = timestampMs
    }

    fun onNodeRecreated() {
        nodeRecreateCount += 1
    }

    fun onRendererError(
        stage: String,
        throwable: Throwable,
    ) {
        rendererErrorStage = stage
        rendererErrorMessage = throwable.message ?: throwable::class.java.simpleName
    }

    fun snapshot(): RuntimeMetrics {
        val avgDetectionMs = if (detectionUpdates == 0L) 0.0 else totalDetectionNs / detectionUpdates / 1_000_000.0
        val avgInterval = if (detectionUpdates <= 1L) 0.0 else totalUpdateIntervalMs / (detectionUpdates - 1).toDouble()
        val renderStateDurationSec =
            if (renderStateUpdates <= 1L) {
                0.0
            } else {
                (lastRenderStateUpdateTs - firstRenderStateUpdateTs).coerceAtLeast(1L) / 1000.0
            }
        val renderStateUpdateHz =
            if (renderStateDurationSec == 0.0) {
                0.0
            } else {
                (renderStateUpdates - 1L) / renderStateDurationSec
            }
        return RuntimeMetrics(
            analyzedFrames = analyzedFrames,
            detectionUpdates = detectionUpdates,
            avgDetectionMs = avgDetectionMs,
            detectorLatencyMs = avgDetectionMs,
            avgUpdateIntervalMs = avgInterval,
            renderStateUpdates = renderStateUpdates,
            renderStateUpdateHz = renderStateUpdateHz,
            nodeRecreateCount = nodeRecreateCount,
            rendererErrorStage = rendererErrorStage,
            rendererErrorMessage = rendererErrorMessage,
            approxMemoryDeltaKb = usedMemoryKb() - baselineMemory,
        )
    }

    private fun usedMemoryKb(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / 1024L
    }
}
