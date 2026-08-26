package com.aireautoroute.app

import com.aireautoroute.app.data.Aire
import com.aireautoroute.app.data.Autoroute
import com.aireautoroute.app.data.Catalogue
import com.aireautoroute.app.data.PositionUtilisateur
import com.aireautoroute.app.data.Sens
import com.aireautoroute.app.data.SourcePosition
import com.aireautoroute.app.data.airesDeLItineraire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La carte montre l'autoroute entière, mais pas les deux chaussées : les aires qui se font
 * face partagent leurs coordonnées, et les garder toutes les deux ne produirait que des
 * pastilles superposées.
 */
class AiresItineraireTest {

    private val a11 = Autoroute(
        id = "A11",
        nom = "A11",
        terminusDebut = "Paris",
        terminusFin = "Nantes",
        longueurKm = 340.0,
    )

    private val autre = Autoroute(
        id = "A10",
        nom = "A10",
        terminusDebut = "Paris",
        terminusFin = "Bordeaux",
        longueurKm = 550.0,
    )

    private val catalogue = Catalogue(
        autoroutes = listOf(a11, autre),
        aires = listOf(
            aire("le-cellier", pk = 331.0, sens = Sens.CROISSANT),
            // Même sortie, même point sur la carte, autre chaussée.
            aire("le-launay", pk = 331.0, sens = Sens.DECROISSANT),
            aire("ancenis-croissant", pk = 315.0, sens = Sens.CROISSANT),
            aire("des-deux-cotes", pk = 200.0, sens = Sens.LES_DEUX),
            // Déjà dépassée pour qui roule en sens décroissant depuis le PK 348.
            aire("deja-passee", pk = 350.0, sens = Sens.DECROISSANT),
            aire("hors-autoroute", pk = 50.0, sens = Sens.CROISSANT, autorouteId = "A10"),
        ),
        enseignes = emptyList(),
        liensEnseignes = emptyList(),
    )

    private fun aire(id: String, pk: Double, sens: Sens, autorouteId: String = "A11") = Aire(
        id = id,
        autorouteId = autorouteId,
        nom = id,
        pk = pk,
        sens = sens,
        lat = 47.0,
        lon = -1.0,
    )

    private fun position(sens: Sens) =
        PositionUtilisateur(a11, pk = 348.0, sens = sens, source = SourcePosition.MANUELLE)

    @Test
    fun `ne garde que les aires de l'autoroute accessibles dans le sens parcouru`() {
        val resultat = airesDeLItineraire(catalogue, position(Sens.DECROISSANT))
        assertEquals(listOf("des-deux-cotes", "le-launay", "deja-passee"), resultat.map { it.id })
    }

    @Test
    fun `l'autre sens montre l'autre chaussee, jamais les deux ensemble`() {
        val resultat = airesDeLItineraire(catalogue, position(Sens.CROISSANT))
        assertEquals(
            listOf("des-deux-cotes", "ancenis-croissant", "le-cellier"),
            resultat.map { it.id },
        )
    }

    @Test
    fun `garde les aires deja depassees, la carte montrant tout l'itineraire`() {
        // Contrairement à « prochainesAires », la carte ne coupe pas derrière l'utilisateur :
        // elle les dessine simplement dans la couleur des aires passées.
        val resultat = airesDeLItineraire(catalogue, position(Sens.DECROISSANT))
        assertTrue("deja-passee" in resultat.map { it.id })
    }
}
