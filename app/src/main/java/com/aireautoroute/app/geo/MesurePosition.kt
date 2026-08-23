package com.aireautoroute.app.geo

/**
 * Une mesure de position, accompagnée de ce qui permet d'en juger la valeur : son âge, sa
 * précision et le cap.
 *
 * Le type est volontairement dépourvu de toute dépendance à Android : c'est [versMesure] qui
 * traduit un `Location` du système, et les règles d'exploitabilité restent vérifiables en test.
 */
data class MesurePosition(
    val latitude: Double,
    val longitude: Double,
    /** Temps écoulé depuis la mesure, en millisecondes. */
    val ageMs: Long,
    /** Rayon d'incertitude annoncé par le fournisseur, `null` s'il n'en donne pas. */
    val precisionMetres: Float?,
    /** Cap en degrés depuis le nord, `null` si le véhicule est trop lent pour qu'il ait un sens. */
    val capDegres: Float?,
) {
    /**
     * Assez récente pour décrire encore où se trouve le véhicule.
     *
     * C'est le garde-fou principal : à 130 km/h, un point vieux d'une minute est à plus de deux
     * kilomètres, et le dernier point connu du système peut dater d'heures et d'un autre
     * département. C'est bien l'âge, et non la provenance, qui décide : un point relu du cache
     * mais vieux de trois secondes décrit parfaitement la position courante.
     */
    val fraiche: Boolean get() = ageMs <= AGE_FRAIS_MS

    /**
     * Assez précise pour être projetée sur un tracé sans risque de confusion.
     *
     * Une position cellulaire annoncée à plusieurs kilomètres près tomberait à peu près n'importe
     * où, et [LocalisateurPk] l'accrocherait volontiers à la mauvaise autoroute.
     */
    val precise: Boolean get() = precisionMetres == null || precisionMetres <= PRECISION_MAX_METRES

    companion object {
        /** Au-delà, une mesure ne décrit plus la position courante. */
        const val AGE_FRAIS_MS = 10_000L

        /** Un point du cache plus vieux que cela n'a même plus valeur d'aperçu. */
        const val AGE_CACHE_MAX_MS = 120_000L

        /** Incertitude au-delà de laquelle le point ne permet plus de désigner une autoroute. */
        const val PRECISION_MAX_METRES = 200f

        /** Sous ~11 km/h, le cap renvoyé par le GPS n'est que du bruit. */
        const val VITESSE_MIN_CAP_MS = 3f
    }
}
