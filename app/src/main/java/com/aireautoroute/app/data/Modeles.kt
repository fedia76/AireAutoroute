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

    /** Utilisé uniquement pour les aires accessibles depuis les deux chaussées. */
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
)

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
