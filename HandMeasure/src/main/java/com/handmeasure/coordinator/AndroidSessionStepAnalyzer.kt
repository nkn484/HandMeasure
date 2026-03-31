package com.handmeasure.coordinator

import android.graphics.BitmapFactory
import com.handmeasure.core.session.SessionScale
import com.handmeasure.core.session.SessionStepAnalysis
import com.handmeasure.core.session.SessionStepAnalyzer
import com.handmeasure.core.session.SessionStepCandidate
import com.handmeasure.core.session.StepRuntimeAnalysisRequest

internal class AndroidSessionStepAnalyzer(
    private val overlayEnabled: Boolean,
    private val stepRuntimeAnalysisUseCase: AndroidStepRuntimeAnalysisUseCase,
) : SessionStepAnalyzer {
    override fun analyze(
        candidate: SessionStepCandidate,
        currentScale: SessionScale,
    ): SessionStepAnalysis? {
        val bitmap = BitmapFactory.decodeByteArray(candidate.frameBytes, 0, candidate.frameBytes.size) ?: return null
        return try {
            stepRuntimeAnalysisUseCase.analyze(
                StepRuntimeAnalysisRequest(
                    step = candidate.step,
                    frame = bitmap,
                    currentScale = currentScale,
                    overlayEnabled = overlayEnabled,
                ),
            )
        } finally {
            bitmap.recycle()
        }
    }
}
