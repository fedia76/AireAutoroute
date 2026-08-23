package com.aireautoroute.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.aireautoroute.app.EtatUi
import com.aireautoroute.app.carte.CarteMapLibre
import com.aireautoroute.app.carte.CouleursTrace
import com.aireautoroute.app.carte.PaletteCarte
import com.aireautoroute.app.ui.theme.ThemeApp
import java.util.Locale

/**
 * Vue carte : un fond cartographique embarqué, l'itinéraire en surbrillance et
 * les aires en pastilles cliquables.
 *
 * Contrairement à la version précédente, l'écran ne réclame plus de position
 * pour s'afficher : avec un fond, une carte sans position reste lisible, et
 * c'est une porte d'entrée acceptable vers le reste de l'application.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranCarte(
    etat: EtatUi,
    themeCourant: ThemeApp,
    onRetour: () -> Unit,
    onAire: (String) -> Unit,
) {
    val position = etat.position
    var recadrages by remember { mutableIntStateOf(0) }

    val sombre = themeCourant.toujoursSombre || isSystemInDarkTheme()
    val palette = remember(themeCourant, sombre) { themeCourant.paletteCarte(sombre) }

    val schema = MaterialTheme.colorScheme
    val couleurs = remember(schema) {
        CouleursTrace(
            devant = schema.primary.hexa(),
            derriere = schema.outline.hexa(),
            aireDevant = schema.primary.hexa(),
            aireDerriere = schema.onSurfaceVariant.hexa(),
            aireNotee = schema.tertiary.hexa(),
            groupe = schema.secondary.hexa(),
            position = schema.secondary.hexa(),
            surTrace = schema.surface.hexa(),
        )
    }
    val notees = remember(etat.prochainesAires) {
        etat.prochainesAires.filter { it.noteGenerale != null }.map { it.aire.id }.toSet()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(position?.autoroute?.nom?.let { "Carte · $it" } ?: "Carte") },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            if (position != null) {
                FloatingActionButton(onClick = { recadrages++ }) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "Recentrer")
                }
            }
        },
    ) { encarts ->
        Box(Modifier.fillMaxSize().padding(encarts)) {
            CarteMapLibre(
                position = position,
                aires = etat.airesAutoroute,
                airesNotees = notees,
                palette = palette,
                couleurs = couleurs,
                recadrages = recadrages,
                onAire = onAire,
                modifier = Modifier.fillMaxSize(),
            )

            Card(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .fillMaxWidth(0.72f),
            ) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = position?.libelle ?: "Position inconnue",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = if (position == null) {
                            "Indiquez votre position pour voir les aires devant vous."
                        } else {
                            "${etat.airesAutoroute.size} aires sur l'itinéraire · " +
                                "appuyez sur une pastille pour l'ouvrir"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // L'ODbL demande que la provenance des données reste visible.
                    Text(
                        text = "Fond : © OpenStreetMap, Protomaps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Habillage cartographique associé à un thème de l'application. */
private fun ThemeApp.paletteCarte(sombre: Boolean): PaletteCarte = when (this) {
    ThemeApp.SIGNALETIQUE -> if (sombre) PaletteCarte.SOMBRE else PaletteCarte.CLAIRE
    ThemeApp.CARNET -> if (sombre) PaletteCarte.SOMBRE else PaletteCarte.SEPIA
    ThemeApp.COPILOTE -> PaletteCarte.SOMBRE
}

/** MapLibre attend des couleurs CSS, pas des entiers Compose. */
private fun Color.hexa(): String =
    String.format(Locale.ROOT, "#%06X", 0xFFFFFF and toArgb())
