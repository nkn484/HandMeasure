package com.handmeasure.coordinator

import android.graphics.Bitmap
import com.handmeasure.api.TargetFinger
import com.handmeasure.measurement.MetricScale
import com.handmeasure.vision.HandDetection
import com.handmeasure.core.session.SessionFingerMeasurementPort

internal typealias AndroidFingerMeasurementPort =
    SessionFingerMeasurementPort<Bitmap, HandDetection, TargetFinger, MetricScale>
