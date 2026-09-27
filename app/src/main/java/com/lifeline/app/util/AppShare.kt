package com.lifeline.app.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.lifeline.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A copy of the installed LifeLine APK, ready to hand to another phone. */
data class ShareableApp(val file: File, val version: String) {
    val sizeMb: String get() = String.format(java.util.Locale.US, "%.1f", file.length() / 1_048_576.0)
}

sealed interface AppShareResult {
    data class Ready(val app: ShareableApp) : AppShareResult
    data class Unavailable(val reason: String) : AppShareResult
}

/**
 * Offline app distribution: LifeLine spreads phone to phone by sharing the APK it was installed
 * from (Android share sheet, Bluetooth, Nearby Share, or the Wi-Fi hotspot page).
 */
object AppShare {
    private const val TAG = "AppShare"
    private const val DIR_NAME = "apk_share" // matches res/xml/file_paths.xml
    private const val FILE_PREFIX = "lifeline-"

    private fun dir(context: Context): File = File(context.cacheDir, DIR_NAME).also { it.mkdirs() }

    /** The APK prepared earlier, if any. */
    fun cached(context: Context): ShareableApp? =
        dir(context).listFiles { f -> f.name.startsWith(FILE_PREFIX) && f.name.endsWith(".apk") }
            ?.maxByOrNull { it.lastModified() }
            ?.let { ShareableApp(it, it.name.removePrefix(FILE_PREFIX).removeSuffix(".apk")) }

    /** Copies the installed APK into the share folder (once per version). */
    suspend fun prepare(context: Context): AppShareResult = withContext(Dispatchers.IO) {
        val info = context.applicationInfo
        if (!info.splitSourceDirs.isNullOrEmpty()) {
            return@withContext AppShareResult.Unavailable(
                "This copy of LifeLine was installed in several parts (e.g. from an app store), " +
                    "so it can't be passed on directly. Install it from an APK file to share it."
            )
        }
        val installed = File(info.sourceDir)
        if (!installed.isFile || installed.length() <= 0L) {
            return@withContext AppShareResult.Unavailable("Couldn't read the installed app.")
        }
        try {
            val version = versionName(context)
            val target = File(dir(context), "$FILE_PREFIX${version.replace(Regex("[^A-Za-z0-9._-]"), "_")}.apk")
            if (!target.isFile || target.length() != installed.length()) {
                val pending = File(target.path + ".new")
                installed.inputStream().use { input -> pending.outputStream().use { input.copyTo(it) } }
                if (!pending.renameTo(target)) {
                    pending.copyTo(target, overwrite = true)
                    pending.delete()
                }
            }
            dir(context).listFiles()?.filter { it != target }?.forEach { it.delete() }
            AppShareResult.Ready(ShareableApp(target, version))
        } catch (e: Exception) {
            Log.w(TAG, "Preparing the APK for sharing failed", e)
            AppShareResult.Unavailable("Not enough space to prepare the app for sharing.")
        }
    }

    /** Android share sheet for the APK (Bluetooth, Nearby Share, messaging apps…). */
    fun shareIntent(context: Context, app: ShareableApp): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", app.file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share LifeLine").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun versionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
            ?.takeIf { it.isNotBlank() } ?: BuildConfig.VERSION_NAME
}
