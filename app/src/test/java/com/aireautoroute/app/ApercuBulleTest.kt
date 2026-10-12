package com.aireautoroute.app

import com.aireautoroute.app.bulle.positionPourBulle
import com.aireautoroute.app.data.Autoroute
import com.aireautoroute.app.data.PositionUtilisateur
import com.aireautoroute.app.data.Sens
import com.aireautoroute.app.data.SourcePosition
import com.aireautoroute.app.geo.ResultatLocalisation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApercuBulleTest {

    private val a13 = Autoroute(
        id = "A13", nom = "A13", terminusDebut = "Paris", terminusFin = "Caen", longueurKm = 227.0,
    )
    private val a14 = Autoroute(
        id = "A14", nom = "A14", terminusDebut = "Orgeval", terminusFin = "Paris", longueurKm = 20.0,
    )

    private fun resultat(autoroute: Autoroute, sens: Sens, sensFiable: Boolean) =
        ResultatLocalisation(autoroute, pk = 55.0, sens = sens, ecartMetres = 12.0, sensFiable = sensFiable)

    private fun precedente(autoroute: Autoroute, sens: Sens) =
        PositionUtilisateur(autoroute, 50.0, sens, SourcePosition.GPS)

    @Test
    fun `un sens fiable l'emporte sur la position precedente`() {
        val position = positionPourBulle(
            resultat(a13, Sens.DECROISSANT, sensFiable = true),
            precedente(a13, Sens.CROISSANT),
        )
        assertEquals(Sens.DECROISSANT, position.sens)
        assertTrue(position.sensFiable)
    }

    @Test
    fun `sans cap, le sens connu sur la meme autoroute est conserve`() {
        val position = positionPourBulle(
            resultat(a13, Sens.DECROISSANT, sensFiable = false),
            precedente(a13, Sens.CROISSANT),
        )
        assertEquals(Sens.CROISSANT, position.sens)
        assertFalse(position.sensFiable)
    }

    @Test
    fun `sans cap, le sens d'une autre autoroute n'est pas reporte`() {
        val position = positionPourBulle(
            resultat(a14, Sens.DECROISSANT, sensFiable = false),
            precedente(a13, Sens.CROISSANT),
        )
        assertEquals(Sens.DECROISSANT, position.sens)
    }

    @Test
    fun `sans cap ni position precedente, l'hypothese du resultat reste`() {
        val position = positionPourBulle(resultat(a13, Sens.CROISSANT, sensFiable = false), null)
        assertEquals(Sens.CROISSANT, position.sens)
        assertEquals(SourcePosition.GPS, position.source)
    }
}
