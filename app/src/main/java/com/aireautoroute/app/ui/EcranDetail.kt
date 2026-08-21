package com.aireautoroute.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aireautoroute.app.data.AireDetail
import com.aireautoroute.app.data.ConsensusEquipement
import com.aireautoroute.app.data.DetailCritere
import com.aireautoroute.app.data.Notation
import com.aireautoroute.app.data.StatutEquipement

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EcranDetail(
    detail: AireDetail?,
    onRetour: () -> Unit,
    onNoter: () -> Unit,
    onAjouterEnseigne: (String) -> Unit,
    onRetirerEnseigne: (String) -> Unit,
) {
    var dialogueEnseigne by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.aire?.nom ?: "Aire") },
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "%s · %s · PK %.1f".format(
                            detail.autoroute.nom,
                            detail.aire.type.libelle,
                            detail.aire.pk,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Accessible en direction de ${detail.autoroute.terminus(detail.aire.sens)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    NoteAvecDetail(detail.noteGenerale, Modifier.padding(top = 4.dp))
                }
            }

            item {
                Button(onClick = onNoter, modifier = Modifier.fillMaxWidth()) {
                    Text("Noter cette aire")
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Enseignes", style = MaterialTheme.typography.titleMedium)
                        if (detail.enseignes.isEmpty()) {
                            Text(
                                "Aucune enseigne connue sur cette aire.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            detail.enseignes.forEach { enseigne ->
                                if (enseigne.id in detail.enseignesAjoutees) {
                                    InputChip(
                                        selected = false,
                                        onClick = { onRetirerEnseigne(enseigne.id) },
                                        label = { Text("${enseigne.nom} ✕") },
                                    )
                                } else {
                                    AssistChip(
                                        onClick = { },
                                        label = { Text(enseigne.nom) },
                                    )
                                }
                            }
                        }
                        TextButton(onClick = { dialogueEnseigne = true }) {
                            Text("Ajouter une enseigne")
                        }
                    }
                }
            }

            items(detail.criteres, key = { it.critere.name }) { critere ->
                CarteCritere(critere)
            }

            item {
                Text(
                    text = "${detail.nombreAvis} note(s) enregistrée(s) sur cette aire.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (dialogueEnseigne) {
        var saisie by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dialogueEnseigne = false },
            title = { Text("Ajouter une enseigne") },
            text = {
                OutlinedTextField(
                    value = saisie,
                    onValueChange = { saisie = it },
                    label = { Text("Nom de l'enseigne") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAjouterEnseigne(saisie)
                        dialogueEnseigne = false
                    },
                    enabled = saisie.isNotBlank(),
                ) { Text("Ajouter") }
            },
            dismissButton = {
                TextButton(onClick = { dialogueEnseigne = false }) { Text("Annuler") }
            },
        )
    }
}

@Composable
private fun CarteCritere(detail: DetailCritere) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${detail.critere.emoji}  ${detail.critere.libelle}",
                    style = MaterialTheme.typography.titleSmall,
                )
                detail.consensus?.let { EtiquetteStatut(it) }
            }

            detail.consensus?.let { consensus ->
                Text(
                    text = consensus.detailDeclarations,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (detail.consensus?.statut != StatutEquipement.ABSENT) {
                NoteAvecDetail(detail.note)
            }

            if (detail.critere.parTrancheAge && detail.parTrancheAge.isNotEmpty()) {
                detail.parTrancheAge.forEach { (tranche, note) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tranche.libelle,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        EtoilesLecture(note.moyenne, taille = 14)
                        Text(
                            text = " %.1f".format(note.moyenne),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (detail.commentaires.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                detail.commentaires.forEach { notation -> LigneCommentaire(notation) }
            }
        }
    }
}

@Composable
private fun LigneCommentaire(notation: Notation) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EtoilesLecture(notation.note.toDouble(), taille = 12)
            Text(
                text = " ${notation.auteur}" +
                    (notation.trancheAge?.let { " · ${it.libelle}" } ?: "") +
                    " · ${notation.date.take(10)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = notation.commentaire.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Normal,
        )
    }
}

/** Pastille colorée résumant ce que l'on sait de la présence d'un équipement. */
@Composable
private fun EtiquetteStatut(consensus: ConsensusEquipement) {
    val couleur = when (consensus.statut) {
        StatutEquipement.CONFIRME -> MaterialTheme.colorScheme.primary
        StatutEquipement.A_CONFIRMER, StatutEquipement.ANNONCE -> MaterialTheme.colorScheme.secondary
        StatutEquipement.ABSENT -> MaterialTheme.colorScheme.error
        StatutEquipement.INCONNU -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = (if (consensus.presentAvere) "✓ " else "") + consensus.statut.libelle,
        style = MaterialTheme.typography.labelSmall,
        color = couleur,
    )
}
