package com.handmeasure.sample.measure.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.handmeasure.api.HandMeasureConfig
import com.handmeasure.api.HandMeasureContract
import com.handmeasure.api.HandMeasureResult
import com.handmeasure.api.HandMeasureWarning
import com.handmeasure.api.QualityThresholds
import com.handmeasure.api.TargetFinger

@Composable
fun HandMeasureDemoScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var latestSummary by rememberSaveable { mutableStateOf("Chưa có kết quả.") }
    var latestWarnings by rememberSaveable { mutableStateOf("Cảnh báo: không có") }

    val launcher =
        rememberLauncherForActivityResult(HandMeasureContract()) { result ->
            if (result == null) {
                latestSummary = "Không nhận được kết quả (đã hủy hoặc thoát sớm)."
                latestWarnings = "Cảnh báo: không áp dụng"
            } else {
                latestSummary = result.toSummaryText()
                latestWarnings =
                    if (result.warnings.isEmpty()) {
                        "Cảnh báo: không có"
                    } else {
                        "Cảnh báo: ${result.warnings.joinToString { it.toVietnameseLabel() }}"
                    }
            }
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Demo HandMeasure",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Sử dụng cấu hình demo ổn định để đo ngón áp út.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = { launcher.launch(defaultDemoMeasureConfig()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Bắt đầu đo tay")
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Quay lại màn hình demo")
        }
        Text(text = latestSummary, style = MaterialTheme.typography.bodyMedium)
        Text(text = latestWarnings, style = MaterialTheme.typography.bodySmall)
    }
}

fun defaultDemoMeasureConfig(): HandMeasureConfig =
    HandMeasureConfig(
        targetFinger = TargetFinger.RING,
        qualityThresholds =
            QualityThresholds(
                autoCaptureScore = 0.80f,
                bestCandidateProgressScore = 0.52f,
            ),
    )

private fun HandMeasureResult.toSummaryText(): String =
    "Size ${suggestedRingSizeLabel}, đường kính ${"%.2f".format(equivalentDiameterMm)} mm, độ tin cậy ${"%.2f".format(confidenceScore)}"

private fun HandMeasureWarning.toVietnameseLabel(): String =
    when (this) {
        HandMeasureWarning.BEST_EFFORT_ESTIMATE -> "Ước tính tốt nhất có thể"
        HandMeasureWarning.LOW_CARD_CONFIDENCE -> "Độ tin cậy thẻ chuẩn thấp"
        HandMeasureWarning.LOW_POSE_CONFIDENCE -> "Độ tin cậy tư thế thấp"
        HandMeasureWarning.LOW_LIGHTING -> "Ánh sáng yếu"
        HandMeasureWarning.HIGH_MOTION -> "Chuyển động cao"
        HandMeasureWarning.HIGH_BLUR -> "Hình ảnh bị mờ"
        HandMeasureWarning.THICKNESS_ESTIMATED_FROM_WEAK_ANGLES -> "Độ dày ước tính từ góc chụp yếu"
        HandMeasureWarning.CALIBRATION_WEAK -> "Hiệu chuẩn yếu"
        HandMeasureWarning.LOW_RESULT_RELIABILITY -> "Độ tin cậy kết quả thấp"
    }
