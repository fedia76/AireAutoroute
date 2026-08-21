package com.aireautoroute.app

import com.aireautoroute.app.data.Aire
import com.aireautoroute.app.data.Critere
import com.aireautoroute.app.data.DeclarationEquipement
import com.aireautoroute.app.data.Presence
import com.aireautoroute.app.data.SEUIL_CONFIRMATION
import com.aireautoroute.app.data.Sens
import com.aireautoroute.app.data.StatutEquipement
import com.aireautoroute.app.data.consensus
import org.junit.Assert.assertEquals
import org.junit.Test

class ConsensusTest {

    /** Aire dont l'exploitant annonce des toilettes, mais pas de table à langer. */
    private val aire = Aire(
        id = "aire",
        autorouteId = "A13",
        nom = "Aire de test",
        pk = 50.0,
        sens = Sens.LES_DEUX,
        equipements = listOf(Critere.TOILETTES),
    )

    private fun declarations(critere: Critere, vararg presences: Presence) = presences.mapIndexed { i, p ->
        DeclarationEquipement(
            id = "d$i-$critere-$p",
            aireId = "aire",
            critere = critere,
            presence = p,
            date = "2026-08-20T10:00:00Z",
        )
    }

    @Test
    fun `sans declaration un equipement annonce reste a verifier`() {
        val resultat = consensus(aire, emptyList(), Critere.TOILETTES)
        assertEquals(StatutEquipement.ANNONCE, resultat.statut)
        assertEquals(false, resultat.presentAvere)
    }

    @Test
    fun `sans declaration un equipement non annonce est inconnu`() {
        assertEquals(
            StatutEquipement.INCONNU,
            consensus(aire, emptyList(), Critere.TABLE_A_LANGER).statut,
        )
    }

    @Test
    fun `une seule declaration ne suffit pas a confirmer`() {
        val resultat = consensus(
            aire,
            declarations(Critere.TABLE_A_LANGER, Presence.OUI),
            Critere.TABLE_A_LANGER,
        )
        assertEquals(StatutEquipement.A_CONFIRMER, resultat.statut)
        assertEquals(false, resultat.presentAvere)
        assertEquals(1, resultat.declarationsOui)
    }

    @Test
    fun `le seuil de declarations concordantes confirme l equipement`() {
        val resultat = consensus(
            aire,
            declarations(Critere.TABLE_A_LANGER, Presence.OUI, Presence.OUI),
            Critere.TABLE_A_LANGER,
        )
        assertEquals(StatutEquipement.CONFIRME, resultat.statut)
        assertEquals(true, resultat.presentAvere)
        assertEquals(SEUIL_CONFIRMATION, resultat.declarationsOui)
    }

    @Test
    fun `les avis ne sais pas ne comptent pas`() {
        val resultat = consensus(
            aire,
            declarations(
                Critere.TABLE_A_LANGER,
                Presence.OUI,
                Presence.NE_SAIS_PAS,
                Presence.NE_SAIS_PAS,
            ),
            Critere.TABLE_A_LANGER,
        )
        assertEquals(StatutEquipement.A_CONFIRMER, resultat.statut)
        assertEquals(0, resultat.declarationsNon)
    }

    @Test
    fun `un equipement annonce mais dementi passe absent`() {
        val resultat = consensus(
            aire,
            declarations(Critere.TOILETTES, Presence.NON, Presence.NON),
            Critere.TOILETTES,
        )
        assertEquals(StatutEquipement.ABSENT, resultat.statut)
        assertEquals(2, resultat.declarationsNon)
    }

    @Test
    fun `la majorite tranche quand les avis divergent`() {
        val majoritairementPresent = consensus(
            aire,
            declarations(Critere.TOILETTES, Presence.OUI, Presence.OUI, Presence.OUI, Presence.NON),
            Critere.TOILETTES,
        )
        assertEquals(StatutEquipement.CONFIRME, majoritairementPresent.statut)

        val majoritairementAbsent = consensus(
            aire,
            declarations(Critere.TOILETTES, Presence.OUI, Presence.NON, Presence.NON, Presence.NON),
            Critere.TOILETTES,
        )
        assertEquals(StatutEquipement.ABSENT, majoritairementAbsent.statut)
    }

    @Test
    fun `ne tient pas compte des declarations d une autre aire ou d un autre critere`() {
        val autres = declarations(Critere.TOILETTES, Presence.OUI, Presence.OUI)
            .map { it.copy(aireId = "autre-aire") } +
            declarations(Critere.STATION_SERVICE, Presence.OUI, Presence.OUI)
        assertEquals(StatutEquipement.ANNONCE, consensus(aire, autres, Critere.TOILETTES).statut)
    }
}
