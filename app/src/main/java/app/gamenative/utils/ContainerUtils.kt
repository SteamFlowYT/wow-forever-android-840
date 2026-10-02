package app.gamenative.utils

import android.content.Context
import android.os.Build
import app.gamenative.PrefManager
import app.gamenative.data.GameSource
import app.gamenative.enums.Marker
import com.winlator.container.Container
import com.winlator.container.ContainerData
import com.winlator.container.ContainerManager
import com.winlator.core.DefaultVersion
import com.winlator.core.FileUtils
import com.winlator.core.KeyValueSet
import com.winlator.core.GPUInformation
import com.winlator.core.envvars.EnvVars
import com.winlator.core.WineRegistryEditor
import com.winlator.core.WineThemeManager
import com.winlator.winhandler.WinHandler.PreferredInputApi
import com.winlator.xenvironment.ImageFs
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

object ContainerUtils {
    data class GpuInfo(
        val deviceId: Int,
        val vendorId: Int,
        val name: String,
    )

    const val WRAPPER_TURNIP_CAPABLE = "Turnip v26.2.0 R4"
    const val WRAPPER_ADRENO_8ELITE_GEN5 = "Turnip Adreno Driver T26 (@Mr_Purple_666)"
    const val WRAPPER_ADRENO_8ELITE = "Turnip Gen8 V30"
    const val WRAPPER_ADRENO_A12 = "Turnip v26.1.0 A12 Fix"

    val wrapperDriverDefaults: List<String> =
        listOf(WRAPPER_TURNIP_CAPABLE, WRAPPER_ADRENO_8ELITE_GEN5, WRAPPER_ADRENO_8ELITE, WRAPPER_ADRENO_A12)

    fun setContainerDefaults(context: Context) {
        // Override default driver and DXVK version based on Turnip capability
        if (GPUInformation.isTurnipCapable(context)) {
            DefaultVersion.VARIANT = Container.BIONIC
            DefaultVersion.WINE_VERSION = "proton-10.0-arm64ec-2"
            DefaultVersion.DEFAULT_GRAPHICS_DRIVER = "Wrapper"
            DefaultVersion.DXVK = if (GPUInformation.isAdreno6xx(context)) "1.11.1-sarek" else "2.4.1-gplasync"
            DefaultVersion.VKD3D = "2.14.1"
            DefaultVersion.WRAPPER = WRAPPER_TURNIP_CAPABLE
            DefaultVersion.STEAM_TYPE = Container.STEAM_TYPE_HEADLESS
            DefaultVersion.ASYNC_CACHE = "1"
        } else if (GPUInformation.isAdrenoA12(context)) {
            DefaultVersion.VARIANT = Container.BIONIC
            DefaultVersion.WINE_VERSION = "proton-10.0-arm64ec-2"
            DefaultVersion.DEFAULT_GRAPHICS_DRIVER = "Wrapper"
            DefaultVersion.DXVK = "2.4.1-gplasync"
            DefaultVersion.VKD3D = "2.14.1"
            DefaultVersion.WRAPPER = WRAPPER_ADRENO_A12
            DefaultVersion.STEAM_TYPE = Container.STEAM_TYPE_HEADLESS
            DefaultVersion.ASYNC_CACHE = "1"
        } else if (GPUInformation.isAdreno8EliteGen5(context)) {
            DefaultVersion.VARIANT = Container.BIONIC
            DefaultVersion.WINE_VERSION = "proton-10.0-arm64ec-2"
            DefaultVersion.DEFAULT_GRAPHICS_DRIVER = "Wrapper"
            DefaultVersion.DXVK = "2.4.1-gplasync"
            DefaultVersion.VKD3D = "2.14.1"
            DefaultVersion.WRAPPER = WRAPPER_ADRENO_8ELITE_GEN5
            DefaultVersion.STEAM_TYPE = Container.STEAM_TYPE_HEADLESS
            DefaultVersion.ASYNC_CACHE = "1"
        } else if (GPUInformation.isAdreno8Elite(context)) {
            DefaultVersion.VARIANT = Container.BIONIC
            DefaultVersion.WINE_VERSION = "proton-10.0-arm64ec-2"
            DefaultVersion.DEFAULT_GRAPHICS_DRIVER = "Wrapper"
            DefaultVersion.DXVK = "2.4.1-gplasync"
            DefaultVersion.VKD3D = "2.14.1"
            DefaultVersion.WRAPPER = WRAPPER_ADRENO_8ELITE
            DefaultVersion.STEAM_TYPE = Container.STEAM_TYPE_HEADLESS
            DefaultVersion.ASYNC_CACHE = "1"
        } else {
            DefaultVersion.VARIANT = Container.BIONIC
            DefaultVersion.WINE_VERSION = "proton-10.0-arm64ec-2"
            DefaultVersion.DEFAULT_GRAPHICS_DRIVER =
                if (GPUInformation.isAdrenoGPU(context)) "Wrapper" else "Wrapper-gamenative"
            DefaultVersion.DXVK = "async-1.10.3"
            DefaultVersion.VKD3D = "2.14.1"
            DefaultVersion.STEAM_TYPE = Container.STEAM_TYPE_HEADLESS
            DefaultVersion.ASYNC_CACHE = "0"
        }
    }

    fun getGPUCards(context: Context): Map<Int, GpuInfo> {
        val gpuNames = JSONArray(FileUtils.readString(context, "gpu_cards.json"))
        return List(gpuNames.length()) {
            val deviceId = gpuNames.getJSONObject(it).getInt("deviceID")
            Pair(
                deviceId,
                GpuInfo(
                    deviceId = deviceId,
                    vendorId = gpuNames.getJSONObject(it).getInt("vendorID"),
                    name = gpuNames.getJSONObject(it).getString("name"),
                ),
            )
        }.toMap()
    }

    fun getDefaultContainerData(): ContainerData {
        return ContainerData(
            screenSize = PrefManager.screenSize,
            envVars = PrefManager.envVars,
            graphicsDriver = PrefManager.graphicsDriver,
            graphicsDriverVersion = PrefManager.graphicsDriverVersion,
            graphicsDriverConfig = PrefManager.graphicsDriverConfig,
            rendererPresentMode = PrefManager.rendererPresentMode,
            displayRenderer = PrefManager.displayRendererMode,
            sfCompatMode = PrefManager.sfCompatMode,
            dxwrapper = PrefManager.dxWrapper,
            dxwrapperConfig = PrefManager.dxWrapperConfig,
            audioDriver = PrefManager.audioDriver,
            pulseaudioLowLatency = PrefManager.pulseaudioLowLatency,
            micEnabled = PrefManager.micEnabled,
            wincomponents = PrefManager.winComponents,
            drives = PrefManager.drives,
            execArgs = PrefManager.execArgs,
            showFPS = false,
            launchRealSteam = PrefManager.launchRealSteam,
            launchBionicSteam = PrefManager.launchBionicSteam,
            cpuList = PrefManager.cpuList,
            cpuListWoW64 = PrefManager.cpuListWoW64,
            wow64Mode = PrefManager.wow64Mode,
            startupSelection = PrefManager.startupSelection.toByte(),
            box86Version = PrefManager.box86Version,
            box64Version = PrefManager.box64Version,
            box86Preset = PrefManager.box86Preset,
            box64Preset = PrefManager.box64Preset,
            desktopTheme = WineThemeManager.DEFAULT_DESKTOP_THEME,
            language = PrefManager.containerLanguage,
            containerVariant = PrefManager.containerVariant,
            forceDlc = PrefManager.forceDlc,
            localSavesOnly = PrefManager.localSavesOnly,
            steamOfflineMode = PrefManager.steamOfflineMode,
            epicOfflineMode = PrefManager.epicOfflineMode,
            useLegacyDRM = PrefManager.useLegacyDRM,
            unpackFiles = PrefManager.unpackFiles,
            suspendPolicy = PrefManager.suspendPolicy,
            fasterExternalLoading = PrefManager.fasterExternalLoading,
            disableLibredirect = PrefManager.disableLibredirect,
            wineVersion = PrefManager.wineVersion,
            emulator = PrefManager.emulator,
            fexcoreVersion = PrefManager.fexcoreVersion,
            fexcoreTSOMode = PrefManager.fexcoreTSOMode,
            fexcoreX87Mode = PrefManager.fexcoreX87Mode,
            fexcoreMultiBlock = PrefManager.fexcoreMultiBlock,
            fexcorePreset = PrefManager.fexcorePreset,
            renderer = PrefManager.renderer,
            csmt = PrefManager.csmt,
            videoPciDeviceID = PrefManager.videoPciDeviceID,
            offScreenRenderingMode = PrefManager.offScreenRenderingMode,
            strictShaderMath = PrefManager.strictShaderMath,
            videoMemorySize = PrefManager.videoMemorySize,
            mouseWarpOverride = PrefManager.mouseWarpOverride,
            useDRI3 = PrefManager.useDRI3,
            useSteamInput = PrefManager.useSteamInput,
            enableXInput = PrefManager.xinputEnabled,
			enableDInput = PrefManager.dinputEnabled,
			dinputMapperType = PrefManager.dinputMapperType.toByte(),
            disableMouseInput = PrefManager.disableMouseInput,
            portraitMode = PrefManager.portraitMode,
            portraitBelowCutout = PrefManager.portraitBelowCutout,
            externalDisplayMode = PrefManager.externalDisplayInputMode,
            externalDisplaySwap = PrefManager.externalDisplaySwap,
            sharpnessEffect = PrefManager.sharpnessEffect,
            sharpnessLevel = PrefManager.sharpnessLevel,
            sharpnessDenoise = PrefManager.sharpnessDenoise,
        )
    }

    fun setDefaultContainerData(containerData: ContainerData) {
        PrefManager.screenSize = containerData.screenSize
        PrefManager.envVars = containerData.envVars
        PrefManager.graphicsDriver = containerData.graphicsDriver
        PrefManager.graphicsDriverVersion = containerData.graphicsDriverVersion
        PrefManager.graphicsDriverConfig = containerData.graphicsDriverConfig
        PrefManager.rendererPresentMode = containerData.rendererPresentMode
        PrefManager.displayRendererMode = containerData.displayRenderer
        PrefManager.sfCompatMode = containerData.sfCompatMode
        PrefManager.dxWrapper = containerData.dxwrapper
        PrefManager.dxWrapperConfig = containerData.dxwrapperConfig
        PrefManager.audioDriver = containerData.audioDriver
        PrefManager.pulseaudioLowLatency = containerData.pulseaudioLowLatency
        PrefManager.micEnabled = containerData.micEnabled
        PrefManager.winComponents = containerData.wincomponents
        PrefManager.drives = containerData.drives
        PrefManager.execArgs = containerData.execArgs
        PrefManager.launchRealSteam = containerData.launchRealSteam
        PrefManager.launchBionicSteam = containerData.launchBionicSteam
        PrefManager.cpuList = containerData.cpuList
        PrefManager.cpuListWoW64 = containerData.cpuListWoW64
        PrefManager.wow64Mode = containerData.wow64Mode
        PrefManager.startupSelection = containerData.startupSelection.toInt()
        PrefManager.box86Version = containerData.box86Version
        PrefManager.box64Version = containerData.box64Version
        PrefManager.box86Preset = containerData.box86Preset
        PrefManager.box64Preset = containerData.box64Preset

        PrefManager.csmt = containerData.csmt
        PrefManager.videoPciDeviceID = containerData.videoPciDeviceID
        PrefManager.offScreenRenderingMode = containerData.offScreenRenderingMode
        PrefManager.strictShaderMath = containerData.strictShaderMath
        PrefManager.videoMemorySize = containerData.videoMemorySize
        PrefManager.mouseWarpOverride = containerData.mouseWarpOverride
        PrefManager.useDRI3 = containerData.useDRI3
        PrefManager.disableMouseInput = containerData.disableMouseInput
        PrefManager.externalDisplayInputMode = containerData.externalDisplayMode
        PrefManager.externalDisplaySwap = containerData.externalDisplaySwap
        PrefManager.containerLanguage = containerData.language
        PrefManager.containerVariant = containerData.containerVariant
        PrefManager.wineVersion = containerData.wineVersion
        // Persist emulator/fexcore defaults for future containers
        PrefManager.emulator = containerData.emulator
        PrefManager.fexcoreVersion = containerData.fexcoreVersion
        PrefManager.fexcoreTSOMode = containerData.fexcoreTSOMode
        PrefManager.fexcoreX87Mode = containerData.fexcoreX87Mode
        PrefManager.fexcoreMultiBlock = containerData.fexcoreMultiBlock
        PrefManager.fexcorePreset = containerData.fexcorePreset
		// Persist renderer and controller defaults
		PrefManager.renderer = containerData.renderer
        PrefManager.useSteamInput = containerData.useSteamInput
        PrefManager.xinputEnabled = containerData.enableXInput
		PrefManager.dinputEnabled = containerData.enableDInput
		PrefManager.dinputMapperType = containerData.dinputMapperType.toInt()
        PrefManager.forceDlc = containerData.forceDlc
        PrefManager.localSavesOnly = containerData.localSavesOnly
        PrefManager.steamOfflineMode = containerData.steamOfflineMode
        PrefManager.epicOfflineMode = containerData.epicOfflineMode
        PrefManager.useLegacyDRM = containerData.useLegacyDRM
        PrefManager.unpackFiles = containerData.unpackFiles
        PrefManager.suspendPolicy = containerData.suspendPolicy
        PrefManager.fasterExternalLoading = containerData.fasterExternalLoading
        PrefManager.disableLibredirect = containerData.disableLibredirect
        PrefManager.portraitMode = containerData.portraitMode
        PrefManager.portraitBelowCutout = containerData.portraitBelowCutout
        PrefManager.sharpnessEffect = containerData.sharpnessEffect
        PrefManager.sharpnessLevel = containerData.sharpnessLevel
        PrefManager.sharpnessDenoise = containerData.sharpnessDenoise
    }

    fun toContainerData(container: Container): ContainerData {
        val renderer: String
        val csmt: Boolean
        val videoPciDeviceID: Int
        val offScreenRenderingMode: String
        val strictShaderMath: Boolean
        val videoMemorySize: String
        val mouseWarpOverride: String

        val userRegFile = File(container.rootDir, ".wine/user.reg")
        WineRegistryEditor(userRegFile).use { registryEditor ->
            renderer =
                registryEditor.getStringValue("Software\\Wine\\Direct3D", "renderer", PrefManager.renderer)
            csmt =
                registryEditor.getDwordValue("Software\\Wine\\Direct3D", "csmt", if (PrefManager.csmt) 3 else 0) != 0

            videoPciDeviceID =
                registryEditor.getDwordValue("Software\\Wine\\Direct3D", "VideoPciDeviceID", PrefManager.videoPciDeviceID)

            offScreenRenderingMode =
                registryEditor.getStringValue("Software\\Wine\\Direct3D", "OffScreenRenderingMode", PrefManager.offScreenRenderingMode)

            val strictShader = if (PrefManager.strictShaderMath) 1 else 0
            strictShaderMath =
                registryEditor.getDwordValue("Software\\Wine\\Direct3D", "strict_shader_math", strictShader) != 0

            videoMemorySize =
                registryEditor.getStringValue("Software\\Wine\\Direct3D", "VideoMemorySize", PrefManager.videoMemorySize)

            mouseWarpOverride =
                registryEditor.getStringValue("Software\\Wine\\DirectInput", "MouseWarpOverride", PrefManager.mouseWarpOverride)
        }

        // Read controller API settings from container
        val apiOrdinal = container.getInputType()
        val enableX = apiOrdinal == PreferredInputApi.XINPUT.ordinal || apiOrdinal == PreferredInputApi.BOTH.ordinal
        val enableD = apiOrdinal == PreferredInputApi.DINPUT.ordinal || apiOrdinal == PreferredInputApi.BOTH.ordinal
        val mapperType = container.getDinputMapperType()
        val useSteamInput = container.getExtra("useSteamInput", "false").toBoolean()
        // Read disable-mouse flag from container
        val disableMouse = container.isDisableMouseInput()
        // Read touchscreen-mode flag from container
        val touchscreenMode = container.isTouchscreenMode()
        // Read shooter-mode flag from container
        val shooterMode = container.isShooterMode()
        // Read gesture configuration JSON
        val gestureConfig = container.getGestureConfig()
        // Read shooter mode configuration JSON
        val shooterConfig = container.getShooterConfig()
        val externalDisplayMode = container.getExternalDisplayMode()
        val externalDisplaySwap = container.isExternalDisplaySwap()

        return ContainerData(
            name = container.name,
            screenSize = container.screenSize,
            envVars = container.envVars,
            graphicsDriver = container.graphicsDriver,
            graphicsDriverVersion = container.graphicsDriverVersion,
            graphicsDriverConfig = container.graphicsDriverConfig,
            rendererPresentMode = container.rendererPresentMode,
            displayRenderer = container.displayRenderer,
            xrRefreshRate = container.xrRefreshRate,
            xrRenderScale = container.xrRenderScale,
            sfCompatMode = container.sfCompatMode,
            dxwrapper = container.dxWrapper,
            dxwrapperConfig = container.dxWrapperConfig,
            audioDriver = container.audioDriver,
            pulseaudioLowLatency = container.getPulseaudioLowLatency(),
            micEnabled = container.getMicEnabled(),
            wincomponents = container.winComponents,
            drives = container.drives,
            execArgs = container.execArgs,
            executablePath = container.executablePath,
            showFPS = false,
            launchRealSteam = container.isLaunchRealSteam,
            launchBionicSteam = container.isLaunchBionicSteam,
            allowSteamUpdates = container.isAllowSteamUpdates,
            steamType = container.getSteamType(),
            cpuList = container.cpuList,
            cpuListWoW64 = container.cpuListWoW64,
            wow64Mode = container.isWoW64Mode,
            startupSelection = container.startupSelection.toByte(),
            box86Version = container.box86Version,
            box64Version = container.box64Version,
            box86Preset = container.box86Preset,
            box64Preset = container.box64Preset,
            desktopTheme = container.desktopTheme,
            containerVariant = container.containerVariant,
            wineVersion = container.wineVersion,
            emulator = container.emulator,
            fexcoreVersion = container.fexCoreVersion,
            fexcorePreset = container.getFEXCorePreset(),
            language = container.language,
            sdlControllerAPI = container.isSdlControllerAPI,
            fasterExternalLoading = container.isFasterExternalLoading,
            disableLibredirect = container.isDisableLibredirect,
            useSteamInput = useSteamInput,
            forceDlc = container.isForceDlc,
            localSavesOnly = container.isLocalSavesOnly,
            steamOfflineMode = container.isSteamOfflineMode(),
            epicOfflineMode = container.isEpicOfflineMode(),
            disableEpicOverlay = container.isDisableEpicOverlay,
            useLegacyDRM = container.isUseLegacyDRM(),
            unpackFiles = container.isUnpackFiles(),
            suspendPolicy = container.suspendPolicy,
            portraitMode = container.isPortraitMode,
            portraitBelowCutout = container.isPortraitBelowCutout,
            enableXInput = enableX,
            enableDInput = enableD,
            dinputMapperType = mapperType,
            disableMouseInput = disableMouse,
            touchscreenMode = touchscreenMode,
            shooterMode = shooterMode,
            gestureConfig = gestureConfig,
            shooterConfig = shooterConfig,
            externalDisplayMode = externalDisplayMode,
            externalDisplaySwap = externalDisplaySwap,
            csmt = csmt,
            videoPciDeviceID = videoPciDeviceID,
            offScreenRenderingMode = offScreenRenderingMode,
            strictShaderMath = strictShaderMath,
            useDRI3 = container.isUseDRI3(),
            videoMemorySize = videoMemorySize,
            mouseWarpOverride = mouseWarpOverride,
            sharpnessEffect = container.getExtra("sharpnessEffect", "None"),
            sharpnessLevel = container.getExtra("sharpnessLevel", "100").toIntOrNull() ?: 100,
            sharpnessDenoise = container.getExtra("sharpnessDenoise", "100").toIntOrNull() ?: 100,
            // LSFG Vulkan frame generation
            lsfgEnabled = container.getExtra(LsfgVkManager.EXTRA_ARMED, "false").toBoolean(),
            windowsVrEnabled = container.getExtra("windowsVrEnabled", "false").toBoolean(),
            openCompositeEnabled = container.getExtra("windowsVrOpenCompositeEnabled", "false").toBoolean(),
        )
    }

    fun applyToContainer(context: Context, appId: String, containerData: ContainerData) {
        val container = getContainer(context, appId)
        applyToContainer(context, container, containerData)
    }



    fun applyToContainer(context: Context, container: Container, containerData: ContainerData) {
        applyToContainer(context, container, containerData, saveToDisk = true)
    }

    fun applyToContainer(context: Context, container: Container, containerData: ContainerData, saveToDisk: Boolean) {
        Timber.d("Applying containerData to container. execArgs: '${containerData.execArgs}', saveToDisk: $saveToDisk")

        val previousUnpackFiles: Boolean = container.isUnpackFiles
        val previousLaunchBionicSteam: Boolean = container.isLaunchBionicSteam
        val previousLaunchRealSteam: Boolean = container.isLaunchRealSteam
        val userRegFile = File(container.rootDir, ".wine/user.reg")
        WineRegistryEditor(userRegFile).use { registryEditor ->
            registryEditor.setStringValue("Software\\Wine\\Direct3D", "renderer", containerData.renderer)
            registryEditor.setDwordValue("Software\\Wine\\Direct3D", "csmt", if (containerData.csmt) 3 else 0)
            registryEditor.setDwordValue("Software\\Wine\\Direct3D", "VideoPciDeviceID", containerData.videoPciDeviceID)
            registryEditor.setDwordValue(
                "Software\\Wine\\Direct3D",
                "VideoPciVendorID",
                getGPUCards(context)[containerData.videoPciDeviceID]!!.vendorId,
            )
            registryEditor.setStringValue("Software\\Wine\\Direct3D", "OffScreenRenderingMode", containerData.offScreenRenderingMode)
            registryEditor.setDwordValue("Software\\Wine\\Direct3D", "strict_shader_math", if (containerData.strictShaderMath) 1 else 0)
            registryEditor.setStringValue("Software\\Wine\\Direct3D", "VideoMemorySize", containerData.videoMemorySize)
            registryEditor.setStringValue("Software\\Wine\\DirectInput", "MouseWarpOverride", containerData.mouseWarpOverride)
            registryEditor.setStringValue("Software\\Wine\\Direct3D", "shader_backend", "glsl")
            registryEditor.setStringValue("Software\\Wine\\Direct3D", "UseGLSL", "enabled")
        }

        container.name = containerData.name
        container.screenSize = containerData.screenSize
        container.envVars = containerData.envVars
        container.graphicsDriver = containerData.graphicsDriver
        // Save driver config through to container
        container.graphicsDriverConfig = containerData.graphicsDriverConfig
        container.rendererPresentMode = containerData.rendererPresentMode
        container.displayRenderer = containerData.displayRenderer
        container.xrRefreshRate = containerData.xrRefreshRate
        container.xrRenderScale = containerData.xrRenderScale
        container.sfCompatMode = containerData.sfCompatMode
        container.dxWrapper = containerData.dxwrapper
        container.dxWrapperConfig = containerData.dxwrapperConfig
        container.audioDriver = containerData.audioDriver
        container.setPulseaudioLowLatency(containerData.pulseaudioLowLatency)
        container.setMicEnabled(containerData.micEnabled)
        container.winComponents = containerData.wincomponents
        container.drives = containerData.drives
        container.execArgs = containerData.execArgs
        if (container.executablePath != containerData.executablePath && container.executablePath != "") {
            container.setNeedsUnpacking(true)
        }
        container.executablePath = containerData.executablePath
        container.isShowFPS = false
        container.isLaunchRealSteam = containerData.launchRealSteam
        container.isLaunchBionicSteam = containerData.launchBionicSteam
        if (previousLaunchBionicSteam != containerData.launchBionicSteam ||
            previousLaunchRealSteam != containerData.launchRealSteam) {
            container.setNeedsUnpacking(true)
        }
        container.isAllowSteamUpdates = containerData.allowSteamUpdates
        container.setSteamType(containerData.steamType)
        container.cpuList = containerData.cpuList
        container.cpuListWoW64 = containerData.cpuListWoW64
        container.isWoW64Mode = containerData.wow64Mode
        container.startupSelection = containerData.startupSelection
        container.box86Version = containerData.box86Version
        container.box64Version = containerData.box64Version
        container.box86Preset = containerData.box86Preset
        container.box64Preset = containerData.box64Preset
        container.isSdlControllerAPI = containerData.sdlControllerAPI
        container.isFasterExternalLoading = containerData.fasterExternalLoading
        container.isDisableLibredirect = containerData.disableLibredirect
        container.putExtra("useSteamInput", containerData.useSteamInput)
        container.desktopTheme = containerData.desktopTheme
        container.graphicsDriverVersion = containerData.graphicsDriverVersion
        container.containerVariant = containerData.containerVariant
        container.wineVersion = containerData.wineVersion
        container.emulator = containerData.emulator
        container.fexCoreVersion = containerData.fexcoreVersion
        container.setFEXCorePreset(containerData.fexcorePreset)
        container.setDisableMouseInput(containerData.disableMouseInput)
        container.setTouchscreenMode(containerData.touchscreenMode)
        container.setShooterMode(containerData.shooterMode)
        container.setGestureConfig(containerData.gestureConfig)
        container.setShooterConfig(containerData.shooterConfig)
        container.setExternalDisplayMode(containerData.externalDisplayMode)
        container.setExternalDisplaySwap(containerData.externalDisplaySwap)
        container.setForceDlc(containerData.forceDlc)
        container.setLocalSavesOnly(containerData.localSavesOnly)
        container.setSteamOfflineMode(containerData.steamOfflineMode)
        container.setEpicOfflineMode(containerData.epicOfflineMode)
        container.setDisableEpicOverlay(containerData.disableEpicOverlay)
        container.setUseLegacyDRM(containerData.useLegacyDRM)
        container.setUnpackFiles(containerData.unpackFiles)
        container.setSuspendPolicy(containerData.suspendPolicy)
        container.setPortraitMode(containerData.portraitMode)
        container.setPortraitBelowCutout(containerData.portraitBelowCutout)
        if (previousUnpackFiles != containerData.unpackFiles && containerData.unpackFiles) {
            container.setNeedsUnpacking(true)
        }
        container.putExtra("sharpnessEffect", containerData.sharpnessEffect)
        container.putExtra("sharpnessLevel", containerData.sharpnessLevel.toString())
        container.putExtra("sharpnessDenoise", containerData.sharpnessDenoise.toString())
        // LSFG Vulkan frame generation
        container.putExtra(LsfgVkManager.EXTRA_ARMED, containerData.lsfgEnabled.toString())
        container.putExtra("windowsVrEnabled", containerData.windowsVrEnabled.toString())
        container.putExtra("windowsVrOpenCompositeEnabled", containerData.openCompositeEnabled.toString())
        try {
            container.language = containerData.language
        } catch (e: Exception) {
            container.putExtra("language", containerData.language)
        }
        // Set container LC_ALL according to selected language
        val lcAll = mapLanguageToLocale(containerData.language)
        container.setLC_ALL(lcAll)

        // Apply controller settings to container
        val api = when {
            containerData.enableXInput && containerData.enableDInput -> PreferredInputApi.BOTH
            containerData.enableXInput -> PreferredInputApi.XINPUT
            containerData.enableDInput -> PreferredInputApi.DINPUT
            else -> PreferredInputApi.AUTO
        }
        container.setInputType(api.ordinal)
        container.setDinputMapperType(containerData.dinputMapperType)
        container.setUseDRI3(containerData.useDRI3)
        Timber.d("Container set: preferredInputApi=%s, dinputMapperType=0x%02x", api, containerData.dinputMapperType)

        if (saveToDisk) {
            // Mark that config has been changed, so we can show feedback dialog after next game run
            container.putExtra("config_changed", "true")
            container.saveData()
        }
        Timber.d("Set container.execArgs to '${containerData.execArgs}'")
    }

    private fun mapLanguageToLocale(language: String): String {
        return when (language.lowercase()) {
            "arabic" -> "ar_SA.utf8"
            "bulgarian" -> "bg_BG.utf8"
            "schinese" -> "zh_CN.utf8"
            "tchinese" -> "zh_TW.utf8"
            "czech" -> "cs_CZ.utf8"
            "danish" -> "da_DK.utf8"
            "dutch" -> "nl_NL.utf8"
            "english" -> "en_US.utf8"
            "finnish" -> "fi_FI.utf8"
            "french" -> "fr_FR.utf8"
            "german" -> "de_DE.utf8"
            "greek" -> "el_GR.utf8"
            "hungarian" -> "hu_HU.utf8"
            "italian" -> "it_IT.utf8"
            "japanese" -> "ja_JP.utf8"
            "koreana" -> "ko_KR.utf8"
            "norwegian" -> "nb_NO.utf8"
            "polish" -> "pl_PL.utf8"
            "portuguese" -> "pt_PT.utf8"
            "brazilian" -> "pt_BR.utf8"
            "romanian" -> "ro_RO.utf8"
            "russian" -> "ru_RU.utf8"
            "spanish" -> "es_ES.utf8"
            "latam" -> "es_MX.utf8"
            "swedish" -> "sv_SE.utf8"
            "thai" -> "th_TH.utf8"
            "turkish" -> "tr_TR.utf8"
            "ukrainian" -> "uk_UA.utf8"
            "vietnamese" -> "vi_VN.utf8"
            else -> "en_US.utf8"
        }
    }

    fun getContainerId(appId: String): String {
        return appId
    }

    fun hasContainer(context: Context, appId: String): Boolean {
        val containerManager = ContainerManager(context)
        return containerManager.hasContainer(appId)
    }

    fun getContainer(context: Context, appId: String): Container {
        val containerManager = ContainerManager(context)
        return if (containerManager.hasContainer(appId)) {
            containerManager.getContainerById(appId)
        } else {
            throw Exception("Container does not exist for game $appId")
        }
    }

    private fun createNewContainer(
        context: Context,
        appId: String,
        containerId: String,
        containerManager: ContainerManager,
        customConfig: ContainerData? = null,
    ): Container {
         // Determine game source
        val gameSource = extractGameSourceFromContainerId(appId)

        val defaultDrives = PrefManager.drives
        val drives = defaultDrives
        Timber.d("Prepared container drives: $drives")

        // Prepare container data with default DX wrapper to start
        val initialDxWrapper = if (customConfig?.dxwrapper != null) {
            customConfig.dxwrapper
        } else {
            PrefManager.dxWrapper // Use default until we get the real version
        }

        // Set up data for container creation
        val data = JSONObject()
        data.put("name", "container_$containerId")

        // Create the actual container
        var container = containerManager.createContainerFuture(containerId, data).get()

        // If container creation failed, it might be because directory already exists but is corrupted
        // Try to clean it up and retry once
        if (container == null) {
            Timber.w("Container creation failed for $containerId, checking for corrupted directory...")
            // Get the container directory path
            val rootDir = ImageFs.find(context).getRootDir()
            val homeDir = File(rootDir, "home")
            val containerDir = File(homeDir, ImageFs.USER + "-" + containerId)

            if (containerDir.exists() && !containerManager.hasContainer(containerId)) {
                Timber.w("Found orphaned/corrupted container directory, deleting and retrying: $containerId")
                try {
                    FileUtils.delete(containerDir)
                    // Retry container creation after cleanup
                    container = containerManager.createContainerFuture(containerId, data).get()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to clean up corrupted container directory: $containerId")
                }
            }

            // If still null after retry, throw exception
            if (container == null) {
                Timber.e("Failed to create container for $containerId after cleanup attempt")
                throw IllegalStateException("Failed to create container: $containerId")
            }
        }




        // Initialize container with default/custom config or best config
        var containerData = if (customConfig != null) {
            // Use custom config, but ensure drives are set if not specified
            if (customConfig.drives == Container.DEFAULT_DRIVES) {
                customConfig.copy(drives = drives)
            } else {
                customConfig
            }
        } else {
            // Use default config with drives
            ContainerData(
                screenSize = PrefManager.screenSize,
                envVars = PrefManager.envVars,
                cpuList = PrefManager.cpuList,
                cpuListWoW64 = PrefManager.cpuListWoW64,
                graphicsDriver = PrefManager.graphicsDriver,
                graphicsDriverVersion = PrefManager.graphicsDriverVersion,
                graphicsDriverConfig = PrefManager.graphicsDriverConfig,
                rendererPresentMode = PrefManager.rendererPresentMode,
                displayRenderer = PrefManager.displayRendererMode,
                sfCompatMode = PrefManager.sfCompatMode,
                dxwrapper = initialDxWrapper,
                dxwrapperConfig = PrefManager.dxWrapperConfig,
                audioDriver = PrefManager.audioDriver,
                pulseaudioLowLatency = PrefManager.pulseaudioLowLatency,
                micEnabled = PrefManager.micEnabled,
                wincomponents = PrefManager.winComponents,
                drives = drives,
                execArgs = PrefManager.execArgs,
                showFPS = false,
                launchRealSteam = PrefManager.launchRealSteam,
                launchBionicSteam = PrefManager.launchBionicSteam,
                wow64Mode = PrefManager.wow64Mode,
                startupSelection = PrefManager.startupSelection.toByte(),
                box86Version = PrefManager.box86Version,
                box64Version = PrefManager.box64Version,
                box86Preset = PrefManager.box86Preset,
                box64Preset = PrefManager.box64Preset,
                desktopTheme = WineThemeManager.DEFAULT_DESKTOP_THEME,
                language = PrefManager.containerLanguage,
                containerVariant = PrefManager.containerVariant,
                wineVersion = PrefManager.wineVersion,
                emulator = PrefManager.emulator,
                fexcoreVersion = PrefManager.fexcoreVersion,
                fexcoreTSOMode = PrefManager.fexcoreTSOMode,
                fexcoreX87Mode = PrefManager.fexcoreX87Mode,
                fexcoreMultiBlock = PrefManager.fexcoreMultiBlock,
                fexcorePreset = PrefManager.fexcorePreset,
                renderer = PrefManager.renderer,
                csmt = PrefManager.csmt,
                videoPciDeviceID = PrefManager.videoPciDeviceID,
                offScreenRenderingMode = PrefManager.offScreenRenderingMode,
                strictShaderMath = PrefManager.strictShaderMath,
                useDRI3 = PrefManager.useDRI3,
                videoMemorySize = PrefManager.videoMemorySize,
                mouseWarpOverride = PrefManager.mouseWarpOverride,
                enableXInput = PrefManager.xinputEnabled,
                enableDInput = PrefManager.dinputEnabled,
                dinputMapperType = PrefManager.dinputMapperType.toByte(),
                disableMouseInput = PrefManager.disableMouseInput,
                forceDlc = PrefManager.forceDlc,
                steamOfflineMode = PrefManager.steamOfflineMode,
                epicOfflineMode = PrefManager.epicOfflineMode,
                useLegacyDRM = PrefManager.useLegacyDRM,
                unpackFiles = PrefManager.unpackFiles,
                suspendPolicy = PrefManager.suspendPolicy,
                fasterExternalLoading = PrefManager.fasterExternalLoading,
                disableLibredirect = PrefManager.disableLibredirect,
                portraitMode = PrefManager.portraitMode,
                portraitBelowCutout = PrefManager.portraitBelowCutout,
                externalDisplayMode = PrefManager.externalDisplayInputMode,
                externalDisplaySwap = PrefManager.externalDisplaySwap,
            )
        }


        if (Build.MANUFACTURER.equals("samsung", ignoreCase = true) && GPUInformation.isAdreno740(context)) {
            val ev = EnvVars(containerData.envVars)
            if (!ev.has("FD_DEV_FEATURES")) {
                ev.put("FD_DEV_FEATURES", "enable_tp_ubwc_flag_hint=1")
                containerData = containerData.copy(envVars = ev.toString())
            }
        }

        // If custom config is provided, just apply it and return
        if (customConfig?.dxwrapper != null) {
            applyToContainer(context, container, containerData)
            return container
        }

        // Apply container data with the determined DX wrapper
        applyToContainer(context, container, containerData)
        return container
    }

    fun getOrCreateContainer(context: Context, appId: String): Container {
        val containerManager = ContainerManager(context)

        val container = if (containerManager.hasContainer(appId)) {
            containerManager.getContainerById(appId)
        } else {
            createNewContainer(context, appId, appId, containerManager)
        }

        // Ensure Custom Games have the A: drive mapped to the game folder
        // and GOG games have a drive mapped to the GOG games directory
        // and Epic games have a drive mapped to the Epic game directory
        val gameSource = extractGameSourceFromContainerId(appId)
        val gameFolderPath: String? = null
        val resolvedGameFolderPath = StorageUtils.resolveLegacyGameDir(gameFolderPath)

        if (resolvedGameFolderPath != null) {
            // Check if A: drive is already mapped to the correct path
            var hasCorrectADrive = false
            for (drive in Container.drivesIterator(container.drives)) {
                if (drive[0] == "A" && drive[1] == resolvedGameFolderPath) {
                    hasCorrectADrive = true
                    break
                }
            }

            // If A: drive is not mapped correctly, update it
            if (!hasCorrectADrive) {
                val currentDrives = container.drives
                // Rebuild drives string, excluding existing A: drive and adding new one
                val drivesBuilder = StringBuilder()
                drivesBuilder.append("A:$resolvedGameFolderPath")

                // Add all other drives (excluding A:)
                for (drive in Container.drivesIterator(currentDrives)) {
                    if (drive[0] != "A") {
                        drivesBuilder.append("${drive[0]}:${drive[1]}")
                    }
                }

                val updatedDrives = drivesBuilder.toString()
                container.drives = updatedDrives
                container.saveData()
                Timber.d("Updated container drives to include A: drive mapping: $updatedDrives")
            }
        } else {
            Timber.w("Could not find gameFolderPath for game $appId, skipping drive mapping update")
        }
        return container
    }

    fun getOrCreateContainerWithOverride(context: Context, appId: String): Container {
        val containerManager = ContainerManager(context)

        return if (containerManager.hasContainer(appId)) {
            val container = containerManager.getContainerById(appId)

            // Apply temporary override if present (without saving to disk)
            if (IntentLaunchManager.hasTemporaryOverride(appId)) {
                val overrideConfig = IntentLaunchManager.getTemporaryOverride(appId)
                if (overrideConfig != null) {
                    // Backup original config before applying override (if not already backed up)
                    if (IntentLaunchManager.getOriginalConfig(appId) == null) {
                        val originalConfig = toContainerData(container)
                        IntentLaunchManager.setOriginalConfig(appId, originalConfig)
                    }

                    // Get the effective config (merge base with override)
                    val effectiveConfig = IntentLaunchManager.getEffectiveContainerConfig(context, appId)
                    if (effectiveConfig != null) {
                        applyToContainer(context, container, effectiveConfig, saveToDisk = false)
                        Timber.i("Applied temporary config override to existing container for app $appId (in-memory only)")
                    }
                }
            }

            container
        } else {
            // Create new container with override config if present
            val overrideConfig = if (IntentLaunchManager.hasTemporaryOverride(appId)) {
                IntentLaunchManager.getTemporaryOverride(appId)
            } else {
                null
            }

            createNewContainer(context, appId, appId, containerManager, overrideConfig)
        }
    }

    /**
     * Deletes the container associated with the given appId, if it exists.
     */
    fun deleteContainer(context: Context, appId: String) {
        Timber.i("[ContainerDeletion] Attempting to delete container for appId=$appId")
        val manager = ContainerManager(context)
        val hasContainer = manager.hasContainer(appId)
        Timber.i("[ContainerDeletion] hasContainer($appId) = $hasContainer")
        if (hasContainer) {
            // Remove the container directory asynchronously
            manager.removeContainerAsync(
                manager.getContainerById(appId),
            ) {
                Timber.i("[ContainerDeletion] Successfully deleted container for appId=$appId")
            }
        } else {
            Timber.w("[ContainerDeletion] No container found for appId=$appId — deletion aborted.")

            // Containers successfully parsed by ContainerManager (config file was readable)
            val loadedIds = manager.containers.map { it.id }
            Timber.w("[ContainerDeletion] Loaded containers (${loadedIds.size}): $loadedIds")

            // Raw filesystem scan — catches directories whose config file was empty/corrupt and
            // were silently skipped by ContainerManager. These are potential orphans.
            // Directory layout: <filesDir>/imagefs/home/xuser-<containerId>
            val homeDir = java.io.File(context.filesDir, "imagefs/home")
            val prefix = "${com.winlator.xenvironment.ImageFs.USER}-"
            val rawIds = homeDir.listFiles()
                ?.filter { it.isDirectory && it.name.startsWith(prefix) }
                ?.map { it.name.removePrefix(prefix) }
                ?: emptyList()
            val unloadedIds = rawIds - loadedIds.toSet()
            Timber.w("[ContainerDeletion] Raw filesystem dirs (${rawIds.size}): $rawIds")
            if (unloadedIds.isNotEmpty()) {
                Timber.w("[ContainerDeletion] Dirs present on disk but NOT loaded by ContainerManager (corrupt/empty config): $unloadedIds")
            }
        }
    }

    /**
     * Extracts the game ID from a container ID string
     * Handles formats like:
     * - STEAM_123456 -> 123456
     * - EPIC_2938123
     * - CUSTOM_GAME_571969840 -> 571969840
     * - GOG_19283103 -> 19283103
     * - STEAM_123456(1) -> 123456
     * - 19283103 -> 19283103 (legacy GOG format)
     */
    fun extractGameIdFromContainerId(containerId: String): Int {
        // Remove duplicate suffix like (1), (2) if present
        val idWithoutSuffix = if (containerId.contains("(")) {
            containerId.substringBefore("(")
        } else {
            containerId
        }

        // Split by underscores and find the last numeric part
        val parts = idWithoutSuffix.split("_")
        // The last part should be the numeric ID
        val lastPart = parts.lastOrNull() ?: throw IllegalArgumentException("Invalid container ID format: $containerId")

        return try {
            lastPart.toInt()
        } catch (e: NumberFormatException) {
            1
        }
    }

    /**
     * Extracts the game source from a container ID string
     */
    fun extractGameSourceFromContainerId(containerId: String): GameSource {
        return when {
            containerId.startsWith("CUSTOM_GAME_") || containerId.contains("wow", ignoreCase = true) -> GameSource.CUSTOM_GAME
            containerId.startsWith("STEAM_") -> GameSource.STEAM
            containerId.startsWith("GOG_") -> GameSource.GOG
            containerId.startsWith("EPIC_") -> GameSource.EPIC
            containerId.startsWith("AMAZON_") -> GameSource.AMAZON
            else -> GameSource.CUSTOM_GAME
        }
    }

    fun isLocalSavesOnly(context: Context, appId: String): Boolean {
        if (!hasContainer(context, appId)) return false
        val container = getContainer(context, appId)
        return container.isLocalSavesOnly
    }

    fun supportsKnownConfigAutoApply(gameSource: GameSource): Boolean = when (gameSource) {
        GameSource.STEAM,
        GameSource.GOG,
        GameSource.EPIC,
        GameSource.AMAZON,
        GameSource.CUSTOM_GAME,
        -> true
    }

    fun resolveGameName(containerId: String): String {
        return if (containerId.contains("wow", ignoreCase = true)) "World of Warcraft" else containerId
    }

    /**
     * Gets the file system path for the container's A: drive
     */
    fun getADrivePath(drives: String): String? {
        // Use the existing Container.drivesIterator logic
        for (drive in Container.drivesIterator(drives)) {
            if (drive[0] == "A") {
                return drive[1]
            }
        }
        return null
    }

    fun isAbsoluteWindowsPath(path: String): Boolean =
        Regex("^[A-Za-z]:[\\\\/]").containsMatchIn(path)

    /**
     * Scans the container's A: drive for all .exe and .bat files
     */
    fun scanExecutablesInADrive(drives: String): List<String> {
        val executables = mutableListOf<String>()

        try {
            // Find the A: drive path from container drives
            val aDrivePath = getADrivePath(drives)
            if (aDrivePath == null) {
                Timber.w("No A: drive found in container drives")
                return emptyList()
            }

            val aDir = File(aDrivePath)
            if (!aDir.exists() || !aDir.isDirectory) {
                Timber.w("A: drive path does not exist or is not a directory: $aDrivePath")
                return emptyList()
            }

            Timber.d("Scanning for executables in A: drive: $aDrivePath")

            // Recursively scan for .exe/.bat files using listFiles with depth limit.
            // Symlinked directories are skipped to avoid cycles (e.g. GOG ISI rootdir -> game root).
            fun scanRecursive(dir: File, baseDir: File, depth: Int = 0, maxDepth: Int = 10) {
                if (depth > maxDepth) return

                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        if (FileUtils.isSymlink(file)) return@forEach
                        scanRecursive(file, baseDir, depth + 1, maxDepth)
                    } else if (file.isFile && (file.name.lowercase().endsWith(".exe") || file.name.lowercase().endsWith(".bat"))) {
                        // Convert to relative Windows path format
                        val relativePath = baseDir.toURI().relativize(file.toURI()).path
                        executables.add(relativePath)
                    }
                }
            }

            scanRecursive(aDir, aDir)

            // Sort alphabetically and prioritize common game executables
            executables.sortWith { a, b ->
                val aScore = getExecutablePriority(a)
                val bScore = getExecutablePriority(b)

                if (aScore != bScore) {
                    bScore.compareTo(aScore) // Higher priority first
                } else {
                    a.compareTo(b, ignoreCase = true) // Alphabetical
                }
            }

            Timber.d("Found ${executables.size} executables in A: drive")
        } catch (e: Exception) {
            Timber.e(e, "Error scanning A: drive for executables")
        }

        return executables
    }

    /**
     * Filters a list of exe paths to exclude system/utility executables (e.g. uninstallers, setup, crash handlers).
     * Used when unpackFiles is enabled to determine which exes to run Steamless on.
     */
    fun filterExesForUnpacking(exePaths: List<String>): List<String> = exePaths.filter { path ->
        val fileName = path.substringAfterLast('/').substringAfterLast('\\').lowercase()
        fileName.endsWith(".exe") && !isSystemExecutable(fileName)
    }

    /**
     * Assigns priority scores to executables for better sorting
     */
    private fun getExecutablePriority(exePath: String): Int {
        val fileName = exePath.substringAfterLast('\\').lowercase()
        val baseName = fileName.substringBeforeLast('.')

        return when {
            // Highest priority: common game executable patterns
            fileName.contains("game") -> 100

            fileName.contains("start") -> 85

            fileName.contains("main") -> 80

            fileName.contains("launcher") && !fileName.contains("unins") -> 75

            // High priority: probable main executables
            baseName.length >= 4 && !isSystemExecutable(fileName) -> 70

            // Medium priority: any non-system executable
            !isSystemExecutable(fileName) -> 50

            // Low priority: system/utility executables
            else -> 10
        }
    }

    /**
     * Checks if an executable is likely a system/utility file
     */
    private fun isSystemExecutable(fileName: String): Boolean {
        val baseName = fileName.removeSuffix(".exe")
        val strongPrefixes = listOf(
            "unins",
            "uninstall",
            "setup",
            "install",
            "redist",
            "vcredist",
            "vc_redist",
            "dxsetup",
            "directx",
            "crashhandler",
            "crashreporter",
        )

        if (strongPrefixes.any { baseName.startsWith(it) }) {
            return true
        }

        val denylistTokens = setOf(
            "unins",
            "uninstall",
            "setup",
            "installer",
            "redist",
            "vcredist",
            "directx",
            "dxsetup",
            "crashhandler",
            "crashreporter",
        )
        val tokens = baseName.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
        return tokens.any { it in denylistTokens }
    }
}
