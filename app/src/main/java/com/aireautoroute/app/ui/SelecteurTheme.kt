package com.aireautoroute.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aireautoroute.app.ui.theme.ThemeApp

/** Choix de l'habillage de l'application, avec un aperçu des couleurs de chaque thème. */
@Composable
fun DialogueTheme(
    themeCourant: ThemeApp,
    onTheme: (ThemeApp) -> Unit,
    onFermer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("Apparence") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ThemeApp.entries.forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = theme == themeCourant,
                                role = Role.RadioButton,
                                onClick = { onTheme(theme) },
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = theme == themeCourant,
                            onClick = { onTheme(theme) },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(theme.libelle, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = theme.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            theme.apercu.forEach { couleur ->
                                Row(
                                    Modifier
                                        .size(16.dp)
                                        .background(couleur, CircleShape)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                ) {}
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onFermer) { Text("Fermer") }
        },
    )
}
