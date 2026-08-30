package com.aireautoroute.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.util.UUID

/** Jeu de données livré avec l'application (fichiers JSON des assets). */
data class Catalogue(
    val autoroutes: List<Autoroute>,
    val aires: List<Aire>,
    val enseignes: List<Enseigne>,
    val liensEnseignes: List<LienAireEnseigne>,
)

/** Où en est l'application vis-à-vis du service de contributions. */
enum class EtatSynchro {
    EN_COURS,
    A_JOUR,

    /** Le service est injoignable : on affiche ce que le dernier passage avait rapporté. */
    HORS_LIGNE,
}

/**
 * Accès aux données de l'application.
 *
 * Deux sources de nature différente :
 *  - le **catalogue** — autoroutes, tracés, aires — est lu dans les fichiers JSON de
 *    `assets/seed/`. Il change une fois par an et doit s'afficher sans réseau.
 *  - les **contributions** — notes, déclarations, enseignes — viennent du service partagé.
 *    Le fichier `filesDir/donnees_utilisateur.json` n'en garde qu'une copie, pour avoir
 *    quelque chose à montrer quand le réseau manque.
 */
class DepotDonnees(
    private val context: Context,
    private val service: ServiceContributions = ServiceContributions(context),
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val fichierCache: File
        get() = File(context.filesDir, FICHIER_CACHE)

    private val mutexEcriture = Mutex()

    private val _catalogue = MutableStateFlow<Catalogue?>(null)
    val catalogue: StateFlow<Catalogue?> = _catalogue.asStateFlow()

    /**
     * Le jeu complet, tel que le service l'a rendu. Il n'est jamais exposé directement : ce qui
     * sort d'ici est filtré des contributeurs masqués.
     *
     * Le garder entier est délibéré. Démasquer quelqu'un réaffiche alors ses avis
     * immédiatement, sans attendre un aller-retour réseau qui, hors couverture, n'arriverait
     * jamais.
     */
    private var brutes = DonneesUtilisateur()
    private var auteursMasques: Set<String> = emptySet()

    private val _contributions = MutableStateFlow(DonneesUtilisateur())
    val contributions: StateFlow<DonneesUtilisateur> = _contributions.asStateFlow()

    /**
     * Point de filtrage unique.
     *
     * `prochainesAires` et `detailAire` lisent tous deux ce seul flux : filtrer ici suffit à
     * écarter les contributeurs masqués des listes, des détails, des moyennes et des consensus,
     * sans toucher au calcul lui-même.
     */
    private fun publier() {
        _contributions.value = brutes.sansAuteurs(auteursMasques)
    }

    fun definirAuteursMasques(auteurs: Set<String>) {
        if (auteurs == auteursMasques) return
        auteursMasques = auteurs
        publier()
    }

    private val _synchro = MutableStateFlow(EtatSynchro.EN_COURS)
    val synchro: StateFlow<EtatSynchro> = _synchro.asStateFlow()

    suspend fun charger() {
        withContext(Dispatchers.IO) {
            _catalogue.value = Catalogue(
                autoroutes = lireAsset("seed/autoroutes.json"),
                aires = lireAsset("seed/aires.json"),
                enseignes = lireAsset("seed/enseignes.json"),
                liensEnseignes = lireAsset("seed/aire_enseignes.json"),
            )
            brutes = lireCache()
            publier()
        }
        rafraichir()
    }

    /** Rapatrie les contributions publiées. Sans réseau, on garde la copie locale. */
    suspend fun rafraichir() {
        _synchro.value = EtatSynchro.EN_COURS
        val resultat = runCatching { service.lireContributions() }
        resultat
            .onSuccess { distantes ->
                brutes = distantes
                publier()
                ecrireCache(distantes)
                _synchro.value = EtatSynchro.A_JOUR
            }
            .onFailure { _synchro.value = EtatSynchro.HORS_LIGNE }
    }

    private inline fun <reified T> lireAsset(chemin: String): List<T> =
        context.assets.open(chemin).bufferedReader().use { json.decodeFromString(it.readText()) }

    private fun lireCache(): DonneesUtilisateur {
        val fichier = fichierCache
        if (!fichier.exists()) return DonneesUtilisateur()
        return runCatching { json.decodeFromString<DonneesUtilisateur>(fichier.readText()) }
            .getOrElse { DonneesUtilisateur() }
    }

    private suspend fun ecrireCache(donnees: DonneesUtilisateur) {
        mutexEcriture.withLock {
            withContext(Dispatchers.IO) {
                val temporaire = File(context.filesDir, "$FICHIER_CACHE.tmp")
                temporaire.writeText(json.encodeToString(donnees))
                temporaire.renameTo(fichierCache)
            }
        }
    }

    /**
     * Publie la contribution d'un utilisateur, puis relit ce que le service en a fait.
     *
     * Sans réseau, l'envoi échoue franchement : mieux vaut le dire que laisser croire que
     * l'avis est parti.
     */
    suspend fun enregistrerContribution(
        declarations: List<DeclarationEquipement>,
        notations: List<Notation>,
    ): Result<Unit> {
        if (declarations.isEmpty() && notations.isEmpty()) return Result.success(Unit)
        return runCatching {
            service.publierContribution(notations, declarations)
            rafraichir()
        }
    }

    suspend fun ajouterEnseigneAAire(
        aireId: String,
        nomEnseigne: String,
        icone: IconeEnseigne?,
    ): Result<Unit> =
        runCatching {
            service.rattacherEnseigne(aireId, nomEnseigne, icone)
            rafraichir()
        }

    suspend fun retirerEnseigneDAire(aireId: String, enseigneId: String): Result<Unit> =
        runCatching {
            service.detacherEnseigne(aireId, enseigneId)
            rafraichir()
        }

    suspend fun signalerContributeur(auteurId: String, motif: MotifSignalement): Result<Unit> =
        runCatching { service.signalerContributeur(auteurId, motif) }

    suspend fun signaler(notationId: String, motif: MotifSignalement): Result<Unit> =
        runCatching {
            service.signalerNotation(notationId, motif)
            // Relire aussitôt : au-delà du seuil, le service a masqué l'avis et il doit
            // disparaître de l'écran sans attendre le prochain rafraîchissement.
            rafraichir()
        }

    suspend fun supprimerMesContributions(): Result<Unit> =
        runCatching {
            service.supprimerMesContributions()
            rafraichir()
        }

    companion object {
        private const val FICHIER_CACHE = "donnees_utilisateur.json"
        private const val AUTEUR_PAR_DEFAUT = "Moi"

        fun nouvelleDeclaration(
            aireId: String,
            critere: Critere,
            presence: Presence,
            auteur: String,
        ) = DeclarationEquipement(
            id = UUID.randomUUID().toString(),
            aireId = aireId,
            critere = critere,
            presence = presence,
            auteur = auteur.trim().takeIf { it.isNotEmpty() } ?: AUTEUR_PAR_DEFAUT,
            date = Instant.now().toString(),
        )

        fun nouvelleNotation(
            aireId: String,
            critere: Critere,
            trancheAge: TrancheAge?,
            note: Int,
            commentaire: String?,
            auteur: String,
        ) = Notation(
            id = UUID.randomUUID().toString(),
            aireId = aireId,
            critere = critere,
            trancheAge = trancheAge,
            note = note,
            commentaire = commentaire?.trim()?.takeIf { it.isNotEmpty() },
            auteur = auteur.trim().takeIf { it.isNotEmpty() } ?: AUTEUR_PAR_DEFAUT,
            date = Instant.now().toString(),
        )
    }
}
