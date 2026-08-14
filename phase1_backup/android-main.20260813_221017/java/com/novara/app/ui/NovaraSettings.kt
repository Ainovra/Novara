package com.novara.app.ui

import android.content.Context

object NovaraSettings {

    private const val PREFS = "novara_settings"

    fun get(context: Context, key: String, default: Boolean): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(key, default)

    fun set(
        context: Context,
        key: String,
        value: Boolean
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, value)
            .apply()
    }

    fun getModel(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("model", "fast") ?: "fast"

    fun setModel(
        context: Context,
        value: String
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("model", value)
            .apply()
    }
}
