package com.novara.app

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebView
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class NovaraApp : Application() {
    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val crashFile = File("/storage/emulated/0/Download/novara_crash.txt")
                crashFile.writeText(sw.toString())
            } catch (e: Exception) {
                // ignore
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(10)
        }

        CookieManager.getInstance().setAcceptCookie(true)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                WebView.setDataDirectorySuffix("novara")
            } catch (e: IllegalStateException) {
                // WebView already initialized elsewhere; safe to ignore
            }
        }
    }
}
