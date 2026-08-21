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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aireautoroute.app.SaisieCritere
import com.aireautoroute.app.data.AireDetail
import com.aireautoroute.app.data.Critere
import com.aireautoroute.app.data.Presence
import com.aireautoroute.app.data.TrancheAge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranNotation(
    detail: AireDetail?,
    onRetour: () -> Unit,
    onValider: (List<SaisieCritere>, String) -> Unit,
) {
    val presences = remember { mutableStateMapOf<Critere, Presence>() }
    // Clé : "CRITERE" ou "CRITERE|TRANCHE".
    val notes = remember { mutableStateMapOf<String, Int>() }
    val commentaires = remember { mutableStateMapOf<Critere, String>() }
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
                    "Dites d'abord si l'équipement existe sur cette aire. La note n'est proposée " +
                        "que si vous l'avez trouvé sur place.",
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

            items(Critere.ordreAffichage, key = { it.name }) { critere ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${critere.emoji}  ${critere.libelle}",
                            style = MaterialTheme.typography.titleSmall,
                        )

                        val presence = presences[critere] ?: Presence.NE_SAIS_PAS
                        if (critere.estEquipement) {
                            ChoixPresence(
                                presence = presence,
                                onPresence = { choix -> presences[critere] = choix },
                            )
                        }

                        val notable = !critere.estEquipement || presence == Presence.OUI
                        if (notable) {
                            HorizontalDivider()
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
                                value = commentaires[critere].orEmpty(),
                                onValueChange = { texte -> commentaires[critere] = texte },
                                label = { Text("Commentaire (facultatif)") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            item {
                val saisies = construireSaisies(presences, notes, commentaires)
                Button(
                    onClick = { onValider(saisies, auteur) },
                    enabled = saisies.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Enregistrer")
                }
            }
        }
    }
}

@Composable
private fun ChoixPresence(presence: Presence, onPresence: (Presence) -> Unit) {
    Column {
        Text(
            "Cet équipement existe-t-il ?",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Presence.entries.forEach { choix ->
                Row(
                    modifier = Modifier
                        .selectable(
                            selected = presence == choix,
                            role = Role.RadioButton,
                            onClick = { onPresence(choix) },
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = presence == choix, onClick = { onPresence(choix) })
                    Text(choix.libelle, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/**
 * Transforme l'état du formulaire en contributions.
 *
 * Un critère n'est retenu que si l'utilisateur s'est prononcé sur sa présence ou lui a mis une
 * note ; les notes d'un équipement déclaré absent ou inconnu sont ignorées.
 */
private fun construireSaisies(
    presences: Map<Critere, Presence>,
    notes: Map<String, Int>,
    commentaires: Map<Critere, String>,
): List<SaisieCritere> = Critere.ordreAffichage.mapNotNull { critere ->
    val presence = presences[critere] ?: Presence.NE_SAIS_PAS
    val notesDuCritere: Map<TrancheAge?, Int> = if (critere.parTrancheAge) {
        TrancheAge.entries
            .mapNotNull { tranche ->
                notes["${critere.name}|${tranche.name}"]
                    ?.takeIf { it in 1..5 }
                    ?.let { tranche to it }
            }
            .toMap()
    } else {
        notes[critere.name]
            ?.takeIf { it in 1..5 }
            ?.let { mapOf<TrancheAge?, Int>(null to it) }
            .orEmpty()
    }

    val declare = critere.estEquipement && presence != Presence.NE_SAIS_PAS
    val note = notesDuCritere.isNotEmpty() && (!critere.estEquipement || presence == Presence.OUI)
    if (!declare && !note) return@mapNotNull null

    SaisieCritere(
        critere = critere,
        presence = presence,
        notes = if (note) notesDuCritere else emptyMap(),
        commentaire = commentaires[critere].orEmpty(),
    )
}
