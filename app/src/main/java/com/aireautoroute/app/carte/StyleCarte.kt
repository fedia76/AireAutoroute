package com.aireautoroute.app.carte

/**
 * Construction du style MapLibre du fond cartographique.
 *
 * Le style est bâti ici plutôt que rangé en trois fichiers JSON : seules les
 * couleurs changent d'un habillage à l'autre, et dupliquer trois fois une
 * centaine de lignes de couches serait le meilleur moyen de les voir diverger.
 *
 * ## Ce que contient le fond, et pourquoi
 *
 * Le jeu de tuiles embarqué s'arrête au zoom 8. Ce n'est pas le zoom maximal
 * d'affichage : au-delà, MapLibre redessine la géométrie du niveau 8 à la
 * nouvelle échelle. On perd du détail, jamais de la netteté. D'où les paliers
 * ci-dessous, qui cessent de croître passé le zoom 10 : sans cela, un trait de
 * 2 px à z8 en ferait 30 à z14.
 *
 * Le profil de génération de Protomaps fixe à quel zoom chaque objet entre dans
 * les tuiles. À z8, on dispose donc de :
 *
 * | Objet                          | Entre à |
 * | ------------------------------ | ------- |
 * | autoroutes                     | z3      |
 * | voies rapides (trunk)          | z6      |
 * | routes principales (primary)   | z7      |
 * | pays                           | z5      |
 * | régions                        | z8      |
 * | villes et bourgs               | z7      |
 *
 * Les routes secondaires (z9) et les rues (z14) sont absentes : c'est le parti
 * pris, et c'est ce qui tient dans une trentaine de mégaoctets.
 */
data class PaletteCarte(
    val fond: String,
    val terre: String,
    val eau: String,
    val vegetation: String,
    val urbain: String,
    val frontiere: String,
    val autoroute: String,
    val routePrincipale: String,
    val liseréRoute: String,
    val texte: String,
    val halo: String,
) {
    companion object {
        /** Papier neutre, pour l'habillage Signalétique en clair. */
        val CLAIRE = PaletteCarte(
            fond = "#eef1f4",
            terre = "#f7f8f9",
            eau = "#bcd6ea",
            vegetation = "#e2ebdd",
            urbain = "#e9eaec",
            frontiere = "#c3cad1",
            autoroute = "#cfd6dd",
            routePrincipale = "#dde2e7",
            liseréRoute = "#ffffff",
            texte = "#4a5560",
            halo = "#f7f8f9",
        )

        /** Papier vieilli, pour l'habillage Carnet de route. */
        val SEPIA = PaletteCarte(
            fond = "#efe6d7",
            terre = "#fbf6ee",
            eau = "#cfdbd8",
            vegetation = "#e6e6d0",
            urbain = "#f0e8dc",
            frontiere = "#cbbda6",
            autoroute = "#ded0b8",
            routePrincipale = "#e8ddc9",
            liseréRoute = "#fbf6ee",
            texte = "#6b5947",
            halo = "#fbf6ee",
        )

        /** Tableau de bord de nuit, pour Copilote et le mode sombre. */
        val SOMBRE = PaletteCarte(
            fond = "#0b1016",
            terre = "#121a23",
            eau = "#0d2233",
            vegetation = "#152219",
            urbain = "#18212b",
            frontiere = "#2c3b4a",
            autoroute = "#2b3846",
            routePrincipale = "#222d38",
            liseréRoute = "#0b1016",
            texte = "#8fa1b3",
            halo = "#0b1016",
        )
    }
}

/** Zoom au-delà duquel le jeu de tuiles n'a plus de détail à offrir. */
const val ZOOM_MAX_TUILES = 8

/**
 * Style complet du fond.
 *
 * Quand l'archive manque — fond non généré —, on renvoie un style réduit au seul
 * aplat de fond : la carte reste utilisable, avec le tracé et les aires, simplement
 * sans repères. C'est volontairement silencieux : une carte sans villes vaut mieux
 * qu'un écran d'erreur au milieu d'un trajet. Les glyphes, eux, sont déclarés dès
 * qu'ils sont disponibles : ils servent aux libellés des aires, fond ou pas.
 */
fun styleCarte(emplacement: FondCarte.Emplacement, palette: PaletteCarte): String {
    // Déclarer des glyphes que l'on n'a pas serait pire que de n'en pas déclarer :
    // MapLibre les réclamerait en vain et retiendrait les tuiles qui en dépendent.
    val glyphes = emplacement.motifGlyphes?.let { "\"glyphs\": \"$it\"," }.orEmpty()

    val archive = emplacement.urlArchive
    if (archive == null) {
        return """
        {
          "version": 8,
          $glyphes
          "sources": {},
          "layers": [
            { "id": "fond", "type": "background",
              "paint": { "background-color": "${palette.fond}" } }
          ]
        }
        """.trimIndent()
    }

    return """
    {
      "version": 8,
      $glyphes
      "sources": {
        "fond": {
          "type": "vector",
          "url": "$archive",
          "maxzoom": $ZOOM_MAX_TUILES,
          "attribution": "© OpenStreetMap, Protomaps"
        }
      },
      "layers": [
        { "id": "fond", "type": "background",
          "paint": { "background-color": "${palette.fond}" } },

        { "id": "terre", "type": "fill", "source": "fond", "source-layer": "earth",
          "paint": { "fill-color": "${palette.terre}" } },

        { "id": "vegetation", "type": "fill", "source": "fond", "source-layer": "landcover",
          "paint": { "fill-color": "${palette.vegetation}", "fill-opacity": 0.7 } },

        { "id": "urbain", "type": "fill", "source": "fond", "source-layer": "landuse",
          "paint": { "fill-color": "${palette.urbain}" } },

        { "id": "eau", "type": "fill", "source": "fond", "source-layer": "water",
          "paint": { "fill-color": "${palette.eau}" } },

        { "id": "riviere", "type": "line", "source": "fond", "source-layer": "water",
          "filter": ["in", "kind", "river", "stream"],
          "paint": {
            "line-color": "${palette.eau}",
            "line-width": ["interpolate", ["linear"], ["zoom"], 6, 0.5, 8, 1.2, 10, 2]
          } },

        { "id": "frontieres", "type": "line", "source": "fond", "source-layer": "boundaries",
          "paint": {
            "line-color": "${palette.frontiere}",
            "line-dasharray": [3, 2],
            "line-width": ["interpolate", ["linear"], ["zoom"], 3, 0.6, 8, 1.2, 10, 1.6]
          } },

        { "id": "routes_principales", "type": "line", "source": "fond", "source-layer": "roads",
          "filter": ["all", ["==", "kind", "major_road"], ["!has", "is_tunnel"]],
          "layout": { "line-cap": "round", "line-join": "round" },
          "paint": {
            "line-color": "${palette.routePrincipale}",
            "line-width": ["interpolate", ["linear"], ["zoom"], 6, 0.6, 8, 1.6, 10, 2.6, 14, 3.2]
          } },

        { "id": "autoroutes_liseré", "type": "line", "source": "fond", "source-layer": "roads",
          "filter": ["all", ["==", "kind", "highway"], ["!has", "is_tunnel"]],
          "layout": { "line-cap": "round", "line-join": "round" },
          "paint": {
            "line-color": "${palette.liseréRoute}",
            "line-width": ["interpolate", ["linear"], ["zoom"], 5, 1.6, 8, 3.6, 10, 5.4, 14, 6.4]
          } },

        { "id": "autoroutes", "type": "line", "source": "fond", "source-layer": "roads",
          "filter": ["all", ["==", "kind", "highway"], ["!has", "is_tunnel"]],
          "layout": { "line-cap": "round", "line-join": "round" },
          "paint": {
            "line-color": "${palette.autoroute}",
            "line-width": ["interpolate", ["linear"], ["zoom"], 5, 0.8, 8, 2.2, 10, 3.4, 14, 4]
          } },

        { "id": "libelle_ville", "type": "symbol", "source": "fond", "source-layer": "places",
          "filter": ["==", "kind", "locality"],
          "minzoom": 5,
          "layout": {
            "text-field": ["get", "name"],
            "text-font": ["Noto Sans Regular"],
            "text-max-width": 7,
            "text-padding": 6,
            "symbol-sort-key": ["get", "min_zoom"],
            "text-size": [
              "interpolate", ["linear"], ["zoom"],
              5, ["case", [">=", ["get", "population_rank"], 12], 12, 9],
              8, ["case", [">=", ["get", "population_rank"], 12], 15, 11],
              11, ["case", [">=", ["get", "population_rank"], 12], 17, 13]
            ]
          },
          "paint": {
            "text-color": "${palette.texte}",
            "text-halo-color": "${palette.halo}",
            "text-halo-width": 1.4
          } },

        { "id": "libelle_region", "type": "symbol", "source": "fond", "source-layer": "places",
          "filter": ["==", "kind", "region"],
          "minzoom": 6, "maxzoom": 9,
          "layout": {
            "text-field": ["get", "name"],
            "text-font": ["Noto Sans Medium"],
            "text-transform": "uppercase",
            "text-letter-spacing": 0.12,
            "text-size": 11
          },
          "paint": {
            "text-color": "${palette.texte}",
            "text-halo-color": "${palette.halo}",
            "text-halo-width": 1.2,
            "text-opacity": 0.7
          } },

        { "id": "libelle_pays", "type": "symbol", "source": "fond", "source-layer": "places",
          "filter": ["==", "kind", "country"],
          "maxzoom": 6,
          "layout": {
            "text-field": ["get", "name"],
            "text-font": ["Noto Sans Medium"],
            "text-transform": "uppercase",
            "text-letter-spacing": 0.15,
            "text-size": 13
          },
          "paint": {
            "text-color": "${palette.texte}",
            "text-halo-color": "${palette.halo}",
            "text-halo-width": 1.4
          } }
      ]
    }
    """.trimIndent()
}
