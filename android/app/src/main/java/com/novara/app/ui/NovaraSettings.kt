package com.novara.app.ui

import android.content.Context

object NovaraSettings {

    private const val PREFS = "novara_settings"

    fun get(
        context: Context,
        key: String,
        default: Boolean
    ): Boolean =
        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getBoolean(
                key,
                default
            )

    fun set(
        context: Context,
        key: String,
        value: Boolean
    ) {
        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putBoolean(
                key,
                value
            )
            .apply()
    }

    fun getString(
        context: Context,
        key: String,
        default: String
    ): String =
        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getString(
                key,
                default
            ) ?: default

    fun setString(
        context: Context,
        key: String,
        value: String
    ) {
        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                key,
                value
            )
            .apply()
    }

    fun getModel(
        context: Context
    ): String =
        getString(
            context,
            "model",
            "fast"
        )

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
