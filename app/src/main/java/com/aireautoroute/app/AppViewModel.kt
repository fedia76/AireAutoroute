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
import com.aireautoroute.app.data.DepotDonnees
import com.aireautoroute.app.data.Notation
import com.aireautoroute.app.data.PositionUtilisateur
import com.aireautoroute.app.data.Sens
import com.aireautoroute.app.data.SourcePosition
import com.aireautoroute.app.data.TrancheAge
import com.aireautoroute.app.data.detailAire
import com.aireautoroute.app.data.prochainesAires
import com.aireautoroute.app.geo.LocalisateurPk
import com.aireautoroute.app.geo.SuiviPosition
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Une note saisie dans le formulaire, avant enregistrement. */
data class SaisieNote(
    val critere: Critere,
    val trancheAge: TrancheAge?,
    val note: Int,
    val commentaire: String,
)

data class EtatUi(
    val chargement: Boolean = true,
    val autoroutes: List<Autoroute> = emptyList(),
    val position: PositionUtilisateur? = null,
    val prochainesAires: List<AireResume> = emptyList(),
    val modeAuto: Boolean = false,
    val message: String? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val depot = DepotDonnees(application)
    private val suivi = SuiviPosition(application)

    private val positionChoisie = MutableStateFlow<PositionUtilisateur?>(null)
    private val modeAuto = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private var jobLocalisation: Job? = null

    val etat: StateFlow<EtatUi> = combine(
        depot.catalogue,
        depot.donneesUtilisateur,
        positionChoisie,
        modeAuto,
        message,
    ) { catalogue, donnees, position, auto, msg ->
        EtatUi(
            chargement = catalogue == null,
            autoroutes = catalogue?.autoroutes.orEmpty(),
            position = position,
            prochainesAires = if (catalogue != null && position != null) {
                prochainesAires(catalogue, donnees, position)
            } else {
                emptyList()
            },
            modeAuto = auto,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EtatUi())

    init {
        viewModelScope.launch { depot.charger() }
    }

    // --- Position ------------------------------------------------------------

    fun definirPositionManuelle(autoroute: Autoroute, pk: Double, sens: Sens) {
        arreterModeAuto()
        positionChoisie.value = PositionUtilisateur(
            autoroute = autoroute,
            pk = pk.coerceIn(0.0, autoroute.longueurKm),
            sens = sens,
            source = SourcePosition.MANUELLE,
        )
        message.value = null
    }

    /** Bascule le mode automatique. La permission doit avoir été accordée au préalable. */
    fun basculerModeAuto(active: Boolean) {
        if (!active) {
            arreterModeAuto()
            return
        }
        if (!suivi.permissionAccordee()) {
            message.value = "Autorisez la localisation pour utiliser le mode automatique."
            return
        }
        if (!suivi.gpsActive()) {
            message.value = "La localisation du téléphone est désactivée."
            return
        }
        modeAuto.value = true
        message.value = "Recherche de la position…"
        jobLocalisation?.cancel()
        jobLocalisation = viewModelScope.launch {
            suivi.positions().collect { location ->
                val catalogue = depot.catalogue.value ?: return@collect
                // Sous ~10 km/h le cap fourni par le GPS n'est pas exploitable.
                val cap = if (location.hasBearing() && location.speed > 3f) location.bearing else null
                val resultat = LocalisateurPk.localiser(
                    autoroutes = catalogue.autoroutes,
                    lat = location.latitude,
                    lon = location.longitude,
                    capDegres = cap,
                )
                if (resultat == null) {
                    message.value = "Aucune autoroute connue à proximité de votre position."
                } else {
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
                    message.value = if (resultat.sensFiable) {
                        null
                    } else {
                        "Sens non confirmé : roulez quelques instants ou corrigez-le à la main."
                    }
                }
            }
        }
    }

    private fun arreterModeAuto() {
        jobLocalisation?.cancel()
        jobLocalisation = null
        modeAuto.value = false
    }

    /** Inverse le sens de circulation sans changer de PK (utile quand le GPS hésite). */
    fun inverserSens() {
        val position = positionChoisie.value ?: return
        positionChoisie.value = position.copy(sens = position.sens.oppose(), sensFiable = true)
    }

    fun effacerMessage() {
        message.value = null
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

    fun enregistrerNotes(aireId: String, saisies: List<SaisieNote>, auteur: String) {
        val notations: List<Notation> = saisies
            .filter { it.note in 1..5 }
            .map {
                DepotDonnees.nouvelleNotation(
                    aireId = aireId,
                    critere = it.critere,
                    trancheAge = it.trancheAge,
                    note = it.note,
                    commentaire = it.commentaire,
                    auteur = auteur,
                )
            }
        viewModelScope.launch { depot.enregistrerNotations(notations) }
    }

    fun ajouterEnseigne(aireId: String, nom: String) {
        viewModelScope.launch { depot.ajouterEnseigneAAire(aireId, nom) }
    }

    fun retirerEnseigne(aireId: String, enseigneId: String) {
        viewModelScope.launch { depot.retirerEnseigneDAire(aireId, enseigneId) }
    }
}
