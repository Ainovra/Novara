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
            val repo = getRepo() ?: return@withContext null

            val url = URL(
                "https://api.github.com/repos/$repo/releases/latest"
            )

            val connection =
                url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty(
                "Accept",
                "application/vnd.github+json"
            )
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode != 200)
                return@withContext null

            val json =
                JSONObject(
                    connection.inputStream
                        .bufferedReader()
                        .use { it.readText() }
                )

            val tag =
                json.optString("tag_name")
                    .removePrefix("v")

            val releaseVersion =
                tag.substringBefore("-")
                    .split(".")
                    .mapNotNull { it.toIntOrNull() }

            val releaseCode =
                releaseVersion.lastOrNull() ?: return@withContext null

            val assets =
                json.optJSONArray("assets")
                    ?: return@withContext null

            var apkUrl: String? = null

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")

                if (name.endsWith(".apk", true)) {
                    apkUrl =
                        asset.optString("browser_download_url")
                    break
                }
            }

            val finalUrl =
                apkUrl ?: return@withContext null

            if (releaseCode >
                BuildConfig.VERSION_CODE
            ) {
                UpdateInfo(
                    releaseCode,
                    tag,
                    finalUrl
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
        val request =
            DownloadManager.Request(
                Uri.parse(info.apkUrl)
            )

        request.setTitle(
            "Novara ${info.versionName}"
        )

        request.setDescription(
            "Downloading Novara update…"
        )

        request.setNotificationVisibility(
            DownloadManager.Request
                .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
        )

        request.setDestinationInExternalFilesDir(
            context,
            Environment.DIRECTORY_DOWNLOADS,
            "novara-update.apk"
        )

        val manager =
            context.getSystemService(
                Context.DOWNLOAD_SERVICE
            ) as DownloadManager

        manager.enqueue(request)
    }

    private fun getRepo(): String? {
        return BuildConfig.GITHUB_REPO
            .takeIf { it.isNotBlank() }
    }
}
