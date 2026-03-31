package com.handmeasure.coordinator

import android.graphics.Bitmap
import com.handmeasure.api.TargetFinger
import com.handmeasure.measurement.MetricScale
import com.handmeasure.vision.CardDetection
import com.handmeasure.vision.HandDetection
import com.handmeasure.core.session.SessionFingerMeasurementPort
import com.handmeasure.core.session.SessionRuntimeAnalyzerPort
import com.handmeasure.core.session.StepRuntimeAnalysisUseCase

internal typealias AndroidFingerMeasurementPort =
    SessionFingerMeasurementPort<Bitmap, HandDetection, TargetFinger, MetricScale>

internal typealias AndroidRuntimeAnalyzerPort =
    SessionRuntimeAnalyzerPort<Bitmap, HandDetection, CardDetection, TargetFinger>

internal typealias AndroidStepRuntimeAnalysisUseCase =
    StepRuntimeAnalysisUseCase<Bitmap, HandDetection, CardDetection, TargetFinger>
