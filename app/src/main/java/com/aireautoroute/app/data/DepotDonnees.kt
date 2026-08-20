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

/**
 * Accès aux données de l'application.
 *
 * Tant qu'il n'y a pas de base de données :
 *  - le catalogue (autoroutes, aires, enseignes, liaisons) est lu dans `assets/seed/*.json` ;
 *  - les contributions de l'utilisateur (notations, enseignes ajoutées) sont écrites dans
 *    `filesDir/donnees_utilisateur.json`.
 *
 * Les deux couches gardent la forme des tables décrites dans le README, pour que le passage à
 * une base Room ne change que cette classe.
 */
class DepotDonnees(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val fichierUtilisateur: File
        get() = File(context.filesDir, FICHIER_UTILISATEUR)

    private val mutexEcriture = Mutex()

    private val _catalogue = MutableStateFlow<Catalogue?>(null)
    val catalogue: StateFlow<Catalogue?> = _catalogue.asStateFlow()

    private val _donneesUtilisateur = MutableStateFlow(DonneesUtilisateur())
    val donneesUtilisateur: StateFlow<DonneesUtilisateur> = _donneesUtilisateur.asStateFlow()

    suspend fun charger() = withContext(Dispatchers.IO) {
        _catalogue.value = Catalogue(
            autoroutes = lireAsset("seed/autoroutes.json"),
            aires = lireAsset("seed/aires.json"),
            enseignes = lireAsset("seed/enseignes.json"),
            liensEnseignes = lireAsset("seed/aire_enseignes.json"),
        )
        _donneesUtilisateur.value = lireDonneesUtilisateur()
    }

    private inline fun <reified T> lireAsset(chemin: String): List<T> =
        context.assets.open(chemin).bufferedReader().use { json.decodeFromString(it.readText()) }

    private fun lireDonneesUtilisateur(): DonneesUtilisateur {
        val fichier = fichierUtilisateur
        if (!fichier.exists()) return DonneesUtilisateur()
        return runCatching { json.decodeFromString<DonneesUtilisateur>(fichier.readText()) }
            .getOrElse { DonneesUtilisateur() }
    }

    private suspend fun modifier(transformation: (DonneesUtilisateur) -> DonneesUtilisateur) {
        mutexEcriture.withLock {
            val nouvelles = transformation(_donneesUtilisateur.value)
            _donneesUtilisateur.value = nouvelles
            withContext(Dispatchers.IO) {
                val temporaire = File(context.filesDir, "$FICHIER_UTILISATEUR.tmp")
                temporaire.writeText(json.encodeToString(nouvelles))
                temporaire.renameTo(fichierUtilisateur)
            }
        }
    }

    /** Enregistre une série de notes saisies en une fois pour une aire. */
    suspend fun enregistrerNotations(nouvelles: List<Notation>) {
        if (nouvelles.isEmpty()) return
        modifier { donnees -> donnees.copy(notations = donnees.notations + nouvelles) }
    }

    suspend fun supprimerNotation(id: String) {
        modifier { donnees -> donnees.copy(notations = donnees.notations.filterNot { it.id == id }) }
    }

    /** Rattache une enseigne à une aire, en la créant au catalogue utilisateur si besoin. */
    suspend fun ajouterEnseigneAAire(aireId: String, nomEnseigne: String) {
        val nom = nomEnseigne.trim()
        if (nom.isEmpty()) return
        val connues = (_catalogue.value?.enseignes.orEmpty() + _donneesUtilisateur.value.enseignes)
        val existante = connues.firstOrNull { it.nom.equals(nom, ignoreCase = true) }
        val enseigne = existante ?: Enseigne(id = "user-" + UUID.randomUUID(), nom = nom)
        modifier { donnees ->
            val dejaLie = (_catalogue.value?.liensEnseignes.orEmpty() + donnees.liensEnseignes)
                .any { it.aireId == aireId && it.enseigneId == enseigne.id }
            if (dejaLie) {
                donnees
            } else {
                donnees.copy(
                    enseignes = if (existante == null) donnees.enseignes + enseigne else donnees.enseignes,
                    liensEnseignes = donnees.liensEnseignes +
                        LienAireEnseigne(aireId, enseigne.id, ajoutParUtilisateur = true),
                )
            }
        }
    }

    suspend fun retirerEnseigneDAire(aireId: String, enseigneId: String) {
        modifier { donnees ->
            donnees.copy(
                liensEnseignes = donnees.liensEnseignes
                    .filterNot { it.aireId == aireId && it.enseigneId == enseigneId },
            )
        }
    }

    companion object {
        private const val FICHIER_UTILISATEUR = "donnees_utilisateur.json"

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
            auteur = auteur.trim().takeIf { it.isNotEmpty() } ?: "Moi",
            date = Instant.now().toString(),
        )
    }
}
