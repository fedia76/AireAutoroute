package com.aireautoroute.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.aireautoroute.app.EtatUi
import com.aireautoroute.app.data.AireResume
import com.aireautoroute.app.data.Autoroute
import com.aireautoroute.app.data.Critere
import com.aireautoroute.app.data.Sens
import com.aireautoroute.app.data.SourcePosition
import com.aireautoroute.app.data.TrancheAge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranAccueil(
    etat: EtatUi,
    onPositionManuelle: (Autoroute, Double, Sens) -> Unit,
    onModeAuto: (Boolean) -> Unit,
    onInverserSens: () -> Unit,
    onAire: (String) -> Unit,
    onDemanderPermission: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aires d'autoroute") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { encarts ->
        if (etat.chargement) {
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
                bottom = encarts.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                CartePosition(
                    etat = etat,
                    onPositionManuelle = onPositionManuelle,
                    onModeAuto = onModeAuto,
                    onInverserSens = onInverserSens,
                    onDemanderPermission = onDemanderPermission,
                )
            }

            if (etat.position != null) {
                item {
                    Text(
                        text = if (etat.prochainesAires.isEmpty()) {
                            "Aucune aire devant vous dans les 200 prochains kilomètres."
                        } else {
                            "Prochaines aires (${etat.prochainesAires.size})"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(etat.prochainesAires, key = { it.aire.id }) { resume ->
                    CarteAireResume(resume = resume, onClick = { onAire(resume.aire.id) })
                }
            }
        }
    }
}

@Composable
private fun CartePosition(
    etat: EtatUi,
    onPositionManuelle: (Autoroute, Double, Sens) -> Unit,
    onModeAuto: (Boolean) -> Unit,
    onInverserSens: () -> Unit,
    onDemanderPermission: () -> Unit,
) {
    var autoroute by remember(etat.autoroutes) {
        mutableStateOf(etat.autoroutes.firstOrNull { it.id == "A13" } ?: etat.autoroutes.firstOrNull())
    }
    var pkTexte by remember { mutableStateOf("") }
    var sens by remember { mutableStateOf(Sens.CROISSANT) }
    var menuOuvert by remember { mutableStateOf(false) }

    // En mode automatique, on reflète l'autoroute détectée dans le formulaire manuel.
    LaunchedEffect(etat.position) {
        val position = etat.position ?: return@LaunchedEffect
        if (position.source == SourcePosition.GPS) {
            autoroute = position.autoroute
            pkTexte = "%.0f".format(position.pk)
            sens = position.sens
        }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Ma position", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mode auto", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = etat.modeAuto,
                        onCheckedChange = { active ->
                            // La demande de permission enclenche elle-même le mode automatique.
                            if (active) onDemanderPermission() else onModeAuto(false)
                        },
                    )
                }
            }

            if (etat.modeAuto) {
                Text(
                    text = "La position et le sens sont déduits du GPS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Box {
                    OutlinedButton(onClick = { menuOuvert = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(autoroute?.let { "${it.nom} — ${it.libelle}" } ?: "Choisir une autoroute")
                    }
                    DropdownMenu(expanded = menuOuvert, onDismissRequest = { menuOuvert = false }) {
                        etat.autoroutes.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text("${candidate.nom} — ${candidate.terminusDebut} / ${candidate.terminusFin}") },
                                onClick = {
                                    autoroute = candidate
                                    menuOuvert = false
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = pkTexte,
                    onValueChange = { saisie -> pkTexte = saisie.filter { it.isDigit() || it == '.' || it == ',' } },
                    label = { Text("Point kilométrique") },
                    suffix = { Text(autoroute?.let { "/ %.0f km".format(it.longueurKm) } ?: "") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                autoroute?.let { choisie ->
                    Text("Direction", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = sens == Sens.CROISSANT,
                            onClick = { sens = Sens.CROISSANT },
                            label = { Text(choisie.terminusFin) },
                        )
                        FilterChip(
                            selected = sens == Sens.DECROISSANT,
                            onClick = { sens = Sens.DECROISSANT },
                            label = { Text(choisie.terminusDebut) },
                        )
                    }
                }

                TextButton(
                    onClick = {
                        val choisie = autoroute ?: return@TextButton
                        val pk = pkTexte.replace(',', '.').toDoubleOrNull() ?: return@TextButton
                        onPositionManuelle(choisie, pk, sens)
                    },
                    enabled = autoroute != null && pkTexte.replace(',', '.').toDoubleOrNull() != null,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Voir les prochaines aires")
                }
            }

            etat.message?.let { texte ->
                Text(
                    text = texte,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            etat.position?.let { position ->
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(position.libelle, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = buildString {
                                append(
                                    if (position.source == SourcePosition.GPS) {
                                        "Position GPS"
                                    } else {
                                        "Position saisie"
                                    },
                                )
                                position.ecartMetres?.let { append(" · à %.0f m du tracé".format(it)) }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onInverserSens) { Text("Inverser") }
                }
            }
        }
    }
}

@Composable
private fun CarteAireResume(resume: AireResume, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(resume.aire.nom, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "%s · PK %.1f".format(resume.aire.type.libelle, resume.aire.pk),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "dans %.0f km".format(resume.distanceKm),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            NoteAvecDetail(resume.noteGenerale)

            if (resume.notesJeux.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TrancheAge.entries.forEach { tranche ->
                        resume.notesJeux[tranche]?.let { note ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = tranche.libelle + " ",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                EtoilesLecture(note.moyenne, taille = 12)
                            }
                        }
                    }
                }
            }

            val equipements = resume.aire.equipements.filter { it != Critere.APPRECIATION_GENERALE }
            if (equipements.isNotEmpty()) {
                Text(
                    text = equipements.joinToString("  ") { "${it.emoji} ${it.libelle}" },
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (resume.enseignes.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    resume.enseignes.take(4).forEach { enseigne ->
                        AssistChip(onClick = onClick, label = { Text(enseigne.nom) })
                    }
                }
            }
        }
    }
}
