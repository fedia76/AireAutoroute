package com.aireautoroute.app.data

import kotlin.math.abs

/** Position de l'utilisateur sur le réseau, saisie à la main ou déduite du GPS. */
data class PositionUtilisateur(
    val autoroute: Autoroute,
    val pk: Double,
    val sens: Sens,
    val source: SourcePosition,
    /** Distance entre le point GPS et le tracé de l'autoroute (mode automatique). */
    val ecartMetres: Double? = null,
    val sensFiable: Boolean = true,
) {
    val libelle: String
        get() = "${autoroute.nom} · PK ${"%.1f".format(pk)} · direction ${autoroute.terminus(sens)}"
}

enum class SourcePosition { MANUELLE, GPS }

/** Moyenne d'un critère sur l'ensemble des avis. */
data class NoteAgregee(val moyenne: Double, val nombre: Int)

/** Vue résumée d'une aire dans la liste « prochaines aires ». */
data class AireResume(
    val aire: Aire,
    val distanceKm: Double,
    val noteGenerale: NoteAgregee?,
    val notesJeux: Map<TrancheAge, NoteAgregee>,
    val enseignes: List<Enseigne>,
    val nombreAvis: Int,
)

/** Détail d'un critère pour une aire. */
data class DetailCritere(
    val critere: Critere,
    val disponible: Boolean,
    val note: NoteAgregee?,
    val parTrancheAge: Map<TrancheAge, NoteAgregee>,
    val commentaires: List<Notation>,
)

data class AireDetail(
    val aire: Aire,
    val autoroute: Autoroute,
    val enseignes: List<Enseigne>,
    val enseignesAjoutees: Set<String>,
    val criteres: List<DetailCritere>,
    val noteGenerale: NoteAgregee?,
    val nombreAvis: Int,
)

/** Portée par défaut de la recherche des prochaines aires. */
const val PORTEE_RECHERCHE_KM = 200.0

private fun List<Notation>.agreger(): NoteAgregee? =
    if (isEmpty()) null else NoteAgregee(sumOf { it.note }.toDouble() / size, size)

/** Toutes les enseignes rattachées à une aire, catalogue livré + ajouts de l'utilisateur. */
fun enseignesDeLAire(
    catalogue: Catalogue,
    donnees: DonneesUtilisateur,
    aireId: String,
): List<Enseigne> {
    val parId = (catalogue.enseignes + donnees.enseignes).associateBy { it.id }
    return (catalogue.liensEnseignes + donnees.liensEnseignes)
        .filter { it.aireId == aireId }
        .mapNotNull { parId[it.enseigneId] }
        .distinctBy { it.id }
        .sortedWith(compareBy({ it.categorie.ordinal }, { it.nom }))
}

/**
 * Aires situées devant l'utilisateur, dans son sens de circulation, triées par distance.
 *
 * Une aire n'est retenue que si elle est accessible depuis la chaussée empruntée : les aires
 * `LES_DEUX` le sont toujours, les autres seulement dans leur sens.
 */
fun prochainesAires(
    catalogue: Catalogue,
    donnees: DonneesUtilisateur,
    position: PositionUtilisateur,
    porteeKm: Double = PORTEE_RECHERCHE_KM,
): List<AireResume> {
    val notationsParAire = donnees.notations.groupBy { it.aireId }
    return catalogue.aires
        .asSequence()
        .filter { it.autorouteId == position.autoroute.id }
        .filter { it.estAccessibleDans(position.sens) }
        .mapNotNull { aire ->
            val distance = when (position.sens) {
                Sens.DECROISSANT -> position.pk - aire.pk
                else -> aire.pk - position.pk
            }
            if (distance < -1.0 || distance > porteeKm) return@mapNotNull null
            val notations = notationsParAire[aire.id].orEmpty()
            AireResume(
                aire = aire,
                distanceKm = abs(distance),
                noteGenerale = notations
                    .filter { it.critere == Critere.APPRECIATION_GENERALE }
                    .agreger(),
                notesJeux = TrancheAge.entries.mapNotNull { tranche ->
                    notations
                        .filter { it.critere.parTrancheAge && it.trancheAge == tranche }
                        .agreger()
                        ?.let { tranche to it }
                }.toMap(),
                enseignes = enseignesDeLAire(catalogue, donnees, aire.id),
                nombreAvis = notations.size,
            )
        }
        .sortedBy { it.distanceKm }
        .toList()
}

fun detailAire(catalogue: Catalogue, donnees: DonneesUtilisateur, aireId: String): AireDetail? {
    val aire = catalogue.aires.firstOrNull { it.id == aireId } ?: return null
    val autoroute = catalogue.autoroutes.firstOrNull { it.id == aire.autorouteId } ?: return null
    val notations = donnees.notations.filter { it.aireId == aireId }
    val criteres = Critere.ordreAffichage.map { critere ->
        val duCritere = notations.filter { it.critere == critere }
        DetailCritere(
            critere = critere,
            disponible = critere == Critere.APPRECIATION_GENERALE || critere in aire.equipements,
            note = duCritere.agreger(),
            parTrancheAge = if (!critere.parTrancheAge) {
                emptyMap()
            } else {
                TrancheAge.entries.mapNotNull { tranche ->
                    duCritere.filter { it.trancheAge == tranche }.agreger()?.let { tranche to it }
                }.toMap()
            },
            commentaires = duCritere
                .filter { !it.commentaire.isNullOrBlank() }
                .sortedByDescending { it.date },
        )
    }
    return AireDetail(
        aire = aire,
        autoroute = autoroute,
        enseignes = enseignesDeLAire(catalogue, donnees, aireId),
        enseignesAjoutees = donnees.liensEnseignes
            .filter { it.aireId == aireId }
            .map { it.enseigneId }
            .toSet(),
        criteres = criteres,
        noteGenerale = notations.filter { it.critere == Critere.APPRECIATION_GENERALE }.agreger(),
        nombreAvis = notations.size,
    )
}
