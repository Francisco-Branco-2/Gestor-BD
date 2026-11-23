package pt.ubi.di.pdm.gestorbd.ui.common

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

enum class ThemeMode(val nightMode: Int) {
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
    DARK(AppCompatDelegate.MODE_NIGHT_YES),
}

/**
 * Preferência de tema (claro / escuro / sistema) em SharedPreferences.
 * Nota do enunciado: SharedPreferences serve para definições, não para os registos
 * da coleção — esses ficam no Room.
 */
class ThemePreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = prefs.getString(KEY_THEME, null)
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: ThemeMode.SYSTEM
        set(value) {
            prefs.edit { putString(KEY_THEME, value.name) }
            AppCompatDelegate.setDefaultNightMode(value.nightMode)
        }

    fun applySavedTheme() {
        AppCompatDelegate.setDefaultNightMode(themeMode.nightMode)
    }

    private companion object {
        const val PREFS_NAME = "settings"
        const val KEY_THEME = "theme_mode"
    }
}
