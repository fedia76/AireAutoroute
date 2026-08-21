package com.aireautoroute.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Préférences d'affichage (le thème choisi).
 *
 * Ce sont des réglages d'interface, pas des données métier : ils vivent dans les préférences
 * Android et non dans le fichier de données utilisateur.
 */
class PreferencesUi(context: Context) {

    private val prefs = context.getSharedPreferences("preferences_ui", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(lireTheme())
    val theme: StateFlow<String> = _theme.asStateFlow()

    private fun lireTheme(): String = prefs.getString(CLE_THEME, null) ?: THEME_PAR_DEFAUT

    fun definirTheme(nom: String) {
        prefs.edit().putString(CLE_THEME, nom).apply()
        _theme.value = nom
    }

    private companion object {
        const val CLE_THEME = "theme"
        const val THEME_PAR_DEFAUT = "SIGNALETIQUE"
    }
}
