package com.aireautoroute.app

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aireautoroute.app.data.Aire
import com.aireautoroute.app.data.AireDetail
import com.aireautoroute.app.data.AireResume
import com.aireautoroute.app.data.Autoroute
import com.aireautoroute.app.data.Catalogue
import com.aireautoroute.app.data.Critere
import com.aireautoroute.app.data.DeclarationEquipement
import com.aireautoroute.app.data.DepotDonnees
import com.aireautoroute.app.data.EtatSynchro
import com.aireautoroute.app.data.MotifSignalement
import com.aireautoroute.app.data.Notation
import com.aireautoroute.app.data.PreferencesUi
import com.aireautoroute.app.data.PositionUtilisateur
import com.aireautoroute.app.data.Presence
import com.aireautoroute.app.data.Sens
import com.aireautoroute.app.data.SourcePosition
import com.aireautoroute.app.data.TrancheAge
import com.aireautoroute.app.data.detailAire
import com.aireautoroute.app.data.prochainesAires
import com.aireautoroute.app.geo.LocalisateurPk
import com.aireautoroute.app.geo.ResultatLocalisation
import com.aireautoroute.app.geo.SuiviPosition
import com.aireautoroute.app.ui.theme.ThemeApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Contribution saisie dans le formulaire, avant enregistrement : ce que l'utilisateur déclare
 * pour un critère, et la note qu'il lui donne s'il l'a déclaré présent.
 */
data class SaisieCritere(
    val critere: Critere,
    val presence: Presence,
    /** Clé : la tranche d'âge pour les aires de jeux, `null` pour les autres critères. */
    val notes: Map<TrancheAge?, Int>,
    val commentaire: String,
)

data class EtatLocalisation(
    val enCours: Boolean = false,
    val message: String? = null,
    /** `true` quand [message] signale un échec : l'écran le met alors en évidence. */
    val estErreur: Boolean = false,
)

data class EtatUi(
    val chargement: Boolean = true,
    val synchro: EtatSynchro = EtatSynchro.EN_COURS,
    /** Message à montrer après une tentative de publication. */
    val messageContribution: String? = null,
    val autoroutes: List<Autoroute> = emptyList(),
    val position: PositionUtilisateur? = null,
    val prochainesAires: List<AireResume> = emptyList(),
    /** Toutes les aires de l'autoroute courante, pour la vue carte. */
    val airesAutoroute: List<Aire> = emptyList(),
    val localisation: EtatLocalisation = EtatLocalisation(),
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val depot = DepotDonnees(application)
    private val suivi = SuiviPosition(application)
    private val preferences = PreferencesUi(application)

    /** Habillage choisi par l'utilisateur, appliqué à toute l'application. */
    val theme: StateFlow<ThemeApp> = preferences.theme
        .map { nom -> ThemeApp.entries.firstOrNull { it.name == nom } ?: ThemeApp.SIGNALETIQUE }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeApp.SIGNALETIQUE)

    fun definirTheme(theme: ThemeApp) = preferences.definirTheme(theme.name)

    private val positionChoisie = MutableStateFlow<PositionUtilisateur?>(null)
    private val etatLocalisation = MutableStateFlow(EtatLocalisation())
    private var jobLocalisation: Job? = null

    private val messageContribution = MutableStateFlow<String?>(null)

    val etat: StateFlow<EtatUi> = combine(
        depot.catalogue,
        depot.contributions,
        positionChoisie,
        etatLocalisation,
        combine(depot.synchro, messageContribution) { synchro, message -> synchro to message },
    ) { catalogue, donnees, position, localisation, (synchro, message) ->
        EtatUi(
            chargement = catalogue == null,
            synchro = synchro,
            messageContribution = message,
            autoroutes = catalogue?.autoroutes.orEmpty(),
            position = position,
            prochainesAires = if (catalogue != null && position != null) {
                prochainesAires(catalogue, donnees, position)
            } else {
                emptyList()
            },
            airesAutoroute = if (catalogue != null && position != null) {
                catalogue.aires.filter { it.autorouteId == position.autoroute.id }
            } else {
                emptyList()
            },
            localisation = localisation,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EtatUi())

    init {
        viewModelScope.launch { depot.charger() }
    }

    // --- Position ------------------------------------------------------------

    fun definirPositionManuelle(autoroute: Autoroute, pk: Double, sens: Sens) {
        jobLocalisation?.cancel()
        positionChoisie.value = PositionUtilisateur(
            autoroute = autoroute,
            pk = pk.coerceIn(autoroute.pkDebut, autoroute.pkFin),
            sens = sens,
            source = SourcePosition.MANUELLE,
        )
        etatLocalisation.value = EtatLocalisation()
    }

    /**
     * Localisation ponctuelle : on écoute la position le temps d'obtenir un point exploitable.
     *
     * Deux exigences, dans cet ordre. La mesure doit d'abord être **fraîche** : le dernier point
     * connu du système peut dater d'heures et d'un autre département, et il transporte le cap
     * qu'il avait alors — il aurait donc toutes les apparences d'une position fiable. Il n'est
     * affiché qu'à titre d'aperçu, jamais retenu comme résultat. Le cap n'étant ensuite fiable
     * qu'en mouvement, on laisse [DELAI_CAP_MS] à la mesure fraîche pour en fournir un, puis on
     * s'en contente en prévenant que le sens reste à confirmer.
     */
    fun localiser() {
        if (!suivi.permissionAccordee()) {
            etatLocalisation.value = EtatLocalisation(
                message = "Autorisez la localisation pour utiliser ce bouton.",
                estErreur = true,
            )
            return
        }
        if (!suivi.gpsActive()) {
            etatLocalisation.value = EtatLocalisation(
                message = "La localisation du téléphone est désactivée.",
                estErreur = true,
            )
            return
        }

        jobLocalisation?.cancel()
        etatLocalisation.value = EtatLocalisation(enCours = true)
        jobLocalisation = viewModelScope.launch {
            var frais: ResultatLocalisation? = null
            var apercu: ResultatLocalisation? = null
            var premierFraisMs = 0L

            withTimeoutOrNull(DELAI_LOCALISATION_MS) {
                suivi.positions()
                    .mapNotNull { mesure ->
                        // Un point imprécis se projetterait au hasard sur le réseau : il ne peut
                        // servir ni de résultat, ni d'aperçu.
                        if (!mesure.precise) return@mapNotNull null
                        val catalogue = depot.catalogue.value ?: return@mapNotNull null
                        val resultat = LocalisateurPk.localiser(
                            autoroutes = catalogue.autoroutes,
                            lat = mesure.latitude,
                            lon = mesure.longitude,
                            capDegres = mesure.capDegres,
                        ) ?: return@mapNotNull null
                        mesure to resultat
                    }
                    .firstOrNull { (mesure, resultat) ->
                        if (!mesure.fraiche) {
                            apercu = resultat
                            afficherApercu(resultat)
                            return@firstOrNull false
                        }
                        frais = resultat
                        if (resultat.sensFiable) return@firstOrNull true
                        // Sans cap, on laisse une courte fenêtre au GPS pour en produire un :
                        // au-delà, mieux vaut une position juste avec un sens à confirmer qu'un
                        // écran qui tourne encore vingt secondes.
                        if (premierFraisMs == 0L) premierFraisMs = SystemClock.elapsedRealtime()
                        SystemClock.elapsedRealtime() - premierFraisMs >= DELAI_CAP_MS
                    }
            }
            appliquer(frais, apercu)
        }
    }

    /**
     * Montre le dernier point connu en attendant la vraie mesure, sans jamais écraser une position
     * déjà établie : un point du cache lui est forcément antérieur.
     */
    private fun afficherApercu(resultat: ResultatLocalisation) {
        val actuelle = positionChoisie.value
        if (actuelle != null && !actuelle.provisoire) return
        positionChoisie.value = versPosition(resultat, provisoire = true)
    }

    private fun appliquer(frais: ResultatLocalisation?, apercu: ResultatLocalisation?) {
        if (frais != null) {
            positionChoisie.value = versPosition(frais, provisoire = false)
            etatLocalisation.value = EtatLocalisation(
                message = if (frais.sensFiable) {
                    null
                } else {
                    "Sens non confirmé : vérifiez la direction ci-dessous."
                },
                estErreur = false,
            )
            return
        }
        // Aucune mesure fraîche : on ne fait surtout pas passer le cache pour la position du
        // moment, c'est exactement ce qui affichait une position vieille de plusieurs dizaines
        // de kilomètres.
        etatLocalisation.value = EtatLocalisation(
            message = if (apercu != null) {
                "Signal GPS non acquis : la position ci-dessus est approximative. " +
                    "Dégagez la vue du ciel, puis réessayez."
            } else {
                "Position introuvable : signal GPS non acquis, ou aucune autoroute connue à proximité."
            },
            estErreur = true,
        )
    }

    private fun versPosition(resultat: ResultatLocalisation, provisoire: Boolean): PositionUtilisateur {
        // Sans cap exploitable, on conserve le sens déjà affiché sur la même autoroute — mais pas
        // celui d'un simple aperçu, qui n'a lui-même rien confirmé.
        val sens = if (resultat.sensFiable) {
            resultat.sens
        } else {
            positionChoisie.value
                ?.takeIf { !it.provisoire && it.autoroute.id == resultat.autoroute.id }
                ?.sens
                ?: resultat.sens
        }
        return PositionUtilisateur(
            autoroute = resultat.autoroute,
            pk = resultat.pk,
            sens = sens,
            source = SourcePosition.GPS,
            ecartMetres = resultat.ecartMetres,
            sensFiable = resultat.sensFiable,
            provisoire = provisoire,
        )
    }

    /** Inverse le sens de circulation sans changer de PK (utile quand le GPS hésite). */
    fun inverserSens() {
        val position = positionChoisie.value ?: return
        positionChoisie.value = position.copy(sens = position.sens.oppose(), sensFiable = true)
    }

    // --- Aires ---------------------------------------------------------------

    fun detail(aireId: String): StateFlow<AireDetail?> = combine(
        depot.catalogue,
        depot.contributions,
    ) { catalogue, donnees ->
        catalogue?.let { detailAire(it, donnees, aireId) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun aire(aireId: String): Aire? = depot.catalogue.value?.aires?.firstOrNull { it.id == aireId }

    fun catalogue(): Catalogue? = depot.catalogue.value

    /**
     * Enregistre la contribution : une déclaration de présence par équipement renseigné, et les
     * notes des seuls équipements déclarés présents.
     */
    fun enregistrerContribution(aireId: String, saisies: List<SaisieCritere>, auteur: String) {
        val declarations = mutableListOf<DeclarationEquipement>()
        val notations = mutableListOf<Notation>()

        saisies.forEach { saisie ->
            if (saisie.critere.estEquipement && saisie.presence != Presence.NE_SAIS_PAS) {
                declarations += DepotDonnees.nouvelleDeclaration(
                    aireId = aireId,
                    critere = saisie.critere,
                    presence = saisie.presence,
                    auteur = auteur,
                )
            }
            // Une note n'a de sens que si le contributeur a constaté l'équipement.
            if (saisie.critere.estEquipement && saisie.presence != Presence.OUI) return@forEach

            var commentairePlace = false
            saisie.notes
                .filterValues { it in 1..5 }
                .forEach { (tranche, note) ->
                    notations += DepotDonnees.nouvelleNotation(
                        aireId = aireId,
                        critere = saisie.critere,
                        trancheAge = tranche,
                        note = note,
                        // Le commentaire n'est rattaché qu'à une ligne pour ne pas le dupliquer.
                        commentaire = if (commentairePlace) null else saisie.commentaire,
                        auteur = auteur,
                    )
                    commentairePlace = true
                }
        }

        viewModelScope.launch {
            rendreCompte(depot.enregistrerContribution(declarations, notations), "Merci ! Votre avis est publié.")
        }
    }

    fun ajouterEnseigne(aireId: String, nom: String) {
        viewModelScope.launch {
            rendreCompte(depot.ajouterEnseigneAAire(aireId, nom), "Enseigne ajoutée.")
        }
    }

    fun retirerEnseigne(aireId: String, enseigneId: String) {
        viewModelScope.launch {
            rendreCompte(depot.retirerEnseigneDAire(aireId, enseigneId), "Enseigne retirée.")
        }
    }

    fun signaler(notationId: String, motif: MotifSignalement) {
        viewModelScope.launch {
            rendreCompte(depot.signaler(notationId, motif), "Signalement enregistré, merci.")
        }
    }

    fun supprimerMesContributions() {
        viewModelScope.launch {
            rendreCompte(
                depot.supprimerMesContributions(),
                "Vos contributions ont été supprimées.",
            )
        }
    }

    /** Une contribution qui ne part pas doit se voir : l'utilisateur croirait l'avoir publiée. */
    private fun rendreCompte(resultat: Result<Unit>, succes: String) {
        messageContribution.value = if (resultat.isSuccess) {
            succes
        } else {
            "Envoi impossible : vérifiez votre connexion, votre contribution n'a pas été publiée."
        }
    }

    fun effacerMessageContribution() {
        messageContribution.value = null
    }

    fun rafraichir() {
        viewModelScope.launch { depot.rafraichir() }
    }

    private companion object {
        /**
         * Budget total d'une localisation. Une première acquisition GPS à froid dépasse
         * couramment quinze secondes ; abandonner avant, c'est se rabattre sur le cache.
         */
        const val DELAI_LOCALISATION_MS = 30_000L

        /** Fenêtre laissée à une mesure fraîche pour fournir un cap avant qu'on s'en passe. */
        const val DELAI_CAP_MS = 8_000L
    }
}
