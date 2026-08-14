package com.novara.app.ui

import android.content.Context

object NovaraSettings {

    private const val PREFS = "novara_settings"

    fun get(
        context: Context,
        key: String,
        default: Boolean
    ): Boolean {
        return context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(key, default)
    }

    fun set(
        context: Context,
        key: String,
        value: Boolean
    ) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, value)
            .apply()
    }

    fun getString(
        context: Context,
        key: String,
        default: String
    ): String {
        return context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key, default)
            ?: default
    }

    fun setString(
        context: Context,
        key: String,
        value: String
    ) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key, value)
            .apply()
    }

    fun getFloat(
        context: Context,
        key: String,
        default: Float
    ): Float {
        return context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(key, default)
    }

    fun setFloat(
        context: Context,
        key: String,
        value: Float
    ) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(key, value)
            .apply()
    }

    fun getModel(context: Context): String {
        return getString(
            context,
            "model",
            "fast"
        )
    }

    fun setModel(
        context: Context,
        value: String
    ) {
        setString(
            context,
            "model",
            value
        )
    }
}
