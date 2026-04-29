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
        sourceLabel = "Live HandMeasure result",
        summary =
            String.format(
                Locale.US,
                "Handoff: live size %s, diameter %.2f mm, confidence %.2f",
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
        sourceLabel = "Simulated handoff",
        summary = "Handoff: simulated sample (diameter 16.20 mm, confidence 0.75)",
    )
}

private const val MIN_USABLE_MEASUREMENT_CONFIDENCE = 0.30f
