package com.aireautoroute.app.geo

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

/**
 * Projection plane locale pour l'affichage cartographique.
 *
 * À l'échelle d'une autoroute, une projection équirectangulaire centrée sur la zone affichée est
 * indiscernable d'une projection savante : on travaille donc dans un repère en kilomètres, l'axe
 * des x vers l'est et l'axe des y vers le sud (le sens des écrans).
 */
class ProjectionCarte(private val latRef: Double, private val lonRef: Double) {

    private val kmParDegreLongitude = KM_PAR_DEGRE * cos(Math.toRadians(latRef))

    fun x(lon: Double): Double = (lon - lonRef) * kmParDegreLongitude

    fun y(lat: Double): Double = (latRef - lat) * KM_PAR_DEGRE

    /** Latitude correspondant à une ordonnée du repère (utile pour interpréter un appui). */
    fun latitude(y: Double): Double = latRef - y / KM_PAR_DEGRE

    fun longitude(x: Double): Double = lonRef + x / kmParDegreLongitude

    companion object {
        const val KM_PAR_DEGRE = 111.32

        fun centreeSur(points: List<Pair<Double, Double>>): ProjectionCarte {
            if (points.isEmpty()) return ProjectionCarte(46.5, 2.5)
            return ProjectionCarte(
                latRef = points.sumOf { it.first } / points.size,
                lonRef = points.sumOf { it.second } / points.size,
            )
        }
    }
}

/** Fenêtre affichée : un centre en kilomètres et une échelle en pixels par kilomètre. */
data class VueCarte(
    val centreX: Double,
    val centreY: Double,
    val pixelsParKm: Double,
) {
    fun deplacee(dxPixels: Double, dyPixels: Double): VueCarte =
        copy(centreX = centreX - dxPixels / pixelsParKm, centreY = centreY - dyPixels / pixelsParKm)

    /** Zoom centré sur un point de l'écran, pour que ce point reste sous le doigt. */
    fun zoomee(facteur: Double, ancreX: Double, ancreY: Double, largeur: Double, hauteur: Double): VueCarte {
        val nouvelleEchelle = (pixelsParKm * facteur).coerceIn(ECHELLE_MIN, ECHELLE_MAX)
        if (nouvelleEchelle == pixelsParKm) return this
        val mondeX = versMondeX(ancreX, largeur)
        val mondeY = versMondeY(ancreY, hauteur)
        val rapport = pixelsParKm / nouvelleEchelle
        return VueCarte(
            centreX = mondeX + (centreX - mondeX) * rapport,
            centreY = mondeY + (centreY - mondeY) * rapport,
            pixelsParKm = nouvelleEchelle,
        )
    }

    fun versEcranX(x: Double, largeur: Double): Double = largeur / 2 + (x - centreX) * pixelsParKm

    fun versEcranY(y: Double, hauteur: Double): Double = hauteur / 2 + (y - centreY) * pixelsParKm

    fun versMondeX(ecranX: Double, largeur: Double): Double = centreX + (ecranX - largeur / 2) / pixelsParKm

    fun versMondeY(ecranY: Double, hauteur: Double): Double = centreY + (ecranY - hauteur / 2) / pixelsParKm

    companion object {
        const val ECHELLE_MIN = 0.05
        const val ECHELLE_MAX = 60.0

        /** Cadre la vue sur un ensemble de points, avec une marge. */
        fun cadree(
            points: List<Pair<Double, Double>>,
            largeurPixels: Double,
            hauteurPixels: Double,
            margeRelative: Double = 0.15,
        ): VueCarte {
            if (points.isEmpty() || largeurPixels <= 0 || hauteurPixels <= 0) {
                return VueCarte(0.0, 0.0, 1.0)
            }
            val minX = points.minOf { it.first }
            val maxX = points.maxOf { it.first }
            val minY = points.minOf { it.second }
            val maxY = points.maxOf { it.second }
            // Une largeur nulle (un seul point) donnerait une échelle infinie.
            val largeurKm = max(abs(maxX - minX), 0.5)
            val hauteurKm = max(abs(maxY - minY), 0.5)
            val echelle = minOf(
                largeurPixels / (largeurKm * (1 + margeRelative)),
                hauteurPixels / (hauteurKm * (1 + margeRelative)),
            ).coerceIn(ECHELLE_MIN, ECHELLE_MAX)
            return VueCarte(
                centreX = (minX + maxX) / 2,
                centreY = (minY + maxY) / 2,
                pixelsParKm = echelle,
            )
        }
    }
}
