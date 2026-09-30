package app.gamenative.enums

import timber.log.Timber

enum class PathType {
    GameInstall,
    SteamUserData,
    WinMyDocuments,
    WinAppDataLocal,
    WinAppDataLocalLow,
    WinAppDataRoaming,
    WinSavedGames,
    WinProgramData,
    LinuxHome,
    LinuxXdgDataHome,
    LinuxXdgConfigHome,
    MacHome,
    MacAppSupport,
    None,
    Root,
    ;

    val isWindows: Boolean
        get() = when (this) {
            GameInstall,
            SteamUserData,
            WinMyDocuments,
            WinAppDataLocal,
            WinAppDataLocalLow,
            WinAppDataRoaming,
            WinSavedGames,
            WinProgramData,
            Root,
            -> true
            else -> false
        }

    companion object {
        val DEFAULT = SteamUserData

        fun from(keyValue: String?): PathType {
            return when (keyValue?.lowercase()) {
                "%${GameInstall.name.lowercase()}%",
                GameInstall.name.lowercase(),
                -> GameInstall
                "%${SteamUserData.name.lowercase()}%",
                SteamUserData.name.lowercase(),
                "steamuserbasestorage",
                "%steamuserbasestorage%",
                -> SteamUserData
                "%${WinMyDocuments.name.lowercase()}%",
                WinMyDocuments.name.lowercase(),
                "steamclouddocuments",
                "%steamclouddocuments%",
                -> WinMyDocuments
                "%${WinAppDataLocal.name.lowercase()}%",
                WinAppDataLocal.name.lowercase(),
                -> WinAppDataLocal
                "%${WinAppDataLocalLow.name.lowercase()}%",
                WinAppDataLocalLow.name.lowercase(),
                -> WinAppDataLocalLow
                "%${WinAppDataRoaming.name.lowercase()}%",
                WinAppDataRoaming.name.lowercase(),
                -> WinAppDataRoaming
                "%${WinSavedGames.name.lowercase()}%",
                WinSavedGames.name.lowercase(),
                -> WinSavedGames
                "%${WinProgramData.name.lowercase()}%",
                WinProgramData.name.lowercase(),
                -> WinProgramData
                "%${LinuxHome.name.lowercase()}%",
                LinuxHome.name.lowercase(),
                -> LinuxHome
                "%${LinuxXdgDataHome.name.lowercase()}%",
                LinuxXdgDataHome.name.lowercase(),
                -> LinuxXdgDataHome
                "%${LinuxXdgConfigHome.name.lowercase()}%",
                LinuxXdgConfigHome.name.lowercase(),
                -> LinuxXdgConfigHome
                "%${MacHome.name.lowercase()}%",
                MacHome.name.lowercase(),
                -> MacHome
                "%${MacAppSupport.name.lowercase()}%",
                MacAppSupport.name.lowercase(),
                -> MacAppSupport
                "%${Root.name.lowercase()}%",
                Root.name.lowercase(),
                "windowshome",
                "%windowshome%",
                "%root_mod%",
                "root_mod",
                -> Root
                else -> {
                    if (keyValue != null) {
                        Timber.w("Could not identify $keyValue as PathType")
                    }
                    None
                }
            }
        }
    }
}
