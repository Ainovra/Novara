package com.novara.app

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    data class UpdateInfo(
        val versionName: String,
        val apkUrl: String,
        val isMajorUpdate: Boolean
    )

    /**
     * Parses a version string like "1.6" or "1.6.2" into a 3-part
     * [major, minor, patch] list, padding missing parts with 0.
     * Works regardless of whether the project uses 2-part or 3-part versions.
     */
    private fun parseVersion(raw: String): List<Int> {
        val parts = raw.trim()
            .removePrefix("v")
            .substringBefore("-")
            .split(".")
            .map { it.toIntOrNull() ?: 0 }
        return List(3) { i -> parts.getOrElse(i) { 0 } }
    }

    /** True if [latest] is strictly newer than [current]. */
    private fun isNewer(latest: List<Int>, current: List<Int>): Boolean {
        for (i in 0..2) {
            if (latest[i] != current[i]) return latest[i] > current[i]
        }
        return false
    }

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val repo = BuildConfig.GITHUB_REPO
                .takeIf { it.isNotBlank() && it != "REPLACE_GITHUB_REPO" }
                ?: return@withContext null

            val url = URL(
                "https://api.github.com/repos/$repo/releases/latest"
            )

            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty(
                "Accept",
                "application/vnd.github+json"
            )
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode != 200) {
                connection.disconnect()
                return@withContext null
            }

            val json = JSONObject(
                connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }
            )

            connection.disconnect()

            val tag = json.optString("tag_name")

            val latestParts = parseVersion(tag)
            val currentParts = parseVersion(BuildConfig.VERSION_NAME)

            if (!isNewer(latestParts, currentParts)) {
                return@withContext null
            }

            val isMajor = latestParts[0] > currentParts[0]

            val assets = json.optJSONArray("assets")
                ?: return@withContext null

            var apkUrl: String? = null

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")

                if (name.endsWith(".apk", true)) {
                    apkUrl = asset.optString("browser_download_url")
                    break
                }
            }

            val finalUrl = apkUrl ?: return@withContext null

            UpdateInfo(
                versionName = tag.removePrefix("v"),
                apkUrl = finalUrl,
                isMajorUpdate = isMajor
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Downloads the update APK. If [autoInstallOnComplete] is true, a
     * receiver is registered that automatically launches the installer
     * the moment the download finishes (still requires one OS-level
     * tap to confirm install — that's an Android security requirement
     * that cannot be bypassed without device-owner/root permissions).
     */
    fun downloadAndInstall(
        context: Context,
        info: UpdateInfo,
        autoInstallOnComplete: Boolean = false
    ) {
        val fileName = "novara-update.apk"

        val request = DownloadManager.Request(
            Uri.parse(info.apkUrl)
        )

        request.setTitle("Novara ${info.versionName}")
        request.setDescription("Downloading Novara update…")
        request.setNotificationVisibility(
            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
        )

        request.setDestinationInExternalFilesDir(
            context,
            Environment.DIRECTORY_DOWNLOADS,
            fileName
        )

        val manager = context.getSystemService(
            Context.DOWNLOAD_SERVICE
        ) as DownloadManager

        val downloadId = manager.enqueue(request)

        UpdateDownloadState.downloadId = downloadId

        if (autoInstallOnComplete) {
            val appContext = context.applicationContext

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val completedId = intent.getLongExtra(
                        DownloadManager.EXTRA_DOWNLOAD_ID, -1L
                    )
                    if (completedId == downloadId) {
                        installDownloadedApk(appContext)
                        try {
                            appContext.unregisterReceiver(this)
                        } catch (_: Exception) {
                            // Already unregistered; ignore.
                        }
                    }
                }
            }

            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    fun installDownloadedApk(context: Context) {
        try {
            val apkFile = java.io.File(
                context.getExternalFilesDir(
                    Environment.DIRECTORY_DOWNLOADS
                ),
                "novara-update.apk"
            )

            if (!apkFile.exists()) return

            val uri = FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    uri,
                    "application/vnd.android.package-archive"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        } catch (_: Exception) {
            // Ignore installer errors.
        }
    }

    object UpdateDownloadState {
        var downloadId: Long = -1L
    }
}
