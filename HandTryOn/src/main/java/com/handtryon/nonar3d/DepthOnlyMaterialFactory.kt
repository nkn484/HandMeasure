package com.handtryon.nonar3d

import androidx.annotation.ColorInt
import io.github.sceneview.loaders.MaterialLoader

class DepthOnlyMaterialFactory(
    @ColorInt private val transparentColor: Int = 0x00000000,
) {
    fun create(materialLoader: MaterialLoader) =
        materialLoader.createColorInstance(
            transparentColor,
            MATERIAL_METALLIC,
            MATERIAL_ROUGHNESS,
            MATERIAL_REFLECTANCE,
        )

    private companion object {
        const val MATERIAL_METALLIC = 0f
        const val MATERIAL_ROUGHNESS = 1f
        const val MATERIAL_REFLECTANCE = 0f
    }
}
