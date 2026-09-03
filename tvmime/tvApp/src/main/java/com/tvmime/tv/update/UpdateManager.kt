package com.tvmime.tv.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.tvmime.network.XtreamClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersionCode: Int = 0,
    val latestVersionName: String = "",
    val downloadUrl: String = "",
    val changelog: String = ""
)

object UpdateManager {
    private const val VERSION_API_URL = "https://tivimime.vercel.app/api/version"
    private const val DIRECT_TV_APK_URL = "https://tivimime.vercel.app/tv.apk"

    /**
     * Checks if a newer version is available by querying tivimime.vercel.app/api/version
     */
    suspend fun checkForUpdate(context: Context): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val currentCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            }

            val connection = URL(VERSION_API_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", XtreamClient.EVASION_USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")

            if (connection.responseCode == 200) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                val remoteCode = json.optInt("versionCode", currentCode)
                val remoteName = json.optString("versionName", "")
                val apkUrl = json.optString("tvApkUrl", DIRECT_TV_APK_URL)
                val changelog = json.optString("changelog", "Bug fixes and performance enhancements")

                if (remoteCode > currentCode) {
                    return@withContext UpdateInfo(
                        hasUpdate = true,
                        latestVersionCode = remoteCode,
                        latestVersionName = remoteName,
                        downloadUrl = apkUrl.ifBlank { DIRECT_TV_APK_URL },
                        changelog = changelog
                    )
                }
            }
            UpdateInfo(hasUpdate = false)
        } catch (e: Exception) {
            UpdateInfo(hasUpdate = false)
        }
    }

    /**
     * Downloads the APK file into app cache and triggers the native Android Package Installer.
     */
    suspend fun downloadAndInstall(
        activity: Activity,
        downloadUrl: String = DIRECT_TV_APK_URL,
        onProgress: (Int) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Verify unknown sources permission on Android 8+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    withContext(Dispatchers.Main) {
                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${activity.packageName}")
                        }
                        activity.startActivity(intent)
                    }
                    return@withContext Result.failure(
                        SecurityException("Please grant permission to install unknown apps, then click update again.")
                    )
                }
            }

            // 2. Download APK to local cache
            val targetUrl = downloadUrl.ifBlank { DIRECT_TV_APK_URL }
            val connection = URL(targetUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 10000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", XtreamClient.EVASION_USER_AGENT)

            val totalBytes = connection.contentLength
            val apkFile = File(activity.cacheDir, "tvmime_latest_update.apk")
            if (apkFile.exists()) apkFile.delete()

            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var totalRead = 0
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        totalRead += read
                        if (totalBytes > 0) {
                            val progress = ((totalRead * 100L) / totalBytes).toInt()
                            withContext(Dispatchers.Main) { onProgress(progress) }
                        }
                    }
                }
            }

            // 3. Launch Android in-place Package Installer via FileProvider
            withContext(Dispatchers.Main) {
                val apkUri = FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.fileprovider",
                    apkFile
                )

                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                activity.startActivity(installIntent)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
