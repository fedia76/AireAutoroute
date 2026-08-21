package com.aireautoroute.app.geo

import com.aireautoroute.app.data.Autoroute
import com.aireautoroute.app.data.Sens
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

/** Résultat de la conversion « coordonnées GPS → autoroute + point kilométrique ». */
data class ResultatLocalisation(
    val autoroute: Autoroute,
    val pk: Double,
    val sens: Sens,
    /** Distance entre le point GPS et le tracé retenu, en mètres. */
    val ecartMetres: Double,
    /** `false` quand le cap GPS était indisponible : le sens est alors une hypothèse. */
    val sensFiable: Boolean,
)

/**
 * Convertit une position GPS en (autoroute, PK, sens).
 *
 * Principe : chaque autoroute est décrite par une poly-ligne de points de référence portant leur
 * PK. On projette le point GPS sur chaque segment de chaque poly-ligne (géométrie plane locale,
 * l'erreur est négligeable à cette échelle), on garde le segment le plus proche, puis on
 * interpole le PK entre les deux extrémités du segment. Le sens vient de la comparaison entre le
 * cap GPS et l'azimut du segment orienté dans le sens des PK croissants.
 */
object LocalisateurPk {

    private const val RAYON_TERRE_M = 6_371_008.8

    /** Au-delà de cet écart au tracé, on considère que l'utilisateur n'est pas sur l'autoroute. */
    const val ECART_MAX_METRES = 3_000.0

    fun localiser(
        autoroutes: List<Autoroute>,
        lat: Double,
        lon: Double,
        capDegres: Float?,
        ecartMaxMetres: Double = ECART_MAX_METRES,
    ): ResultatLocalisation? {
        var meilleur: ResultatLocalisation? = null
        for (autoroute in autoroutes) {
            val geometrie = autoroute.geometrie
            // Le réseau complet représente des dizaines de milliers de segments : un rejet
            // grossier sur les coordonnées évite d'en projeter la quasi-totalité.
            val margeLat = ecartMaxMetres / 111_320.0
            val margeLon = margeLat / cos(Math.toRadians(lat)).coerceAtLeast(0.1)

            for (i in 1 until geometrie.size) {
                val depart = geometrie[i - 1]
                val arrivee = geometrie[i]
                if (lat < minOf(depart.lat, arrivee.lat) - margeLat) continue
                if (lat > maxOf(depart.lat, arrivee.lat) + margeLat) continue
                if (lon < minOf(depart.lon, arrivee.lon) - margeLon) continue
                if (lon > maxOf(depart.lon, arrivee.lon) + margeLon) continue
                // Repère plan local centré sur le début du segment (en mètres).
                val (bx, by) = versPlan(arrivee.lat, arrivee.lon, depart.lat, depart.lon)
                val (px, py) = versPlan(lat, lon, depart.lat, depart.lon)
                val longueur2 = bx * bx + by * by
                val t = if (longueur2 == 0.0) {
                    0.0
                } else {
                    ((px * bx + py * by) / longueur2).coerceIn(0.0, 1.0)
                }
                val ecart = hypot(px - t * bx, py - t * by)
                if (ecart > ecartMaxMetres) continue
                if (meilleur != null && ecart >= meilleur.ecartMetres) continue

                val azimutCroissant = azimut(bx, by)
                val sens = when {
                    capDegres == null -> Sens.CROISSANT
                    ecartAngulaire(capDegres.toDouble(), azimutCroissant) <= 90.0 -> Sens.CROISSANT
                    else -> Sens.DECROISSANT
                }
                meilleur = ResultatLocalisation(
                    autoroute = autoroute,
                    pk = depart.pk + t * (arrivee.pk - depart.pk),
                    sens = sens,
                    ecartMetres = ecart,
                    sensFiable = capDegres != null,
                )
            }
        }
        return meilleur
    }

    /** Projection équirectangulaire locale : (lat, lon) → (x est, y nord) en mètres. */
    private fun versPlan(lat: Double, lon: Double, latOrigine: Double, lonOrigine: Double): Pair<Double, Double> {
        val x = Math.toRadians(lon - lonOrigine) * cos(Math.toRadians(latOrigine)) * RAYON_TERRE_M
        val y = Math.toRadians(lat - latOrigine) * RAYON_TERRE_M
        return x to y
    }

    /** Azimut d'un vecteur du repère local, en degrés depuis le nord (0-360). */
    private fun azimut(x: Double, y: Double): Double =
        (Math.toDegrees(atan2(x, y)) + 360.0) % 360.0

    /** Écart absolu entre deux caps, ramené dans 0-180°. */
    fun ecartAngulaire(a: Double, b: Double): Double =
        abs(((a - b + 540.0) % 360.0) - 180.0)
}
