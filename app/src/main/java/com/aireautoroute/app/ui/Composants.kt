package com.aireautoroute.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aireautoroute.app.data.ConsensusEquipement
import com.aireautoroute.app.data.NoteAgregee
import com.aireautoroute.app.data.StatutEquipement
import com.aireautoroute.app.ui.theme.LocalThemeApp

/** Couleur associée à chaque statut de présence, déclinée dans la palette du thème courant. */
@Composable
fun couleurStatut(statut: StatutEquipement): Color = when (statut) {
    StatutEquipement.CONFIRME -> MaterialTheme.colorScheme.tertiary
    StatutEquipement.A_CONFIRMER, StatutEquipement.ANNONCE -> MaterialTheme.colorScheme.secondary
    StatutEquipement.ABSENT -> MaterialTheme.colorScheme.error
    StatutEquipement.INCONNU -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Étoiles en lecture seule (moyenne arrondie à l'étoile la plus proche). */
@Composable
fun EtoilesLecture(
    note: Double,
    modifier: Modifier = Modifier,
    taille: Dp = 16.dp,
) {
    val pleines = Math.round(note).toInt().coerceIn(0, 5)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { index ->
            Icon(
                imageVector = if (index < pleines) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(taille),
            )
        }
    }
}

/** Étoiles + moyenne chiffrée + nombre d'avis, ou mention « pas encore notée ». */
@Composable
fun NoteAvecDetail(note: NoteAgregee?, modifier: Modifier = Modifier) {
    if (note == null) {
        Text(
            text = "Pas encore notée",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        EtoilesLecture(note.moyenne)
        Text(
            text = " %.1f · %d avis".format(note.moyenne, note.nombre),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = LocalThemeApp.current.policeChiffres,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Étoiles cliquables pour la saisie d'une note (0 = non renseigné). */
@Composable
fun EtoilesSaisie(
    note: Int,
    onNote: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..5).forEach { valeur ->
            Icon(
                imageVector = if (valeur <= note) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = "$valeur étoile(s)",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .size(32.dp)
                    .clickable { onNote(if (note == valeur) 0 else valeur) }
                    .padding(2.dp),
            )
        }
    }
}

/** Ce que l'on sait de la présence d'un équipement : coche verte si le consensus est atteint. */
@Composable
fun EtiquetteStatut(
    consensus: ConsensusEquipement,
    modifier: Modifier = Modifier,
    avecDetail: Boolean = false,
) {
    val couleur = couleurStatut(consensus.statut)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (consensus.presentAvere) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = couleur,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = consensus.statut.libelle + if (avecDetail) " · ${consensus.detailDeclarations}" else "",
            style = MaterialTheme.typography.labelSmall,
            color = couleur,
        )
    }
}
