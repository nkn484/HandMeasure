package com.handtryon.validation

data class RuntimeMetrics(
    val analyzedFrames: Long,
    val detectionUpdates: Long,
    val avgDetectionMs: Double,
    val avgUpdateIntervalMs: Double,
    val approxMemoryDeltaKb: Long,
)

class RuntimeMetricsTracker {
    private var analyzedFrames = 0L
    private var detectionUpdates = 0L
    private var totalDetectionNs = 0L
    private var totalUpdateIntervalMs = 0L
    private var lastUpdateTs = 0L
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

    fun snapshot(): RuntimeMetrics {
        val avgDetectionMs = if (detectionUpdates == 0L) 0.0 else totalDetectionNs / detectionUpdates / 1_000_000.0
        val avgInterval = if (detectionUpdates <= 1L) 0.0 else totalUpdateIntervalMs / (detectionUpdates - 1).toDouble()
        return RuntimeMetrics(
            analyzedFrames = analyzedFrames,
            detectionUpdates = detectionUpdates,
            avgDetectionMs = avgDetectionMs,
            avgUpdateIntervalMs = avgInterval,
            approxMemoryDeltaKb = usedMemoryKb() - baselineMemory,
        )
    }

    private fun usedMemoryKb(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / 1024L
    }
}
