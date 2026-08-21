package com.aireautoroute.app

import com.aireautoroute.app.data.Aire
import com.aireautoroute.app.data.Autoroute
import com.aireautoroute.app.data.Catalogue
import com.aireautoroute.app.data.Critere
import com.aireautoroute.app.data.DonneesUtilisateur
import com.aireautoroute.app.data.Notation
import com.aireautoroute.app.data.PositionUtilisateur
import com.aireautoroute.app.data.Sens
import com.aireautoroute.app.data.SourcePosition
import com.aireautoroute.app.data.StatutEquipement
import com.aireautoroute.app.data.TrancheAge
import com.aireautoroute.app.data.detailAire
import com.aireautoroute.app.data.prochainesAires
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProchainesAiresTest {

    private val a13 = Autoroute(
        id = "A13",
        nom = "A13",
        terminusDebut = "Paris",
        terminusFin = "Caen",
        longueurKm = 227.0,
    )

    private val aires = listOf(
        aire("derriere", pk = 20.0, sens = Sens.CROISSANT),
        aire("devant-proche", pk = 60.0, sens = Sens.CROISSANT),
        aire("devant-loin", pk = 130.0, sens = Sens.CROISSANT),
        aire("hors-portee", pk = 300.0, sens = Sens.CROISSANT),
        aire("autre-sens", pk = 70.0, sens = Sens.DECROISSANT),
        aire("les-deux", pk = 80.0, sens = Sens.LES_DEUX),
    )

    private fun aire(id: String, pk: Double, sens: Sens) = Aire(
        id = id,
        autorouteId = "A13",
        nom = id,
        pk = pk,
        sens = sens,
        equipements = listOf(Critere.TOILETTES, Critere.AIRE_JEUX_EXTERIEURE),
    )

    private val catalogue = Catalogue(listOf(a13), aires, emptyList(), emptyList())

    private fun position(pk: Double, sens: Sens) =
        PositionUtilisateur(a13, pk, sens, SourcePosition.MANUELLE)

    @Test
    fun `ne garde que les aires devant, dans le bon sens et a portee`() {
        val resultat = prochainesAires(catalogue, DonneesUtilisateur(), position(55.0, Sens.CROISSANT))
        assertEquals(listOf("devant-proche", "les-deux", "devant-loin"), resultat.map { it.aire.id })
        assertEquals(5.0, resultat.first().distanceKm, 0.001)
    }

    @Test
    fun `inverse la logique dans le sens decroissant`() {
        val resultat = prochainesAires(catalogue, DonneesUtilisateur(), position(100.0, Sens.DECROISSANT))
        assertEquals(listOf("les-deux", "autre-sens"), resultat.map { it.aire.id })
        assertEquals(20.0, resultat.first().distanceKm, 0.001)
    }

    @Test
    fun `moyenne les notes par critere et par tranche d age`() {
        val donnees = DonneesUtilisateur(
            notations = listOf(
                notation("devant-proche", Critere.APPRECIATION_GENERALE, null, 4),
                notation("devant-proche", Critere.APPRECIATION_GENERALE, null, 2),
                notation("devant-proche", Critere.AIRE_JEUX_EXTERIEURE, TrancheAge.ANS_3_6, 5),
                notation("devant-proche", Critere.AIRE_JEUX_EXTERIEURE, TrancheAge.ANS_3_6, 4),
            ),
        )
        val resume = prochainesAires(catalogue, donnees, position(55.0, Sens.CROISSANT)).first()
        assertEquals(3.0, resume.noteGenerale!!.moyenne, 0.001)
        assertEquals(2, resume.noteGenerale!!.nombre)
        assertEquals(4.5, resume.notesJeux[TrancheAge.ANS_3_6]!!.moyenne, 0.001)

        val detail = detailAire(catalogue, donnees, "devant-proche")!!
        val jeux = detail.criteres.first { it.critere == Critere.AIRE_JEUX_EXTERIEURE }
        assertEquals(4.5, jeux.parTrancheAge[TrancheAge.ANS_3_6]!!.moyenne, 0.001)
        assertEquals(StatutEquipement.ANNONCE, jeux.consensus?.statut)
        assertEquals(
            StatutEquipement.INCONNU,
            detail.criteres.first { it.critere == Critere.STATION_SERVICE }.consensus?.statut,
        )
    }

    private fun notation(aireId: String, critere: Critere, tranche: TrancheAge?, note: Int) = Notation(
        id = "$aireId-$critere-$tranche-$note",
        aireId = aireId,
        critere = critere,
        trancheAge = tranche,
        note = note,
        date = "2026-08-20T10:00:00Z",
    )
}
