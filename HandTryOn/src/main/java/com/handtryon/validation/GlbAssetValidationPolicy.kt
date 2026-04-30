package com.handtryon.validation

import com.handtryon.domain.GlbAssetSummary
import kotlin.math.abs
import kotlin.math.max

enum class AssetValidationSeverity {
    Info,
    Warning,
    Error,
}

data class AssetValidationIssue(
    val severity: AssetValidationSeverity,
    val code: String,
    val message: String,
    val suggestedFix: String,
)

data class AssetValidationReport(
    val status: AssetValidationSeverity,
    val issues: List<AssetValidationIssue>,
) {
    val hasErrors: Boolean
        get() = issues.any { it.severity == AssetValidationSeverity.Error }
}

class GlbAssetValidationPolicy(
    private val maxFileSizeBytes: Long = 8L * 1024L * 1024L,
) {
    fun validate(summary: GlbAssetSummary?): AssetValidationReport {
        if (summary == null) {
            return AssetValidationReport(
                status = AssetValidationSeverity.Error,
                issues =
                    listOf(
                        AssetValidationIssue(
                            severity = AssetValidationSeverity.Error,
                            code = "asset.summary.missing",
                            message = "GLB summary is missing.",
                            suggestedFix = "Provide a valid GLB model asset and metadata.",
                        ),
                    ),
            )
        }
        val issues = mutableListOf<AssetValidationIssue>()
        if (summary.glbVersion != 2) {
            issues +=
                AssetValidationIssue(
                    severity = AssetValidationSeverity.Error,
                    code = "glb.version.unsupported",
                    message = "Unsupported GLB version ${summary.glbVersion}.",
                    suggestedFix = "Export asset as GLB 2.0.",
                )
        }
        if (summary.fileSizeBytes <= 0L) {
            issues +=
                AssetValidationIssue(
                    severity = AssetValidationSeverity.Warning,
                    code = "glb.filesize.unknown",
                    message = "GLB file size is unknown.",
                    suggestedFix = "Populate file size metadata during GLB summary parsing.",
                )
        } else if (summary.fileSizeBytes > maxFileSizeBytes) {
            issues +=
                AssetValidationIssue(
                    severity = AssetValidationSeverity.Error,
                    code = "glb.filesize.too_large",
                    message = "GLB file size ${summary.fileSizeBytes} exceeds budget $maxFileSizeBytes.",
                    suggestedFix = "Compress mesh/material textures and reduce geometry detail.",
                )
        }

        summary.notes
            .filter { it.startsWith("required_extension:", ignoreCase = true) }
            .map { it.substringAfter(':') }
            .filter { it !in SUPPORTED_REQUIRED_EXTENSIONS }
            .forEach { extension ->
                issues +=
                    AssetValidationIssue(
                        severity = AssetValidationSeverity.Error,
                        code = "glb.extension.unsupported",
                        message = "Unsupported required extension '$extension'.",
                        suggestedFix = "Re-export GLB without unsupported required extension.",
                    )
            }

        val bounds = summary.estimatedBoundsMm
        if (bounds == null) {
            issues +=
                AssetValidationIssue(
                    severity = AssetValidationSeverity.Warning,
                    code = "glb.bounds.missing",
                    message = "Estimated bounds are missing.",
                    suggestedFix = "Ensure mesh POSITION accessors expose min/max values.",
                )
        } else {
            val maxDim = max(bounds.x, max(bounds.y, bounds.z))
            val minDim = listOf(bounds.x, bounds.y, bounds.z).minOrNull() ?: 0f
            if (maxDim !in 5f..80f) {
                issues +=
                    AssetValidationIssue(
                        severity = AssetValidationSeverity.Error,
                        code = "glb.bounds.out_of_range",
                        message = "Estimated jewelry bounds out of range: ${bounds.x}x${bounds.y}x${bounds.z} mm.",
                        suggestedFix = "Normalize asset scale to jewelry-sized millimeter dimensions.",
                    )
            }
            if (minDim <= 0f) {
                issues +=
                    AssetValidationIssue(
                        severity = AssetValidationSeverity.Error,
                        code = "glb.bounds.invalid",
                        message = "Estimated bounds include non-positive dimension.",
                        suggestedFix = "Repair mesh geometry and export valid POSITION min/max.",
                    )
            }
        }

        val pivot = summary.pivot
        if (pivot == null) {
            issues +=
                AssetValidationIssue(
                    severity = AssetValidationSeverity.Warning,
                    code = "glb.pivot.missing",
                    message = "Pivot metadata missing.",
                    suggestedFix = "Provide tryOn pivot metadata near ring center.",
                )
        } else {
            val unitsToMeters = summary.scale.unitsToMeters.coerceAtLeast(1e-6f)
            val pivotAbsMm =
                if (pivot.units.equals("meters", ignoreCase = true)) {
                    max(abs(pivot.x), max(abs(pivot.y), abs(pivot.z))) * 1000f
                } else {
                    max(abs(pivot.x), max(abs(pivot.y), abs(pivot.z))) * unitsToMeters * 1000f
                }
            if (pivotAbsMm > 10f) {
                issues +=
                    AssetValidationIssue(
                        severity = AssetValidationSeverity.Warning,
                        code = "glb.pivot.far_from_center",
                        message = "Pivot appears far from ring center ($pivotAbsMm mm).",
                        suggestedFix = "Re-center pivot around the ring hole center.",
                    )
            }
        }

        if (summary.scale.unitsToMeters !in 0.0005f..2.0f) {
            issues +=
                AssetValidationIssue(
                    severity = AssetValidationSeverity.Error,
                    code = "glb.units.invalid",
                    message = "unitsToMeters ${summary.scale.unitsToMeters} is invalid.",
                    suggestedFix = "Provide realistic units metadata (mm/cm/m).",
                )
        }

        if (summary.materialCount <= 0 || summary.materials.isEmpty()) {
            issues +=
                AssetValidationIssue(
                    severity = AssetValidationSeverity.Error,
                    code = "glb.material.missing",
                    message = "No material metadata found.",
                    suggestedFix = "Add material profile/name for product rendering.",
                )
        }

        val status =
            when {
                issues.any { it.severity == AssetValidationSeverity.Error } -> AssetValidationSeverity.Error
                issues.any { it.severity == AssetValidationSeverity.Warning } -> AssetValidationSeverity.Warning
                else -> AssetValidationSeverity.Info
            }
        return AssetValidationReport(status = status, issues = issues)
    }

    private companion object {
        val SUPPORTED_REQUIRED_EXTENSIONS =
            setOf(
                "KHR_materials_pbrSpecularGlossiness",
                "KHR_materials_transmission",
                "KHR_materials_ior",
                "KHR_texture_transform",
            )
    }
}
