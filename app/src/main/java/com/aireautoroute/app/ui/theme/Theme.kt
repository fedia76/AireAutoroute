package com.aireautoroute.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun AireAutorouteTheme(
    theme: ThemeApp = ThemeApp.SIGNALETIQUE,
    sombreSysteme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val sombre = theme.toujoursSombre || sombreSysteme
    val couleurs = theme.palette(sombre)

    val vue = LocalView.current
    if (!vue.isInEditMode) {
        SideEffect {
            val fenetre = (vue.context as Activity).window
            fenetre.statusBarColor = couleurs.primary.toArgb()
            WindowCompat.getInsetsController(fenetre, vue).isAppearanceLightStatusBars =
                couleurs.primary.luminance() > 0.5f
        }
    }

    CompositionLocalProvider(LocalThemeApp provides theme) {
        MaterialTheme(
            colorScheme = couleurs,
            typography = theme.typographie(),
            shapes = theme.formes(),
            content = content,
        )
    }
}
