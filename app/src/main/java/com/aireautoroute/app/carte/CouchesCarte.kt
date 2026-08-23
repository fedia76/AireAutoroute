package com.aireautoroute.app.carte

import android.graphics.PointF
import android.graphics.RectF
import com.aireautoroute.app.data.Aire
import com.aireautoroute.app.data.PositionUtilisateur
import com.aireautoroute.app.data.Sens
import com.aireautoroute.app.data.TypeAire
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.abs

/**
 * Sources, couches et données de la surcouche posée sur le fond.
 *
 * Séparé du composable : ce fichier ne dépend ni de Compose ni du cycle de vie
 * d'une vue, seulement de MapLibre et des modèles de l'application. C'est la
 * partie où se logent les erreurs de style et de filtre, et elle se relit — et
 * se compile — sans rien monter à l'écran.
 */
private const val CADRAGE_KM = 60.0

/** Tolérance d'appui autour du doigt, en pixels. */
private const val TOLERANCE_APPUI = 28f

/** Cadrage de repli quand aucune position n'est connue : la France entière. */
private val FRANCE = LatLngBounds.Builder()
    .include(LatLng(51.1, -5.2))
    .include(LatLng(41.3, 9.6))
    .build()

internal const val SOURCE_ITINERAIRE = "itineraire"
internal const val SOURCE_AIRES = "aires"
internal const val SOURCE_POSITION = "position"

private const val COUCHE_PASTILLE = "aires-pastille"
private const val COUCHE_GROUPE = "aires-groupe"

/** Couleurs de la surcouche, reprises de l'habillage de l'application. */
data class CouleursTrace(
    val devant: String,
    val derriere: String,
    val aireDevant: String,
    val aireDerriere: String,
    val aireNotee: String,
    val groupe: String,
    val position: String,
    val surTrace: String,
)

/** Résultat d'un appui : soit une aire, soit un groupe à ouvrir en zoomant. */
internal data class Touche(val id: String, val groupe: Boolean)

internal fun chercherSousLeDoigt(carte: MapLibreMap, point: LatLng): Touche? {
    val ecran: PointF = carte.projection.toScreenLocation(point)
    val zone = RectF(
        ecran.x - TOLERANCE_APPUI,
        ecran.y - TOLERANCE_APPUI,
        ecran.x + TOLERANCE_APPUI,
        ecran.y + TOLERANCE_APPUI,
    )
    // Les pastilles d'abord : sous le doigt, une aire précise prime sur le
    // groupe qui la contiendrait.
    carte.queryRenderedFeatures(zone, COUCHE_PASTILLE)
        .firstNotNullOfOrNull { it.getStringProperty("id") }
        ?.let { return Touche(it, groupe = false) }
    if (carte.queryRenderedFeatures(zone, COUCHE_GROUPE).isNotEmpty()) {
        return Touche("", groupe = true)
    }
    return null
}

// --- Données -----------------------------------------------------------------

/**
 * L'itinéraire, coupé en deux tronçons : ce qui reste à parcourir et le reste.
 *
 * Les deux tronçons partagent le point courant, sinon le trait se briserait
 * visiblement à l'endroit précis où l'œil se pose.
 */
internal fun traceItineraire(position: PositionUtilisateur?): FeatureCollection {
    if (position == null) return FeatureCollection.fromFeatures(emptyList())
    val geometrie = position.autoroute.geometrie
    if (geometrie.size < 2) return FeatureCollection.fromFeatures(emptyList())

    val devant = mutableListOf<Point>()
    val derriere = mutableListOf<Point>()
    geometrie.forEach { reference ->
        val point = Point.fromLngLat(reference.lon, reference.lat)
        val aVenir = when (position.sens) {
            Sens.DECROISSANT -> reference.pk <= position.pk
            else -> reference.pk >= position.pk
        }
        if (aVenir) devant += point else derriere += point
    }
    // Raccord : le dernier point du tronçon parcouru rejoint le premier du reste.
    if (devant.isNotEmpty() && derriere.isNotEmpty()) {
        if (position.sens == Sens.DECROISSANT) derriere.add(0, devant.last())
        else derriere += devant.first()
    }

    val traces = mutableListOf<Feature>()
    if (derriere.size >= 2) {
        traces += Feature.fromGeometry(LineString.fromLngLats(derriere)).apply {
            addBooleanProperty("devant", false)
        }
    }
    if (devant.size >= 2) {
        traces += Feature.fromGeometry(LineString.fromLngLats(devant)).apply {
            addBooleanProperty("devant", true)
        }
    }
    return FeatureCollection.fromFeatures(traces)
}

internal fun pointsAires(
    aires: List<Aire>,
    notees: Set<String>,
    position: PositionUtilisateur?,
): FeatureCollection {
    val points = aires.mapNotNull { aire ->
        val lat = aire.lat ?: return@mapNotNull null
        val lon = aire.lon ?: return@mapNotNull null
        val devant = when {
            position == null -> true
            position.sens == Sens.DECROISSANT -> aire.pk <= position.pk
            else -> aire.pk >= position.pk
        }
        Feature.fromGeometry(Point.fromLngLat(lon, lat)).apply {
            addStringProperty("id", aire.id)
            addStringProperty("nom", aire.nom)
            addBooleanProperty("devant", devant)
            addBooleanProperty("notee", aire.id in notees)
            addBooleanProperty("service", aire.type == TypeAire.SERVICE)
        }
    }
    return FeatureCollection.fromFeatures(points)
}

internal fun pointPosition(position: PositionUtilisateur?): FeatureCollection {
    val coordonnees = position?.let { interpolerPosition(it) }
        ?: return FeatureCollection.fromFeatures(emptyList())
    return FeatureCollection.fromFeatures(
        listOf(Feature.fromGeometry(Point.fromLngLat(coordonnees.second, coordonnees.first))),
    )
}

/** Position sur le tracé, interpolée entre les deux points de référence qui l'encadrent. */
private fun interpolerPosition(position: PositionUtilisateur): Pair<Double, Double>? {
    val geometrie = position.autoroute.geometrie
    if (geometrie.isEmpty()) return null
    val encadrant = geometrie.zipWithNext().firstOrNull { (a, b) -> position.pk in a.pk..b.pk }
    if (encadrant == null) {
        val extremite =
            if (position.pk <= geometrie.first().pk) geometrie.first() else geometrie.last()
        return extremite.lat to extremite.lon
    }
    val (avant, apres) = encadrant
    val ecart = apres.pk - avant.pk
    val part = if (ecart == 0.0) 0.0 else (position.pk - avant.pk) / ecart
    return (avant.lat + part * (apres.lat - avant.lat)) to
        (avant.lon + part * (apres.lon - avant.lon))
}

internal fun cadrage(position: PositionUtilisateur?): LatLngBounds {
    if (position == null) return FRANCE
    val interessants = position.autoroute.geometrie
        .filter { abs(it.pk - position.pk) <= CADRAGE_KM }
        .ifEmpty { position.autoroute.geometrie }
        .map { LatLng(it.lat, it.lon) }
    if (interessants.size < 2) return FRANCE
    return LatLngBounds.Builder().includes(interessants).build()
}

// --- Couches ------------------------------------------------------------------

internal fun installerCouches(style: Style, couleurs: CouleursTrace) {
    style.addSource(GeoJsonSource(SOURCE_ITINERAIRE))
    style.addSource(
        GeoJsonSource(
            SOURCE_AIRES,
            GeoJsonOptions()
                .withCluster(true)
                .withClusterRadius(44)
                // Au-delà, les aires d'une même autoroute sont assez espacées
                // pour ne plus se marcher dessus.
                .withClusterMaxZoom(10),
        ),
    )
    style.addSource(GeoJsonSource(SOURCE_POSITION))

    style.addLayer(
        LineLayer("itineraire-trace", SOURCE_ITINERAIRE).withProperties(
            PropertyFactory.lineCap("round"),
            PropertyFactory.lineJoin("round"),
            PropertyFactory.lineColor(
                Expression.switchCase(
                    Expression.get("devant"), Expression.color(couleurs.devant.toCouleur()),
                    Expression.color(couleurs.derriere.toCouleur()),
                ),
            ),
            PropertyFactory.lineWidth(
                Expression.interpolate(
                    Expression.linear(), Expression.zoom(),
                    Expression.stop(6, 2.5f),
                    Expression.stop(9, 4.5f),
                    Expression.stop(13, 7f),
                ),
            ),
        ),
    )

    style.addLayer(
        CircleLayer(COUCHE_GROUPE, SOURCE_AIRES).apply {
            setFilter(Expression.has("point_count"))
            withProperties(
                PropertyFactory.circleColor(couleurs.groupe.toCouleur()),
                PropertyFactory.circleStrokeColor(couleurs.surTrace.toCouleur()),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleRadius(
                    Expression.step(
                        Expression.get("point_count"), Expression.literal(14f),
                        Expression.stop(5, 18f),
                        Expression.stop(15, 22f),
                    ),
                ),
            )
        },
    )

    style.addLayer(
        SymbolLayer("aires-groupe-nombre", SOURCE_AIRES).apply {
            setFilter(Expression.has("point_count"))
            withProperties(
                PropertyFactory.textField(Expression.toString(Expression.get("point_count"))),
                PropertyFactory.textFont(arrayOf("Noto Sans Medium")),
                PropertyFactory.textSize(13f),
                PropertyFactory.textColor(couleurs.surTrace.toCouleur()),
                PropertyFactory.textAllowOverlap(true),
            )
        },
    )

    style.addLayer(
        CircleLayer(COUCHE_PASTILLE, SOURCE_AIRES).apply {
            setFilter(Expression.not(Expression.has("point_count")))
            withProperties(
                PropertyFactory.circleColor(
                    Expression.switchCase(
                        Expression.get("notee"), Expression.color(couleurs.aireNotee.toCouleur()),
                        Expression.get("devant"), Expression.color(couleurs.aireDevant.toCouleur()),
                        Expression.color(couleurs.aireDerriere.toCouleur()),
                    ),
                ),
                PropertyFactory.circleStrokeColor(couleurs.surTrace.toCouleur()),
                PropertyFactory.circleStrokeWidth(2f),
                // Les aires de service, plus grosses, se distinguent des aires
                // de repos sans qu'on ait à lire l'étiquette.
                PropertyFactory.circleRadius(
                    Expression.switchCase(
                        Expression.get("service"), Expression.literal(10f),
                        Expression.literal(7f),
                    ),
                ),
            )
        },
    )

    style.addLayer(
        SymbolLayer("aires-libelle", SOURCE_AIRES).apply {
            setFilter(Expression.not(Expression.has("point_count")))
            setMinZoom(9.5f)
            withProperties(
                PropertyFactory.textField(Expression.get("nom")),
                PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                PropertyFactory.textSize(12f),
                PropertyFactory.textAnchor("left"),
                PropertyFactory.textOffset(arrayOf(1.1f, 0f)),
                PropertyFactory.textMaxWidth(8f),
                PropertyFactory.textColor(couleurs.devant.toCouleur()),
                PropertyFactory.textHaloColor(couleurs.surTrace.toCouleur()),
                PropertyFactory.textHaloWidth(1.4f),
            )
        },
    )

    style.addLayer(
        CircleLayer("position-halo", SOURCE_POSITION).withProperties(
            PropertyFactory.circleColor(couleurs.position.toCouleur()),
            PropertyFactory.circleOpacity(0.25f),
            PropertyFactory.circleRadius(20f),
        ),
    )
    style.addLayer(
        CircleLayer("position-point", SOURCE_POSITION).withProperties(
            PropertyFactory.circleColor(couleurs.position.toCouleur()),
            PropertyFactory.circleStrokeColor(couleurs.surTrace.toCouleur()),
            PropertyFactory.circleStrokeWidth(3f),
            PropertyFactory.circleRadius(8f),
        ),
    )
}

private fun String.toCouleur(): Int = android.graphics.Color.parseColor(this)

