package com.aireautoroute.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sens de circulation sur une autoroute.
 *
 * Les points kilométriques (PK) sont bornés depuis le terminus de départ : rouler dans le sens
 * [CROISSANT] revient donc à voir son PK augmenter, et à se diriger vers le terminus d'arrivée.
 */
@Serializable
enum class Sens {
    CROISSANT,
    DECROISSANT,

    /**
     * Aire accessible depuis les deux chaussées — une aire enjambant l'autoroute, par exemple.
     * Le catalogue actuel n'en produit pas : WikiSara décrit une aire par sens de circulation.
     */
    @SerialName("LES_DEUX")
    LES_DEUX;

    fun oppose(): Sens = when (this) {
        CROISSANT -> DECROISSANT
        DECROISSANT -> CROISSANT
        LES_DEUX -> LES_DEUX
    }
}

/** Un point de référence de la géométrie d'une autoroute : coordonnées GPS + PK associé. */
@Serializable
data class PointReference(
    val pk: Double,
    val lat: Double,
    val lon: Double,
)

/** Table `autoroute`. */
@Serializable
data class Autoroute(
    val id: String,
    val nom: String,
    val libelle: String = "",
    val terminusDebut: String,
    val terminusFin: String,
    val longueurKm: Double,
    val geometrie: List<PointReference> = emptyList(),
) {
    /** Premier point kilométrique couvert par le tracé. */
    val pkDebut: Double get() = geometrie.firstOrNull()?.pk ?: 0.0

    /** Dernier point kilométrique couvert par le tracé. */
    val pkFin: Double get() = geometrie.lastOrNull()?.pk ?: longueurKm

    /** Terminus vers lequel on roule dans le sens donné. */
    fun terminus(sens: Sens): String = when (sens) {
        Sens.CROISSANT -> terminusFin
        Sens.DECROISSANT -> terminusDebut
        Sens.LES_DEUX -> "$terminusDebut / $terminusFin"
    }
}

/** Type d'aire au sens des exploitants autoroutiers. */
@Serializable
enum class TypeAire(val libelle: String) {
    SERVICE("Aire de service"),
    REPOS("Aire de repos"),
}

/** Table `aire`. */
@Serializable
data class Aire(
    val id: String,
    val autorouteId: String,
    val nom: String,
    val pk: Double,
    val sens: Sens,
    val type: TypeAire = TypeAire.SERVICE,
    val lat: Double? = null,
    val lon: Double? = null,
    /** Équipements annoncés par l'exploitant, utilisés pour la vue résumée. */
    val equipements: List<Critere> = emptyList(),
) {
    fun estAccessibleDans(sensDeMarche: Sens): Boolean =
        sens == Sens.LES_DEUX || sens == sensDeMarche
}

/** Table `enseigne`. */
@Serializable
data class Enseigne(
    val id: String,
    val nom: String,
    val categorie: CategorieEnseigne = CategorieEnseigne.AUTRE,
    /**
     * Pictogramme de l'enseigne, `null` quand personne ne l'a renseigné : l'enseigne s'affiche
     * alors sous son seul nom, ce qui reste préférable à une image approximative.
     */
    val icone: IconeEnseigne? = null,
)

/**
 * Jeu — fixe — des pictogrammes d'enseigne.
 *
 * Il dit la **nature** du commerce, jamais la marque : aucun logo n'y figure, et deux
 * contributeurs qui décrivent le même McDonald's doivent poser la même image. Le laisser ouvert
 * reviendrait à demander à chacun d'inventer sa signalétique, et à n'en obtenir aucune.
 *
 * Ce qu'un passager cherche à un kilomètre de la sortie tient en une poignée de catégories :
 * de quoi faire le plein, de quoi manger, de quoi acheter, de quoi dormir. La liste s'arrête là.
 */
@Serializable
enum class IconeEnseigne(val libelle: String) {
    CARBURANT("Station-service"),
    RECHARGE_ELECTRIQUE("Recharge électrique"),
    RESTAURATION_RAPIDE("Restauration rapide"),
    RESTAURANT("Restaurant"),
    BOULANGERIE("Boulangerie"),
    CAFE("Café"),
    PIZZERIA("Pizzeria"),
    SUPERETTE("Supérette"),
    BOUTIQUE("Boutique"),
    HOTEL("Hôtel");

    /**
     * Catégorie que l'icône implique.
     *
     * Le pictogramme est plus fin que la catégorie — il distingue la boulangerie du restaurant,
     * là où le catalogue ne connaît que « restauration ». Le contributeur ne renseigne donc que
     * l'icône, et la catégorie s'en déduit : deux questions pour un seul renseignement feraient
     * surtout abandonner la saisie.
     */
    val categorie: CategorieEnseigne
        get() = when (this) {
            CARBURANT, RECHARGE_ELECTRIQUE -> CategorieEnseigne.CARBURANT
            RESTAURATION_RAPIDE, RESTAURANT, BOULANGERIE, CAFE, PIZZERIA ->
                CategorieEnseigne.RESTAURATION
            SUPERETTE, BOUTIQUE -> CategorieEnseigne.BOUTIQUE
            HOTEL -> CategorieEnseigne.HOTEL
        }
}

@Serializable
enum class CategorieEnseigne(val libelle: String) {
    CARBURANT("Carburant"),
    RESTAURATION("Restauration"),
    BOUTIQUE("Boutique"),
    HOTEL("Hôtel"),
    AUTRE("Autre"),
}

/** Table de liaison `aire_enseigne` (relation many-to-many). */
@Serializable
data class LienAireEnseigne(
    val aireId: String,
    val enseigneId: String,
    /** Session ayant publié le lien. Vide pour les liens du jeu de données livré. */
    val auteurId: String = "",
    /** `false` pour les liens issus du jeu de données livré, `true` pour ceux ajoutés par l'utilisateur. */
    val ajoutParUtilisateur: Boolean = false,
)

/** Critères notés (l'enseigne, elle, est listée et non notée). */
@Serializable
enum class Critere(
    val libelle: String,
    val parTrancheAge: Boolean,
    /** `false` pour l'appréciation générale, qui n'est pas un équipement à déclarer. */
    val estEquipement: Boolean = true,
) {
    AIRE_JEUX_INTERIEURE("Aire de jeux intérieure", true),
    AIRE_JEUX_EXTERIEURE("Aire de jeux extérieure", true),
    TOILETTES("Toilettes", false),
    STATION_SERVICE("Station-service", false),
    TABLE_A_LANGER("Table à langer", false),
    APPRECIATION_GENERALE("Appréciation générale", false, estEquipement = false);

    companion object {
        /** Ordre d'affichage dans le détail et le formulaire de notation. */
        val ordreAffichage: List<Critere> = listOf(
            APPRECIATION_GENERALE,
            AIRE_JEUX_INTERIEURE,
            AIRE_JEUX_EXTERIEURE,
            TOILETTES,
            TABLE_A_LANGER,
            STATION_SERVICE,
        )

        /** Les critères correspondant à un équipement, dont la présence se déclare. */
        val equipements: List<Critere> = ordreAffichage.filter { it.estEquipement }
    }
}

@Serializable
enum class TrancheAge(val libelle: String) {
    ANS_0_3("0-3 ans"),
    ANS_3_6("3-6 ans"),
    ANS_6_12("6-12 ans"),
}

/** Réponse d'un contributeur à la question « cet équipement existe-t-il sur l'aire ? ». */
@Serializable
enum class Presence(val libelle: String) {
    OUI("Oui"),
    NON("Non"),
    NE_SAIS_PAS("Ne sais pas"),
}

/**
 * Table `declaration_equipement`.
 *
 * Une ligne par contributeur et par équipement. C'est le cumul de ces déclarations qui décide si
 * l'application affirme la présence d'un équipement (voir `consensus` dans Vues.kt) : une aire
 * n'est jamais déclarée équipée sur la foi d'un seul avis.
 */
@Serializable
data class DeclarationEquipement(
    val id: String,
    val aireId: String,
    val critere: Critere,
    val presence: Presence,
    val auteur: String = "Moi",
    /**
     * Session ayant publié la contribution. Vide tant que la contribution n'est pas partie :
     * l'identifiant est posé par le service, pas par l'application.
     */
    val auteurId: String = "",
    /** Date ISO-8601 (UTC). */
    val date: String,
)

/** Table `notation`. Une ligne par critère (et par tranche d'âge pour les aires de jeux). */
@Serializable
data class Notation(
    val id: String,
    val aireId: String,
    val critere: Critere,
    val trancheAge: TrancheAge? = null,
    /** Note de 1 à 5 étoiles. */
    val note: Int,
    val commentaire: String? = null,
    val auteur: String = "Moi",
    /**
     * Session ayant publié la contribution. Vide tant que la contribution n'est pas partie :
     * l'identifiant est posé par le service, pas par l'application.
     */
    val auteurId: String = "",
    /** Date ISO-8601 (UTC). */
    val date: String,
)

/** Contenu du fichier de données utilisateur (remplacera une base de données plus tard). */
@Serializable
data class DonneesUtilisateur(
    val version: Int = 1,
    val notations: List<Notation> = emptyList(),
    val declarations: List<DeclarationEquipement> = emptyList(),
    val liensEnseignes: List<LienAireEnseigne> = emptyList(),
    /** Enseignes créées par l'utilisateur quand elle n'existait pas dans le catalogue livré. */
    val enseignes: List<Enseigne> = emptyList(),
)

/**
 * Motif d'un signalement.
 *
 * La liste reste courte : un choix long décourage le signalement, et le tri fin se fait de
 * toute façon à la lecture du commentaire.
 */
@Serializable
enum class MotifSignalement(val libelle: String) {
    INJURIEUX("Propos injurieux ou haineux"),
    PERSONNEL("Contient des informations personnelles"),
    INEXACT("Information fausse"),
    HORS_SUJET("Hors sujet ou publicité"),
    AUTRE("Autre"),
}

/**
 * Écarte tout ce qu'ont publié les sessions masquées.
 *
 * Le filtrage porte sur l'ensemble des contributions, pas sur les seuls commentaires : masquer
 * quelqu'un en laissant ses notes peser sur les moyennes et ses déclarations sur les consensus
 * serait cosmétique. Deux lecteurs peuvent donc voir des moyennes différentes — c'est le prix
 * d'un masquage qui fait ce qu'il annonce.
 *
 * Fonction pure : c'est ce qui la rend vérifiable sans appareil ni réseau.
 */
fun DonneesUtilisateur.sansAuteurs(masques: Set<String>): DonneesUtilisateur {
    // Un identifiant vide désigne une contribution créée localement et pas encore publiée :
    // le service ne lui a pas encore attribué de session. Ni elle ni un masque vide ne doivent
    // entrer dans la comparaison, sous peine d'effacer ce que l'utilisateur vient d'écrire.
    val effectifs = masques.filterTo(HashSet()) { it.isNotBlank() }
    if (effectifs.isEmpty()) return this

    fun masque(auteurId: String) = auteurId.isNotBlank() && auteurId in effectifs

    return copy(
        notations = notations.filterNot { masque(it.auteurId) },
        declarations = declarations.filterNot { masque(it.auteurId) },
        liensEnseignes = liensEnseignes.filterNot { masque(it.auteurId) },
    )
}
