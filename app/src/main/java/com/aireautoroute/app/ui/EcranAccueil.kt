package com.aireautoroute.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.sp
import com.aireautoroute.app.EtatUi
import com.aireautoroute.app.data.AireResume
import com.aireautoroute.app.data.Autoroute
import com.aireautoroute.app.data.Enseigne
import com.aireautoroute.app.data.EtatSynchro
import com.aireautoroute.app.data.ConsensusEquipement
import com.aireautoroute.app.data.NoteAgregee
import com.aireautoroute.app.data.Sens
import com.aireautoroute.app.data.SourcePosition
import com.aireautoroute.app.data.TrancheAge
import com.aireautoroute.app.ui.theme.LocalThemeApp
import com.aireautoroute.app.ui.theme.StyleListe
import com.aireautoroute.app.ui.theme.ThemeApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranAccueil(
    etat: EtatUi,
    themeCourant: ThemeApp,
    onTheme: (ThemeApp) -> Unit,
    onPositionManuelle: (Autoroute, Double, Sens) -> Unit,
    onInverserSens: () -> Unit,
    onAire: (String) -> Unit,
    onLocaliser: () -> Unit,
    onSources: () -> Unit,
    onCarte: () -> Unit,
    onMessageLu: () -> Unit,
) {
    var dialogueTheme by remember { mutableStateOf(false) }
    val messages = remember { SnackbarHostState() }

    // Une contribution qui n'est pas partie doit se voir.
    LaunchedEffect(etat.messageContribution) {
        val message = etat.messageContribution ?: return@LaunchedEffect
        messages.showSnackbar(message)
        onMessageLu()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(messages) },
        topBar = {
            TopAppBar(
                title = { Text("Aires d'autoroute") },
                actions = {
                    if (etat.synchro == EtatSynchro.HORS_LIGNE) {
                        Icon(
                            imageVector = Icons.Filled.CloudOff,
                            contentDescription = "Avis non rafraîchis, service injoignable",
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    if (etat.position != null) {
                        IconButton(onClick = onCarte) {
                            Icon(Icons.Filled.Map, contentDescription = "Voir la carte")
                        }
                    }
                    IconButton(onClick = { dialogueTheme = true }) {
                        Icon(Icons.Filled.Palette, contentDescription = "Changer d'apparence")
                    }
                    IconButton(onClick = onSources) {
                        Icon(Icons.Filled.Info, contentDescription = "Sources et licences")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
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

        val dense = LocalThemeApp.current.styleListe == StyleListe.TABLEAU_DE_BORD

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = if (dense) 0.dp else 16.dp,
                end = if (dense) 0.dp else 16.dp,
                top = encarts.calculateTopPadding() + 12.dp,
                bottom = encarts.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (dense) 0.dp else 12.dp),
        ) {
            item {
                CartePosition(
                    etat = etat,
                    modifier = if (dense) Modifier.padding(horizontal = 16.dp) else Modifier,
                    onPositionManuelle = onPositionManuelle,
                    onInverserSens = onInverserSens,
                    onLocaliser = onLocaliser,
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
                        modifier = Modifier.padding(
                            start = if (dense) 16.dp else 0.dp,
                            end = if (dense) 16.dp else 0.dp,
                            top = if (dense) 18.dp else 4.dp,
                            bottom = if (dense) 8.dp else 0.dp,
                        ),
                    )
                }
                items(etat.prochainesAires, key = { it.aire.id }) { resume ->
                    when (LocalThemeApp.current.styleListe) {
                        StyleListe.PANNEAU -> AirePanneau(resume) { onAire(resume.aire.id) }
                        StyleListe.CARNET -> AireCarnet(resume) { onAire(resume.aire.id) }
                        StyleListe.TABLEAU_DE_BORD -> AireTableauDeBord(resume) { onAire(resume.aire.id) }
                    }
                }
            }
        }
    }

    if (dialogueTheme) {
        DialogueTheme(
            themeCourant = themeCourant,
            onTheme = onTheme,
            onFermer = { dialogueTheme = false },
        )
    }
}

// --- Bloc « ma position » ----------------------------------------------------

@Composable
private fun CartePosition(
    etat: EtatUi,
    modifier: Modifier = Modifier,
    onPositionManuelle: (Autoroute, Double, Sens) -> Unit,
    onInverserSens: () -> Unit,
    onLocaliser: () -> Unit,
) {
    var autoroute by remember(etat.autoroutes) {
        mutableStateOf(etat.autoroutes.firstOrNull { it.id == "A13" } ?: etat.autoroutes.firstOrNull())
    }
    var pkTexte by remember { mutableStateOf("") }
    var sens by remember { mutableStateOf(Sens.CROISSANT) }
    var menuOuvert by remember { mutableStateOf(false) }
    val theme = LocalThemeApp.current

    // Une localisation réussie remplit le formulaire manuel, qui reste corrigeable.
    LaunchedEffect(etat.position) {
        val position = etat.position ?: return@LaunchedEffect
        if (position.source == SourcePosition.GPS) {
            autoroute = position.autoroute
            pkTexte = "%.0f".format(position.pk)
            sens = position.sens
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (theme.styleListe == StyleListe.CARNET) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (theme.styleListe == StyleListe.CARNET) 2.dp else 0.dp,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            etat.position?.let { position ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = position.autoroute.nom,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = theme.policeChiffres,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(
                            text = "PK %.1f · %s".format(position.pk, position.autoroute.terminus(position.sens)),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = theme.policeChiffres,
                            ),
                        )
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
                    IconButton(onClick = onInverserSens) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = "Inverser le sens")
                    }
                }
                HorizontalDivider()
            }

            Button(
                onClick = onLocaliser,
                enabled = !etat.localisation.enCours,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (etat.localisation.enCours) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text("  Localisation en cours…")
                } else {
                    Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = if (etat.position?.source == SourcePosition.GPS) {
                            "  Actualiser ma position"
                        } else {
                            "  Me localiser"
                        },
                    )
                }
            }

            Text(
                "…ou indiquez votre position à la main :",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Box {
                OutlinedButton(onClick = { menuOuvert = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(autoroute?.let { "${it.nom} · ${it.libelle}" } ?: "Choisir une autoroute")
                }
                DropdownMenu(expanded = menuOuvert, onDismissRequest = { menuOuvert = false }) {
                    etat.autoroutes.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text("${candidate.nom} · ${candidate.libelle}") },
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
                suffix = {
                    Text(autoroute?.let { "PK %.0f à %.0f".format(it.pkDebut, it.pkFin) } ?: "")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            autoroute?.let { choisie ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = sens == Sens.CROISSANT,
                        onClick = { sens = Sens.CROISSANT },
                        label = { Text("vers ${choisie.terminusFin}") },
                    )
                    FilterChip(
                        selected = sens == Sens.DECROISSANT,
                        onClick = { sens = Sens.DECROISSANT },
                        label = { Text("vers ${choisie.terminusDebut}") },
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

            etat.localisation.message?.let { texte ->
                Text(
                    text = texte,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// --- Les trois mises en page de la liste -------------------------------------

/** Thème « Signalétique » : liseré coloré, distance en cartouche. */
@Composable
private fun AirePanneau(resume: AireResume, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(
                        if (resume.noteGenerale == null) {
                            MaterialTheme.colorScheme.outline
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    ),
            )
            Column(
                Modifier.padding(14.dp).weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
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
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "%.0f".format(resume.distanceKm),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = LocalThemeApp.current.policeChiffres,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "KM",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                NoteAvecDetail(resume.noteGenerale)
                TranchesAgeChiffrees(resume.notesJeux)
                Equipements(resume.equipements)
                Enseignes(resume.enseignes)
            }
        }
    }
}

/** Thème « Carnet de route » : carte souple, notes des jeux en barres. */
@Composable
private fun AireCarnet(resume: AireResume, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = resume.aire.nom,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "dans %.0f km".format(resume.distanceKm),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                NoteAvecDetail(resume.noteGenerale)
            }
            Text(
                text = "%s · PK %.1f".format(resume.aire.type.libelle, resume.aire.pk),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TranchesAgeBarres(resume.notesJeux)
            Equipements(resume.equipements)
            Enseignes(resume.enseignes)
        }
    }
}

/** Thème « Copilote » : ligne dense, colonne de distance en chasse fixe. */
@Composable
private fun AireTableauDeBord(resume: AireResume, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier.width(56.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = "%.0f".format(resume.distanceKm),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = LocalThemeApp.current.policeChiffres,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "KM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = resume.aire.nom,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "PK %.0f".format(resume.aire.pk),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = LocalThemeApp.current.policeChiffres,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                NoteAvecDetail(resume.noteGenerale)
                TranchesAgeChiffrees(resume.notesJeux)
                Equipements(resume.equipements)
                Enseignes(resume.enseignes)
            }
        }
    }
}

// --- Fragments partagés ------------------------------------------------------

@Composable
private fun TranchesAgeChiffrees(notes: Map<TrancheAge, NoteAgregee>) {
    if (notes.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TrancheAge.entries.forEach { tranche ->
            val note = notes[tranche] ?: return@forEach
            val bonne = note.moyenne >= 4.0
            Column(
                Modifier
                    .border(
                        1.dp,
                        if (bonne) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                        MaterialTheme.shapes.extraSmall,
                    )
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Text(
                    text = tranche.libelle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "%.1f".format(note.moyenne),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = LocalThemeApp.current.policeChiffres,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = if (bonne) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun TranchesAgeBarres(notes: Map<TrancheAge, NoteAgregee>) {
    if (notes.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        TrancheAge.entries.forEach { tranche ->
            val note = notes[tranche] ?: return@forEach
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tranche.libelle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(62.dp),
                )
                LinearProgressIndicator(
                    progress = { (note.moyenne / 5.0).toFloat() },
                    modifier = Modifier.weight(1f).height(7.dp),
                    color = if (note.moyenne >= 4.0) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    text = " %.1f".format(note.moyenne),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(34.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Equipements(equipements: List<ConsensusEquipement>) {
    if (equipements.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        equipements.forEach { equipement ->
            val couleur = if (equipement.presentAvere) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = equipement.critere.icone,
                    contentDescription = null,
                    tint = couleurStatut(equipement.statut),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = " " + equipement.critere.libelle,
                    style = MaterialTheme.typography.bodySmall,
                    color = couleur,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Enseignes(enseignes: List<Enseigne>) {
    if (enseignes.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        enseignes.take(4).forEach { enseigne ->
            Text(
                text = enseigne.nom,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
    }
}
