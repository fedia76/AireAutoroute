package com.aireautoroute.app

import com.aireautoroute.app.geo.MesurePosition
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ces règles sont le garde-fou du bug « position ancienne » : un point relu du cache du système
 * garde le cap et la vitesse qu'il avait au moment de sa prise, donc rien dans son contenu ne le
 * distingue d'une mesure du moment — sauf son âge.
 */
class MesurePositionTest {

    private fun mesure(
        ageMs: Long = 0L,
        precisionMetres: Float? = 8f,
        capDegres: Float? = 92f,
    ) = MesurePosition(
        latitude = 49.31,
        longitude = 1.05,
        ageMs = ageMs,
        precisionMetres = precisionMetres,
        capDegres = capDegres,
    )

    @Test
    fun `une mesure du moment est fraiche et precise`() {
        val recente = mesure(ageMs = 500L)
        assertTrue(recente.fraiche)
        assertTrue(recente.precise)
    }

    @Test
    fun `un point vieux d'une heure est refuse malgre un cap credible`() {
        val ancien = mesure(ageMs = 3_600_000L)
        assertFalse(ancien.fraiche)
        // Il paraît pourtant parfait sur tous les autres critères : c'est tout le piège.
        assertTrue(ancien.precise)
        assertTrue(ancien.capDegres != null)
    }

    @Test
    fun `la limite de fraicheur est inclusive`() {
        assertTrue(mesure(ageMs = MesurePosition.AGE_FRAIS_MS).fraiche)
        assertFalse(mesure(ageMs = MesurePosition.AGE_FRAIS_MS + 1).fraiche)
    }

    @Test
    fun `une position cellulaire annoncee au kilometre pres est refusee`() {
        assertFalse(mesure(precisionMetres = 1_500f).precise)
    }

    @Test
    fun `un fournisseur qui n'annonce pas sa precision reste accepte`() {
        assertTrue(mesure(precisionMetres = null).precise)
    }

    @Test
    fun `la limite de precision est inclusive`() {
        assertTrue(mesure(precisionMetres = MesurePosition.PRECISION_MAX_METRES).precise)
        assertFalse(mesure(precisionMetres = MesurePosition.PRECISION_MAX_METRES + 1f).precise)
    }
}
