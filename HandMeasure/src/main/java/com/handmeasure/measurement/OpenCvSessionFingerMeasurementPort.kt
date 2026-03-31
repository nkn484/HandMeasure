package com.handmeasure.measurement

import android.graphics.Bitmap
import com.handmeasure.api.TargetFinger
import com.handmeasure.vision.HandDetection
import com.handmeasure.core.session.SessionFingerMeasurement
import com.handmeasure.core.session.SessionFingerMeasurementPort
import com.handmeasure.core.measurement.WidthMeasurementSource as CoreWidthMeasurementSource

internal class OpenCvSessionFingerMeasurementPort(
    private val engine: FingerMeasurementEngine = FingerMeasurementEngine(),
) : SessionFingerMeasurementPort<Bitmap, HandDetection, TargetFinger, MetricScale> {
    override fun measureVisibleWidth(
        frame: Bitmap,
        hand: HandDetection,
        targetFinger: TargetFinger,
        scale: MetricScale,
    ): SessionFingerMeasurement = engine.measureVisibleWidth(frame, hand, targetFinger, scale).toCoreMeasurement()

    private fun FingerWidthMeasurement.toCoreMeasurement(): SessionFingerMeasurement =
        SessionFingerMeasurement(
            widthPx = widthPx,
            widthMm = widthMm,
            usedFallback = usedFallback,
            source = source.toCoreWidthMeasurementSource(),
            validSamples = validSamples,
            widthVarianceMm = widthVarianceMm,
            sampledWidthsMm = sampledWidthsMm,
        )

    private fun WidthMeasurementSource.toCoreWidthMeasurementSource(): CoreWidthMeasurementSource =
        when (this) {
            WidthMeasurementSource.EDGE_PROFILE -> CoreWidthMeasurementSource.EDGE_PROFILE
            WidthMeasurementSource.LANDMARK_HEURISTIC -> CoreWidthMeasurementSource.LANDMARK_HEURISTIC
            WidthMeasurementSource.DEFAULT_HEURISTIC -> CoreWidthMeasurementSource.DEFAULT_HEURISTIC
        }
}
