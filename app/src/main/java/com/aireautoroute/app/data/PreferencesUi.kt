package com.aireautoroute.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Préférences d'affichage : le thème choisi, et les contributeurs que l'utilisateur ne veut
 * plus lire.
 *
 * Ce sont des réglages d'interface, pas des données métier : ils vivent dans les préférences
 * Android et non dans le fichier de données utilisateur. Pour les contributeurs masqués, ce
 * n'est pas qu'une question de rangement — `donnees_utilisateur.json` est réécrit en entier à
 * chaque rafraîchissement, la liste y serait effacée au premier retour du réseau.
 */
class PreferencesUi(context: Context) {

    private val prefs = context.getSharedPreferences("preferences_ui", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(lireTheme())
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _auteursMasques = MutableStateFlow(lireAuteursMasques())
    val auteursMasques: StateFlow<Set<String>> = _auteursMasques.asStateFlow()

    private fun lireTheme(): String = prefs.getString(CLE_THEME, null) ?: THEME_PAR_DEFAUT

    fun definirTheme(nom: String) {
        prefs.edit().putString(CLE_THEME, nom).apply()
        _theme.value = nom
    }

    // La copie défensive n'est pas un excès de prudence : SharedPreferences documente que le
    // Set rendu ne doit pas être modifié, et le réutiliser tel quel corromprait le stockage.
    private fun lireAuteursMasques(): Set<String> =
        prefs.getStringSet(CLE_AUTEURS_MASQUES, null)?.toSet() ?: emptySet()

    fun masquerAuteur(auteurId: String) {
        if (auteurId.isBlank()) return
        ecrireAuteursMasques(_auteursMasques.value + auteurId)
    }

    fun demasquerAuteur(auteurId: String) {
        ecrireAuteursMasques(_auteursMasques.value - auteurId)
    }

    fun demasquerTous() = ecrireAuteursMasques(emptySet())

    private fun ecrireAuteursMasques(auteurs: Set<String>) {
        prefs.edit().putStringSet(CLE_AUTEURS_MASQUES, auteurs).apply()
        _auteursMasques.value = auteurs
    }

    private companion object {
        const val CLE_THEME = "theme"
        const val THEME_PAR_DEFAUT = "SIGNALETIQUE"
        const val CLE_AUTEURS_MASQUES = "auteurs_masques"
    }
}
