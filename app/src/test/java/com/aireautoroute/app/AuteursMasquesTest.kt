package com.aireautoroute.app

import com.aireautoroute.app.data.Critere
import com.aireautoroute.app.data.DeclarationEquipement
import com.aireautoroute.app.data.DonneesUtilisateur
import com.aireautoroute.app.data.LienAireEnseigne
import com.aireautoroute.app.data.Notation
import com.aireautoroute.app.data.Presence
import com.aireautoroute.app.data.sansAuteurs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le masquage d'un contributeur est un filtre pur sur les contributions : c'est ce qui permet
 * de le vérifier ici, sans appareil ni service.
 */
class AuteursMasquesTest {

    private fun notation(id: String, auteurId: String) = Notation(
        id = id,
        aireId = "a13-vironvay-nord",
        critere = Critere.APPRECIATION_GENERALE,
        trancheAge = null,
        note = 4,
        commentaire = "Correct",
        auteur = "Quelqu'un",
        auteurId = auteurId,
        date = "2026-08-24T10:00:00Z",
    )

    private fun declaration(id: String, auteurId: String) = DeclarationEquipement(
        id = id,
        aireId = "a13-vironvay-nord",
        critere = Critere.TOILETTES,
        presence = Presence.OUI,
        auteur = "Quelqu'un",
        auteurId = auteurId,
        date = "2026-08-24T10:00:00Z",
    )

    private val donnees = DonneesUtilisateur(
        notations = listOf(notation("n1", "gene"), notation("n2", "paisible")),
        declarations = listOf(declaration("d1", "gene"), declaration("d2", "paisible")),
        liensEnseignes = listOf(
            LienAireEnseigne("a13-vironvay-nord", "e1", true, "gene"),
            LienAireEnseigne("a13-vironvay-nord", "e2", true, "paisible"),
        ),
    )

    @Test
    fun `sans masque, le jeu est rendu tel quel`() {
        assertSame(donnees, donnees.sansAuteurs(emptySet()))
    }

    @Test
    fun `le masquage retire les trois natures de contribution`() {
        val filtre = donnees.sansAuteurs(setOf("gene"))

        assertEquals(listOf("n2"), filtre.notations.map { it.id })
        assertEquals(listOf("d2"), filtre.declarations.map { it.id })
        assertEquals(listOf("e2"), filtre.liensEnseignes.map { it.enseigneId })
    }

    /** Masquer sans écarter les notes laisserait la personne peser sur ce qu'on voit. */
    @Test
    fun `les notes du contributeur masque ne comptent plus`() {
        val filtre = donnees.sansAuteurs(setOf("gene"))
        assertTrue(filtre.notations.none { it.auteurId == "gene" })
    }

    @Test
    fun `masquer un inconnu ne retire rien`() {
        val filtre = donnees.sansAuteurs(setOf("personne-de-ce-nom"))
        assertEquals(donnees.notations.size, filtre.notations.size)
        assertEquals(donnees.declarations.size, filtre.declarations.size)
    }

    @Test
    fun `plusieurs masques s'appliquent ensemble`() {
        val filtre = donnees.sansAuteurs(setOf("gene", "paisible"))
        assertTrue(filtre.notations.isEmpty())
        assertTrue(filtre.declarations.isEmpty())
        assertTrue(filtre.liensEnseignes.isEmpty())
    }

    /**
     * Les contributions créées localement n'ont pas encore d'identifiant : le service le pose à
     * la publication. Un masque vide ne doit pas les emporter.
     */
    @Test
    fun `une contribution sans identifiant d'auteur survit a un masque vide`() {
        val locale = DonneesUtilisateur(notations = listOf(notation("local", "")))
        assertEquals(1, locale.sansAuteurs(setOf("")).notations.size)
    }
}
