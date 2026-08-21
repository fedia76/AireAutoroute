package com.aireautoroute.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aireautoroute.app.EtatUi
import com.aireautoroute.app.data.Aire
import com.aireautoroute.app.data.Autoroute
import com.aireautoroute.app.data.PositionUtilisateur
import com.aireautoroute.app.data.PointReference
import com.aireautoroute.app.data.Sens
import com.aireautoroute.app.data.TypeAire
import com.aireautoroute.app.geo.ProjectionCarte
import com.aireautoroute.app.geo.VueCarte
import kotlin.math.hypot

/** Portée du cadrage initial, de part et d'autre de la position, en kilomètres. */
private const val CADRAGE_KM = 60.0

/** Rayon d'appui autour d'une aire, en pixels. */
private const val RAYON_APPUI = 56f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranCarte(
    etat: EtatUi,
    onRetour: () -> Unit,
    onAire: (String) -> Unit,
) {
    val position = etat.position
    val mesureur = rememberTextMeasurer()

    var taille by remember { mutableStateOf(IntSize.Zero) }
    var vue by remember(position?.autoroute?.id) { mutableStateOf<VueCarte?>(null) }
    var recadrages by remember { mutableStateOf(0) }

    val projection = remember(position?.autoroute?.id) {
        val points = position?.autoroute?.geometrie.orEmpty().map { it.lat to it.lon }
        ProjectionCarte.centreeSur(points)
    }

    // Cadrage initial (et à chaque demande de recentrage) sur la portion utile de l'itinéraire.
    LaunchedEffect(taille, position?.autoroute?.id, position?.pk, recadrages) {
        if (taille.width == 0 || position == null) return@LaunchedEffect
        val interessants = position.autoroute.geometrie
            .filter { kotlin.math.abs(it.pk - position.pk) <= CADRAGE_KM }
            .ifEmpty { position.autoroute.geometrie }
            .map { projection.x(it.lon) to projection.y(it.lat) }
        vue = VueCarte.cadree(interessants, taille.width.toDouble(), taille.height.toDouble())
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(position?.autoroute?.nom?.let { "Carte · $it" } ?: "Carte") },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            if (position != null) {
                FloatingActionButton(onClick = { recadrages++ }) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "Recentrer")
                }
            }
        },
    ) { encarts ->
        if (position == null) {
            Box(
                Modifier.fillMaxSize().padding(encarts).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Indiquez d'abord votre position pour afficher la carte.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        val couleurs = CouleursCarte(
            fond = MaterialTheme.colorScheme.surfaceVariant,
            trace = MaterialTheme.colorScheme.outline,
            traceDevant = MaterialTheme.colorScheme.primary,
            aireDevant = MaterialTheme.colorScheme.primary,
            aireDerriere = MaterialTheme.colorScheme.outline,
            aireNotee = MaterialTheme.colorScheme.tertiary,
            texte = MaterialTheme.colorScheme.onSurface,
            position = MaterialTheme.colorScheme.secondary,
        )
        val notees = etat.prochainesAires.filter { it.noteGenerale != null }.map { it.aire.id }.toSet()

        Box(
            Modifier
                .fillMaxSize()
                .padding(encarts)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { taille = it }
                    .pointerInput(position.autoroute.id) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val courante = vue ?: return@detectTransformGestures
                            val deplacee = courante.deplacee(pan.x.toDouble(), pan.y.toDouble())
                            vue = if (zoom == 1f) {
                                deplacee
                            } else {
                                deplacee.zoomee(
                                    facteur = zoom.toDouble(),
                                    ancreX = centroid.x.toDouble(),
                                    ancreY = centroid.y.toDouble(),
                                    largeur = size.width.toDouble(),
                                    hauteur = size.height.toDouble(),
                                )
                            }
                        }
                    }
                    .pointerInput(position.autoroute.id, etat.airesAutoroute) {
                        detectTapGestures { appui ->
                            val courante = vue ?: return@detectTapGestures
                            val touchee = aireLaPlusProche(
                                appui = appui,
                                aires = etat.airesAutoroute,
                                projection = projection,
                                vue = courante,
                                largeur = size.width.toDouble(),
                                hauteur = size.height.toDouble(),
                            )
                            if (touchee != null) onAire(touchee.id)
                        }
                    },
            ) {
                val courante = vue ?: return@Canvas
                dessinerCarte(
                    vue = courante,
                    projection = projection,
                    position = position,
                    aires = etat.airesAutoroute,
                    notees = notees,
                    couleurs = couleurs,
                    mesureur = mesureur,
                )
            }

            Card(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .fillMaxWidth(0.72f),
            ) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(position.libelle, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "${etat.airesAutoroute.size} aires sur l'itinéraire · " +
                            "appuyez sur une aire pour l'ouvrir",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private data class CouleursCarte(
    val fond: Color,
    val trace: Color,
    val traceDevant: Color,
    val aireDevant: Color,
    val aireDerriere: Color,
    val aireNotee: Color,
    val texte: Color,
    val position: Color,
)

/** Aire sous le doigt, s'il y en a une dans le rayon d'appui. */
private fun aireLaPlusProche(
    appui: Offset,
    aires: List<Aire>,
    projection: ProjectionCarte,
    vue: VueCarte,
    largeur: Double,
    hauteur: Double,
): Aire? = aires
    .mapNotNull { aire ->
        val lat = aire.lat ?: return@mapNotNull null
        val lon = aire.lon ?: return@mapNotNull null
        val x = vue.versEcranX(projection.x(lon), largeur)
        val y = vue.versEcranY(projection.y(lat), hauteur)
        val distance = hypot(appui.x - x, appui.y - y)
        if (distance <= RAYON_APPUI) aire to distance else null
    }
    .minByOrNull { it.second }
    ?.first

private fun DrawScope.dessinerCarte(
    vue: VueCarte,
    projection: ProjectionCarte,
    position: PositionUtilisateur,
    aires: List<Aire>,
    notees: Set<String>,
    couleurs: CouleursCarte,
    mesureur: TextMeasurer,
) {
    drawRect(couleurs.fond)

    val largeur = size.width.toDouble()
    val hauteur = size.height.toDouble()
    fun ecran(lat: Double, lon: Double) = Offset(
        vue.versEcranX(projection.x(lon), largeur).toFloat(),
        vue.versEcranY(projection.y(lat), hauteur).toFloat(),
    )

    dessinerTrace(position.autoroute, position, couleurs, ::ecran)

    // Les aires : un disque plus large pour les aires de service, une étiquette si la place le permet.
    val etiquettes = vue.pixelsParKm > 1.2
    aires.forEach { aire ->
        val lat = aire.lat ?: return@forEach
        val lon = aire.lon ?: return@forEach
        val centre = ecran(lat, lon)
        if (centre.x < -80 || centre.x > size.width + 80) return@forEach
        if (centre.y < -80 || centre.y > size.height + 80) return@forEach

        val devant = when (position.sens) {
            Sens.DECROISSANT -> aire.pk <= position.pk
            else -> aire.pk >= position.pk
        }
        val couleur = when {
            aire.id in notees -> couleurs.aireNotee
            devant -> couleurs.aireDevant
            else -> couleurs.aireDerriere
        }
        val rayon = if (aire.type == TypeAire.SERVICE) 9f else 6f
        drawCircle(color = couleur, radius = rayon, center = centre)
        drawCircle(color = couleurs.fond, radius = rayon / 2.4f, center = centre)

        if (etiquettes && devant) {
            val mesure = mesureur.measure(
                text = aire.nom,
                style = TextStyle(fontSize = 11.sp, color = couleurs.texte),
            )
            drawText(
                textLayoutResult = mesure,
                topLeft = Offset(centre.x + rayon + 6f, centre.y - mesure.size.height / 2f),
            )
        }
    }

    dessinerPosition(ecran(latitudeDe(position), longitudeDe(position)), couleurs)
}

private fun DrawScope.dessinerTrace(
    autoroute: Autoroute,
    position: PositionUtilisateur,
    couleurs: CouleursCarte,
    ecran: (Double, Double) -> Offset,
) {
    val geometrie = autoroute.geometrie
    if (geometrie.size < 2) return

    // Deux tracés : ce qui reste à parcourir se détache de ce qui est derrière.
    val derriere = Path()
    val devant = Path()
    var derriereCommence = false
    var devantCommence = false

    geometrie.forEach { point ->
        val offset = ecran(point.lat, point.lon)
        val aVenir = when (position.sens) {
            Sens.DECROISSANT -> point.pk <= position.pk
            else -> point.pk >= position.pk
        }
        val cible = if (aVenir) devant else derriere
        val commence = if (aVenir) devantCommence else derriereCommence
        if (commence) cible.lineTo(offset.x, offset.y) else cible.moveTo(offset.x, offset.y)
        if (aVenir) devantCommence = true else derriereCommence = true
    }

    drawPath(derriere, color = couleurs.trace, style = Stroke(width = 5f))
    drawPath(devant, color = couleurs.traceDevant, style = Stroke(width = 7f))
}

private fun DrawScope.dessinerPosition(centre: Offset, couleurs: CouleursCarte) {
    drawCircle(color = couleurs.position.copy(alpha = 0.25f), radius = 22f, center = centre)
    drawCircle(color = couleurs.position, radius = 10f, center = centre)
    drawCircle(color = couleurs.fond, radius = 4f, center = centre)
}

/** Position de l'utilisateur sur le tracé, interpolée entre les deux points qui l'encadrent. */
private fun latitudeDe(position: PositionUtilisateur): Double = interpoler(position) { it.lat }

private fun longitudeDe(position: PositionUtilisateur): Double = interpoler(position) { it.lon }

private inline fun interpoler(
    position: PositionUtilisateur,
    valeur: (PointReference) -> Double,
): Double {
    val geometrie = position.autoroute.geometrie
    if (geometrie.isEmpty()) return 0.0
    val encadrant = geometrie.zipWithNext().firstOrNull { (a, b) -> position.pk in a.pk..b.pk }
        ?: return valeur(if (position.pk <= geometrie.first().pk) geometrie.first() else geometrie.last())
    val (avant, apres) = encadrant
    val ecart = apres.pk - avant.pk
    val part = if (ecart == 0.0) 0.0 else (position.pk - avant.pk) / ecart
    return valeur(avant) + part * (valeur(apres) - valeur(avant))
}
