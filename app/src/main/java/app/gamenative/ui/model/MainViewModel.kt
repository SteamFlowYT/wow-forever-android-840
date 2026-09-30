package app.gamenative.ui.model

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gamenative.PluviaApp
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.data.GameSource
import app.gamenative.di.IAppTheme
import app.gamenative.enums.AppTheme
import app.gamenative.events.AndroidEvent
import app.gamenative.ui.enums.Orientation
import java.util.EnumSet
import app.gamenative.utils.CustomGameScanner
import app.gamenative.ui.data.MainState
import app.gamenative.ui.screen.PluviaScreen
import app.gamenative.ui.util.SnackbarManager
import app.gamenative.utils.ContainerUtils
import app.gamenative.utils.DebugReportUtils
import app.gamenative.utils.IntentLaunchManager
import app.gamenative.utils.WineProcessSnapshotHelper
import com.materialkolor.PaletteStyle
import com.winlator.xserver.Window
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltViewModel
class MainViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val appTheme: IAppTheme,
) : ViewModel() {

    companion object {
        private const val KEY_CURRENT_SCREEN_ROUTE = "current_screen_route"
        private const val SHORT_SESSION_MS = 90 * 1000L
        private const val AI_DEBUG_OFFER_INTERVAL_MS = 3 * 24 * 60 * 60 * 1000L
        private const val LOW_RATING_MAX = 3
        private val FAILURE_TAGS = setOf("does_not_open", "no_graphics", "directx_error")

        var gamePlayedThisSession = false
            private set
    }

    private var gameSessionStartTime = 0L
    private var gameWindowSeen = false
    private var pendingFeedbackAppId: String? = null

    fun onGameFeedbackResolved(context: Context, rating: Int?, tags: Set<String> = emptySet()) {
        val appId = pendingFeedbackAppId ?: return
        pendingFeedbackAppId = null
        if (rating != null && (rating <= LOW_RATING_MAX || tags.any { it in FAILURE_TAGS })) {
            viewModelScope.launch { offerAiDebugRun(context, appId, "low_rating") }
        }
    }

    sealed class MainUiEvent {
        data object OnBackPressed : MainUiEvent()
        data object LaunchApp : MainUiEvent()
        data class ExternalGameLaunch(val appId: String) : MainUiEvent()
        data object ShowDiscordSupportDialog : MainUiEvent()
        data class ShowGameFeedbackDialog(val appId: String) : MainUiEvent()
        data class ShowDebugReportDialog(val appId: String, val reportDir: String) : MainUiEvent()
        data class ShowAiDebugOffer(val appId: String, val trigger: String) : MainUiEvent()
    }

    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()

    private val _uiEvent = Channel<MainUiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    private val _offline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> get() = _offline

    fun setOffline(value: Boolean) {
        _offline.value = value
    }

    private val onBackPressed: (AndroidEvent.BackPressed) -> Unit = {
        viewModelScope.launch {
            _uiEvent.send(MainUiEvent.OnBackPressed)
        }
    }

    private val onExternalGameLaunch: (AndroidEvent.ExternalGameLaunch) -> Unit = {
        Timber.tag("MainViewModel").i("Received external game launch event for app ${it.appId}")
        viewModelScope.launch {
            Timber.tag("MainViewModel").i("Sending ExternalGameLaunch UI event for app ${it.appId}")
            _uiEvent.send(MainUiEvent.ExternalGameLaunch(it.appId))
        }
    }

    private val onSetBootingSplashText: (AndroidEvent.SetBootingSplashText) -> Unit = {
        setBootingSplashText(it.text)
        setShowBootingSplash(true)
    }

    private val onClearBootingSplash: (AndroidEvent.ClearBootingSplash) -> Unit = {
        bootingSplashTimeoutJob?.cancel()
        bootingSplashTimeoutJob = null
        setShowBootingSplash(false)
    }

    private var bootingSplashTimeoutJob: Job? = null

    init {
        // Restore persisted screen from SavedStateHandle if available
        val persistedRoute = savedStateHandle.get<String>(KEY_CURRENT_SCREEN_ROUTE)
        val restoredScreen = when (persistedRoute) {
            PluviaScreen.XServer.route -> PluviaScreen.XServer
            else -> null
        }

        _state.update {
            it.copy(
                hasCrashedLastStart = PrefManager.recentlyCrashed,
                launchedAppId = "",
                currentScreen = restoredScreen,
            )
        }

        // Register event handlers
        PluviaApp.events.on<AndroidEvent.BackPressed, Unit>(onBackPressed)
        PluviaApp.events.on<AndroidEvent.ExternalGameLaunch, Unit>(onExternalGameLaunch)
        PluviaApp.events.on<AndroidEvent.SetBootingSplashText, Unit>(onSetBootingSplashText)
        PluviaApp.events.on<AndroidEvent.ClearBootingSplash, Unit>(onClearBootingSplash)

        // Collect theme preferences
        viewModelScope.launch {
            appTheme.themeFlow.collect { value ->
                _state.update { it.copy(appTheme = value) }
            }
        }

        viewModelScope.launch {
            appTheme.paletteFlow.collect { value ->
                _state.update { it.copy(paletteStyle = value) }
            }
        }
    }

    override fun onCleared() {
        PluviaApp.events.off<AndroidEvent.BackPressed, Unit>(onBackPressed)
        PluviaApp.events.off<AndroidEvent.ExternalGameLaunch, Unit>(onExternalGameLaunch)
        PluviaApp.events.off<AndroidEvent.SetBootingSplashText, Unit>(onSetBootingSplashText)
        PluviaApp.events.off<AndroidEvent.ClearBootingSplash, Unit>(onClearBootingSplash)
    }

    fun setTheme(value: AppTheme) {
        appTheme.currentTheme = value
    }

    fun setPalette(value: PaletteStyle) {
        appTheme.currentPalette = value
    }

    fun setLoadingDialogVisible(value: Boolean) {
        _state.update { it.copy(loadingDialogVisible = value) }
    }

    fun setLoadingDialogProgress(value: Float) {
        _state.update { it.copy(loadingDialogProgress = value) }
    }

    fun setLoadingDialogMessage(value: String) {
        _state.update { it.copy(loadingDialogMessage = value) }
    }

    fun setHasLaunched(value: Boolean) {
        _state.update { it.copy(hasLaunched = value) }
    }

    fun setShowBootingSplash(value: Boolean) {
        PluviaApp.isBootingSplashShowing = value
        _state.update { it.copy(showBootingSplash = value) }
    }

    fun setBootingSplashText(value: String) {
        _state.update { it.copy(bootingSplashText = value) }
    }

    fun setBootingSplashHeroImageUrl(url: String) {
        _state.update { it.copy(bootingSplashHeroImageUrl = url) }
    }

    fun setCurrentScreen(currentScreen: String?) {
        // Route matching accounts for query params and path params in templates
        // e.g., "home?offline={offline}" should match Home, "chat/{id}" should match Chat
        val screen = when (currentScreen) {
            PluviaScreen.XServer.route -> PluviaScreen.XServer
            else -> PluviaScreen.WoWLauncher
        }

        setCurrentScreen(screen)
    }

    fun setCurrentScreen(value: PluviaScreen) {
        _state.update { it.copy(currentScreen = value) }
        savedStateHandle[KEY_CURRENT_SCREEN_ROUTE] = value.route
    }

    fun setHasCrashedLastStart(value: Boolean) {
        if (value.not()) {
            PrefManager.recentlyCrashed = false
        }
        _state.update { it.copy(hasCrashedLastStart = value) }
    }

    fun setScreen() {
        _state.update { it.copy(resettedScreen = it.currentScreen) }
    }

    fun setLaunchedAppId(value: String) {
        _state.update { it.copy(launchedAppId = value) }
    }

    fun setBootToContainer(value: Boolean) {
        _state.update { it.copy(bootToContainer = value) }
    }

    fun setTestGraphics(value: Boolean) {
        _state.update { it.copy(testGraphics = value) }
    }

    fun setDiagnostics(value: Boolean) {
        _state.update { it.copy(diagnostics = value) }
    }

    fun setDebugRun(value: Boolean) {
        _state.update { it.copy(debugRun = value) }
    }

    fun launchApp(context: Context, appId: String) {
        gameSessionStartTime = System.currentTimeMillis()
        gameWindowSeen = false
        gamePlayedThisSession = true
        PrefManager.hasAttemptedGameLaunch = true
        // Show booting splash before launching the app
        viewModelScope.launch {
            setShowBootingSplash(true)
            PluviaApp.events.emit(AndroidEvent.SetAllowedOrientation(PrefManager.allowedOrientation))

            val heroUrl = withContext(Dispatchers.IO) {
                when (ContainerUtils.extractGameSourceFromContainerId(appId)) {
                    GameSource.CUSTOM_GAME -> {
                        val folderPath = CustomGameScanner.getFolderPathFromAppId(appId) ?: return@withContext ""
                        val folder = java.io.File(folderPath)
                        val heroFile = folder.listFiles()?.firstOrNull { file ->
                            file.isFile &&
                                file.name.startsWith("steamgriddb_hero", ignoreCase = true) &&
                                !file.name.contains("grid_", ignoreCase = true) &&
                                (file.name.endsWith(".png", ignoreCase = true) ||
                                    file.name.endsWith(".jpg", ignoreCase = true) ||
                                    file.name.endsWith(".webp", ignoreCase = true))
                        }
                        heroFile?.let { android.net.Uri.fromFile(it).toString() } ?: ""
                    }
                    else -> ""
                }
            }
            setBootingSplashHeroImageUrl(heroUrl)

            val apiJob = viewModelScope.async(Dispatchers.IO) {
                ContainerUtils.getOrCreateContainer(context, appId)
            }

            // Small delay to ensure the splash screen is visible before proceeding
            delay(100)

            apiJob.await()
            _uiEvent.send(MainUiEvent.LaunchApp)
        }
    }

    fun exitSteamApp(context: Context, appId: String, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                Timber.tag("Exit").i("Exiting, getting feedback for appId: $appId")
                bootingSplashTimeoutJob?.cancel()
                bootingSplashTimeoutJob = null
                setShowBootingSplash(false)
                PluviaApp.events.emit(AndroidEvent.ClearBootingSplash)
                // Check if we have a temporary override before doing anything
                val hadTemporaryOverride = IntentLaunchManager.hasTemporaryOverride(appId)


                // Prompt user to save temporary container configuration if one was applied
                if (hadTemporaryOverride) {
                    PluviaApp.events.emit(AndroidEvent.PromptSaveContainerConfig(appId))
                    // Dialog handler in PluviaMain manages the save/discard logic
                }

                // After app closes, check if we need to show the feedback dialog
                // Show feedback if: first time running this game OR config was changed
                val sessionLengthMs = if (gameSessionStartTime > 0) {
                    System.currentTimeMillis() - gameSessionStartTime
                } else {
                    0L
                }
                gameSessionStartTime = 0L

                if (_state.value.debugRun) {
                    setDebugRun(false)
                    val reportDir = DebugReportUtils.createPendingReport(context, appId)
                    if (reportDir != null) {
                        _uiEvent.send(MainUiEvent.ShowDebugReportDialog(appId, reportDir.absolutePath))
                    } else {
                        SnackbarManager.show(context.getString(R.string.debug_report_no_log))
                    }
                    return@launch
                }

                val aiOfferRequested = maybeOfferAiDebugRun(context, appId, sessionLengthMs)
                if (aiOfferRequested) {
                    return@launch
                }

                var feedbackRequested = false
                try {
                    // Show feedback for all stores except custom games.
                    val feedbackGameSource = ContainerUtils.extractGameSourceFromContainerId(appId)
                    if (feedbackGameSource != GameSource.CUSTOM_GAME) {
                        val container = ContainerUtils.getContainer(context, appId)

                        val shown = container.getExtra("discord_support_prompt_shown", "false") == "true"
                        val configChanged = container.getExtra("config_changed", "false") == "true"
                        if (!shown) {
                            container.putExtra("discord_support_prompt_shown", "true")
                            container.saveData()
                            feedbackRequested = true
                            _uiEvent.send(MainUiEvent.ShowGameFeedbackDialog(appId))
                        }

                        // Only show feedback if container config was changed before this game run
                        if (configChanged) {
                            // Clear the flag
                            container.putExtra("config_changed", "false")
                            container.saveData()
                            // Show the feedback dialog
                            feedbackRequested = true
                            _uiEvent.send(MainUiEvent.ShowGameFeedbackDialog(appId))
                        }
                    } else {
                        Timber.d("Custom game detected, not showing feedback")
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to check/update feedback dialog state for $appId")
                }

                if (feedbackRequested) {
                    pendingFeedbackAppId = appId
                }
            } finally {
                onComplete?.invoke()
            }
        }
    }

    private suspend fun maybeOfferAiDebugRun(context: Context, appId: String, sessionLengthMs: Long): Boolean {
        val trigger = when {
            !gameWindowSeen -> "no_window"
            sessionLengthMs in 1 until SHORT_SESSION_MS -> "short_session"
            else -> return false
        }
        return offerAiDebugRun(context, appId, trigger)
    }

    private suspend fun offerAiDebugRun(context: Context, appId: String, trigger: String): Boolean {
        if (PrefManager.hideAiFeatures) return false
        return try {
            val container = ContainerUtils.getContainer(context, appId)
            val now = System.currentTimeMillis()
            val lastShownForGame = container.getExtra("ai_debug_offer_last_shown", "0").toLongOrNull() ?: 0L
            if (now - lastShownForGame < AI_DEBUG_OFFER_INTERVAL_MS) return false

            container.putExtra("ai_debug_offer_last_shown", now.toString())
            container.saveData()
            _uiEvent.send(MainUiEvent.ShowAiDebugOffer(appId, trigger))
            true
        } catch (e: Exception) {
            Timber.w(e, "Failed to evaluate AI debug offer for $appId")
            false
        }
    }

    fun onWindowMapped(context: Context, window: Window, appId: String) {
        viewModelScope.launch {
            if (window.isApplicationWindow() && !WineProcessSnapshotHelper.isSystemProcessName(window.className)) {
                gameWindowSeen = true
            }
            bootingSplashTimeoutJob?.cancel()
            bootingSplashTimeoutJob = null
            setShowBootingSplash(false)
            PluviaApp.events.emit(AndroidEvent.ClearBootingSplash)
        }
    }

    fun onGameLaunchError(error: String) {
        viewModelScope.launch {
            // Hide the splash screen if it's still showing
            bootingSplashTimeoutJob?.cancel()
            bootingSplashTimeoutJob = null
            setShowBootingSplash(false)
            PluviaApp.events.emit(AndroidEvent.ClearBootingSplash)

            // You could also show an error dialog here if needed
            Timber.tag("MainViewModel").e("Game launch error: $error")
        }
    }

    /** The splash's back button: hide the splash and close the guest the way a blocked session does. */
    fun abortBoot() {
        viewModelScope.launch {
            Timber.tag("MainViewModel").i("Boot aborted from the splash")
            bootingSplashTimeoutJob?.cancel()
            bootingSplashTimeoutJob = null
            setShowBootingSplash(false)
            PluviaApp.events.emit(AndroidEvent.ClearBootingSplash)
            PluviaApp.events.emit(AndroidEvent.ForceCloseApp)
        }
    }

}
