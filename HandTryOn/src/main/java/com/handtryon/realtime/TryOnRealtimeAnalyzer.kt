package com.handtryon.realtime

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.handtryon.validation.RuntimeMetrics
import com.handtryon.validation.RuntimeMetricsTracker

class TryOnRealtimeAnalyzer(
    private val minDetectionIntervalMs: Long = 110L,
    private val converter: RgbaFrameBitmapConverter = RgbaFrameBitmapConverter(),
    private val onDetectionFrame: (Bitmap, Long, Int) -> Unit,
    private val onMetricsUpdated: (RuntimeMetrics) -> Unit = {},
) : ImageAnalysis.Analyzer {
    private val metrics = RuntimeMetricsTracker()
    private var lastDetectionTs = 0L

    override fun analyze(image: ImageProxy) {
        val startNs = SystemClock.elapsedRealtimeNanos()
        try {
            metrics.onFrameAnalyzed()
            val bitmap = converter.acquireBitmap(image) ?: return
            val now = SystemClock.elapsedRealtime()
            if (now - lastDetectionTs < minDetectionIntervalMs) return
            lastDetectionTs = now
            onDetectionFrame(bitmap, now, image.imageInfo.rotationDegrees)
            metrics.onDetectionUpdate(durationNs = SystemClock.elapsedRealtimeNanos() - startNs, timestampMs = now)
            if (metrics.snapshot().detectionUpdates % 10L == 0L) {
                onMetricsUpdated(metrics.snapshot())
            }
        } finally {
            image.close()
        }
    }

    fun close() {
        converter.close()
    }
}
