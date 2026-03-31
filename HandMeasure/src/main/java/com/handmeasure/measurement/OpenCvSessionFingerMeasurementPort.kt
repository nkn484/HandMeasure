package com.handmeasure.measurement

import android.graphics.Bitmap
import com.handmeasure.api.TargetFinger
import com.handmeasure.vision.HandDetection
import com.handmeasure.core.session.SessionFingerMeasurement
import com.handmeasure.core.session.SessionFingerMeasurementPort
import com.handmeasure.core.session.SessionFingerMeasurementRequest
import com.handmeasure.core.session.SessionScale
import com.handmeasure.core.measurement.WidthMeasurementSource as CoreWidthMeasurementSource

internal class OpenCvSessionFingerMeasurementPort(
    private val executor: OpenCvFingerMeasurementExecutor = OpenCvFingerMeasurementEngineExecutor(),
    private val mapper: OpenCvSessionFingerMeasurementMapper = OpenCvSessionFingerMeasurementMapper(),
) : SessionFingerMeasurementPort<Bitmap, HandDetection, TargetFinger> {
    override fun measureVisibleWidth(request: SessionFingerMeasurementRequest<Bitmap, HandDetection, TargetFinger>): SessionFingerMeasurement =
        executor.execute(
            OpenCvFingerMeasurementRequest(
                frame = request.frame,
                hand = request.hand,
                targetFinger = request.targetFinger,
                scale = mapper.toMetricScale(request.scale),
            ),
        ).let(mapper::toCoreMeasurement)
}

internal data class OpenCvFingerMeasurementRequest(
    val frame: Bitmap,
    val hand: HandDetection,
    val targetFinger: TargetFinger,
    val scale: MetricScale,
)

internal fun interface OpenCvFingerMeasurementExecutor {
    fun execute(request: OpenCvFingerMeasurementRequest): FingerWidthMeasurement
}

internal class OpenCvFingerMeasurementMapper {
    fun toMetricScale(scale: SessionScale): MetricScale =
        MetricScale(
            mmPerPxX = scale.mmPerPxX,
            mmPerPxY = scale.mmPerPxY,
        )

    fun toCoreMeasurement(measurement: FingerWidthMeasurement): SessionFingerMeasurement =
        SessionFingerMeasurement(
            widthPx = measurement.widthPx,
            widthMm = measurement.widthMm,
            usedFallback = measurement.usedFallback,
            source = measurement.source.toCoreWidthMeasurementSource(),
            validSamples = measurement.validSamples,
            widthVarianceMm = measurement.widthVarianceMm,
            sampledWidthsMm = measurement.sampledWidthsMm,
        )

    private fun WidthMeasurementSource.toCoreWidthMeasurementSource(): CoreWidthMeasurementSource =
        when (this) {
            WidthMeasurementSource.EDGE_PROFILE -> CoreWidthMeasurementSource.EDGE_PROFILE
            WidthMeasurementSource.LANDMARK_HEURISTIC -> CoreWidthMeasurementSource.LANDMARK_HEURISTIC
            WidthMeasurementSource.DEFAULT_HEURISTIC -> CoreWidthMeasurementSource.DEFAULT_HEURISTIC
        }
}

internal class OpenCvFingerMeasurementEngineExecutor(
    private val engine: FingerMeasurementEngine = FingerMeasurementEngine(),
) : OpenCvFingerMeasurementExecutor {
    override fun execute(request: OpenCvFingerMeasurementRequest): FingerWidthMeasurement =
        engine.measureVisibleWidth(
            bitmap = request.frame,
            handDetection = request.hand,
            targetFinger = request.targetFinger,
            scale = request.scale,
        )
}
