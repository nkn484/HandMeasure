package com.handmeasure.sample.tryon.model

import com.handmeasure.api.HandMeasureResult
import com.handtryon.domain.MeasurementSnapshot
import java.util.Locale

data class TryOnDemoHandoff(
    val snapshot: MeasurementSnapshot,
    val sourceLabel: String,
    val summary: String,
)

fun resolveDemoHandoff(
    result: HandMeasureResult?,
    allowSimulatedFallback: Boolean,
): TryOnDemoHandoff? =
    when {
        result != null -> handoffFromHandMeasureResult(result)
        allowSimulatedFallback -> sampleTryOnDemoHandoff()
        else -> null
    }

fun handoffFromHandMeasureResult(result: HandMeasureResult): TryOnDemoHandoff {
    val snapshot =
        MeasurementSnapshot(
            equivalentDiameterMm = result.equivalentDiameterMm.toFloat(),
            fingerWidthMm = result.fingerWidthMm.toFloat(),
            confidence = result.confidenceScore,
            mmPerPx = result.debugMetadata?.mmPerPxX?.toFloat(),
            usable = result.confidenceScore >= MIN_USABLE_MEASUREMENT_CONFIDENCE,
        )
    return TryOnDemoHandoff(
        snapshot = snapshot,
        sourceLabel = "Kết quả HandMeasure trực tiếp",
        summary =
            String.format(
                Locale.US,
                "Handoff: size live %s, đường kính %.2f mm, độ tin cậy %.2f",
                result.suggestedRingSizeLabel,
                result.equivalentDiameterMm,
                result.confidenceScore,
            ),
    )
}

fun sampleTryOnDemoHandoff(): TryOnDemoHandoff {
    val snapshot =
        MeasurementSnapshot(
            equivalentDiameterMm = 16.20f,
            fingerWidthMm = 18.00f,
            confidence = 0.75f,
            mmPerPx = null,
            usable = true,
        )
    return TryOnDemoHandoff(
        snapshot = snapshot,
        sourceLabel = "Handoff mô phỏng",
        summary = "Handoff: mẫu mô phỏng (đường kính 16.20 mm, độ tin cậy 0.75)",
    )
}

private const val MIN_USABLE_MEASUREMENT_CONFIDENCE = 0.30f
