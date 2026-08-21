package com.aireautoroute.app.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Style de liste propre à chaque thème : ce que le seul jeu de couleurs ne suffit pas à exprimer.
 */
enum class StyleListe {
    /** Cartes rectangulaires bordées d'un liseré de couleur, distance en gros chiffre. */
    PANNEAU,

    /** Cartes arrondies posées sur un fond papier, distance en toutes lettres. */
    CARNET,

    /** Lignes denses séparées par des filets, colonne de distance en chasse fixe. */
    TABLEAU_DE_BORD,
}

/**
 * Les trois habillages proposés à l'utilisateur.
 *
 * Un thème regroupe une palette (claire et sombre), une typographie, des formes et un style de
 * liste ; la structure des écrans, elle, ne change pas.
 */
enum class ThemeApp(
    val libelle: String,
    val description: String,
    val styleListe: StyleListe,
    /** `true` quand le thème n'existe qu'en version sombre. */
    val toujoursSombre: Boolean = false,
) {
    SIGNALETIQUE(
        libelle = "Signalétique",
        description = "Le bleu des panneaux d'autoroute, distances en gros chiffres.",
        styleListe = StyleListe.PANNEAU,
    ),
    CARNET(
        libelle = "Carnet de route",
        description = "Fond papier, titres en serif, avis mis en avant.",
        styleListe = StyleListe.CARNET,
    ),
    COPILOTE(
        libelle = "Copilote",
        description = "Tableau de bord sombre, lecture rapide de nuit.",
        styleListe = StyleListe.TABLEAU_DE_BORD,
        toujoursSombre = true,
    );

    /** Couleurs d'aperçu du sélecteur : fond, accent, seconde teinte. */
    val apercu: List<Color>
        get() = when (this) {
            SIGNALETIQUE -> listOf(Color(0xFFFFFFFF), Color(0xFF0B5FA5), Color(0xFFF2A93B))
            CARNET -> listOf(Color(0xFFFBF6EE), Color(0xFFB5502F), Color(0xFFD08A2C))
            COPILOTE -> listOf(Color(0xFF0E1116), Color(0xFF7BD88F), Color(0xFFF0B429))
        }

    /** Famille utilisée pour les distances, les PK et les moyennes. */
    val policeChiffres: FontFamily
        get() = when (this) {
            SIGNALETIQUE -> FontFamily.SansSerif
            CARNET -> FontFamily.Serif
            COPILOTE -> FontFamily.Monospace
        }
}

private val PaletteSignalétiqueClaire = lightColorScheme(
    primary = Color(0xFF0B5FA5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E4F4),
    onPrimaryContainer = Color(0xFF07304F),
    secondary = Color(0xFFF2A93B),
    onSecondary = Color(0xFF3A2600),
    tertiary = Color(0xFF1E7A45),
    onTertiary = Color.White,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF14202B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14202B),
    surfaceVariant = Color(0xFFF2F6FA),
    onSurfaceVariant = Color(0xFF5B6B7B),
    outline = Color(0xFFD5DFE8),
    outlineVariant = Color(0xFFE4EAF0),
    error = Color(0xFFB3261E),
)

private val PaletteSignalétiqueSombre = darkColorScheme(
    primary = Color(0xFF8FC1EC),
    onPrimary = Color(0xFF00325A),
    primaryContainer = Color(0xFF0B4A7A),
    onPrimaryContainer = Color(0xFFD5E4F4),
    secondary = Color(0xFFF2A93B),
    onSecondary = Color(0xFF3A2600),
    tertiary = Color(0xFF6FCB93),
    onTertiary = Color(0xFF00351A),
    background = Color(0xFF101720),
    onBackground = Color(0xFFE3EAF1),
    surface = Color(0xFF16202B),
    onSurface = Color(0xFFE3EAF1),
    surfaceVariant = Color(0xFF1E2A36),
    onSurfaceVariant = Color(0xFF9FB0C0),
    outline = Color(0xFF32404E),
    outlineVariant = Color(0xFF25313D),
    error = Color(0xFFF2B8B5),
)

private val PaletteCarnetClaire = lightColorScheme(
    primary = Color(0xFFB5502F),
    onPrimary = Color(0xFFFFF8F0),
    primaryContainer = Color(0xFFF6EADA),
    onPrimaryContainer = Color(0xFF5C2413),
    secondary = Color(0xFFD08A2C),
    onSecondary = Color(0xFF2C2621),
    tertiary = Color(0xFF3F6A48),
    onTertiary = Color(0xFFFFF8F0),
    background = Color(0xFFFBF6EE),
    onBackground = Color(0xFF2C2621),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2C2621),
    surfaceVariant = Color(0xFFF6EFE3),
    onSurfaceVariant = Color(0xFF8A7C6C),
    outline = Color(0xFFEADFCE),
    outlineVariant = Color(0xFFF0E7D8),
    error = Color(0xFF9B3B22),
)

private val PaletteCarnetSombre = darkColorScheme(
    primary = Color(0xFFE99770),
    onPrimary = Color(0xFF3B1608),
    primaryContainer = Color(0xFF5C2413),
    onPrimaryContainer = Color(0xFFF6EADA),
    secondary = Color(0xFFE0AC5F),
    onSecondary = Color(0xFF2C2621),
    tertiary = Color(0xFF8FBE97),
    onTertiary = Color(0xFF14301B),
    background = Color(0xFF221E1A),
    onBackground = Color(0xFFF0E7DB),
    surface = Color(0xFF2B2621),
    onSurface = Color(0xFFF0E7DB),
    surfaceVariant = Color(0xFF352E27),
    onSurfaceVariant = Color(0xFFBBAA97),
    outline = Color(0xFF463D34),
    outlineVariant = Color(0xFF3A322B),
    error = Color(0xFFE79080),
)

private val PaletteCopilote = darkColorScheme(
    primary = Color(0xFF7BD88F),
    onPrimary = Color(0xFF0E1116),
    primaryContainer = Color(0xFF16241B),
    onPrimaryContainer = Color(0xFF7BD88F),
    secondary = Color(0xFFF0B429),
    onSecondary = Color(0xFF0E1116),
    tertiary = Color(0xFF7BD88F),
    onTertiary = Color(0xFF0E1116),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE8EDF3),
    surface = Color(0xFF171C24),
    onSurface = Color(0xFFE8EDF3),
    surfaceVariant = Color(0xFF131923),
    onSurfaceVariant = Color(0xFF8A97A6),
    outline = Color(0xFF262E39),
    outlineVariant = Color(0xFF222B36),
    error = Color(0xFFE06C6C),
)

internal fun ThemeApp.palette(sombre: Boolean) = when (this) {
    ThemeApp.SIGNALETIQUE -> if (sombre) PaletteSignalétiqueSombre else PaletteSignalétiqueClaire
    ThemeApp.CARNET -> if (sombre) PaletteCarnetSombre else PaletteCarnetClaire
    ThemeApp.COPILOTE -> PaletteCopilote
}

internal fun ThemeApp.formes(): Shapes = when (this) {
    // Angles francs, comme un panneau routier.
    ThemeApp.SIGNALETIQUE -> Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(3.dp),
        medium = RoundedCornerShape(4.dp),
        large = RoundedCornerShape(4.dp),
        extraLarge = RoundedCornerShape(6.dp),
    )
    ThemeApp.CARNET -> Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(18.dp),
        extraLarge = RoundedCornerShape(24.dp),
    )
    ThemeApp.COPILOTE -> Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(5.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(10.dp),
        extraLarge = RoundedCornerShape(12.dp),
    )
}

/**
 * Typographies distinctes.
 *
 * Faute de pouvoir embarquer les fontes des maquettes (Barlow, Source Serif, IBM Plex), on joue
 * sur les familles du système : la structure des trois ramps reste celle des maquettes.
 */
internal fun ThemeApp.typographie(): Typography {
    val base = Typography()
    return when (this) {
        ThemeApp.SIGNALETIQUE -> base.copy(
            titleLarge = base.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
            ),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            labelSmall = base.labelSmall.copy(letterSpacing = 0.6.sp),
        )
        ThemeApp.CARNET -> base.copy(
            titleLarge = TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 30.sp,
            ),
            titleMedium = TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 19.sp,
                lineHeight = 25.sp,
            ),
            titleSmall = TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                lineHeight = 23.sp,
            ),
            bodyMedium = base.bodyMedium.copy(lineHeight = 21.sp),
        )
        ThemeApp.COPILOTE -> base.copy(
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            labelLarge = base.labelLarge.copy(
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp,
            ),
            labelSmall = base.labelSmall.copy(letterSpacing = 0.8.sp),
        )
    }
}

/** Thème courant, pour les quelques différences de mise en page qui ne passent pas par Material. */
val LocalThemeApp = staticCompositionLocalOf { ThemeApp.SIGNALETIQUE }
