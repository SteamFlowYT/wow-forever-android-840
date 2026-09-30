package app.gamenative.ui.data

import app.gamenative.enums.AppTheme
import app.gamenative.ui.screen.PluviaScreen
import com.materialkolor.PaletteStyle

data class MainState(
    val appTheme: AppTheme = AppTheme.NIGHT,
    val paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    val resettedScreen: PluviaScreen? = null,
    val currentScreen: PluviaScreen? = PluviaScreen.WoWLauncher,
    val hasLaunched: Boolean = false,
    val loadingDialogVisible: Boolean = false,
    val loadingDialogProgress: Float = 0F,
    val loadingDialogMessage: String = "Loading...",
    val hasCrashedLastStart: Boolean = false,
    val launchedAppId: String = "",
    val bootToContainer: Boolean = false,
    val testGraphics: Boolean = false,
    val diagnostics: Boolean = false,
    val debugRun: Boolean = false,
    val showBootingSplash: Boolean = false,
    val bootingSplashText: String = "Booting...",
    val bootingSplashHeroImageUrl: String = "",
)
