package com.ayaz.viewer.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { DARK, LIGHT, SYSTEM }

enum class NumberLocale { TR, EN }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val numberLocale: NumberLocale = NumberLocale.TR,
    val rememberLastPage: Boolean = true,
    val lightSheets: Boolean = false,
)

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("ayaz_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun read() = AppSettings(
        themeMode = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "DARK") }.getOrDefault(ThemeMode.DARK),
        numberLocale = runCatching { NumberLocale.valueOf(prefs.getString(KEY_LOCALE, null) ?: "TR") }.getOrDefault(NumberLocale.TR),
        rememberLastPage = prefs.getBoolean(KEY_REMEMBER_PAGE, true),
        lightSheets = prefs.getBoolean(KEY_LIGHT_SHEETS, false),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putString(KEY_THEME, next.themeMode.name)
            .putString(KEY_LOCALE, next.numberLocale.name)
            .putBoolean(KEY_REMEMBER_PAGE, next.rememberLastPage)
            .putBoolean(KEY_LIGHT_SHEETS, next.lightSheets)
            .apply()
        _settings.value = next
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_LOCALE = "number_locale"
        const val KEY_REMEMBER_PAGE = "remember_last_page"
        const val KEY_LIGHT_SHEETS = "light_sheets"
    }
}
