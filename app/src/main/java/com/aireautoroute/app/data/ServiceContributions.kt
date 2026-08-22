package com.aireautoroute.app.data

import android.content.Context
import com.aireautoroute.app.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Accès au service de contributions (Supabase).
 *
 * Le référentiel — autoroutes, tracés, aires — reste embarqué dans l'application ; seules les
 * contributions transitent par le réseau. L'utilisateur ne crée jamais de compte : une session
 * anonyme est ouverte silencieusement au premier lancement, et c'est elle qui garantit que
 * personne ne modifie les avis d'un autre.
 */
class ServiceContributions(@Suppress("unused") private val contexte: Context) {

    private val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_CLE_PUBLIQUE,
    ) {
        install(Auth)
        install(Postgrest)
    }

    /** Identifiant de la session anonyme, posé par le serveur. */
    private suspend fun identifiantContributeur(): String {
        client.auth.awaitInitialization()
        if (client.auth.currentSessionOrNull() == null) {
            client.auth.signInAnonymously()
        }
        return client.auth.currentUserOrNull()?.id
            ?: error("Session anonyme indisponible")
    }

    suspend fun ouvrirSession() {
        identifiantContributeur()
    }

    /** Rapatrie l'ensemble des contributions publiées. */
    suspend fun lireContributions(): DonneesUtilisateur {
        client.auth.awaitInitialization()

        val notations = client.from("notation")
            .select { limit(LIMITE_LIGNES) }
            .decodeList<NotationLue>()
        val declarations = client.from("declaration_equipement")
            .select { limit(LIMITE_LIGNES) }
            .decodeList<DeclarationLue>()
        val enseignes = client.from("enseigne")
            .select { limit(LIMITE_LIGNES) }
            .decodeList<EnseigneLue>()
        val liens = client.from("aire_enseigne")
            .select { limit(LIMITE_LIGNES) }
            .decodeList<LienLu>()

        return DonneesUtilisateur(
            notations = notations.map { it.versModele() },
            declarations = declarations.map { it.versModele() },
            enseignes = enseignes.map { it.versModele() },
            liensEnseignes = liens.map { LienAireEnseigne(it.aireId, it.enseigneId, true) },
        )
    }

    /**
     * Publie une contribution. Repasser sur un critère déjà noté remplace l'avis précédent
     * plutôt que de s'y ajouter : c'est la contrainte d'unicité du schéma qui l'impose.
     */
    suspend fun publierContribution(
        notations: List<Notation>,
        declarations: List<DeclarationEquipement>,
    ) {
        if (notations.isEmpty() && declarations.isEmpty()) return
        val auteurId = identifiantContributeur()

        if (notations.isNotEmpty()) {
            client.from("notation").upsert(
                notations.map { NotationAEnvoyer.depuis(it, auteurId) },
            ) {
                onConflict = "auteur_id,aire_id,critere,tranche_age"
            }
        }
        if (declarations.isNotEmpty()) {
            client.from("declaration_equipement").upsert(
                declarations.map { DeclarationAEnvoyer.depuis(it, auteurId) },
            ) {
                onConflict = "auteur_id,aire_id,critere"
            }
        }
    }

    /** Rattache une enseigne à une aire, en la créant au catalogue partagé si elle est nouvelle. */
    suspend fun rattacherEnseigne(aireId: String, nomEnseigne: String) {
        val nom = nomEnseigne.trim()
        if (nom.isEmpty()) return
        val auteurId = identifiantContributeur()

        val existantes = client.from("enseigne")
            .select { filter { ilike("nom", nom) } }
            .decodeList<EnseigneLue>()

        val enseigneId = existantes.firstOrNull()?.id
            ?: client.from("enseigne")
                .insert(EnseigneAEnvoyer(nom = nom)) { select() }
                .decodeSingle<EnseigneLue>()
                .id

        client.from("aire_enseigne").upsert(
            LienAEnvoyer(aireId = aireId, enseigneId = enseigneId, auteurId = auteurId),
        ) {
            onConflict = "aire_id,enseigne_id,auteur_id"
        }
    }

    suspend fun detacherEnseigne(aireId: String, enseigneId: String) {
        val auteurId = identifiantContributeur()
        client.from("aire_enseigne").delete {
            filter {
                eq("aire_id", aireId)
                eq("enseigne_id", enseigneId)
                eq("auteur_id", auteurId)
            }
        }
    }

    private companion object {
        const val LIMITE_LIGNES = 5_000L
    }
}

// --- Représentations transportées ---------------------------------------------------------
// Deux familles distinctes : ce qu'on lit porte les colonnes posées par le serveur (identifiant,
// date), ce qu'on écrit ne les mentionne pas, pour laisser jouer les valeurs par défaut.

@Serializable
private data class NotationLue(
    val id: String,
    @SerialName("aire_id") val aireId: String,
    val critere: Critere,
    @SerialName("tranche_age") val trancheAge: TrancheAge? = null,
    val note: Int,
    val commentaire: String? = null,
    val auteur: String? = null,
    @SerialName("creee_le") val creeeLe: String,
) {
    fun versModele() = Notation(
        id = id,
        aireId = aireId,
        critere = critere,
        trancheAge = trancheAge,
        note = note,
        commentaire = commentaire,
        auteur = auteur ?: "Anonyme",
        date = creeeLe,
    )
}

@Serializable
private data class NotationAEnvoyer(
    @SerialName("auteur_id") val auteurId: String,
    @SerialName("aire_id") val aireId: String,
    val critere: Critere,
    @SerialName("tranche_age") val trancheAge: TrancheAge?,
    val note: Int,
    val commentaire: String?,
    val auteur: String?,
) {
    companion object {
        fun depuis(notation: Notation, auteurId: String) = NotationAEnvoyer(
            auteurId = auteurId,
            aireId = notation.aireId,
            critere = notation.critere,
            trancheAge = notation.trancheAge,
            note = notation.note,
            commentaire = notation.commentaire,
            auteur = notation.auteur.takeIf { it.isNotBlank() && it != "Moi" },
        )
    }
}

@Serializable
private data class DeclarationLue(
    val id: String,
    @SerialName("aire_id") val aireId: String,
    val critere: Critere,
    val presence: Presence,
    val auteur: String? = null,
    @SerialName("creee_le") val creeeLe: String,
) {
    fun versModele() = DeclarationEquipement(
        id = id,
        aireId = aireId,
        critere = critere,
        presence = presence,
        auteur = auteur ?: "Anonyme",
        date = creeeLe,
    )
}

@Serializable
private data class DeclarationAEnvoyer(
    @SerialName("auteur_id") val auteurId: String,
    @SerialName("aire_id") val aireId: String,
    val critere: Critere,
    val presence: Presence,
    val auteur: String?,
) {
    companion object {
        fun depuis(declaration: DeclarationEquipement, auteurId: String) = DeclarationAEnvoyer(
            auteurId = auteurId,
            aireId = declaration.aireId,
            critere = declaration.critere,
            presence = declaration.presence,
            auteur = declaration.auteur.takeIf { it.isNotBlank() && it != "Moi" },
        )
    }
}

@Serializable
private data class EnseigneLue(
    val id: String,
    val nom: String,
    val categorie: String = "AUTRE",
) {
    fun versModele() = Enseigne(
        id = id,
        nom = nom,
        categorie = runCatching { CategorieEnseigne.valueOf(categorie) }
            .getOrDefault(CategorieEnseigne.AUTRE),
    )
}

@Serializable
private data class EnseigneAEnvoyer(val nom: String, val categorie: String = "AUTRE")

@Serializable
private data class LienLu(
    @SerialName("aire_id") val aireId: String,
    @SerialName("enseigne_id") val enseigneId: String,
)

@Serializable
private data class LienAEnvoyer(
    @SerialName("aire_id") val aireId: String,
    @SerialName("enseigne_id") val enseigneId: String,
    @SerialName("auteur_id") val auteurId: String,
)
