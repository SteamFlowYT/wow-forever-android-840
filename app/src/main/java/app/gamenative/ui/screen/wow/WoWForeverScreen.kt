package app.gamenative.ui.screen.wow

import android.content.Context
import android.os.Environment
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.container.Container
import com.winlator.container.ContainerManager
import com.winlator.contents.AdrenotoolsManager
import com.winlator.contents.ContentProfile
import com.winlator.contents.ContentsManager
import com.winlator.core.TarCompressorUtils
import com.winlator.xenvironment.ImageFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

@Composable
fun WoWForeverScreen(
    onLaunch: (appId: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var gamePath by remember {
        mutableStateOf(
            File(Environment.getExternalStorageDirectory(), "WoW Forever").absolutePath
        )
    }

    var isLaunching by remember { mutableStateOf(false) }
    var hasSavedLogin by remember { mutableStateOf(BattleNetSignIn.load(context) != null) }
    var statusText by remember { mutableStateOf("Ready to launch") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var exeExists by remember { mutableStateOf(false) }
    var dataExists by remember { mutableStateOf(false) }
    var buildInfoExists by remember { mutableStateOf(false) }

    fun checkFiles() {
        val root = File(gamePath)
        val exe = File(root, "_classic_beta_/WowB-ARM64.exe")
        val dataDir = File(root, "Data")

        exeExists = exe.exists()
        dataExists = dataDir.exists() && (dataDir.listFiles()?.isNotEmpty() == true)
        buildInfoExists = File(root, ".build.info").exists()
    }

    LaunchedEffect(gamePath) {
        checkFiles()
    }

    fun launchGame() {
        if (isLaunching) return
        isLaunching = true
        errorMessage = null
        statusText = "Preparing components..."

        scope.launch(Dispatchers.IO) {
            try {
                // 1. Ensure components from assets are installed
                val componentsOk = installBundledComponents(context) { msg ->
                    scope.launch(Dispatchers.Main) { statusText = msg }
                }

                if (!componentsOk) {
                    throw IllegalStateException("Failed to extract Proton or graphics components.")
                }

                check(File(gamePath, ".build.info").exists()) {
                    "Missing .build.info in $gamePath. Copy it from your WoW install."
                }
                ensureGameConfig(File(gamePath))

                // 2. Configure container
                scope.launch(Dispatchers.Main) { statusText = "Configuring Adreno 740 container..." }
                val containerManager = ContainerManager(context)
                val containerId = "wow_forever"

                val configData = JSONObject().apply {
                    put("id", containerId)
                    put("name", "WoW Forever")
                    put("screenSize", "1920x1080")
                    put("envVars", "WRAPPER_MAX_IMAGE_COUNT=0 ZINK_DESCRIPTORS=lazy ZINK_DEBUG=compact,deck_emu MESA_SHADER_CACHE_DISABLE=false MESA_SHADER_CACHE_MAX_SIZE=512MB mesa_glthread=true WINEESYNC=0 MESA_VK_WSI_PRESENT_MODE=mailbox TU_DEBUG=noconform VKD3D_SHADER_MODEL=6_0 PULSE_LATENCY_MSEC=144")
                    put("graphicsDriver", "Wrapper")
                    put("graphicsDriverVersion", "Turnip-WoW-scheduler-test")
                    put("graphicsDriverConfig", "version=Turnip-WoW-scheduler-test,adrenotoolsTurnip=1,resourceType=buffer,bcnEmulation=auto,quality=high")
                    put("displayRenderer", "vulkan")
                    put("dxwrapper", "dxvk-2.4.1-wow-aarch64-test-1")
                    put("dxwrapperConfig", "version=2.4.1-wow-aarch64-test-1")
                    put("wineVersion", "proton-11.0-90624-arm64ec-1")
                    put("containerVariant", "bionic")
                    put("fexcoreVersion", "2609-0")
                    put("drives", "D:/storage/emulated/0/DownloadG:$gamePath")
                    put("executablePath", "G:\\_classic_beta_\\WowB-ARM64.exe")
                    put("execArgs", "-d3d11")
                    put("showFPS", true)
                    put("startupSelection", Container.STARTUP_SELECTION_NORMAL.toInt())
                    put("wow64Mode", true)
                }

                var container = containerManager.getContainerById(containerId)
                if (container == null) {
                    scope.launch(Dispatchers.Main) { statusText = "Creating prefix environment (first boot)..." }
                    container = containerManager.createContainer(containerId, configData)
                } else {
                    container.loadData(configData)
                    container.saveData()
                }

                if (container == null || !containerManager.hasContainer(containerId)) {
                    throw IllegalStateException("Container creation failed. Check system storage and logs.")
                }

                scope.launch(Dispatchers.Main) {
                    statusText = "Booting into World of Warcraft..."
                    onLaunch(containerId)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error launching WoW Forever")
                scope.launch(Dispatchers.Main) {
                    errorMessage = e.message ?: "Launch failed"
                    statusText = "Launch failed"
                    isLaunching = false
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0E17),
                        Color(0xFF10192A),
                        Color(0xFF080C14)
                    )
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "WORLD OF WARCRAFT",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Serif,
                    letterSpacing = 3.sp,
                    color = Color(0xFFF0E6D2)
                )

                Text(
                    text = "FOREVER BETA (ARM64 NATIVE)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = Color(0xFFC79C6E)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Snapdragon 8 Gen 2 / Adreno 740 Edition",
                    fontSize = 11.sp,
                    color = Color(0xFF7A92B0)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .border(1.dp, Color(0xFF2A3C54), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2E).copy(alpha = 0.85f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "ENVIRONMENT READINESS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color(0xFFC79C6E)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    CheckItem(label = "ARM64 Binary (WowB-ARM64.exe)", ready = exeExists)
                    Spacer(modifier = Modifier.height(8.dp))
                    CheckItem(label = "Game Asset Archives (Data/)", ready = dataExists)
                    Spacer(modifier = Modifier.height(8.dp))
                    CheckItem(label = "Install Info (.build.info)", ready = buildInfoExists)
                    Spacer(modifier = Modifier.height(8.dp))
                    CheckItem(label = "Turnip Driver & Proton 11 ARM64EC (Bundled)", ready = true)

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFF1E2D44))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Target Path: $gamePath",
                        fontSize = 11.sp,
                        color = Color(0xFF7A92B0),
                        maxLines = 2
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Error: $errorMessage",
                            fontSize = 12.sp,
                            color = Color(0xFFFC8181),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isLaunching) {
                    CircularProgressIndicator(
                        color = Color(0xFFC79C6E),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = statusText,
                        fontSize = 13.sp,
                        color = Color(0xFFE2E8F0),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Button(
                        onClick = { launchGame() },
                        enabled = !isLaunching,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF9E7138),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "PLAY WORLD OF WARCRAFT",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { checkFiles() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF88A0C0))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Refresh Status", fontSize = 12.sp, color = Color(0xFF88A0C0))
                        }

                        if (hasSavedLogin) {
                            TextButton(onClick = {
                                BattleNetSignIn.forget(context)
                                hasSavedLogin = false
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF88A0C0))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Forget Saved Login", fontSize = 12.sp, color = Color(0xFF88A0C0))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CheckItem(label: String, ready: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (ready) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (ready) Color(0xFF48BB78) else Color(0xFFED8936),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (ready) Color(0xFFCBD5E1) else Color(0xFFED8936)
        )
    }
}

private fun ensureGameConfig(root: File) {
    val flavorDir = File(root, "_classic_beta_")
    val flavorInfo = File(flavorDir, ".flavor.info")
    if (!flavorInfo.exists()) {
        flavorInfo.writeText("Product Flavor!STRING:0\nwow_classic_beta\n")
    }
    val configWtf = File(flavorDir, "WTF/Config.wtf")
    if (!configWtf.exists()) {
        configWtf.parentFile?.mkdirs()
        configWtf.writeText(
            """
            SET portal "test"
            SET agentUID "wow_classic_beta"
            SET gxApi "D3D11"
            SET textLocale "enUS"
            SET audioLocale "enUS"
            SET gxMaximize "1"
            SET gxWindowedResolution "1920x1080"
            SET graphicsQuality "3"
            """.trimIndent() + "\n",
        )
    } else {
        val lines = configWtf.readLines()
        val forcedApi = lines.filterNot { it.startsWith("SET gxApi", ignoreCase = true) } + "SET gxApi \"D3D11\""
        if (forcedApi.toSet() != lines.toSet()) configWtf.writeText(forcedApi.joinToString("\n") + "\n")
    }
}

private fun installBundledComponents(context: Context, onStatus: (String) -> Unit): Boolean {
    val assetManager = context.assets
    val cacheDir = context.cacheDir

    // 1. Install Turnip Driver if needed
    try {
        val driverDir = File(context.filesDir, "contents/adrenotools/Turnip-WoW-scheduler-test")
        val metaFile = File(driverDir, "meta.json")
        if (!metaFile.exists()) {
            onStatus("Installing custom Turnip Adreno 740 driver...")
            driverDir.mkdirs()
            assetManager.open("bundled_components/turnip-wow-scheduler-test.zip").use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val outFile = File(driverDir, entry.name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "Error checking/installing bundled driver")
    }

    // 2. Install Wine/Proton 11
    try {
        val protonDir = File(ImageFs.getSharedProtonDir(context), "proton-11.0-90624-arm64ec")
        val protonProfile = File(protonDir, "profile.json")
        val protonBin = File(protonDir, "bin/wine")
        if (!protonProfile.exists() || !protonBin.exists()) {
            onStatus("Extracting Proton 11 ARM64EC (takes ~10s)...")
            protonDir.mkdirs()
            val tempWcp = File(cacheDir, "proton-temp.wcp")
            try {
                assetManager.open("bundled_components/proton-11.0-90624-arm64ec.wcp").use { input ->
                    FileOutputStream(tempWcp).use { output -> input.copyTo(output) }
                }
                val ok = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, tempWcp, protonDir)
                if (!ok) {
                    Timber.e("Failed to extract proton wcp")
                    return false
                }
            } finally {
                tempWcp.delete()
            }
            // Set executable permissions on bin/ files
            val binDir = File(protonDir, "bin")
            if (binDir.exists() && binDir.isDirectory) {
                binDir.listFiles()?.forEach { f ->
                    f.setExecutable(true, false)
                    f.setReadable(true, false)
                }
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "Error extracting Proton 11")
        return false
    }

    // 3. Install DXVK ARM64
    try {
        val dxvkDir = File(ContentsManager.getContentTypeDir(context, ContentProfile.ContentType.CONTENT_TYPE_DXVK), "2.4.1-wow-aarch64-test")
        val dxvkProfile = File(dxvkDir, "profile.json")
        if (!dxvkProfile.exists()) {
            onStatus("Extracting DXVK ARM64...")
            dxvkDir.mkdirs()
            val tempWcp = File(cacheDir, "dxvk-temp.wcp")
            try {
                assetManager.open("bundled_components/dxvk-2.4.1-wow-aarch64-test.wcp").use { input ->
                    FileOutputStream(tempWcp).use { output -> input.copyTo(output) }
                }
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, tempWcp, dxvkDir)
            } finally {
                tempWcp.delete()
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "Error extracting DXVK")
    }

    // Ensure bionic libs
    com.winlator.xenvironment.ImageFsInstaller.ensureBionicLib(context, ImageFs.find(context).rootDir)

    // 4. Sync ContentsManager so all profiles are registered
    val contentsManager = ContentsManager(context)
    contentsManager.syncContents()

    return true
}
