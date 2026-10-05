package app.gamenative.ui.screen.wow

import android.content.Context
import android.os.Build
import java.io.File

enum class WowGpuProfileId(val prefValue: String) {
    AUTO("auto"),
    ADRENO_A8XX("adrenoA8xx"),
}

data class WowGpuProfile(
    val id: WowGpuProfileId,
    val title: String,
    val shortTitle: String,
    val driverVersion: String,
    val driverAsset: String,
    val screenSize: String,
    val envVars: String,
    val focusNote: String,
)

data class WowGpuDetection(
    val profile: WowGpuProfile,
    val detectedLabel: String,
    val evidence: String,
    val automatic: Boolean,
)

object WowGpuProfiles {
    private const val PREFS = "wow_forever"
    private const val KEY_PROFILE_OVERRIDE = "gpu_profile_override"

    val ADRENO_A8XX = WowGpuProfile(
        id = WowGpuProfileId.ADRENO_A8XX,
        title = "Adreno 830 / 840 · Snapdragon 8 Elite",
        shortTitle = "Adreno 830 / 840",
        driverVersion = "Turnip-V32-RP6sched",
        driverAsset = "Turnip-V32-RP6sched-A8xx.zip",
        screenSize = "1280x720",
        envVars = "WRAPPER_MAX_IMAGE_COUNT=0 ZINK_DESCRIPTORS=lazy ZINK_DEBUG=compact,deck_emu MESA_SHADER_CACHE_DISABLE=false MESA_SHADER_CACHE_MAX_SIZE=512MB mesa_glthread=true WINEESYNC=0 MESA_VK_WSI_PRESENT_MODE=mailbox TU_DEBUG=noconform,sysmem VKD3D_SHADER_MODEL=6_0 PULSE_LATENCY_MSEC=144",
        focusNote = "SteamFlow A8xx profile using the patched Gen8 V32 Turnip driver. Confirmed on Adreno 830 and Adreno 840 setups.",
    )

    fun loadOverride(context: Context): WowGpuProfileId {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PROFILE_OVERRIDE, WowGpuProfileId.AUTO.prefValue)
        return WowGpuProfileId.entries.firstOrNull { it.prefValue == value } ?: WowGpuProfileId.AUTO
    }

    fun saveOverride(context: Context, id: WowGpuProfileId) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROFILE_OVERRIDE, id.prefValue)
            .apply()
    }

    fun resolve(context: Context, override: WowGpuProfileId = loadOverride(context)): WowGpuDetection {
        if (override == WowGpuProfileId.ADRENO_A8XX) {
            return WowGpuDetection(ADRENO_A8XX, "Manual override", "SteamFlow A8xx profile selected", false)
        }
        return autoDetect()
    }

    private fun autoDetect(): WowGpuDetection {
        val evidence = linkedSetOf<String>()

        readFile("/sys/class/kgsl/kgsl-3d0/gpu_model")?.let { evidence += "KGSL: $it" }
        readFile("/sys/class/kgsl/kgsl-3d0/device/gpu_model")?.let { evidence += "KGSL device: $it" }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { Build.SOC_MODEL }.getOrNull()?.takeIf { it.isNotBlank() }?.let {
                evidence += "SoC: $it"
            }
        }

        listOf(
            "Model: ${Build.MODEL}",
            "Device: ${Build.DEVICE}",
            "Board: ${Build.BOARD}",
            "Hardware: ${Build.HARDWARE}",
        ).forEach { evidence += it }

        getProp("ro.soc.model")?.let { evidence += "ro.soc.model: $it" }
        getProp("ro.board.platform")?.let { evidence += "ro.board.platform: $it" }
        getProp("ro.hardware.vulkan")?.let { evidence += "ro.hardware.vulkan: $it" }

        val blob = evidence.joinToString(" | ").lowercase()

        if (listOf("adreno 840", "adreno (tm) 840", "sm8850").any { blob.contains(it) }) {
            return WowGpuDetection(ADRENO_A8XX, "Adreno 840", evidence.joinToString("\n"), true)
        }

        if (listOf("adreno 830", "adreno (tm) 830", "sm8750").any { blob.contains(it) }) {
            return WowGpuDetection(ADRENO_A8XX, "Adreno 830", evidence.joinToString("\n"), true)
        }

        return WowGpuDetection(
            profile = ADRENO_A8XX,
            detectedLabel = "Unverified GPU · A8xx profile",
            evidence = evidence.joinToString("\n"),
            automatic = true,
        )
    }

    private fun readFile(path: String): String? =
        runCatching { File(path).takeIf { it.isFile }?.readText()?.trim() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    private fun getProp(name: String): String? =
        runCatching {
            ProcessBuilder("getprop", name)
                .redirectErrorStream(true)
                .start()
                .inputStream
                .bufferedReader()
                .use { it.readText().trim() }
        }.getOrNull()?.takeIf { it.isNotBlank() }
}
