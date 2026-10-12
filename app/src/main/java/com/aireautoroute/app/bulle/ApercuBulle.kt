package com.aireautoroute.app.bulle

import com.aireautoroute.app.data.AireResume
import com.aireautoroute.app.data.Catalogue
import com.aireautoroute.app.data.DonneesUtilisateur
import com.aireautoroute.app.data.PositionUtilisateur
import com.aireautoroute.app.data.SourcePosition
import com.aireautoroute.app.data.prochainesAires
import com.aireautoroute.app.geo.ResultatLocalisation

/**
 * Ce que la bulle affiche : où l'on est, et les quelques aires qui suivent.
 *
 * [position] est `null` quand le véhicule n'est sur aucune autoroute connue : la bulle le dit
 * plutôt que de garder les aires du dernier tronçon, qui ne sont plus devant.
 */
data class ApercuBulle(
    val position: PositionUtilisateur?,
    val aires: List<AireResume>,
) {
    companion object {
        val AUCUNE_POSITION = ApercuBulle(position = null, aires = emptyList())
    }
}

/** Nombre d'aires montrées dans la bulle ouverte. */
internal const val AIRES_DANS_LA_BULLE = 3

/** Au-delà, une aire n'aide pas à décider de s'arrêter : la bulle reste lisible d'un coup d'œil. */
internal const val PORTEE_BULLE_KM = 100.0

/**
 * Traduit un résultat de localisation en position.
 *
 * Même règle que dans l'écran d'accueil : sans cap exploitable, le sens déjà connu sur la même
 * autoroute l'emporte sur l'hypothèse du résultat. Sans quoi, un ralentissement ferait basculer la
 * bulle sur les aires de l'autre chaussée.
 */
internal fun positionPourBulle(
    resultat: ResultatLocalisation,
    precedente: PositionUtilisateur?,
): PositionUtilisateur {
    val sens = if (resultat.sensFiable) {
        resultat.sens
    } else {
        precedente
            ?.takeIf { it.autoroute.id == resultat.autoroute.id }
            ?.sens
            ?: resultat.sens
    }
    return PositionUtilisateur(
        autoroute = resultat.autoroute,
        pk = resultat.pk,
        sens = sens,
        source = SourcePosition.GPS,
        ecartMetres = resultat.ecartMetres,
        sensFiable = resultat.sensFiable,
    )
}

internal fun apercuBulle(
    catalogue: Catalogue,
    donnees: DonneesUtilisateur,
    position: PositionUtilisateur,
): ApercuBulle = ApercuBulle(
    position = position,
    aires = prochainesAires(catalogue, donnees, position, PORTEE_BULLE_KM)
        .take(AIRES_DANS_LA_BULLE),
)
