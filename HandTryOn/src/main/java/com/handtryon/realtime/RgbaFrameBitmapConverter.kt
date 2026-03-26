package com.handtryon.realtime

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

class RgbaFrameBitmapConverter {
    private var reusableBitmap: Bitmap? = null
    private var repackBuffer: ByteBuffer? = null
    private var repackBytes: ByteArray? = null

    fun acquireBitmap(image: ImageProxy): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val width = image.width
        val height = image.height
        val targetBitmap = ensureBitmap(width, height)
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val requiredBytes = width * height * 4
        val source = plane.buffer
        source.rewind()

        if (pixelStride == 4 && rowStride == width * 4) {
            targetBitmap.copyPixelsFromBuffer(source)
            source.rewind()
            return targetBitmap
        }

        val packed = ensureRepackBuffer(requiredBytes)
        packed.clear()
        val rowData = ensureRowBuffer(rowStride)
        for (row in 0 until height) {
            source.position(row * rowStride)
            source.get(rowData, 0, rowStride)
            if (pixelStride == 4) {
                packed.put(rowData, 0, width * 4)
            } else {
                var col = 0
                while (col < width) {
                    val offset = col * pixelStride
                    packed.put(rowData[offset])
                    packed.put(rowData[offset + 1])
                    packed.put(rowData[offset + 2])
                    packed.put(rowData[offset + 3])
                    col += 1
                }
            }
        }
        packed.flip()
        targetBitmap.copyPixelsFromBuffer(packed)
        return targetBitmap
    }

    private fun ensureBitmap(
        width: Int,
        height: Int,
    ): Bitmap {
        val existing = reusableBitmap
        if (existing != null && existing.width == width && existing.height == height) {
            return existing
        }
        existing?.recycle()
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { reusableBitmap = it }
    }

    private fun ensureRepackBuffer(requiredBytes: Int): ByteBuffer {
        val existing = repackBuffer
        if (existing != null && existing.capacity() >= requiredBytes) {
            return existing
        }
        return ByteBuffer.allocateDirect(requiredBytes).also { repackBuffer = it }
    }

    private fun ensureRowBuffer(rowStride: Int): ByteArray {
        val existing = repackBytes
        if (existing != null && existing.size >= rowStride) {
            return existing
        }
        return ByteArray(rowStride).also { repackBytes = it }
    }

    fun close() {
        reusableBitmap?.recycle()
        reusableBitmap = null
        repackBuffer = null
        repackBytes = null
    }
}
