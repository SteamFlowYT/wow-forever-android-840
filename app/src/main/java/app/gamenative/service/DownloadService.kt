package app.gamenative.service

import android.content.Context
import android.os.Environment
import app.gamenative.PrefManager
import app.gamenative.utils.StorageUtils
import timber.log.Timber
import java.io.File

object DownloadService {
    var baseDataDirPath: String = ""
        private set(value) {
            field = value
        }
    var baseCacheDirPath: String = ""
        private set(value) {
            field = value
        }
    // Base path to the app-specific external storage directory (Android/data/<package>)
    var baseExternalAppDirPath: String = ""
        private set(value) {
            field = value
        }

    // all mounted non-primary external volumes (SD cards, USB), discovered at init
    var externalVolumePaths: List<String> = emptyList()
        private set

    fun populateDownloadService(context: Context) {
        baseDataDirPath = context.dataDir.path
        baseCacheDirPath = context.cacheDir.path
        // Prefer the parent of external files dir (Android/data/<package>) so we can create siblings of /files
        val extFiles = context.getExternalFilesDir(null)
        baseExternalAppDirPath = extFiles?.parentFile?.path ?: ""

        val sm = context.getSystemService(android.os.storage.StorageManager::class.java)
        val appFilesDirs = StorageUtils.getAllExternalFilesDirs(context)
            .filter { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }
            .filter { sm?.getStorageVolume(it)?.isPrimary != true }
        // both layouts per volume: legacy Android/data (existing installs) + public root (new installs)
        externalVolumePaths = appFilesDirs
            .flatMap { dir -> listOfNotNull(dir.absolutePath, StorageUtils.publicInstallRoot(dir)?.absolutePath) }
            .distinct()

        migrateExternalStoragePath()
    }

    // Android/data paths pay a ~1000x FUSE metadata penalty (MediaProvider disables kernel
    // caching there); repoint the install pref at the public root so new installs avoid it
    private fun migrateExternalStoragePath() {
        val pref = PrefManager.externalStoragePath
        if (pref.isBlank() || !pref.contains("/Android/data/")) return
        val public = StorageUtils.publicInstallRoot(File(pref)) ?: return
        if (StorageUtils.ensureInstallRoot(public)) {
            Timber.i("Migrating external install root from $pref to ${public.absolutePath}")
            PrefManager.externalStoragePath = public.absolutePath
        }
    }
}
