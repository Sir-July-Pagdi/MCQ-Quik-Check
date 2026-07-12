package com.tnhs.mcqscanner

import android.content.Context

enum class AppTheme(val styleResId: Int, val label: String) {
    LIGHT(R.style.Theme_MCQScanner, "Light"),
    DARK(R.style.Theme_MCQScanner_Dark, "Dark"),
    BLACK(R.style.Theme_MCQScanner_Black, "Black")
}

object ThemePrefs {
    private const val PREFS = "mcq_scanner_prefs"
    private const val KEY_THEME = "theme"

    fun getTheme(context: Context): AppTheme {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_THEME, AppTheme.LIGHT.name)
        return AppTheme.values().firstOrNull { it.name == name } ?: AppTheme.LIGHT
    }

    fun setTheme(context: Context, theme: AppTheme) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, theme.name).apply()
    }

    /** Cycles Light -> Dark -> Black -> Light ... */
    fun cycleTheme(context: Context): AppTheme {
        val current = getTheme(context)
        val next = AppTheme.values()[(current.ordinal + 1) % AppTheme.values().size]
        setTheme(context, next)
        return next
    }

    /** Call before super.onCreate() in every activity. */
    fun apply(context: android.app.Activity) {
        context.setTheme(getTheme(context).styleResId)
    }
}
