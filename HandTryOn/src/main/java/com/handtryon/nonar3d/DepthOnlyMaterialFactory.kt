package com.handtryon.nonar3d

import androidx.annotation.ColorInt
import com.google.android.filament.MaterialInstance
import io.github.sceneview.loaders.MaterialLoader

class DepthOnlyMaterialFactory(
    @ColorInt private val transparentColor: Int = 0x00000000,
) {
    fun create(materialLoader: MaterialLoader): MaterialInstance =
        runCatching {
            materialLoader.assets.open(DEPTH_ONLY_MATERIAL_ASSET).close()
            materialLoader.createInstance(materialLoader.createMaterial(DEPTH_ONLY_MATERIAL_ASSET))
        }.getOrElse {
            createTransparentFallback(materialLoader)
        }

    private fun createTransparentFallback(materialLoader: MaterialLoader): MaterialInstance =
        materialLoader.createColorInstance(
            transparentColor,
            MATERIAL_METALLIC,
            MATERIAL_ROUGHNESS,
            MATERIAL_REFLECTANCE,
        )

    private companion object {
        const val DEPTH_ONLY_MATERIAL_ASSET = "materials/depth_only.filamat"
        const val MATERIAL_METALLIC = 0f
        const val MATERIAL_ROUGHNESS = 1f
        const val MATERIAL_REFLECTANCE = 0f
    }
}
