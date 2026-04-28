package com.handmeasure.api

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class HandMeasureConfig(
    val targetFinger: TargetFinger = TargetFinger.RING,
    val debugOverlayEnabled: Boolean = false,
    val debugExportEnabled: Boolean = false,
    val debugReplayInputPath: String? = null,
    val qualityThresholds: QualityThresholds = QualityThresholds(),
    val sanityLimits: MeasurementSanityLimits = MeasurementSanityLimits(),
    val ringSizeTable: RingSizeTable = RingSizeTable.sampleUsLike(),
    val lensFacing: LensFacing = LensFacing.BACK,
    val protocol: CaptureProtocol = CaptureProtocol.DORSAL_V1,
) : Parcelable

@Parcelize
data class QualityThresholds(
    val autoCaptureScore: Float = 0.84f,
    val bestCandidateProgressScore: Float = 0.56f,
    val handMinScore: Float = 0.45f,
    val cardMinScore: Float = 0.45f,
    val lightingMinScore: Float = 0.35f,
    val blurMinScore: Float = 0.35f,
    val motionMinScore: Float = 0.35f,
) : Parcelable

@Parcelize
data class MeasurementSanityLimits(
    val enabled: Boolean = true,
    val minWidthMm: Double = 12.0,
    val maxWidthMm: Double = 26.0,
    val minThicknessMm: Double = 10.0,
    val maxThicknessMm: Double = 24.0,
    val minCircumferenceMm: Double = 40.0,
    val maxCircumferenceMm: Double = 78.0,
    val minEquivalentDiameterMm: Double = 12.0,
    val maxEquivalentDiameterMm: Double = 25.0,
    val extremeMultiplier: Double = 1.6,
    val fallbackWidthMm: Double = 18.0,
    val fallbackThicknessMm: Double = 14.0,
    val maxConfidenceWhenAdjusted: Float = 0.35f,
) : Parcelable

@Parcelize
enum class TargetFinger : Parcelable {
    THUMB,
    INDEX,
    MIDDLE,
    RING,
    LITTLE,
}

@Parcelize
enum class LensFacing : Parcelable {
    BACK,
    FRONT,
}

@Parcelize
enum class CaptureProtocol : Parcelable {
    DORSAL_V1,
    PALMAR_V1,
}
