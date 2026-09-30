package app.gamenative.ui

import android.content.Context
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.FileProvider
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.gamenative.BuildConfig
import app.gamenative.Constants
import app.gamenative.MainActivity
import app.gamenative.NetworkMonitor
import app.gamenative.PluviaApp
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.data.GameSource
import app.gamenative.enums.AppTheme
import app.gamenative.enums.LoginResult
import app.gamenative.enums.PathType
import app.gamenative.enums.SaveLocation
import app.gamenative.enums.SyncResult
import app.gamenative.events.AndroidEvent
import app.gamenative.api.DebugReportApi
import app.gamenative.ui.component.dialog.ContainerConfigDialog
import app.gamenative.ui.component.dialog.DebugPreRunDialog
import app.gamenative.ui.component.dialog.DebugReportDialog
import app.gamenative.ui.component.dialog.GameFeedbackDialog
import app.gamenative.ui.component.dialog.LoadingDialog
import app.gamenative.ui.component.dialog.MessageDialog
import app.gamenative.ui.component.dialog.state.DebugReportDialogState
import app.gamenative.ui.component.dialog.state.GameFeedbackDialogState
import app.gamenative.ui.component.dialog.state.MessageDialogState
import app.gamenative.ui.components.BootingSplash
import app.gamenative.ui.enums.AppOptionMenuType
import app.gamenative.launch.LaunchReadiness
import app.gamenative.ui.enums.DialogType
import app.gamenative.ui.enums.Orientation
import app.gamenative.ui.model.MainViewModel
import app.gamenative.ui.screen.PluviaScreen
import app.gamenative.ui.screen.xserver.XServerScreen
import app.gamenative.ui.theme.PluviaTheme
import app.gamenative.ui.util.LocalSnackbarHostController
import app.gamenative.ui.util.SnackbarManager
import app.gamenative.utils.BestConfigService
import app.gamenative.utils.Net
import app.gamenative.utils.ContainerUtils
import app.gamenative.utils.DebugReportUtils
import app.gamenative.utils.CustomGameScanner
import app.gamenative.utils.ManifestInstaller
import app.gamenative.utils.GameFeedbackUtils
import app.gamenative.utils.IntentLaunchManager
import app.gamenative.utils.LaunchDependencies
import com.google.android.play.core.splitcompat.SplitCompat
import com.winlator.container.Container
import com.winlator.container.ContainerData
import com.winlator.container.ContainerManager
import com.winlator.core.StringUtils
import com.winlator.core.TarCompressorUtils
import com.winlator.xenvironment.ImageFSLegacyMigrator
import com.winlator.xenvironment.ImageFs
import com.winlator.xenvironment.ImageFsInstaller
import java.io.File
import java.security.SecureRandom
import java.util.Locale
import java.util.Date
import java.util.EnumSet
import kotlin.reflect.KFunction2
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

private const val PENDING_LAUNCH_TIMEOUT_MS = 10_000L
private const val SNACKBAR_SHOW_TIMEOUT_MS = 15_000L

private sealed class GameResolutionResult {
    data class Success(
        val finalAppId: String,
        val gameId: Int,
        val isCustomGame: Boolean,
    ) : GameResolutionResult()
    data class NotFound(
        val gameId: Int,
        val originalAppId: String,
    ) : GameResolutionResult()
}

private fun resolveGameAppId(context: Context, appId: String): GameResolutionResult {
    val gameSource = ContainerUtils.extractGameSourceFromContainerId(appId)
    val gameId = ContainerUtils.extractGameIdFromContainerId(appId)
    val isInstalled = CustomGameScanner.isGameInstalled(gameId)

    if (!isInstalled) {
        return GameResolutionResult.NotFound(
            gameId = gameId,
            originalAppId = appId,
        )
    }

    val isCustomGame = gameSource == GameSource.CUSTOM_GAME

    return GameResolutionResult.Success(
        finalAppId = appId,
        gameId = gameId,
        isCustomGame = isCustomGame,
    )
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PluviaMain(
    viewModel: MainViewModel = hiltViewModel(),
    navController: NavHostController = rememberNavController(),
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    val state by viewModel.state.collectAsStateWithLifecycle()

    var msgDialogState by rememberSaveable(stateSaver = MessageDialogState.Saver) {
        mutableStateOf(MessageDialogState(false))
    }
    val setMessageDialogState: (MessageDialogState) -> Unit = { msgDialogState = it }

    var gameFeedbackState by rememberSaveable(stateSaver = GameFeedbackDialogState.Saver) {
        mutableStateOf(GameFeedbackDialogState(false))
    }

    var debugReportState by rememberSaveable(stateSaver = DebugReportDialogState.Saver) {
        mutableStateOf(DebugReportDialogState(false))
    }
    var aiDebugOfferAppId by rememberSaveable { mutableStateOf("") }
    var aiDebugOfferTrigger by rememberSaveable { mutableStateOf("") }
    var debugPreRunVisible by rememberSaveable { mutableStateOf(false) }
    var debugPreRunAppId by rememberSaveable { mutableStateOf("") }
    var debugPreRunOffline by rememberSaveable { mutableStateOf(false) }
    val discordTokenPresent by PrefManager.discordRelayTokenPresent

    LaunchedEffect(Unit) {
        if (!PrefManager.discordRelayTokenPresent.value) {
            PrefManager.discordRelayTokenPresent.value = withContext(Dispatchers.IO) {
                PrefManager.discordRelayToken.isNotEmpty()
            }
        }
    }

    var hasBack by rememberSaveable { mutableStateOf(navController.previousBackStackEntry?.destination?.route != null) }


    var gameBackAction by remember { mutableStateOf<() -> Unit?>({}) }

    var openContainerConfigForAppId by rememberSaveable { mutableStateOf<String?>(null) }

    // shared intent-launch path. resolves isOffline at the call site because intent launches can
    // arrive pre-login (cold-boot via stored creds) and downstream cloud-sync needs a settled answer.
    val launchIntentApp: (resolvedAppId: String, hasTemporaryOverride: Boolean) -> Unit = { resolvedAppId, hasTemporaryOverride ->
        MainActivity.wasLaunchedViaExternalIntent = true
        viewModel.setLaunchedAppId(resolvedAppId)
        viewModel.setBootToContainer(false)
        scope.launch(Dispatchers.IO) {
            viewModel.setOffline(false)
            preLaunchApp(
                context = context,
                appId = resolvedAppId,
                useTemporaryOverride = hasTemporaryOverride,
                setLoadingDialogVisible = viewModel::setLoadingDialogVisible,
                setLoadingProgress = viewModel::setLoadingDialogProgress,
                setLoadingMessage = viewModel::setLoadingDialogMessage,
                setMessageDialogState = setMessageDialogState,
                onSuccess = viewModel::launchApp,
            )
        }
    }

    // process pending launch request from cold start (event bus has no replay)
    LaunchedEffect(Unit) {
        MainActivity.consumePendingLaunchRequest()?.let { launchRequest ->
            Timber.i("[PluviaMain]: Processing pending launch request for app ${launchRequest.appId}")
            when (val resolution = resolveGameAppId(context, launchRequest.appId)) {
                is GameResolutionResult.Success -> {
                    if (launchRequest.containerConfig != null) {
                        IntentLaunchManager.applyTemporaryConfigOverride(
                            context, launchRequest.appId, launchRequest.containerConfig,
                        )
                    }
                    launchIntentApp(resolution.finalAppId, launchRequest.containerConfig != null)
                }

                is GameResolutionResult.NotFound -> {
                    val appName = ContainerUtils.resolveGameName(resolution.originalAppId)
                    Timber.w("[PluviaMain]: Game not installed: $appName (${launchRequest.appId})")
                    msgDialogState = MessageDialogState(
                        visible = true,
                        type = DialogType.SYNC_FAIL,
                        title = context.getString(R.string.game_not_installed_title),
                        message = context.getString(R.string.game_not_installed_message, appName),
                        dismissBtnText = context.getString(R.string.ok),
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                MainViewModel.MainUiEvent.LaunchApp -> {
                    navController.navigate(PluviaScreen.XServer.route)
                }

                is MainViewModel.MainUiEvent.ExternalGameLaunch -> {
                    Timber.i("[PluviaMain]: Received ExternalGameLaunch UI event for app ${event.appId}")

                    when (val resolution = resolveGameAppId(context, event.appId)) {
                        is GameResolutionResult.Success -> {
                            Timber.i("[PluviaMain]: Using appId: ${resolution.finalAppId} (original: ${event.appId}, isCustomGame: ${resolution.isCustomGame})")
                            launchIntentApp(
                                resolution.finalAppId,
                                IntentLaunchManager.hasTemporaryOverride(resolution.finalAppId),
                            )
                        }

                        is GameResolutionResult.NotFound -> {
                            val appName = ContainerUtils.resolveGameName(resolution.originalAppId)
                            Timber.w("[PluviaMain]: Game not installed: $appName (${event.appId})")
                            msgDialogState = MessageDialogState(
                                visible = true,
                                type = DialogType.SYNC_FAIL,
                                title = context.getString(R.string.game_not_installed_title),
                                message = context.getString(R.string.game_not_installed_message, appName),
                                dismissBtnText = context.getString(R.string.ok),
                            )
                        }
                    }
                }

                MainViewModel.MainUiEvent.OnBackPressed -> {
                    if (debugReportState.visible) {
                        if (debugReportState.phase != DebugReportDialogState.PHASE_SENDING) {
                            debugReportState = debugReportState.copy(visible = false)
                            PluviaApp.keepAlive = false
                        }
                    } else if (PluviaApp.keepAlive){
                        gameBackAction?.invoke() ?: run { navController.popBackStack() }
                    } else if (hasBack) {
                        // TODO: check if back leads to log out and present confidence modal
                        navController.popBackStack()
                    } else {
                        // TODO: quit app?
                    }
                }

                MainViewModel.MainUiEvent.ShowDiscordSupportDialog -> {
                    msgDialogState = MessageDialogState(
                        visible = true,
                        type = DialogType.DISCORD,
                        title = context.getString(R.string.main_discord_support_title),
                        message = context.getString(R.string.main_discord_support_message),
                        confirmBtnText = context.getString(R.string.main_open_discord),
                        dismissBtnText = context.getString(R.string.close),
                    )
                }

                is MainViewModel.MainUiEvent.ShowGameFeedbackDialog -> {
                    gameFeedbackState = GameFeedbackDialogState(
                        visible = true,
                        appId = event.appId,
                    )
                }

                is MainViewModel.MainUiEvent.ShowDebugReportDialog -> {
                    val dir = File(event.reportDir)
                    val header = withContext(Dispatchers.IO) { DebugReportUtils.readHeader(dir) }
                    debugReportState = DebugReportDialogState(
                        visible = true,
                        appId = event.appId,
                        reportDir = event.reportDir,
                        gameName = header?.optString("gameName").takeUnless { it.isNullOrEmpty() }
                            ?: ContainerUtils.resolveGameName(event.appId),
                        deviceName = header?.optString("deviceName") ?: "",
                        logSizeBytes = withContext(Dispatchers.IO) { DebugReportUtils.logFile(dir).length() },
                    )
                }

                is MainViewModel.MainUiEvent.ShowAiDebugOffer -> {
                    aiDebugOfferAppId = event.appId
                    aiDebugOfferTrigger = event.trigger
                    val offerMessage = context.getString(
                        R.string.debug_offer_message,
                        ContainerUtils.resolveGameName(event.appId),
                    )
                    msgDialogState = MessageDialogState(
                        visible = true,
                        type = DialogType.AI_DEBUG_OFFER,
                        title = context.getString(R.string.debug_offer_title),
                        message = offerMessage + " " + context.getString(R.string.debug_trial_note),
                        confirmBtnText = context.getString(R.string.debug_offer_confirm),
                        dismissBtnText = context.getString(R.string.close),
                    )
                }
            }
        }
    }

    LaunchedEffect(navController) {
        Timber.i("navController changed")

        if (!state.hasLaunched) {
            viewModel.setHasLaunched(true)

            Timber.i("Creating on destination changed listener")

            PluviaApp.onDestinationChangedListener = NavController.OnDestinationChangedListener { _, destination, _ ->
                Timber.i("onDestinationChanged to ${destination.route}")
                // in order not to trigger the screen changed launch effect
                viewModel.setCurrentScreen(destination.route)
            }
            PluviaApp.events.emit(AndroidEvent.StartOrientator)
        } else {
            PluviaApp.onDestinationChangedListener?.let {
                navController.removeOnDestinationChangedListener(it)
            }
        }

        PluviaApp.onDestinationChangedListener?.let {
            navController.addOnDestinationChangedListener(it)
        }
    }

    // TODO merge to VM?
    LaunchedEffect(state.currentScreen) {
        // do the following each time we navigate to a new screen
        if (state.resettedScreen != state.currentScreen) {
            viewModel.setScreen()
            // Log.d("PluviaMain", "Screen changed to $currentScreen, resetting some values")
            // TODO: remove this if statement once XServerScreen orientation change bug is fixed
            if (state.currentScreen != PluviaScreen.XServer) {
                // Hide or show status bar based on if in game or not
                val shouldShowStatusBar = !PrefManager.hideStatusBarWhenNotInGame
                PluviaApp.events.emit(AndroidEvent.SetSystemUIVisibility(shouldShowStatusBar))

                // reset system ui visibility based on user preference
                // TODO: add option for user to set
                // reset available orientations
                PluviaApp.events.emit(AndroidEvent.SetAllowedOrientation(EnumSet.of(Orientation.UNSPECIFIED)))
            }
            // find out if back is available
            hasBack = navController.previousBackStackEntry?.destination?.route != null
        }
    }

    // Listen for save container config prompt
    var pendingSaveAppId by rememberSaveable { mutableStateOf<String?>(null) }
    val onPromptSaveConfig: (AndroidEvent.PromptSaveContainerConfig) -> Unit = { event ->
        pendingSaveAppId = event.appId
        msgDialogState = MessageDialogState(
            visible = true,
            type = DialogType.SAVE_CONTAINER_CONFIG,
            title = context.getString(R.string.save_container_settings_title),
            message = context.getString(R.string.save_container_settings_message),
            confirmBtnText = context.getString(R.string.save),
            dismissBtnText = context.getString(R.string.discard),
        )
    }

    // Listen for game feedback request
    val onShowGameFeedback: (AndroidEvent.ShowGameFeedback) -> Unit = { event ->
        gameFeedbackState = GameFeedbackDialogState(
            visible = true,
            appId = event.appId,
        )
    }

    LaunchedEffect(Unit) {
        PluviaApp.events.on<AndroidEvent.PromptSaveContainerConfig, Unit>(onPromptSaveConfig)
        PluviaApp.events.on<AndroidEvent.ShowGameFeedback, Unit>(onShowGameFeedback)
    }

    DisposableEffect(Unit) {
        onDispose {
            PluviaApp.events.off<AndroidEvent.PromptSaveContainerConfig, Unit>(onPromptSaveConfig)
            PluviaApp.events.off<AndroidEvent.ShowGameFeedback, Unit>(onShowGameFeedback)
        }
    }

    val onDismissRequest: (() -> Unit)?
    val onDismissClick: (() -> Unit)?
    val onConfirmClick: (() -> Unit)?
    var onActionClick: (() -> Unit)? = null
    when (msgDialogState.type) {
        DialogType.DISCORD -> {
            onConfirmClick = {
                setMessageDialogState(MessageDialogState(false))
                uriHandler.openUri("https://discord.gg/2hKv4VfZfE")
            }
            onDismissClick = {
                setMessageDialogState(MessageDialogState(false))
            }
            onDismissRequest = {
                setMessageDialogState(MessageDialogState(false))
            }
        }

        DialogType.SYNC_FAIL -> {
            onConfirmClick = null
            onDismissClick = {
                setMessageDialogState(MessageDialogState(false))
            }
            onDismissRequest = {
                setMessageDialogState(MessageDialogState(false))
            }
        }


        DialogType.EXECUTABLE_NOT_FOUND -> {
            onConfirmClick = null
            onDismissClick = {
                setMessageDialogState(MessageDialogState(false))
            }
            onDismissRequest = {
                setMessageDialogState(MessageDialogState(false))
            }
            onActionClick = {
                setMessageDialogState(MessageDialogState(false))
                openContainerConfigForAppId = state.launchedAppId
            }
        }

        DialogType.CRASH -> {
            onDismissClick = null
            onDismissRequest = {
                viewModel.setHasCrashedLastStart(false)
                setMessageDialogState(MessageDialogState(false))
            }
            onConfirmClick = {
                viewModel.setHasCrashedLastStart(false)
                setMessageDialogState(MessageDialogState(false))
            }
        }

        DialogType.SAVE_CONTAINER_CONFIG -> {
            onConfirmClick = {
                // Save the container config permanently
                pendingSaveAppId?.let { appId ->
                    IntentLaunchManager.getEffectiveContainerConfig(context, appId)?.let { config ->
                        ContainerUtils.applyToContainer(context, appId, config)
                        Timber.i("[PluviaMain]: Saved container configuration for app $appId")
                    }
                    // Clear the temporary override after saving
                    IntentLaunchManager.clearTemporaryOverride(appId)
                }
                pendingSaveAppId = null
                setMessageDialogState(MessageDialogState(false))
            }
            onDismissClick = {
                // Discard the temporary config and restore original
                pendingSaveAppId?.let { appId ->
                    IntentLaunchManager.restoreOriginalConfiguration(context, appId)
                    IntentLaunchManager.clearTemporaryOverride(appId)
                    Timber.i("[PluviaMain]: Discarded temporary config and restored original for app $appId")
                }
                pendingSaveAppId = null
                setMessageDialogState(MessageDialogState(false))
            }
            onDismissRequest = {
                // Treat closing dialog as discard
                pendingSaveAppId?.let { appId ->
                    IntentLaunchManager.restoreOriginalConfiguration(context, appId)
                    IntentLaunchManager.clearTemporaryOverride(appId)
                }
                pendingSaveAppId = null
                setMessageDialogState(MessageDialogState(false))
            }
        }

        DialogType.AI_DEBUG_OFFER -> {
            onConfirmClick = {
                setMessageDialogState(MessageDialogState(false))
                if (aiDebugOfferAppId.isNotEmpty()) {
                    debugPreRunAppId = aiDebugOfferAppId
                    debugPreRunOffline = viewModel.isOffline.value
                    debugPreRunVisible = true
                }
            }
            onDismissClick = {
                setMessageDialogState(MessageDialogState(false))
            }
            onDismissRequest = {
                setMessageDialogState(MessageDialogState(false))
            }
        }

        else -> {
            onDismissRequest = null
            onDismissClick = null
            onConfirmClick = null
        }
    }

    val snackbarController = LocalSnackbarHostController.current
    var exitSnackbarVisible by remember { mutableStateOf(false) }

    LaunchedEffect(snackbarController) {
        SnackbarManager.messages.collect { message ->
            if (
                withTimeoutOrNull(SNACKBAR_SHOW_TIMEOUT_MS) {
                    snackbarController.hostState.showSnackbar(message)
                } == null
            ) {
                Timber.w("[Snackbar]: Display timed out before dismissal")
            }
            // snackbar dismissed (timeout or new message) — reset exit flag
            exitSnackbarVisible = false
        }
    }

    BackHandler(enabled = state.loadingDialogVisible && !PluviaApp.keepAlive) {
        // TODO: Make prelaunch/loading operations cancellable so Back can exit safely.
    }

    PluviaTheme(
        isDark = when (state.appTheme) {
            AppTheme.AUTO -> isSystemInDarkTheme()
            AppTheme.DAY -> false
            AppTheme.NIGHT -> true
            AppTheme.AMOLED -> true
        },
        isAmoled = (state.appTheme == AppTheme.AMOLED),
        style = state.paletteStyle,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LoadingDialog(
                visible = state.loadingDialogVisible,
                progress = state.loadingDialogProgress,
                message = state.loadingDialogMessage,
            )

            MessageDialog(
                visible = msgDialogState.visible,
                onDismissRequest = onDismissRequest,
                onConfirmClick = onConfirmClick,
                confirmBtnText = msgDialogState.confirmBtnText,
                onDismissClick = onDismissClick,
                dismissBtnText = msgDialogState.dismissBtnText,
                onActionClick = onActionClick,
                actionBtnText = msgDialogState.actionBtnText,
                icon = msgDialogState.type.icon,
                title = msgDialogState.title,
                message = msgDialogState.message,
            )

            val scope = rememberCoroutineScope()
            var containerConfigForDialog by remember(openContainerConfigForAppId) { mutableStateOf<ContainerData?>(null) }
            LaunchedEffect(openContainerConfigForAppId) {
                val appId = openContainerConfigForAppId
                if (appId == null) {
                    containerConfigForDialog = null
                    return@LaunchedEffect
                }
                containerConfigForDialog = withContext(Dispatchers.IO) {
                    val container = ContainerUtils.getOrCreateContainer(context, appId)
                    ContainerUtils.toContainerData(container)
                }
            }
            openContainerConfigForAppId?.let { appId ->
                containerConfigForDialog?.let { config ->
                    ContainerConfigDialog(
                        visible = true,
                        title = context.getString(R.string.container_config_title),
                        initialConfig = config,
                        onDismissRequest = { openContainerConfigForAppId = null },
                        onSave = { newConfig ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    ContainerUtils.applyToContainer(context, appId, newConfig)
                                }
                                openContainerConfigForAppId = null
                            }
                        },
                    )
                }
            }

            GameFeedbackDialog(
                state = gameFeedbackState,
                onStateChange = { gameFeedbackState = it },
                onSubmit = { feedbackState ->
                    Timber.d(
                        "GameFeedback: onSubmit called with rating=${feedbackState.rating}, tags=${feedbackState.selectedTags}, text=${
                            feedbackState.feedbackText.take(
                                20,
                            )
                        }",
                    )
                    try {
                        // Get the container for the app
                        val appId = feedbackState.appId
                        Timber.d("GameFeedback: Got appId=$appId")

                        // Submit feedback via worker API
                        Timber.d("GameFeedback: Starting coroutine for submission")
                        viewModel.viewModelScope.launch {
                            Timber.d("GameFeedback: Inside coroutine scope")
                            try {
                                Timber.d("GameFeedback: Calling submitGameFeedback with rating=${feedbackState.rating}")
                                val result = GameFeedbackUtils.submitGameFeedback(
                                    context = context,
                                    appId = appId,
                                    rating = feedbackState.rating,
                                    tags = feedbackState.selectedTags.toList(),
                                    notes = feedbackState.feedbackText.takeIf { it.isNotBlank() },
                                )

                                Timber.d("GameFeedback: Submission returned $result")
                                if (result) {
                                    Timber.d("GameFeedback: Showing success snackbar")
                                    SnackbarManager.show("Thank you for your feedback!")
                                } else {
                                    Timber.d("GameFeedback: Showing failure snackbar")
                                    SnackbarManager.show("Failed to submit feedback")
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "GameFeedback: Error submitting game feedback")
                                SnackbarManager.show("Error submitting feedback")
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "GameFeedback: Error preparing game feedback")
                        SnackbarManager.show("Failed to submit feedback")
                    } finally {
                        // Close the dialog regardless of success
                        Timber.d("GameFeedback: Closing dialog")
                        gameFeedbackState = GameFeedbackDialogState(visible = false)
                        viewModel.onGameFeedbackResolved(context, feedbackState.rating, feedbackState.selectedTags)
                    }
                },
                onDismiss = {
                    gameFeedbackState = GameFeedbackDialogState(visible = false)
                    viewModel.onGameFeedbackResolved(context, null)
                },
                onDiscordSupport = {
                    uriHandler.openUri("https://discord.gg/2hKv4VfZfE")
                },
            )

            val openDiscordConnect: () -> Unit = {
                val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
                    .joinToString("") { "%02x".format(it) }
                PrefManager.discordOauthNonce = nonce
                CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                    .launchUrl(context, Uri.parse("${DebugReportApi.OAUTH_START_URL}?app_state=$nonce"))
            }

            val shareDebugLog: () -> Unit = {
                val reportDir = File(debugReportState.reportDir)
                val files = listOf(DebugReportUtils.logFile(reportDir), DebugReportUtils.perfFile(reportDir), DebugReportUtils.logcatFile(reportDir))
                    .filter { it.exists() }
                if (files.isNotEmpty()) {
                    val uris = files.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
                    val intent = if (uris.size == 1) {
                        Intent(Intent.ACTION_SEND).apply {
                            type = if (files.single().name.endsWith(".gz")) "application/gzip" else "application/json"
                            putExtra(Intent.EXTRA_STREAM, uris.single())
                        }
                    } else {
                        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "*/*"
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                        }
                    }
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(
                        Intent.createChooser(intent, context.getString(R.string.debug_report_share_log_title)),
                    )
                }
            }

            val submitDebugReport: () -> Unit = submit@{
                val current = debugReportState
                if (current.reportDir.isEmpty()) return@submit
                debugReportState = current.copy(visible = true, phase = DebugReportDialogState.PHASE_SENDING)
                scope.launch {
                    val dir = File(current.reportDir)
                    val header = withContext(Dispatchers.IO) {
                        if (DebugReportUtils.writeIssueText(dir, current.issueText)) {
                            DebugReportUtils.readHeader(dir)
                        } else {
                            null
                        }
                    }
                    val logFile = DebugReportUtils.logFile(dir)
                    if (header == null || !logFile.exists()) {
                        debugReportState = debugReportState.copy(phase = DebugReportDialogState.PHASE_ERROR)
                        return@launch
                    }
                    val perfFile = DebugReportUtils.perfFile(dir)
                    val logcatFile = DebugReportUtils.logcatFile(dir)
                    when (val result = DebugReportApi.submit(header, logFile, PrefManager.discordRelayToken, perfFile, logcatFile)) {
                        is DebugReportApi.SubmitResult.Success -> {
                            withContext(Dispatchers.IO) { DebugReportUtils.deleteReport(dir) }
                            debugReportState = debugReportState.copy(
                                phase = DebugReportDialogState.PHASE_SUCCESS,
                                threadUrl = result.threadUrl,
                            )
                        }

                        else -> {
                            debugReportState = debugReportState.copy(phase = DebugReportDialogState.PHASE_ERROR)
                        }
                    }
                }
            }

            DebugPreRunDialog(
                visible = debugPreRunVisible,
                onStart = {
                    debugPreRunVisible = false
                    val appId = debugPreRunAppId
                    if (appId.isNotEmpty()) {
                        val isOffline = debugPreRunOffline
                        viewModel.setLaunchedAppId(appId)
                        viewModel.setBootToContainer(false)
                        viewModel.setTestGraphics(false)
                        viewModel.setDiagnostics(false)
                        viewModel.setDebugRun(true)
                        viewModel.setOffline(isOffline)
                        preLaunchApp(
                            context = context,
                            appId = appId,
                            setLoadingDialogVisible = viewModel::setLoadingDialogVisible,
                            setLoadingProgress = viewModel::setLoadingDialogProgress,
                            setLoadingMessage = viewModel::setLoadingDialogMessage,
                            setMessageDialogState = setMessageDialogState,
                            onSuccess = viewModel::launchApp,
                            bootToContainer = false,
                        )
                    }
                },
                onDismiss = {
                    debugPreRunVisible = false
                },
            )

            val debugFlowActive = debugReportState.visible
            LaunchedEffect(debugFlowActive) {
                if (debugFlowActive) {
                    PluviaApp.keepAlive = true
                }
            }

            DebugReportDialog(
                state = debugReportState,
                hasDiscordToken = discordTokenPresent,
                onStateChange = { debugReportState = it },
                onSend = submitDebugReport,
                onShare = shareDebugLog,
                onConnectDiscord = openDiscordConnect,
                onOpenThread = {
                    if (debugReportState.threadUrl.isNotEmpty()) {
                        uriHandler.openUri(debugReportState.threadUrl)
                    }
                },
                onDismiss = {
                    debugReportState = debugReportState.copy(visible = false)
                    PluviaApp.keepAlive = false
                },
            )

            Box(modifier = Modifier.zIndex(10f)) {
                BootingSplash(
                    visible = state.showBootingSplash,
                    text = state.bootingSplashText,
                    heroImageUrl = state.bootingSplashHeroImageUrl,
                    onAbort = { viewModel.abortBoot() },
                )
            }

            val startDestination = PluviaScreen.WoWLauncher.route

            NavHost(
                navController = navController,
                startDestination = startDestination,
            ) {
                composable(route = PluviaScreen.WoWLauncher.route) {
                    app.gamenative.ui.screen.wow.WoWForeverScreen(
                        onLaunch = { appId ->
                            viewModel.setLaunchedAppId(appId)
                            viewModel.setBootToContainer(false)
                            viewModel.setTestGraphics(false)
                            viewModel.setDiagnostics(false)
                            viewModel.setDebugRun(false)
                            viewModel.setOffline(true)
                            preLaunchApp(
                                context = context,
                                appId = appId,
                                setLoadingDialogVisible = viewModel::setLoadingDialogVisible,
                                setLoadingProgress = viewModel::setLoadingDialogProgress,
                                setLoadingMessage = viewModel::setLoadingDialogMessage,
                                setMessageDialogState = { msgDialogState = it },
                                onSuccess = viewModel::launchApp,
                            )
                        },
                    )
                }
                /** Game Screen **/
                composable(route = PluviaScreen.XServer.route) {
                    val xServerIsOffline by viewModel.isOffline.collectAsStateWithLifecycle()
                    val launchedAppId = state.launchedAppId
                    val hasContainer = remember(launchedAppId) {
                        launchedAppId.isNotEmpty() && ContainerUtils.hasContainer(context, launchedAppId)
                    }
                    if (!hasContainer) {
                        LaunchedEffect(launchedAppId) {
                            Timber.w("XServer route entered without a container for '$launchedAppId', returning to WoW launcher")
                            navController.navigate(PluviaScreen.WoWLauncher.route) {
                                popUpTo(PluviaScreen.XServer.route) { inclusive = true }
                            }
                        }
                        return@composable
                    }
                    XServerScreen(
                        appId = state.launchedAppId,
                        bootToContainer = state.bootToContainer,
                        testGraphics = state.testGraphics,
                        diagnostics = state.diagnostics,
                        debugRun = state.debugRun,
                        isOffline = xServerIsOffline,
                        registerBackAction = { cb ->
                            Timber.d("registerBackAction called: $cb")
                            gameBackAction = cb
                        },
                        navigateBack = {
                            CoroutineScope(Dispatchers.Main).launch {
                                val currentRoute = navController.currentBackStackEntry
                                    ?.destination
                                    ?.route

                                if (currentRoute == PluviaScreen.XServer.route) {
                                    if (MainActivity.wasLaunchedViaExternalIntent) {
                                        Timber.d("[IntentLaunch]: Finishing activity to return to external launcher")
                                        MainActivity.wasLaunchedViaExternalIntent = false
                                        (context as? android.app.Activity)?.finish()
                                    } else {
                                        navController.popBackStack()
                                    }
                                }
                            }
                        },
                        onWindowMapped = { context, window ->
                            viewModel.onWindowMapped(context, window, state.launchedAppId)
                        },
                        onExit = { onComplete ->
                            viewModel.exitSteamApp(context, state.launchedAppId, onComplete)
                        },
                        onGameLaunchError = { error ->
                            viewModel.onGameLaunchError(error)
                        },
                    )
                }
            }

            if (snackbarController.rootOwnsHost) {
                SnackbarHost(
                    hostState = snackbarController.hostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                        .padding(bottom = 16.dp),
                ) { data ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shadowElevation = 4.dp,
                        ) {
                            Text(
                                text = data.visuals.message,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

        }
    }
}

fun preLaunchApp(
    context: Context,
    appId: String,
    useTemporaryOverride: Boolean = false,
    setLoadingDialogVisible: (Boolean) -> Unit,
    setLoadingProgress: (Float) -> Unit,
    setLoadingMessage: (String) -> Unit,
    setMessageDialogState: (MessageDialogState) -> Unit,
    onSuccess: KFunction2<Context, String, Unit>,
    bootToContainer: Boolean = false,
) {
    setLoadingDialogVisible(true)
    // TODO: add a way to cancel
    // TODO: add fail conditions

    val gameId = ContainerUtils.extractGameIdFromContainerId(appId)

    CoroutineScope(Dispatchers.IO).launch {
        if (LaunchReadiness.pending) {
            setLoadingDialogVisible(false)
            (context as? Activity)?.let { LaunchReadiness.resolve(it) }
            return@launch
        }

        // create container if it does not already exist
        // TODO: combine somehow with container creation in HomeLibraryAppScreen
        val containerManager = ContainerManager(context)
        val container = if (useTemporaryOverride) {
            ContainerUtils.getOrCreateContainerWithOverride(context, appId)
        } else {
            ContainerUtils.getOrCreateContainer(context, appId)
        }

        // Clear session metadata on every launch to ensure fresh values
        container.clearSessionMetadata()

        val gameSource = ContainerUtils.extractGameSourceFromContainerId(appId)

        // Migrate legacy on-disk imagefs layout (e.g. legacy Proton → shared paths) before manifest
        // installs or launch deps — resolveMissingManifestInstallRequests can install Proton too.
        val legacyImageFsRoot = File(context.filesDir, "imagefs")
        val migrationOk = ImageFSLegacyMigrator.migrateLegacyDirsIfNeeded(
            context,
            legacyImageFsRoot,
            container.wineVersion,
        )
        if (!migrationOk) {
            Timber.tag("preLaunchApp").e(
                "Legacy ImageFS migration failed: ${legacyImageFsRoot.absolutePath}",
            )
            setLoadingDialogVisible(false)
            setMessageDialogState(
                MessageDialogState(
                    visible = true,
                    type = DialogType.SYNC_FAIL,
                    title = context.getString(R.string.install_failed_title),
                    message = context.getString(R.string.install_failed_message),
                    dismissBtnText = context.getString(R.string.ok),
                ),
            )
            return@launch
        }

        // When "Open container" is used we boot to desktop/file manager only — skip executable check
        if (!bootToContainer) {
            // Verify we have a launch executable for all platforms before proceeding (fail fast, avoid black screen)
            val effectiveExe = CustomGameScanner.getLaunchExecutable(container)
            if (effectiveExe.isBlank()) {
                Timber.tag("preLaunchApp").w("Cannot launch $appId: no executable found (game source: $gameSource)")
                setLoadingDialogVisible(false)
                setMessageDialogState(
                    MessageDialogState(
                        visible = true,
                        type = DialogType.EXECUTABLE_NOT_FOUND,
                        title = context.getString(R.string.game_executable_not_found_title),
                        message = context.getString(R.string.game_executable_not_found),
                        dismissBtnText = context.getString(R.string.ok),
                        actionBtnText = context.getString(AppOptionMenuType.EditContainer.title),
                    ),
                )
                return@launch
            }
        }

        // download any manifest components (wine/proton, dxvk, etc.) the container's config
        // references but that aren't installed yet — all sources, including custom games
        try {
            val configJson = Json.parseToJsonElement(container.containerJson).jsonObject
            val missingRequests = BestConfigService.resolveMissingManifestInstallRequests(
                context, configJson, "exact_gpu_match",
            )
            for (request in missingRequests) {
                setLoadingMessage(context.getString(R.string.main_downloading_entry, request.entry.name))
                try {
                    ManifestInstaller.installManifestEntry(
                        context, request.entry, request.isDriver, request.contentType,
                    ) { progress -> setLoadingProgress(progress.coerceIn(0f, 1f)) }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to install ${request.entry.name}, continuing")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to install manifest components")
            setLoadingDialogVisible(false)
            return@launch
        }

        // set up Ubuntu file system — download required files and install
        SplitCompat.install(context)

        try {
            LaunchDependencies().ensureLaunchDependencies(
                context = context,
                container = container,
                gameSource = gameSource,
                gameId = gameId,
                setLoadingMessage = setLoadingMessage,
                setLoadingProgress = setLoadingProgress,
            )
        } catch (e: Exception) {
            Timber.tag("preLaunchApp").e(e, "ensureLaunchDependencies failed")
            setLoadingDialogVisible(false)
            setMessageDialogState(
                MessageDialogState(
                    visible = true,
                    type = DialogType.SYNC_FAIL,
                    title = context.getString(R.string.launch_dependency_failed_title),
                    message = e.message ?: context.getString(R.string.launch_dependency_failed_message),
                    dismissBtnText = context.getString(R.string.ok),
                ),
            )
            return@launch
        }

        try {
            val imageFsArchive = if (container.containerVariant.equals(Container.BIONIC)) "imagefs_bionic.txz" else "imagefs_gamenative.txz"
            if (!File(context.filesDir, imageFsArchive).exists() && context.assets.list("")?.contains(imageFsArchive) != true) {
                setLoadingMessage("Downloading first-time files")
                Net.fetchFileWithFallback(imageFsArchive, File(context.filesDir, imageFsArchive), setLoadingProgress)
            }
            if (container.containerVariant.equals(Container.GLIBC) &&
                !File(context.filesDir, "imagefs_patches_gamenative.tzst").exists()
            ) {
                setLoadingMessage("Downloading Wine")
                Net.fetchFileWithFallback("imagefs_patches_gamenative.tzst", File(context.filesDir, "imagefs_patches_gamenative.tzst"), setLoadingProgress)
            }
        } catch (e: Exception) {
            Timber.tag("preLaunchApp").e(e, "File download failed")
            setLoadingDialogVisible(false)
            setMessageDialogState(
                MessageDialogState(
                    visible = true,
                    type = DialogType.SYNC_FAIL,
                    title = context.getString(R.string.download_failed_title),
                    message = e.message ?: context.getString(R.string.download_failed_message),
                    dismissBtnText = context.getString(R.string.ok),
                ),
            )
            return@launch
        }

        val loadingMessage = if (container.containerVariant.equals(Container.GLIBC)) {
            context.getString(R.string.main_installing_glibc)
        } else {
            context.getString(R.string.main_installing_bionic)
        }
        setLoadingMessage(loadingMessage)
        val imageFsInstallSuccess =
            ImageFsInstaller.installIfNeededFuture(context, context.assets, container) { progress ->
                setLoadingProgress(progress / 100f)
            }.get()

        if (!imageFsInstallSuccess) {
            Timber.tag("preLaunchApp").e("ImageFS installation failed")
            setLoadingDialogVisible(false)
            setMessageDialogState(
                MessageDialogState(
                    visible = true,
                    type = DialogType.SYNC_FAIL,
                    title = context.getString(R.string.install_failed_title),
                    message = context.getString(R.string.install_failed_message),
                    dismissBtnText = context.getString(R.string.ok),
                ),
            )
            return@launch
        }

        setLoadingMessage(context.getString(R.string.main_loading))
        setLoadingProgress(-1f)

        // must activate container before downloading save files
        containerManager.activateContainer(container)

        setLoadingDialogVisible(false)
        onSuccess(context, appId)
    }
}
