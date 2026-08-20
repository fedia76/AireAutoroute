package com.aireautoroute.app

import com.aireautoroute.app.data.Autoroute
import com.aireautoroute.app.data.PointReference
import com.aireautoroute.app.data.Sens
import com.aireautoroute.app.geo.LocalisateurPk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalisateurPkTest {

    /** Autoroute rectiligne plein est : les PK croissent vers l'est. */
    private val autorouteTest = Autoroute(
        id = "TEST",
        nom = "A99",
        terminusDebut = "Ouest",
        terminusFin = "Est",
        longueurKm = 100.0,
        geometrie = listOf(
            PointReference(pk = 0.0, lat = 48.0, lon = 0.0),
            PointReference(pk = 100.0, lat = 48.0, lon = 1.0),
        ),
    )

    @Test
    fun `interpole le pk au milieu du segment`() {
        val resultat = LocalisateurPk.localiser(listOf(autorouteTest), 48.0, 0.5, capDegres = 90f)
        requireNotNull(resultat)
        assertEquals("TEST", resultat.autoroute.id)
        assertEquals(50.0, resultat.pk, 0.5)
        assertTrue(resultat.ecartMetres < 50.0)
    }

    @Test
    fun `deduit le sens du cap gps`() {
        val versEst = LocalisateurPk.localiser(listOf(autorouteTest), 48.0, 0.5, capDegres = 90f)
        val versOuest = LocalisateurPk.localiser(listOf(autorouteTest), 48.0, 0.5, capDegres = 270f)
        assertEquals(Sens.CROISSANT, versEst?.sens)
        assertEquals(Sens.DECROISSANT, versOuest?.sens)
        assertEquals(true, versEst?.sensFiable)
    }

    @Test
    fun `signale un sens non fiable sans cap`() {
        val resultat = LocalisateurPk.localiser(listOf(autorouteTest), 48.0, 0.5, capDegres = null)
        assertEquals(false, resultat?.sensFiable)
    }

    @Test
    fun `ne retient rien loin de tout trace`() {
        val resultat = LocalisateurPk.localiser(listOf(autorouteTest), 45.0, 0.5, capDegres = 90f)
        assertNull(resultat)
    }

    @Test
    fun `retient l autoroute la plus proche`() {
        val autre = autorouteTest.copy(
            id = "AUTRE",
            geometrie = listOf(
                PointReference(pk = 0.0, lat = 48.02, lon = 0.0),
                PointReference(pk = 100.0, lat = 48.02, lon = 1.0),
            ),
        )
        val resultat = LocalisateurPk.localiser(listOf(autre, autorouteTest), 48.001, 0.5, capDegres = 90f)
        assertEquals("TEST", resultat?.autoroute?.id)
    }

    @Test
    fun `ecart angulaire circulaire`() {
        assertEquals(10.0, LocalisateurPk.ecartAngulaire(5.0, 355.0), 0.001)
        assertEquals(180.0, LocalisateurPk.ecartAngulaire(0.0, 180.0), 0.001)
    }
}
