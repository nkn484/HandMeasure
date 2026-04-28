package com.handtryon.data

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.handtryon.domain.GlbAssetSummary
import com.handtryon.domain.GlbBoundsMm
import com.handtryon.domain.IntBounds
import com.handtryon.domain.NormalizedAssetMetadata
import com.handtryon.domain.PointF
import com.handtryon.domain.RingAssetSource
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
    fun loadOverlayBitmap(source: RingAssetSource): Bitmap? {
        val overlayPath = source.overlayAssetPath ?: return null
        return assetManager.open(overlayPath).use { stream ->
            BitmapFactory.decodeStream(stream)
                ?: error("Cannot decode overlay bitmap: $overlayPath")
        }
    }

    fun loadMetadata(source: RingAssetSource): NormalizedAssetMetadata? {
        val metadataPath = source.metadataAssetPath ?: return null
        val content =
            assetManager.open(metadataPath).bufferedReader().use { reader ->
                reader.readText()
        }
        return parseMetadata(content)
    }

    fun loadGlbSummary(source: RingAssetSource): GlbAssetSummary? {
        val modelPath = source.modelAssetPath ?: return null
        val bytes =
            assetManager.open(modelPath).use { stream ->
                stream.readBytes()
            }
        return parseGlbSummary(bytes = bytes, modelPath = modelPath)
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

    private fun parseGlbSummary(
        bytes: ByteArray,
        modelPath: String,
    ): GlbAssetSummary {
        require(bytes.size >= GLB_MIN_BYTES) { "Invalid GLB (too small): $modelPath" }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buffer.int
        require(magic == GLB_MAGIC) { "Invalid GLB magic for $modelPath" }

        val glbVersion = buffer.int
        val totalLength = buffer.int
        require(totalLength in GLB_MIN_BYTES..bytes.size) { "Invalid GLB length for $modelPath" }

        val jsonChunkLength = buffer.int
        val jsonChunkType = buffer.int
        require(jsonChunkType == CHUNK_TYPE_JSON) { "GLB first chunk is not JSON: $modelPath" }

        val jsonStart = GLB_HEADER_BYTES + CHUNK_HEADER_BYTES
        val jsonEnd = jsonStart + jsonChunkLength
        require(jsonEnd in (jsonStart + 1)..bytes.size) { "GLB JSON chunk exceeds file length: $modelPath" }

        val jsonText = bytes.copyOfRange(jsonStart, jsonEnd).toString(Charsets.UTF_8).trimEnd('\u0000')
        val root = JSONObject(jsonText)

        val boundsMm = parseEstimatedBoundsMm(root)
        val sceneUnits = root.optJSONArray("scenes")?.optJSONObject(0)?.optJSONObject("extras")?.optString("units")
        val notes = mutableListOf<String>()
        sceneUnits?.let { unitString ->
            if (unitString.contains("meter", ignoreCase = true)) {
                notes += "scene_units_meter"
            }
        }

        return GlbAssetSummary(
            modelAssetPath = modelPath,
            glbVersion = glbVersion,
            gltfVersion = root.optJSONObject("asset")?.optString("version"),
            generator = root.optJSONObject("asset")?.optString("generator"),
            meshCount = root.optJSONArray("meshes")?.length() ?: 0,
            materialCount = root.optJSONArray("materials")?.length() ?: 0,
            nodeCount = root.optJSONArray("nodes")?.length() ?: 0,
            estimatedBoundsMm = boundsMm,
            notes = notes,
        )
    }

    private fun parseEstimatedBoundsMm(root: JSONObject): GlbBoundsMm? {
        val meshes = root.optJSONArray("meshes") ?: return null
        val accessors = root.optJSONArray("accessors") ?: return null

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        var found = false

        for (meshIndex in 0 until meshes.length()) {
            val mesh = meshes.optJSONObject(meshIndex) ?: continue
            val primitives = mesh.optJSONArray("primitives") ?: continue
            for (primitiveIndex in 0 until primitives.length()) {
                val primitive = primitives.optJSONObject(primitiveIndex) ?: continue
                val attributes = primitive.optJSONObject("attributes") ?: continue
                val positionAccessorIndex = attributes.optInt("POSITION", -1)
                if (positionAccessorIndex < 0 || positionAccessorIndex >= accessors.length()) continue
                val accessor = accessors.optJSONObject(positionAccessorIndex) ?: continue
                val minArray = accessor.optJSONArray("min") ?: continue
                val maxArray = accessor.optJSONArray("max") ?: continue
                if (minArray.length() < 3 || maxArray.length() < 3) continue

                val localMinX = minArray.optDouble(0).toFloat()
                val localMinY = minArray.optDouble(1).toFloat()
                val localMinZ = minArray.optDouble(2).toFloat()
                val localMaxX = maxArray.optDouble(0).toFloat()
                val localMaxY = maxArray.optDouble(1).toFloat()
                val localMaxZ = maxArray.optDouble(2).toFloat()

                minX = kotlin.math.min(minX, localMinX)
                minY = kotlin.math.min(minY, localMinY)
                minZ = kotlin.math.min(minZ, localMinZ)
                maxX = kotlin.math.max(maxX, localMaxX)
                maxY = kotlin.math.max(maxY, localMaxY)
                maxZ = kotlin.math.max(maxZ, localMaxZ)
                found = true
            }
        }

        if (!found) return null
        return GlbBoundsMm(
            x = (maxX - minX).coerceAtLeast(0f) * METERS_TO_MM,
            y = (maxY - minY).coerceAtLeast(0f) * METERS_TO_MM,
            z = (maxZ - minZ).coerceAtLeast(0f) * METERS_TO_MM,
        )
    }

    private companion object {
        const val GLB_HEADER_BYTES = 12
        const val CHUNK_HEADER_BYTES = 8
        const val GLB_MIN_BYTES = GLB_HEADER_BYTES + CHUNK_HEADER_BYTES + 2
        const val METERS_TO_MM = 1000f
        const val GLB_MAGIC = 0x46546C67
        const val CHUNK_TYPE_JSON = 0x4E4F534A
    }
}
