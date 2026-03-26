package com.handtryon.data

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.handtryon.domain.IntBounds
import com.handtryon.domain.NormalizedAssetMetadata
import com.handtryon.domain.PointF
import com.handtryon.domain.RingAssetSource
import org.json.JSONObject

interface RingAssetSourceProvider {
    fun getAssetSource(): RingAssetSource
}

class StaticRingAssetSourceProvider(
    private val asset: RingAssetSource,
) : RingAssetSourceProvider {
    override fun getAssetSource(): RingAssetSource = asset
}

class RingAssetLoader(
    private val assetManager: AssetManager,
) {
    fun loadOverlayBitmap(source: RingAssetSource): Bitmap =
        assetManager.open(source.overlayAssetPath).use { stream ->
            BitmapFactory.decodeStream(stream)
                ?: error("Cannot decode overlay bitmap: ${source.overlayAssetPath}")
        }

    fun loadMetadata(source: RingAssetSource): NormalizedAssetMetadata? {
        val metadataPath = source.metadataAssetPath ?: return null
        val content =
            assetManager.open(metadataPath).bufferedReader().use { reader ->
                reader.readText()
            }
        return parseMetadata(content)
    }

    private fun parseMetadata(jsonText: String): NormalizedAssetMetadata {
        val root = JSONObject(jsonText)
        val contentBounds = root.getJSONObject("contentBounds")
        val alphaBounds = root.getJSONObject("alphaBounds")
        val visualCenter = root.getJSONObject("visualCenter")
        return NormalizedAssetMetadata(
            sourceFile = root.optString("sourceFile"),
            contentBounds =
                IntBounds(
                    left = contentBounds.getInt("left"),
                    top = contentBounds.getInt("top"),
                    right = contentBounds.getInt("right"),
                    bottom = contentBounds.getInt("bottom"),
                ),
            visualCenter =
                PointF(
                    x = visualCenter.getDouble("x").toFloat(),
                    y = visualCenter.getDouble("y").toFloat(),
                ),
            alphaBounds =
                IntBounds(
                    left = alphaBounds.getInt("left"),
                    top = alphaBounds.getInt("top"),
                    right = alphaBounds.getInt("right"),
                    bottom = alphaBounds.getInt("bottom"),
                ),
            recommendedWidthRatio = root.optDouble("recommendedWidthRatio", 0.16).toFloat(),
            rotationBiasDeg = root.optDouble("rotationBiasDeg", 0.0).toFloat(),
            assetQualityScore = root.optDouble("assetQualityScore", 0.0).toFloat(),
            backgroundRemovalConfidence = root.optDouble("backgroundRemovalConfidence", 0.0).toFloat(),
            notes =
                root.optJSONArray("notes")?.let { array ->
                    List(array.length()) { index -> array.getString(index) }
                }.orEmpty(),
        )
    }
}
