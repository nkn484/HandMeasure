package com.handtryon.nonar3d

import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import dev.romainguy.kotlin.math.Float2
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Float4
import io.github.sceneview.geometries.Geometry
import io.github.sceneview.node.GeometryNode

class FingerOccluderNodeFactory {
    fun create(
        engine: Engine,
        mesh: FingerOccluderMesh,
        materialInstance: MaterialInstance,
    ): GeometryNode =
        GeometryNode(
            geometry = buildGeometry(engine = engine, mesh = mesh),
            materialInstance = materialInstance,
        ) {
            priority(OCCLUDER_PRIORITY)
            culling(false)
            castShadows(false)
            receiveShadows(false)
        }.apply {
            isEditable = false
            isTouchable = false
            isVisible = true
        }

    fun update(
        node: GeometryNode,
        mesh: FingerOccluderMesh,
    ) {
        node.updateGeometry(
            vertices = mesh.toGeometryVertices(),
            indices = listOf(mesh.indices),
        )
        node.isVisible = true
    }

    private fun buildGeometry(
        engine: Engine,
        mesh: FingerOccluderMesh,
    ): Geometry =
        Geometry.Builder(RenderableManager.PrimitiveType.TRIANGLES)
            .vertices(mesh.toGeometryVertices())
            .indices(mesh.indices)
            .build(engine)

    private fun FingerOccluderMesh.toGeometryVertices(): List<Geometry.Vertex> =
        vertices.map { vertex ->
            Geometry.Vertex(
                position = Float3(vertex.position.x, vertex.position.y, vertex.position.z),
                normal = Float3(vertex.normal.x, vertex.normal.y, vertex.normal.z),
                uvCoordinate = Float2(0f, 0f),
                color = Float4(0f, 0f, 0f, 0f),
            )
        }

    private companion object {
        const val OCCLUDER_PRIORITY = 0
    }
}
