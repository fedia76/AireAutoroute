package com.aireautoroute.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BabyChangingStation
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Wc
import androidx.compose.ui.graphics.vector.ImageVector
import com.aireautoroute.app.data.Critere
import com.aireautoroute.app.data.IconeEnseigne

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

/**
 * Dessin de chaque pictogramme d'enseigne.
 *
 * Le jeu de pictogrammes vit dans le modèle ([IconeEnseigne]) parce qu'il voyage jusqu'au
 * service ; le dessin, lui, reste ici : changer d'icône ne doit pas changer la donnée.
 */
val IconeEnseigne.vecteur: ImageVector
    get() = when (this) {
        IconeEnseigne.CARBURANT -> Icons.Filled.LocalGasStation
        IconeEnseigne.RECHARGE_ELECTRIQUE -> Icons.Filled.EvStation
        IconeEnseigne.RESTAURATION_RAPIDE -> Icons.Filled.Fastfood
        IconeEnseigne.RESTAURANT -> Icons.Filled.Restaurant
        IconeEnseigne.BOULANGERIE -> Icons.Filled.BakeryDining
        IconeEnseigne.CAFE -> Icons.Filled.LocalCafe
        IconeEnseigne.PIZZERIA -> Icons.Filled.LocalPizza
        IconeEnseigne.SUPERETTE -> Icons.Filled.LocalGroceryStore
        IconeEnseigne.BOUTIQUE -> Icons.Filled.Storefront
        IconeEnseigne.HOTEL -> Icons.Filled.Hotel
    }
