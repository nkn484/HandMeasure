package com.handmeasure.coordinator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.handmeasure.vision.CardDetection
import com.handmeasure.vision.HandDetection
import java.io.ByteArrayOutputStream

internal class DebugFrameAnnotator {
    fun encodeAnnotatedJpeg(
        bitmap: Bitmap,
        hand: HandDetection?,
        card: CardDetection?,
    ): ByteArray {
        val annotated = annotateBitmap(bitmap, hand, card)
        return try {
            ByteArrayOutputStream().use { output ->
                annotated.compress(Bitmap.CompressFormat.JPEG, 90, output)
                output.toByteArray()
            }
        } finally {
            annotated.recycle()
        }
    }

    private fun annotateBitmap(
        bitmap: Bitmap,
        hand: HandDetection?,
        card: CardDetection?,
    ): Bitmap {
        val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val handPaint =
            Paint().apply {
                color = Color.CYAN
                style = Paint.Style.FILL
                strokeWidth = 3f
            }
        hand?.imageLandmarks?.forEach { landmark ->
            canvas.drawCircle(landmark.x, landmark.y, 5f, handPaint)
        }
        val cardPaint =
            Paint().apply {
                color = Color.YELLOW
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
        val corners = card?.corners.orEmpty()
        if (corners.size == 4) {
            for (index in corners.indices) {
                val a = corners[index]
                val b = corners[(index + 1) % corners.size]
                canvas.drawLine(a.first, a.second, b.first, b.second, cardPaint)
            }
        }
        return out
    }
}
