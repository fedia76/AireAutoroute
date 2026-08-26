package com.aireautoroute.app.carte

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aireautoroute.app.data.Aire
import com.aireautoroute.app.data.PositionUtilisateur
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * Carte MapLibre : fond embarqué, itinéraire en surbrillance, aires en pastilles.
 *
 * Le réseau autoroutier complet n'est pas redessiné ici : le fond le porte déjà
 * (les autoroutes entrent dans les tuiles dès le zoom 3). On ne trace que
 * l'itinéraire courant, dont c'est justement le propos de le distinguer.
 */
@Composable
fun CarteMapLibre(
    position: PositionUtilisateur?,
    aires: List<Aire>,
    airesNotees: Set<String>,
    palette: PaletteCarte,
    couleurs: CouleursTrace,
    recadrages: Int,
    onAire: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val proprietaireCycle = LocalLifecycleOwner.current

    // La recopie du fond peut durer (une trentaine de mégaoctets au premier
    // lancement) : elle se fait hors du fil principal. Tant qu'elle n'a pas
    // abouti, `null` — à distinguer d'un fond absent, qui est un emplacement
    // sans archive.
    val emplacement by produceState<FondCarte.Emplacement?>(initialValue = null) {
        value = FondCarte.preparer(context)
    }

    val vue = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    val carte = remember { mutableStateOf<MapLibreMap?>(null) }
    val style = remember { mutableStateOf<Style?>(null) }

    // Vrai dès qu'un premier style a été chargé : avant, la vue n'a encore rien
    // à montrer et on la couvre ; après, un changement d'habillage se fait sans
    // repasser par l'aplat d'attente.
    val premierStyle = remember { mutableStateOf(false) }
    val couleurAttente = remember(palette) {
        Color(android.graphics.Color.parseColor(palette.fond))
    }

    // `onAire` peut changer d'une recomposition à l'autre ; l'écouteur d'appui,
    // lui, n'est posé qu'une fois. On lit donc toujours la version courante.
    val onAireCourant by rememberUpdatedState(onAire)

    // Idem pour les données : le style se charge de façon asynchrone, et ses
    // sources naissent avec ce qu'il y a à montrer à ce moment-là.
    val donnees by rememberUpdatedState(DonneesCarte(position, aires, airesNotees))

    DisposableEffect(proprietaireCycle) {
        val observateur = LifecycleEventObserver { _, evenement ->
            when (evenement) {
                Lifecycle.Event.ON_START -> vue.onStart()
                Lifecycle.Event.ON_RESUME -> vue.onResume()
                Lifecycle.Event.ON_PAUSE -> vue.onPause()
                Lifecycle.Event.ON_STOP -> vue.onStop()
                // ON_DESTROY est délibérément absent : la destruction est faite
                // dans `onDispose`, et l'appeler deux fois fait planter la vue.
                else -> Unit
            }
        }
        proprietaireCycle.lifecycle.addObserver(observateur)
        onDispose {
            proprietaireCycle.lifecycle.removeObserver(observateur)
            vue.onDestroy()
        }
    }

    Box(modifier) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { vue })
        // Avant le premier style, MapLibre montre sa propre couleur d'attente.
        // Un aplat aux teintes de la carte évite ce clignotement étranger.
        if (!premierStyle.value) {
            Box(Modifier.fillMaxSize().background(couleurAttente))
        }
    }

    LaunchedEffect(vue) {
        vue.getMapAsync { carte.value = it }
    }

    // Le style est posé quand la carte est prête et que l'on sait de quoi le
    // fond est fait, puis reposé si l'habillage change. Reposer un style vide
    // ses sources : elles sont réinstallées juste après, et les effets de
    // données ci-dessous repartent de `style.value`.
    //
    // On attend délibérément la fin de la préparation du fond plutôt que de
    // poser un style provisoire sans glyphes : une demande de glyphes qui
    // échoue n'est jamais réessayée pour cette vue, et les couches de symboles
    // retiendraient alors les tuiles des aires — pastilles comprises — jusqu'à
    // ce que l'écran soit quitté puis rouvert.
    LaunchedEffect(carte.value, emplacement, palette, couleurs) {
        val instance = carte.value ?: return@LaunchedEffect
        val fond = emplacement ?: return@LaunchedEffect
        style.value = null
        instance.setStyle(Style.Builder().fromJson(styleCarte(fond, palette))) { pret ->
            premierStyle.value = true
            // Un style remplacé entre-temps n'accepte plus rien : le suivant
            // rappellera cette même installation.
            if (!pret.isFullyLoaded) return@setStyle
            installerCouches(
                style = pret,
                couleurs = couleurs,
                donnees = donnees,
                avecLibelles = fond.motifGlyphes != null,
            )
            style.value = pret
        }
    }

    LaunchedEffect(style.value, position) {
        val pret = style.value?.takeIf { it.isFullyLoaded } ?: return@LaunchedEffect
        pret.getSourceAs<GeoJsonSource>(SOURCE_ITINERAIRE)
            ?.setGeoJson(traceItineraire(position))
        pret.getSourceAs<GeoJsonSource>(SOURCE_POSITION)
            ?.setGeoJson(pointPosition(position))
    }

    LaunchedEffect(style.value, aires, airesNotees, position?.pk, position?.sens) {
        val pret = style.value?.takeIf { it.isFullyLoaded } ?: return@LaunchedEffect
        pret.getSourceAs<GeoJsonSource>(SOURCE_AIRES)
            ?.setGeoJson(pointsAires(aires, airesNotees, position))
    }

    // Cadrage initial, et à chaque demande de recentrage.
    LaunchedEffect(style.value, position?.autoroute?.id, position?.pk, recadrages) {
        val instance = carte.value ?: return@LaunchedEffect
        if (style.value == null) return@LaunchedEffect
        instance.animateCamera(CameraUpdateFactory.newLatLngBounds(cadrage(position), 64))
    }

    DisposableEffect(carte.value) {
        val instance = carte.value ?: return@DisposableEffect onDispose { }
        val ecouteur = MapLibreMap.OnMapClickListener { point ->
            when (val touche = chercherSousLeDoigt(instance, point)) {
                null -> false
                // Un groupe ne s'ouvre pas : il se déplie en zoomant dessus.
                else -> {
                    if (touche.groupe) {
                        instance.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                point,
                                instance.cameraPosition.zoom + 2.0,
                            ),
                        )
                    } else {
                        onAireCourant(touche.id)
                    }
                    true
                }
            }
        }
        instance.addOnMapClickListener(ecouteur)
        onDispose { instance.removeOnMapClickListener(ecouteur) }
    }
}

