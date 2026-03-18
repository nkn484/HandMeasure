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
    val ringSizeTable: RingSizeTable = RingSizeTable.sampleUsLike(),
    val lensFacing: LensFacing = LensFacing.BACK,
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
