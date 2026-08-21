package com.aireautoroute.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BabyChangingStation
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Wc
import androidx.compose.ui.graphics.vector.ImageVector
import com.aireautoroute.app.data.Critere

/** Pictogramme de chaque critère, commun aux trois thèmes. */
val Critere.icone: ImageVector
    get() = when (this) {
        Critere.AIRE_JEUX_INTERIEURE -> Icons.Filled.Toys
        Critere.AIRE_JEUX_EXTERIEURE -> Icons.Filled.Park
        Critere.TOILETTES -> Icons.Filled.Wc
        Critere.STATION_SERVICE -> Icons.Filled.LocalGasStation
        Critere.TABLE_A_LANGER -> Icons.Filled.BabyChangingStation
        Critere.APPRECIATION_GENERALE -> Icons.Filled.Star
    }
