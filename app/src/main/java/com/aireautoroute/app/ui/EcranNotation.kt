package com.aireautoroute.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aireautoroute.app.SaisieNote
import com.aireautoroute.app.data.AireDetail
import com.aireautoroute.app.data.Critere
import com.aireautoroute.app.data.TrancheAge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranNotation(
    detail: AireDetail?,
    onRetour: () -> Unit,
    onValider: (List<SaisieNote>, String) -> Unit,
) {
    // Clé : "CRITERE" ou "CRITERE|TRANCHE".
    val notes = remember { mutableStateMapOf<String, Int>() }
    val commentaires = remember { mutableStateMapOf<String, String>() }
    var auteur by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Noter ${detail?.aire?.nom ?: ""}") },
                navigationIcon = {
                    IconButton(onClick = onRetour) { Text("←", style = MaterialTheme.typography.titleLarge) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { encarts ->
        if (detail == null) {
            Box(Modifier.fillMaxSize().padding(encarts), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val criteres = criteresANoter(detail)

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
                    "Laissez à zéro les critères que vous ne souhaitez pas noter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                OutlinedTextField(
                    value = auteur,
                    onValueChange = { auteur = it },
                    label = { Text("Votre nom (facultatif)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            items(criteres, key = { it.name }) { critere ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${critere.emoji}  ${critere.libelle}",
                            style = MaterialTheme.typography.titleSmall,
                        )

                        if (critere.parTrancheAge) {
                            TrancheAge.entries.forEach { tranche ->
                                val cle = "${critere.name}|${tranche.name}"
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(tranche.libelle, style = MaterialTheme.typography.bodyMedium)
                                    EtoilesSaisie(
                                        note = notes[cle] ?: 0,
                                        onNote = { valeur -> notes[cle] = valeur },
                                    )
                                }
                            }
                        } else {
                            EtoilesSaisie(
                                note = notes[critere.name] ?: 0,
                                onNote = { valeur -> notes[critere.name] = valeur },
                            )
                        }

                        OutlinedTextField(
                            value = commentaires[critere.name].orEmpty(),
                            onValueChange = { texte -> commentaires[critere.name] = texte },
                            label = { Text("Commentaire (facultatif)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = { onValider(construireSaisies(criteres, notes, commentaires), auteur) },
                    enabled = notes.values.any { it in 1..5 },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Enregistrer")
                }
            }
        }
    }
}

/**
 * Transforme l'état du formulaire en notations.
 *
 * Pour un critère par tranche d'âge, le commentaire est rattaché à la première tranche notée
 * afin de ne pas le dupliquer sur les trois lignes.
 */
private fun construireSaisies(
    criteres: List<Critere>,
    notes: Map<String, Int>,
    commentaires: Map<String, String>,
): List<SaisieNote> = buildList {
    criteres.forEach { critere ->
        val commentaire = commentaires[critere.name].orEmpty()
        if (critere.parTrancheAge) {
            var commentairePlace = false
            TrancheAge.entries.forEach { tranche ->
                val note = notes["${critere.name}|${tranche.name}"] ?: 0
                if (note in 1..5) {
                    add(
                        SaisieNote(
                            critere = critere,
                            trancheAge = tranche,
                            note = note,
                            commentaire = if (commentairePlace) "" else commentaire,
                        ),
                    )
                    commentairePlace = true
                }
            }
        } else {
            val note = notes[critere.name] ?: 0
            if (note in 1..5) {
                add(SaisieNote(critere, null, note, commentaire))
            }
        }
    }
}
