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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aireautoroute.app.data.AireDetail
import com.aireautoroute.app.data.DetailCritere
import com.aireautoroute.app.data.IconeEnseigne
import com.aireautoroute.app.data.MotifSignalement
import com.aireautoroute.app.data.Notation
import com.aireautoroute.app.data.StatutEquipement

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EcranDetail(
    detail: AireDetail?,
    onRetour: () -> Unit,
    onNoter: () -> Unit,
    onAjouterEnseigne: (String, IconeEnseigne?) -> Unit,
    onRetirerEnseigne: (String) -> Unit,
    onSignaler: (String, MotifSignalement) -> Unit,
    onSignalerContributeur: (String, MotifSignalement) -> Unit,
    onMasquerContributeur: (String) -> Unit,
) {
    var dialogueEnseigne by remember { mutableStateOf(false) }
    // L'avis en cours de signalement, null quand la boîte est fermée.
    var avisSignale by remember { mutableStateOf<Notation?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.aire?.nom ?: "Aire") },
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
                                // Le pictogramme dit la nature du commerce ; sans lui, le nom
                                // seul oblige à connaître l'enseigne pour savoir quoi en attendre.
                                val icone = enseigne.icone
                                val picto: @Composable (() -> Unit)? = if (icone == null) {
                                    null
                                } else {
                                    {
                                        Icon(
                                            imageVector = icone.vecteur,
                                            contentDescription = icone.libelle,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                                if (enseigne.id in detail.enseignesAjoutees) {
                                    InputChip(
                                        selected = false,
                                        onClick = { onRetirerEnseigne(enseigne.id) },
                                        label = { Text("${enseigne.nom} ✕") },
                                        leadingIcon = picto,
                                    )
                                } else {
                                    AssistChip(
                                        onClick = { },
                                        label = { Text(enseigne.nom) },
                                        leadingIcon = picto,
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
                CarteCritere(critere, onSignaler = { avisSignale = it })
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
        var icone by remember { mutableStateOf<IconeEnseigne?>(null) }
        AlertDialog(
            onDismissRequest = { dialogueEnseigne = false },
            title = { Text("Ajouter une enseigne") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = saisie,
                        onValueChange = { saisie = it },
                        label = { Text("Nom de l'enseigne") },
                        singleLine = true,
                    )
                    // Le choix reste facultatif : sur le bord de la route, exiger une catégorie
                    // pour signaler une enseigne ferait surtout renoncer à la signaler.
                    Text(
                        "Icône (facultative)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        IconeEnseigne.entries.forEach { candidate ->
                            FilterChip(
                                selected = icone == candidate,
                                onClick = { icone = if (icone == candidate) null else candidate },
                                label = {
                                    Icon(
                                        imageVector = candidate.vecteur,
                                        contentDescription = candidate.libelle,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                            )
                        }
                    }
                    Text(
                        text = icone?.libelle ?: "Aucune icône",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAjouterEnseigne(saisie, icone)
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

    // Une seule boîte pour les trois gestes : trois boutons sous chaque avis alourdiraient la
    // liste pour une action qui reste rare.
    avisSignale?.let { notation ->
        var motif by remember(notation.id) { mutableStateOf(MotifSignalement.INJURIEUX) }
        var cible by remember(notation.id) { mutableStateOf(CibleSignalement.AVIS) }
        AlertDialog(
            onDismissRequest = { avisSignale = null },
            title = { Text("Signaler ou masquer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    CibleSignalement.entries.forEach { candidate ->
                        // Viser une personne suppose de savoir laquelle : un avis rapatrié avant
                        // que le service ne pose les identifiants n'en désigne aucune.
                        val actif = candidate == CibleSignalement.AVIS ||
                            notation.auteurId.isNotBlank()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = cible == candidate,
                                    enabled = actif,
                                    role = Role.RadioButton,
                                    onClick = { cible = candidate },
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = cible == candidate,
                                onClick = { cible = candidate },
                                enabled = actif,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = candidate.libelle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (actif) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                Text(
                                    candidate.effet,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (cible != CibleSignalement.MASQUER_CONTRIBUTEUR) {
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Text(
                            "Motif",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MotifSignalement.entries.forEach { candidat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = motif == candidat,
                                        role = Role.RadioButton,
                                        onClick = { motif = candidat },
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = motif == candidat,
                                    onClick = { motif = candidat },
                                )
                                Text(candidat.libelle, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (cible) {
                            CibleSignalement.AVIS -> onSignaler(notation.id, motif)
                            CibleSignalement.CONTRIBUTEUR ->
                                onSignalerContributeur(notation.auteurId, motif)
                            CibleSignalement.MASQUER_CONTRIBUTEUR ->
                                onMasquerContributeur(notation.auteurId)
                        }
                        avisSignale = null
                    },
                ) { Text("Valider") }
            },
            dismissButton = {
                TextButton(onClick = { avisSignale = null }) { Text("Annuler") }
            },
        )
    }
}

/** Ce que vise le geste : un avis, ou la personne qui l'a écrit. */
private enum class CibleSignalement(val libelle: String, val effet: String) {
    AVIS(
        "Signaler cet avis",
        "Masqué pour tout le monde s'il est signalé par plusieurs personnes.",
    ),
    CONTRIBUTEUR(
        "Signaler ce contributeur",
        "Remonté pour examen. Utile quand une même personne publie en série.",
    ),
    MASQUER_CONTRIBUTEUR(
        "Masquer ce contributeur",
        "Immédiat, et pour vous seul : ses avis et ses notes disparaissent de votre affichage.",
    ),
}

@Composable
private fun CarteCritere(detail: DetailCritere, onSignaler: (Notation) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = detail.critere.icone,
                        contentDescription = null,
                        tint = detail.consensus
                            ?.let { couleurStatut(it.statut) }
                            ?: MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "  " + detail.critere.libelle,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
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
                        EtoilesLecture(note.moyenne, taille = 14.dp)
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
                detail.commentaires.forEach { notation ->
                    LigneCommentaire(notation, onSignaler = { onSignaler(notation) })
                }
            }
        }
    }
}

@Composable
private fun LigneCommentaire(notation: Notation, onSignaler: () -> Unit) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EtoilesLecture(notation.note.toDouble(), taille = 12.dp)
                Text(
                    text = " ${notation.auteur}" +
                        (notation.trancheAge?.let { " · ${it.libelle}" } ?: "") +
                        " · ${notation.date.take(10)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Le signalement se place sur le commentaire lui-même : relégué dans un écran de
            // réglages, il ne serait pas trouvé au moment où l'on en a besoin.
            TextButton(onClick = onSignaler) {
                Text("Signaler", style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(
            text = notation.commentaire.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Normal,
        )
    }
}
