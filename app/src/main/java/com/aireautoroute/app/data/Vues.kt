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
    /**
     * `true` quand la position vient du dernier point connu du système et non d'une mesure
     * fraîche : elle est affichée en attendant mieux, et signalée comme approximative.
     */
    val provisoire: Boolean = false,
) {
    val libelle: String
        get() = "${autoroute.nom} · PK ${"%.1f".format(pk)} · direction ${autoroute.terminus(sens)}"
}

enum class SourcePosition { MANUELLE, GPS }

/** Ce que l'application peut affirmer de la présence d'un équipement sur une aire. */
enum class StatutEquipement(val libelle: String) {
    /** Assez de contributeurs ont déclaré l'équipement présent. */
    CONFIRME("Confirmé par les visiteurs"),

    /** Des contributeurs l'ont vu, mais pas encore assez pour l'affirmer. */
    A_CONFIRMER("À confirmer"),

    /** Annoncé par l'exploitant dans les données livrées, jamais confirmé sur place. */
    ANNONCE("Annoncé, non vérifié"),

    /** Assez de contributeurs ont déclaré l'équipement absent. */
    ABSENT("Absent"),

    INCONNU("Non renseigné"),
}

/**
 * Nombre de déclarations concordantes nécessaires pour qu'un équipement soit affirmé présent
 * (ou absent). Un seul avis ne suffit jamais.
 */
const val SEUIL_CONFIRMATION = 2

/** Résultat de l'algorithme de consensus pour un équipement d'une aire. */
data class ConsensusEquipement(
    val critere: Critere,
    val statut: StatutEquipement,
    val declarationsOui: Int,
    val declarationsNon: Int,
    /** L'équipement figure dans les données livrées avec l'application. */
    val annonce: Boolean,
) {
    /** Vrai quand l'application peut affirmer que l'équipement existe. */
    val presentAvere: Boolean get() = statut == StatutEquipement.CONFIRME

    /** Vrai quand il vaut la peine de montrer l'équipement dans la vue résumée. */
    val aMontrer: Boolean get() = statut != StatutEquipement.ABSENT && statut != StatutEquipement.INCONNU

    val detailDeclarations: String
        get() = when {
            declarationsOui == 0 && declarationsNon == 0 -> "aucune déclaration"
            declarationsNon == 0 -> "$declarationsOui déclaration(s) « présent »"
            declarationsOui == 0 -> "$declarationsNon déclaration(s) « absent »"
            else -> "$declarationsOui « présent » / $declarationsNon « absent »"
        }
}

/**
 * Décide du statut d'un équipement à partir des déclarations des contributeurs.
 *
 * Les avis « ne sais pas » ne comptent pas. Il faut [SEUIL_CONFIRMATION] déclarations
 * concordantes, et majoritaires, pour trancher dans un sens ou dans l'autre ; en dessous,
 * l'équipement reste « à confirmer », ou simplement « annoncé » s'il vient des données livrées.
 */
fun consensus(
    aire: Aire,
    declarations: List<DeclarationEquipement>,
    critere: Critere,
    seuil: Int = SEUIL_CONFIRMATION,
): ConsensusEquipement {
    val duCritere = declarations.filter { it.aireId == aire.id && it.critere == critere }
    val oui = duCritere.count { it.presence == Presence.OUI }
    val non = duCritere.count { it.presence == Presence.NON }
    val annonce = critere in aire.equipements
    val statut = when {
        oui >= seuil && oui > non -> StatutEquipement.CONFIRME
        non >= seuil && non >= oui -> StatutEquipement.ABSENT
        oui > 0 -> StatutEquipement.A_CONFIRMER
        annonce -> StatutEquipement.ANNONCE
        non > 0 -> StatutEquipement.ABSENT
        else -> StatutEquipement.INCONNU
    }
    return ConsensusEquipement(critere, statut, oui, non, annonce)
}

/** Moyenne d'un critère sur l'ensemble des avis. */
data class NoteAgregee(val moyenne: Double, val nombre: Int)

/** Vue résumée d'une aire dans la liste « prochaines aires ». */
data class AireResume(
    val aire: Aire,
    val distanceKm: Double,
    val noteGenerale: NoteAgregee?,
    val notesJeux: Map<TrancheAge, NoteAgregee>,
    val enseignes: List<Enseigne>,
    /** Équipements dignes d'être affichés, les confirmés d'abord. */
    val equipements: List<ConsensusEquipement>,
    val nombreAvis: Int,
)

/** Détail d'un critère pour une aire. */
data class DetailCritere(
    val critere: Critere,
    val consensus: ConsensusEquipement?,
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
                equipements = Critere.equipements
                    .map { consensus(aire, donnees.declarations, it) }
                    .filter { it.aMontrer }
                    .sortedBy { it.statut.ordinal },
                nombreAvis = notations.size,
            )
        }
        .sortedBy { it.distanceKm }
        .toList()
}

/**
 * Toutes les aires de l'autoroute empruntée, sans condition de distance, pour la vue carte.
 *
 * Le filtre sur le sens n'est pas un raffinement : deux aires qui se font face de part et
 * d'autre de la chaussée portent des noms différents mais partagent les mêmes coordonnées.
 * Les garder toutes les deux ne montrerait pas deux endroits sur la carte, seulement deux
 * pastilles superposées — et un groupe « 2 » qui ne se déplie jamais, si loin qu'on zoome.
 */
fun airesDeLItineraire(catalogue: Catalogue, position: PositionUtilisateur): List<Aire> =
    catalogue.aires
        .filter { it.autorouteId == position.autoroute.id }
        .filter { it.estAccessibleDans(position.sens) }
        .sortedBy { it.pk }

fun detailAire(catalogue: Catalogue, donnees: DonneesUtilisateur, aireId: String): AireDetail? {
    val aire = catalogue.aires.firstOrNull { it.id == aireId } ?: return null
    val autoroute = catalogue.autoroutes.firstOrNull { it.id == aire.autorouteId } ?: return null
    val notations = donnees.notations.filter { it.aireId == aireId }
    val criteres = Critere.ordreAffichage.map { critere ->
        val duCritere = notations.filter { it.critere == critere }
        DetailCritere(
            critere = critere,
            consensus = if (critere.estEquipement) {
                consensus(aire, donnees.declarations, critere)
            } else {
                null
            },
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
