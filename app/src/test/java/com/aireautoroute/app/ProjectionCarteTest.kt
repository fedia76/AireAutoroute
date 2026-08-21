package com.aireautoroute.app

import com.aireautoroute.app.geo.ProjectionCarte
import com.aireautoroute.app.geo.VueCarte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionCarteTest {

    private val projection = ProjectionCarte(latRef = 49.0, lonRef = 1.0)

    @Test
    fun `le point de reference est a l origine`() {
        assertEquals(0.0, projection.x(1.0), 1e-9)
        assertEquals(0.0, projection.y(49.0), 1e-9)
    }

    @Test
    fun `un degre de latitude vaut environ 111 kilometres`() {
        assertEquals(-111.32, projection.y(50.0), 0.01)
    }

    @Test
    fun `l axe des y descend vers le sud`() {
        assertTrue("le sud doit avoir une ordonnée positive", projection.y(48.0) > 0)
    }

    @Test
    fun `un degre de longitude se resserre avec la latitude`() {
        // À 49° de latitude, un degré de longitude vaut environ 73 km.
        assertEquals(73.0, projection.x(2.0), 1.0)
    }

    @Test
    fun `la conversion inverse retrouve les coordonnees`() {
        val x = projection.x(1.234)
        val y = projection.y(48.765)
        assertEquals(1.234, projection.longitude(x), 1e-9)
        assertEquals(48.765, projection.latitude(y), 1e-9)
    }

    @Test
    fun `le cadrage englobe tous les points`() {
        val points = listOf(0.0 to 0.0, 100.0 to 40.0, -20.0 to 10.0)
        val vue = VueCarte.cadree(points, largeurPixels = 1000.0, hauteurPixels = 2000.0)
        points.forEach { (x, y) ->
            val ecranX = vue.versEcranX(x, 1000.0)
            val ecranY = vue.versEcranY(y, 2000.0)
            assertTrue("x hors cadre : $ecranX", ecranX in 0.0..1000.0)
            assertTrue("y hors cadre : $ecranY", ecranY in 0.0..2000.0)
        }
    }

    @Test
    fun `un point unique ne donne pas une echelle infinie`() {
        val vue = VueCarte.cadree(listOf(5.0 to 5.0), 1000.0, 1000.0)
        assertTrue(vue.pixelsParKm <= VueCarte.ECHELLE_MAX)
    }

    @Test
    fun `le deplacement suit le doigt`() {
        val vue = VueCarte(centreX = 0.0, centreY = 0.0, pixelsParKm = 10.0)
        // Glisser de 100 pixels vers la droite ramène le centre de 10 km vers l'ouest.
        assertEquals(-10.0, vue.deplacee(100.0, 0.0).centreX, 1e-9)
    }

    @Test
    fun `le zoom garde le point d ancrage sous le doigt`() {
        val vue = VueCarte(centreX = 0.0, centreY = 0.0, pixelsParKm = 10.0)
        val largeur = 800.0
        val hauteur = 600.0
        val ancreX = 200.0
        val ancreY = 150.0
        val avant = vue.versMondeX(ancreX, largeur) to vue.versMondeY(ancreY, hauteur)

        val zoomee = vue.zoomee(2.0, ancreX, ancreY, largeur, hauteur)

        assertEquals(20.0, zoomee.pixelsParKm, 1e-9)
        assertEquals(avant.first, zoomee.versMondeX(ancreX, largeur), 1e-6)
        assertEquals(avant.second, zoomee.versMondeY(ancreY, hauteur), 1e-6)
    }

    @Test
    fun `le zoom reste borne`() {
        val vue = VueCarte(0.0, 0.0, VueCarte.ECHELLE_MAX)
        assertEquals(VueCarte.ECHELLE_MAX, vue.zoomee(10.0, 0.0, 0.0, 100.0, 100.0).pixelsParKm, 1e-9)
    }
}
