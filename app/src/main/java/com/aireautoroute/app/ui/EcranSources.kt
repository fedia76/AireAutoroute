package com.aireautoroute.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class Source(
    val titre: String,
    val origine: String,
    val licence: String,
    val usage: String,
)

private val SOURCES = listOf(
    Source(
        titre = "Bornage du réseau routier national",
        origine = "data.gouv.fr — millésime 2025",
        licence = "Licence Ouverte / Open Licence",
        usage = "Le tracé de chaque autoroute et l'échelle des points kilométriques. C'est ce " +
            "référentiel qui permet de convertir une position GPS en « A13, PK 55 », dans le " +
            "kilométrage affiché sur les bornes du bord de route.",
    ),
    Source(
        titre = "WikiSara",
        origine = "routes.fandom.com",
        licence = "CC BY-SA",
        usage = "La liste des aires : nom, autoroute, sens de circulation, type et point " +
            "kilométrique.",
    ),
    Source(
        titre = "OpenStreetMap",
        origine = "© les contributeurs OpenStreetMap, relevé via l'API Overpass",
        licence = "ODbL",
        usage = "Les équipements annoncés sur chaque aire — toilettes, station-service, aire " +
            "de jeux, table à langer — et les enseignes qui y sont présentes. Ce qu'OSM " +
            "indique est annoncé, jamais affirmé : seuls vos passages le confirment.",
    ),
    Source(
        titre = "Enseignes",
        origine = "Référentiel tenu à la main dans le dépôt du projet, augmenté des marques " +
            "relevées dans OpenStreetMap",
        licence = "—",
        usage = "Sert de liste de saisie. Une marque n'entre au catalogue qu'à partir de " +
            "trois aires : en deçà, c'est plus probablement une saisie isolée qu'une enseigne.",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranSources(onRetour: () -> Unit, onSupprimerContributions: () -> Unit) {
    var confirmation by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Sources et confidentialité") },
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
    ) { encarts ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = encarts.calculateTopPadding() + 12.dp,
                bottom = encarts.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Les autoroutes et les aires proviennent de données ouvertes et sont " +
                        "embarquées dans l'application. Les avis, eux, sont partagés entre tous " +
                        "les utilisateurs.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            items(SOURCES.size) { index ->
                val source = SOURCES[index]
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(source.titre, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = source.origine + " · " + source.licence,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(source.usage, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Ce que l'application déduit", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Les aires de service sont présentées comme disposant d'une " +
                                "station-service et de sanitaires : c'est ce qui définit ce type " +
                                "d'aire, mais ce n'est pas une donnée vérifiée. Ces équipements " +
                                "restent « annoncés » jusqu'à ce que deux visiteurs les " +
                                "confirment.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Les deux côtés d'un même lieu sont deux aires distinctes, mais leur " +
                                "position est calculée depuis le même point kilométrique : elles " +
                                "se superposent sur la carte, à quelques dizaines de mètres près.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Vos contributions", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Les avis sont publiés sur un service partagé, hébergé en Europe, " +
                                "pour que chacun profite des observations des autres. Aucun " +
                                "compte n'est demandé : l'application ouvre une session anonyme " +
                                "au premier lancement, qui sert uniquement à vous laisser " +
                                "revenir sur vos propres avis.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Ne publiez rien que vous ne diriez pas à voix haute sur une aire : " +
                                "les commentaires sont visibles par tout le monde.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Vous pouvez retirer à tout moment l'ensemble de ce que vous avez " +
                                "publié. La suppression est immédiate et définitive : ni " +
                                "l'application ni le service n'en gardent de copie.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { confirmation = true }) {
                            Text("Supprimer toutes mes contributions")
                        }
                    }
                }
            }
        }
    }

    if (confirmation) {
        AlertDialog(
            onDismissRequest = { confirmation = false },
            title = { Text("Supprimer toutes vos contributions ?") },
            text = {
                Text(
                    "Vos notes, vos déclarations d'équipement et les enseignes que vous avez " +
                        "rattachées seront effacées du service partagé. Cette action est " +
                        "irréversible.",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSupprimerContributions()
                        confirmation = false
                    },
                ) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = false }) { Text("Annuler") }
            },
        )
    }
}
