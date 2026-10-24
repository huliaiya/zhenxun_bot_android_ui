package me.kbai.zhenxunui.tool

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import me.kbai.zhenxunui.Constants
import me.kbai.zhenxunui.ZxApplication

/**
 * Persists the app theme mode and applies it process-wide via AppCompatDelegate.
 */
object ThemeMode {
    const val DEFAULT = "default"
    const val LIGHT = "light"
    const val DARK = "dark"

    private const val SP_KEY_THEME_MODE = "key_theme_mode"

    private val prefs by lazy {
        ZxApplication.getApplication()
            .getSharedPreferences(Constants.SP_NAME_CONFIG, Context.MODE_PRIVATE)
    }

    fun current(): String = prefs.getString(SP_KEY_THEME_MODE, DEFAULT) ?: DEFAULT

    fun init() {
        AppCompatDelegate.setDefaultNightMode(toNightMode(current()))
    }

    fun apply(mode: String) {
        prefs.edit().putString(SP_KEY_THEME_MODE, mode).apply()
        AppCompatDelegate.setDefaultNightMode(toNightMode(mode))
    }

    private fun toNightMode(mode: String): Int = when (mode) {
        LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        DARK -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
}
