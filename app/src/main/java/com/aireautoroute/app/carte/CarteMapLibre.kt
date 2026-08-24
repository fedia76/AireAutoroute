package com.aireautoroute.app.carte

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
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
    // lancement) : elle se fait hors du fil principal, et la carte s'affiche
    // sans repères tant qu'elle n'a pas abouti.
    val emplacement by produceState<FondCarte.Emplacement?>(initialValue = null) {
        value = FondCarte.preparer(context)
    }

    val vue = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    val carte = remember { mutableStateOf<MapLibreMap?>(null) }
    val style = remember { mutableStateOf<Style?>(null) }

    // `onAire` peut changer d'une recomposition à l'autre ; l'écouteur d'appui,
    // lui, n'est posé qu'une fois. On lit donc toujours la version courante.
    val onAireCourant by rememberUpdatedState(onAire)

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

    AndroidView(modifier = modifier, factory = { vue })

    LaunchedEffect(vue) {
        vue.getMapAsync { carte.value = it }
    }

    // Le style est (re)posé quand la carte est prête, que le fond devient
    // disponible ou que l'habillage change. Reposer un style vide ses sources :
    // elles sont réinstallées juste après, et les effets de données ci-dessous
    // repartent de `style.value`.
    LaunchedEffect(carte.value, emplacement, palette, couleurs) {
        val instance = carte.value ?: return@LaunchedEffect
        style.value = null
        instance.setStyle(Style.Builder().fromJson(styleCarte(emplacement, palette))) { pret ->
            installerCouches(pret, couleurs)
            style.value = pret
        }
    }

    LaunchedEffect(style.value, position) {
        val pret = style.value ?: return@LaunchedEffect
        pret.getSourceAs<GeoJsonSource>(SOURCE_ITINERAIRE)
            ?.setGeoJson(traceItineraire(position))
        pret.getSourceAs<GeoJsonSource>(SOURCE_POSITION)
            ?.setGeoJson(pointPosition(position))
    }

    LaunchedEffect(style.value, aires, airesNotees, position?.pk, position?.sens) {
        val pret = style.value ?: return@LaunchedEffect
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

