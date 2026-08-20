package com.aireautoroute.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aireautoroute.app.data.NoteAgregee

private val Or = Color(0xFFF2A93B)

/** Étoiles en lecture seule (moyenne arrondie au demi-point le plus proche). */
@Composable
fun EtoilesLecture(
    note: Double,
    modifier: Modifier = Modifier,
    taille: Int = 16,
) {
    val pleines = Math.round(note).toInt().coerceIn(0, 5)
    Text(
        text = "★".repeat(pleines) + "☆".repeat(5 - pleines),
        color = Or,
        fontSize = taille.sp,
        modifier = modifier,
    )
}

/** Étoiles + moyenne chiffrée + nombre d'avis, ou mention « pas encore noté ». */
@Composable
fun NoteAvecDetail(note: NoteAgregee?, modifier: Modifier = Modifier) {
    if (note == null) {
        Text(
            text = "Pas encore noté",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        EtoilesLecture(note.moyenne)
        Text(
            text = " %.1f (%d avis)".format(note.moyenne, note.nombre),
            style = MaterialTheme.typography.bodySmall,
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
            Text(
                text = if (valeur <= note) "★" else "☆",
                color = Or,
                fontSize = 30.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clickable { onNote(if (note == valeur) 0 else valeur) }
                    .padding(horizontal = 2.dp),
            )
        }
    }
}
