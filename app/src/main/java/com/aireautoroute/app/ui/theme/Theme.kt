package com.aireautoroute.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val BleuAutoroute = Color(0xFF0B5FA5)
private val BleuClair = Color(0xFFD5E4F4)
private val Ambre = Color(0xFFF2A93B)

private val PaletteClaire = lightColorScheme(
    primary = BleuAutoroute,
    onPrimary = Color.White,
    primaryContainer = BleuClair,
    onPrimaryContainer = Color(0xFF07304F),
    secondary = Ambre,
    onSecondary = Color(0xFF3A2600),
    surfaceVariant = Color(0xFFE7EDF3),
)

private val PaletteSombre = darkColorScheme(
    primary = Color(0xFF8FC1EC),
    onPrimary = Color(0xFF00325A),
    primaryContainer = Color(0xFF0B4A7A),
    onPrimaryContainer = BleuClair,
    secondary = Ambre,
    onSecondary = Color(0xFF3A2600),
)

@Composable
fun AireAutorouteTheme(
    sombre: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val couleurs = if (sombre) PaletteSombre else PaletteClaire
    val vue = LocalView.current
    if (!vue.isInEditMode) {
        SideEffect {
            val fenetre = (vue.context as Activity).window
            fenetre.statusBarColor = couleurs.primary.toArgb()
            WindowCompat.getInsetsController(fenetre, vue).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = couleurs,
        typography = MaterialTheme.typography,
        content = content,
    )
}
