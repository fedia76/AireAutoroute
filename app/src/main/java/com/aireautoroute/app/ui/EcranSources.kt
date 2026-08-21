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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
        titre = "Enseignes",
        origine = "Référentiel tenu à la main dans le dépôt du projet",
        licence = "—",
        usage = "Sert de liste de saisie. Aucune enseigne n'est encore rattachée à une aire : " +
            "ce rattachement viendra d'OpenStreetMap.",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranSources(onRetour: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Sources et licences") },
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
                    "Les autoroutes et les aires proviennent de données ouvertes. Les notes et " +
                        "les commentaires, eux, restent sur votre téléphone.",
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
                            "Les deux chaussées d'une même aire partagent la position calculée " +
                                "depuis leur point kilométrique : à quelques dizaines de mètres près.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
