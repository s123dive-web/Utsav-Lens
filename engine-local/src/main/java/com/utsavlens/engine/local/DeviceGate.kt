package com.utsavlens.engine.local

import com.utsavlens.engine.api.Readiness
import com.utsavlens.engine.api.UnsupportedReason

/** What the gate needs to know about the phone; collected by [LocalAiEngine], faked in tests. */
data class DeviceInfo(
    val totalRamBytes: Long,
    val supportedAbis: List<String>,
    val cpuCores: Int,
    val sdkInt: Int,
    val freeStorageBytes: Long,
    val socModel: String = "",
)

/**
 * Decides up front whether on-device diffusion can run, so the engine shows "Unsupported" with a
 * reason instead of crashing or being killed for memory half-way through.
 */
object DeviceGate {
    /** The fp32 UNet alone maps about 3.4 GB; phones sold as "12 GB" report roughly 11.2 GiB. */
    const val MIN_RAM_BYTES = 10L * 1024 * 1024 * 1024
    const val MIN_CORES = 8
    const val MIN_SDK = 28

    fun check(device: DeviceInfo, modelBytes: Long, modelPresent: Boolean): Readiness.Unsupported? {
        if (device.sdkInt < MIN_SDK) return Readiness.Unsupported(UnsupportedReason.ANDROID_VERSION)
        if (device.supportedAbis.none { it == "arm64-v8a" || it == "x86_64" }) {
            return Readiness.Unsupported(UnsupportedReason.CHIPSET, device.supportedAbis.firstOrNull().orEmpty())
        }
        if (device.cpuCores < MIN_CORES) {
            return Readiness.Unsupported(UnsupportedReason.CHIPSET, device.socModel.ifEmpty { "${device.cpuCores} cores" })
        }
        if (device.totalRamBytes < MIN_RAM_BYTES) {
            return Readiness.Unsupported(UnsupportedReason.LOW_RAM, "12 GB")
        }
        if (!modelPresent && device.freeStorageBytes < modelBytes + modelBytes / 8) {
            return Readiness.Unsupported(UnsupportedReason.STORAGE, "${(modelBytes + modelBytes / 8) / 1_000_000_000 + 1} GB")
        }
        return null
    }
}
