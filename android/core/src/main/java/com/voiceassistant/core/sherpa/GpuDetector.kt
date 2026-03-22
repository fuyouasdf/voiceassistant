package com.voiceassistant.core.sherpa

import android.os.Build
import timber.log.Timber

/**
 * Detects available compute providers (GPU/NPU/CPU) on Android
 */
object GpuDetector {

    /**
     * Get the best available provider for ONNX inference
     * Priority: GPU > NPU > CPU
     */
    fun getBestProvider(): String {
        val provider = when {
            isGpuAvailable() -> "gpu"
            isNpuAvailable() -> "npu"
            else -> "cpu"
        }
        Timber.d("Selected provider: $provider (GPU=${isGpuAvailable()}, NPU=${isNpuAvailable()})")
        return provider
    }

    /**
     * Check if GPU is available via OpenGL ES extensions
     */
    private fun isGpuAvailable(): Boolean {
        return try {
            // Most Android devices with GPU support have certain OpenGL extensions
            // This is a simple heuristic check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Check for common GPU vendors that support ONNX GPU inference
                val gpuVendor = android.os.Build.HARDWARE.lowercase()
                val knownGpuVendors = listOf("adreno", "mali", "powervr", "tegra", "apple", "samsung", "huawei")
                knownGpuVendors.any { gpuVendor.contains(it) }
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "GPU detection failed")
            false
        }
    }

    /**
     * Check if NPU is available
     * NPU is typically found on Huawei (HiSilicon), MediaTek, Qualcomm AI Engine
     */
    private fun isNpuAvailable(): Boolean {
        return try {
            // Check for NPU-related system properties
            val npuProp = getSystemProperty("hw.npu.present") ?: ""
            if (npuProp == "true") return true

            // Check for HiAI (Huawei) NPU
            val hwNpuFile = "/dev/hw_npu"
            if (java.io.File(hwNpuFile).exists()) return true

            // Check MediaTek NPU
            val mediatekNpu = getSystemProperty("ro.hardware.npu") ?: ""
            if (mediatekNpu.isNotEmpty()) return true

            // Check Qualcomm AI Engine
            val qualcommAi = getSystemProperty("ro.qualcomm.ai") ?: ""
            if (qualcommAi.isNotEmpty()) return true

            false
        } catch (e: Exception) {
            Timber.e(e, "NPU detection failed")
            false
        }
    }

    @Suppress("SameParameterValue")
    private fun getSystemProperty(key: String): String? {
        return try {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val get = systemProperties.getMethod("get", String::class.java)
            get.invoke(systemProperties, key) as? String
        } catch (e: Exception) {
            null
        }
    }
}
