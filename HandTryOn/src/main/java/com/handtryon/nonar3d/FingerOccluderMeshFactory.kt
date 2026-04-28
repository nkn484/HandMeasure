package com.handtryon.nonar3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class NonAr3dVec3(
    val x: Float,
    val y: Float,
    val z: Float,
) {
    fun normalized(): NonAr3dVec3 {
        val length = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-4f)
        return NonAr3dVec3(x / length, y / length, z / length)
    }
}

data class FingerOccluderVertex(
    val position: NonAr3dVec3,
    val normal: NonAr3dVec3,
)

data class FingerOccluderMesh(
    val vertices: List<FingerOccluderVertex>,
    val indices: List<Int>,
    val radiusMeters: Float,
    val lengthMeters: Float,
)

class FingerOccluderMeshFactory(
    private val sideCount: Int = DEFAULT_SIDE_COUNT,
    private val defaultDepthMeters: Float = DEFAULT_DEPTH_METERS,
    private val viewportWidthAtDepthMeters: Float = VIEWPORT_WIDTH_AT_DEPTH_METERS,
    private val radiusScale: Float = DEFAULT_RADIUS_SCALE,
) {
    fun create(
        pose: RingFingerPose3D,
        frameWidth: Int,
        frameHeight: Int,
    ): FingerOccluderMesh {
        val safeWidth = frameWidth.coerceAtLeast(1).toFloat()
        val safeHeight = frameHeight.coerceAtLeast(1).toFloat()
        val start = project(pose.occluderStartPx, safeWidth, safeHeight)
        val end = project(pose.occluderEndPx, safeWidth, safeHeight)
        val axis = NonAr3dVec3(end.x - start.x, end.y - start.y, end.z - start.z)
        val length = sqrt(axis.x * axis.x + axis.y * axis.y + axis.z * axis.z).coerceAtLeast(1e-4f)
        val tangent = axis.normalized()
        val radius = ((pose.fingerWidthPx / safeWidth) * viewportWidthAtDepthMeters * 0.5f * radiusScale).coerceAtLeast(0.0025f)
        val normalA = NonAr3dVec3(-tangent.y, tangent.x, 0f).normalized()
        val normalB =
            NonAr3dVec3(
                tangent.y * normalA.z - tangent.z * normalA.y,
                tangent.z * normalA.x - tangent.x * normalA.z,
                tangent.x * normalA.y - tangent.y * normalA.x,
            ).normalized()
        val vertices = ArrayList<FingerOccluderVertex>(sideCount * 2)
        val indices = ArrayList<Int>(sideCount * 6)

        for (side in 0 until sideCount) {
            val angle = (side.toDouble() / sideCount.toDouble()) * PI * 2.0
            val radial =
                NonAr3dVec3(
                    x = normalA.x * cos(angle).toFloat() + normalB.x * sin(angle).toFloat(),
                    y = normalA.y * cos(angle).toFloat() + normalB.y * sin(angle).toFloat(),
                    z = normalA.z * cos(angle).toFloat() + normalB.z * sin(angle).toFloat(),
                ).normalized()

            vertices +=
                FingerOccluderVertex(
                    position = start.offset(radial, radius),
                    normal = radial,
                )
            vertices +=
                FingerOccluderVertex(
                    position = end.offset(radial, radius),
                    normal = radial,
                )
        }

        for (side in 0 until sideCount) {
            val next = (side + 1) % sideCount
            val start0 = side * 2
            val end0 = start0 + 1
            val start1 = next * 2
            val end1 = start1 + 1
            indices += start0
            indices += end0
            indices += start1
            indices += start1
            indices += end0
            indices += end1
        }

        return FingerOccluderMesh(
            vertices = vertices,
            indices = indices,
            radiusMeters = radius,
            lengthMeters = length,
        )
    }

    private fun project(
        point: NonAr3dPoint2,
        frameWidth: Float,
        frameHeight: Float,
    ): NonAr3dVec3 {
        val normalizedX = (point.x / frameWidth - 0.5f).coerceIn(-0.5f, 0.5f)
        val normalizedY = (point.y / frameHeight - 0.5f).coerceIn(-0.5f, 0.5f)
        return NonAr3dVec3(
            x = normalizedX * viewportWidthAtDepthMeters,
            y = -normalizedY * viewportWidthAtDepthMeters * (frameHeight / frameWidth),
            z = -defaultDepthMeters,
        )
    }

    private fun NonAr3dVec3.offset(
        normal: NonAr3dVec3,
        radius: Float,
    ): NonAr3dVec3 =
        NonAr3dVec3(
            x = x + normal.x * radius,
            y = y + normal.y * radius,
            z = z + normal.z * radius,
        )

    private companion object {
        const val DEFAULT_SIDE_COUNT = 24
        const val DEFAULT_DEPTH_METERS = 0.42f
        const val VIEWPORT_WIDTH_AT_DEPTH_METERS = 0.32f
        const val DEFAULT_RADIUS_SCALE = 0.56f
    }
}
