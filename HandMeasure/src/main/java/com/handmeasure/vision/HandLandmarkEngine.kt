package com.handmeasure.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker.HandLandmarkerOptions

interface HandLandmarkEngine {
    fun detect(bitmap: Bitmap): HandDetection?
}

class MediaPipeHandLandmarkEngine(
    context: Context,
    private val modelAssetPath: String = "hand_landmarker.task",
) : HandLandmarkEngine, AutoCloseable {
    private val appContext = context.applicationContext
    private val handLandmarker: HandLandmarker? = buildLandmarker()

    override fun detect(bitmap: Bitmap): HandDetection? {
        val landmarker = handLandmarker ?: return null
        return try {
            val result = landmarker.detect(BitmapImageBuilder(bitmap).build())
            val handednessList = result.handednesses().firstOrNull()
            val handedness = handednessList?.firstOrNull()?.categoryName() ?: "Unknown"
            val handScore = handednessList?.firstOrNull()?.score() ?: 0f
            val image = result.landmarks().firstOrNull() ?: return null
            val world = result.worldLandmarks().firstOrNull().orEmpty()
            HandDetection(
                imageLandmarks =
                    image.map { item ->
                        Landmark2D(
                            x = item.x() * bitmap.width,
                            y = item.y() * bitmap.height,
                            z = item.z(),
                        )
                    },
                worldLandmarks =
                    world.map { item ->
                        Landmark3D(
                            x = item.x(),
                            y = item.y(),
                            z = item.z(),
                        )
                    },
                handedness = handedness,
                confidence = handScore.coerceIn(0f, 1f),
                detectionConfidence = handScore.coerceIn(0f, 1f),
                presenceConfidence = if (image.size >= 21) 1f else (image.size / 21f).coerceIn(0f, 1f),
                trackingConfidence = handScore.coerceIn(0f, 1f),
            )
        } catch (error: Throwable) {
            Log.w("HandLandmarkEngine", "MediaPipe detection failed: ${error.message}")
            null
        }
    }

    override fun close() {
        handLandmarker?.close()
    }

    private fun buildLandmarker(): HandLandmarker? =
        try {
            val options =
                HandLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(modelAssetPath).build())
                    .setMinHandDetectionConfidence(0.35f)
                    .setMinHandPresenceConfidence(0.35f)
                    .setMinTrackingConfidence(0.35f)
                    .setNumHands(1)
                    .build()
            HandLandmarker.createFromOptions(appContext, options)
        } catch (error: Throwable) {
            Log.w("HandLandmarkEngine", "Failed to create HandLandmarker: ${error.message}")
            null
        }
}
