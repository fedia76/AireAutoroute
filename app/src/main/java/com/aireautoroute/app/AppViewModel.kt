package com.aireautoroute.app

import android.app.Application
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
import kotlinx.coroutines.flow.onEach
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
)

data class EtatUi(
    val chargement: Boolean = true,
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

    val etat: StateFlow<EtatUi> = combine(
        depot.catalogue,
        depot.donneesUtilisateur,
        positionChoisie,
        etatLocalisation,
    ) { catalogue, donnees, position, localisation ->
        EtatUi(
            chargement = catalogue == null,
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
     * Localisation ponctuelle : on écoute le GPS le temps d'obtenir un point exploitable.
     *
     * Le cap n'étant fiable qu'en mouvement, on attend en priorité une position qui en fournit un ;
     * passé [DELAI_LOCALISATION_MS], on se contente du dernier point obtenu et on prévient que le
     * sens de circulation reste à confirmer.
     */
    fun localiser() {
        if (!suivi.permissionAccordee()) {
            etatLocalisation.value = EtatLocalisation(
                message = "Autorisez la localisation pour utiliser ce bouton.",
            )
            return
        }
        if (!suivi.gpsActive()) {
            etatLocalisation.value = EtatLocalisation(
                message = "La localisation du téléphone est désactivée.",
            )
            return
        }

        jobLocalisation?.cancel()
        etatLocalisation.value = EtatLocalisation(enCours = true, message = "Recherche de la position…")
        jobLocalisation = viewModelScope.launch {
            var dernier: ResultatLocalisation? = null
            withTimeoutOrNull(DELAI_LOCALISATION_MS) {
                suivi.positions()
                    .mapNotNull { location ->
                        val catalogue = depot.catalogue.value ?: return@mapNotNull null
                        // Sous ~10 km/h le cap fourni par le GPS n'est pas exploitable.
                        val cap = if (location.hasBearing() && location.speed > 3f) {
                            location.bearing
                        } else {
                            null
                        }
                        LocalisateurPk.localiser(
                            autoroutes = catalogue.autoroutes,
                            lat = location.latitude,
                            lon = location.longitude,
                            capDegres = cap,
                        )
                    }
                    .onEach { dernier = it }
                    .firstOrNull { it.sensFiable }
            }
            appliquer(dernier)
        }
    }

    private fun appliquer(resultat: ResultatLocalisation?) {
        if (resultat == null) {
            etatLocalisation.value = EtatLocalisation(
                message = "Position introuvable, ou aucune autoroute connue à proximité.",
            )
            return
        }
        // Sans cap exploitable, on conserve le sens déjà affiché sur la même autoroute.
        val sens = if (resultat.sensFiable) {
            resultat.sens
        } else {
            positionChoisie.value
                ?.takeIf { it.autoroute.id == resultat.autoroute.id }
                ?.sens
                ?: resultat.sens
        }
        positionChoisie.value = PositionUtilisateur(
            autoroute = resultat.autoroute,
            pk = resultat.pk,
            sens = sens,
            source = SourcePosition.GPS,
            ecartMetres = resultat.ecartMetres,
            sensFiable = resultat.sensFiable,
        )
        etatLocalisation.value = EtatLocalisation(
            message = if (resultat.sensFiable) {
                null
            } else {
                "Sens non confirmé : vérifiez la direction ci-dessous."
            },
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
        depot.donneesUtilisateur,
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

        viewModelScope.launch { depot.enregistrerContribution(declarations, notations) }
    }

    fun ajouterEnseigne(aireId: String, nom: String) {
        viewModelScope.launch { depot.ajouterEnseigneAAire(aireId, nom) }
    }

    fun retirerEnseigne(aireId: String, enseigneId: String) {
        viewModelScope.launch { depot.retirerEnseigneDAire(aireId, enseigneId) }
    }

    private companion object {
        const val DELAI_LOCALISATION_MS = 15_000L
    }
}
