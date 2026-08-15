package com.novara.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String
    )

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
                .removePrefix("v")

            val releaseCode = tag
                .substringBefore("-")
                .split(".")
                .lastOrNull()
                ?.toIntOrNull()
                ?: return@withContext null

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

            if (releaseCode > BuildConfig.VERSION_CODE) {
                UpdateInfo(
                    versionCode = releaseCode,
                    versionName = tag,
                    apkUrl = finalUrl
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun downloadAndInstall(
        context: Context,
        info: UpdateInfo
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
