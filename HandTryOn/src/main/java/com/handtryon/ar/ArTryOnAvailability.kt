package com.handtryon.ar

import android.content.Context
import com.google.ar.core.ArCoreApk

enum class ArTryOnAvailabilityStatus {
    SupportedInstalled,
    SupportedNeedsInstall,
    Unsupported,
    Unknown,
}

data class ArTryOnAvailability(
    val status: ArTryOnAvailabilityStatus,
    val isUsableNow: Boolean,
) {
    companion object {
        fun from(context: Context): ArTryOnAvailability {
            val availability = ArCoreApk.getInstance().checkAvailability(context)
            val status =
                when (availability) {
                    ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArTryOnAvailabilityStatus.SupportedInstalled
                    ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
                    ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED,
                    -> ArTryOnAvailabilityStatus.SupportedNeedsInstall
                    ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> ArTryOnAvailabilityStatus.Unsupported
                    else -> ArTryOnAvailabilityStatus.Unknown
                }
            return ArTryOnAvailability(
                status = status,
                isUsableNow = status == ArTryOnAvailabilityStatus.SupportedInstalled,
            )
        }
    }
}
