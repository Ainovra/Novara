package com.novara.app

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object NovaraUpdateManager {

    private const val CHANNEL_ID = "novara_updates"
    private const val DOWNLOAD_NOTIFICATION_ID = 1701
    private const val UPDATED_NOTIFICATION_ID = 1702

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val isMajor: Boolean,
        val apkUrl: String,
        val changelog: List<String>
    )

    data class RemoteConfig(
        val versionCode: Int,
        val versionName: String,
        val isMajor: Boolean,
        val apkUrl: String,
        val ui: JSONObject,
        val features: JSONObject,
        val changelog: List<String>,
        val update: UpdateInfo
    )

    private suspend fun getJson(
        baseUrl: String,
        path: String
    ): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val url = URL(
                baseUrl.trimEnd('/') + "/" + path.trimStart('/')
            )

            val connection =
                url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = 7000
            connection.readTimeout = 7000
            connection.setRequestProperty(
                "Accept",
                "application/json"
            )
            connection.setRequestProperty(
                "Cache-Control",
                "no-cache"
            )
            connection.setRequestProperty(
                "Pragma",
                "no-cache"
            )

            if (connection.responseCode !in 200..299) {
                connection.disconnect()
                return@withContext null
            }

            val body =
                connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

            connection.disconnect()

            JSONObject(body)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseUpdate(json: JSONObject): UpdateInfo {
        val changelog = mutableListOf<String>()
        val array = json.optJSONArray("changelog")

        if (array != null) {
            for (i in 0 until array.length()) {
                changelog.add(array.optString(i))
            }
        }

        return UpdateInfo(
            versionCode = json.optInt("versionCode", 1),
            versionName = json.optString("versionName", ""),
            isMajor = json.optBoolean("isMajor", false),
            apkUrl = json.optString(
                "apkUrl",
                "/download/latest"
            ),
            changelog = changelog
        )
    }

    suspend fun refresh(
        baseUrl: String
    ): RemoteConfig? {
        val json =
            getJson(baseUrl, "/api/app-config")
                ?: return null

        val update = parseUpdate(json)

        return RemoteConfig(
            versionCode = update.versionCode,
            versionName = update.versionName,
            isMajor = update.isMajor,
            apkUrl = update.apkUrl,
            ui = json.optJSONObject("ui")
                ?: JSONObject(),
            features = json.optJSONObject("features")
                ?: JSONObject(),
            changelog = update.changelog,
            update = update
        )
    }

    suspend fun check(
        baseUrl: String
    ): UpdateInfo? {
        val json =
            getJson(baseUrl, "/api/app-version")
                ?: return null

        val update = parseUpdate(json)

        return if (
            update.versionCode >
            NovaraVersion.VERSION_CODE
        ) {
            update
        } else {
            null
        }
    }

    fun resolveUrl(
        baseUrl: String,
        path: String
    ): String {
        if (path.isBlank()) return ""

        return if (
            path.startsWith("http://") ||
            path.startsWith("https://")
        ) {
            path
        } else {
            baseUrl.trimEnd('/') +
                "/" +
                path.trimStart('/')
        }
    }

    fun downloadUpdate(
        context: Context,
        baseUrl: String,
        apkUrl: String,
        versionName: String
    ) {
        val finalUrl =
            resolveUrl(baseUrl, apkUrl)

        if (finalUrl.isBlank()) return

        try {
            createChannel(context)

            val manager =
                context.getSystemService(
                    Context.DOWNLOAD_SERVICE
                ) as DownloadManager

            val request =
                DownloadManager.Request(
                    Uri.parse(finalUrl)
                )
                    .setTitle("Novara AI $versionName")
                    .setDescription(
                        "Downloading Novara update..."
                    )
                    .setMimeType(
                        "application/vnd.android.package-archive"
                    )
                    .setNotificationVisibility(
                        DownloadManager.Request
                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    .setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        "Novara-$versionName.apk"
                    )

            val id = manager.enqueue(request)

            context.getSharedPreferences(
                "novara_updates",
                Context.MODE_PRIVATE
            )
                .edit()
                .putLong("download_id", id)
                .putString(
                    "download_version",
                    versionName
                )
                .apply()

            showDownloadingNotification(
                context,
                versionName
            )
        } catch (_: Exception) {
        }
    }

    fun showDownloadingNotification(
        context: Context,
        versionName: String
    ) {
        createChannel(context)

        val notification =
            NotificationCompat.Builder(
                context,
                CHANNEL_ID
            )
                .setSmallIcon(
                    android.R.drawable.stat_sys_download
                )
                .setContentTitle(
                    "Updating Novara AI"
                )
                .setContentText(
                    "Downloading version $versionName..."
                )
                .setOngoing(true)
                .setPriority(
                    NotificationCompat.PRIORITY_LOW
                )
                .build()

        val nm =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        nm.notify(
            DOWNLOAD_NOTIFICATION_ID,
            notification
        )
    }

    fun showUpdatedNotification(
        context: Context,
        versionName: String
    ) {
        createChannel(context)

        val notification =
            NotificationCompat.Builder(
                context,
                CHANNEL_ID
            )
                .setSmallIcon(
                    android.R.drawable.stat_sys_download_done
                )
                .setContentTitle(
                    "Novara updated"
                )
                .setContentText(
                    "You're now on version $versionName!"
                )
                .setAutoCancel(true)
                .setPriority(
                    NotificationCompat.PRIORITY_DEFAULT
                )
                .build()

        val nm =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        nm.cancel(
            DOWNLOAD_NOTIFICATION_ID
        )

        nm.notify(
            UPDATED_NOTIFICATION_ID,
            notification
        )
    }

    fun createChannel(
        context: Context
    ) {
        val nm =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Novara Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            )

        nm.createNotificationChannel(channel)
    }

    fun installDownloadedApk(
        context: Context,
        uri: Uri
    ) {
        try {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW
                ).apply {
                    setDataAndType(
                        uri,
                        "application/vnd.android.package-archive"
                    )
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            )
        } catch (_: Exception) {
        }
    }
}
