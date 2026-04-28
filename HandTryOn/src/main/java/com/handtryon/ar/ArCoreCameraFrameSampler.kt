package com.handtryon.ar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.os.SystemClock
import com.google.ar.core.Frame
import com.google.ar.core.exceptions.NotYetAvailableException
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class ArCoreCameraFrameSampler(
    private val minFrameIntervalMs: Long = DEFAULT_MIN_FRAME_INTERVAL_MS,
    private val jpegQuality: Int = DEFAULT_JPEG_QUALITY,
) {
    private var lastSampledAtMs: Long = 0L

    fun acquireBitmap(frame: Frame): ArCameraBitmapFrame? {
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastSampledAtMs < minFrameIntervalMs) return null

        val image =
            try {
                frame.acquireCameraImage()
            } catch (_: NotYetAvailableException) {
                return null
            }

        image.use { cameraImage ->
            if (cameraImage.format != ImageFormat.YUV_420_888) return null
            val bitmap = cameraImage.toBitmap(jpegQuality) ?: return null
            lastSampledAtMs = nowMs
            return ArCameraBitmapFrame(
                bitmap = bitmap,
                timestampMs = nowMs,
            )
        }
    }

    private companion object {
        const val DEFAULT_MIN_FRAME_INTERVAL_MS = 110L
        const val DEFAULT_JPEG_QUALITY = 72
    }
}

data class ArCameraBitmapFrame(
    val bitmap: Bitmap,
    val timestampMs: Long,
)

private fun Image.toBitmap(jpegQuality: Int): Bitmap? {
    val nv21 = toNv21()
    val output = ByteArrayOutputStream()
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    if (!yuvImage.compressToJpeg(Rect(0, 0, width, height), jpegQuality.coerceIn(1, 100), output)) {
        return null
    }
    val bytes = output.toByteArray()
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

private fun Image.toNv21(): ByteArray {
    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]
    val ySize = width * height
    val output = ByteArray(ySize + ySize / 2)

    copyLumaPlane(
        buffer = yPlane.buffer,
        width = width,
        height = height,
        rowStride = yPlane.rowStride,
        pixelStride = yPlane.pixelStride,
        output = output,
    )

    val chromaWidth = width / 2
    val chromaHeight = height / 2
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer
    for (row in 0 until chromaHeight) {
        for (col in 0 until chromaWidth) {
            val outputIndex = ySize + row * width + col * 2
            output[outputIndex] = vBuffer.get(row * vPlane.rowStride + col * vPlane.pixelStride)
            output[outputIndex + 1] = uBuffer.get(row * uPlane.rowStride + col * uPlane.pixelStride)
        }
    }

    return output
}

private fun copyLumaPlane(
    buffer: ByteBuffer,
    width: Int,
    height: Int,
    rowStride: Int,
    pixelStride: Int,
    output: ByteArray,
) {
    if (pixelStride == 1 && rowStride == width) {
        val duplicate = buffer.duplicate()
        duplicate.position(0)
        duplicate.get(output, 0, width * height)
        return
    }

    for (row in 0 until height) {
        for (col in 0 until width) {
            output[row * width + col] = buffer.get(row * rowStride + col * pixelStride)
        }
    }
}
