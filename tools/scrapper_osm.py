#!/usr/bin/env python3
"""Extrait d'OpenStreetMap les aires d'autoroute françaises et leurs équipements.

Source : OpenStreetMap, sous licence ODbL, interrogé via l'API Overpass.

Dans OSM, une aire est une zone (`highway=services` ou `highway=rest_area`) et ses équipements
sont des objets distincts posés à l'intérieur : les toilettes, la station-service, l'aire de jeux,
les commerces. C'est de là que viendront les équipements et les enseignes de l'application.

Le script n'écrit qu'un fichier brut, `donnees/sources/osm_aires.json` ; le rattachement de ces
objets aux aires du référentiel se fait ensuite, hors ligne, dans la chaîne d'import.

Usage :
    python3 tools/scrapper_osm.py              # France entière
    python3 tools/scrapper_osm.py --autoroutes A13,A10   # limité à quelques axes (mise au point)
"""

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

RACINE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SORTIE = os.path.join(RACINE, "donnees", "sources", "osm_aires.json")
RAPPORT = os.path.join(RACINE, "donnees", "rapport_osm.md")

# Instances publiques, essayées dans l'ordre. Ce sont des services bénévoles : on s'annonce,
# on espace les requêtes et on ne demande que ce dont on a besoin.
SERVEURS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass.osm.ch/api/interpreter",
]

AGENT = "AireAutoroute/0.1 (import de donnees ; https://github.com/fedia76/AireAutoroute)"

DELAI_ENTRE_REQUETES_S = 3
LOT_AIRES = 60
RAYON_EQUIPEMENTS_M = 250

# Ce qu'on va chercher autour de chaque aire, et ce qu'on en fera dans l'application.
FILTRES_EQUIPEMENTS = [
    '["amenity"~"^(toilets|fuel|fast_food|restaurant|cafe|ice_cream)$"]',
    '["leisure"="playground"]',
    '["shop"~"^(convenience|supermarket|bakery|kiosk)$"]',
]

# Tags conservés : le reste alourdirait le fichier sans rien apporter.
TAGS_UTILES = {
    "name", "operator", "brand", "brand:wikidata", "ref",
    "amenity", "leisure", "shop", "highway",
    "changing_table", "changing_table:location",
    "indoor", "covered", "min_age", "max_age",
    "wheelchair", "opening_hours", "access", "fee",
    "toilets:wheelchair", "playground",
}


def interroger(requete: str, delai_s: int = 600) -> dict:
    """Envoie une requête Overpass, en passant au serveur suivant en cas de refus."""
    donnees = urllib.parse.urlencode({"data": requete}).encode("utf-8")
    dernier_echec = None

    for serveur in SERVEURS:
        demande = urllib.request.Request(
            serveur,
            data=donnees,
            headers={"User-Agent": AGENT, "Accept": "application/json"},
        )
        try:
            with urllib.request.urlopen(demande, timeout=delai_s) as reponse:
                return json.loads(reponse.read().decode("utf-8"))
        except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError, ValueError) as echec:
            dernier_echec = f"{serveur} : {echec}"
            print(f"  échec sur {serveur} ({echec}), essai du serveur suivant", file=sys.stderr)
            time.sleep(DELAI_ENTRE_REQUETES_S)

    raise RuntimeError(f"aucun serveur Overpass n'a répondu — dernier échec : {dernier_echec}")


def requete_aires(autoroutes: list[str] | None) -> str:
    if autoroutes:
        # Mise au point : on se limite aux aires proches des autoroutes demandées.
        selection = "|".join(autoroutes)
        return f"""
[out:json][timeout:600];
area["ISO3166-1"="FR"][admin_level=2]->.fr;
way["highway"="motorway"]["ref"~"^({selection})$"](area.fr)->.axes;
(
  way["highway"~"^(services|rest_area)$"](around.axes:800);
  relation["highway"~"^(services|rest_area)$"](around.axes:800);
  node["highway"~"^(services|rest_area)$"](around.axes:800);
);
out geom tags;
"""
    return """
[out:json][timeout:900];
area["ISO3166-1"="FR"][admin_level=2]->.fr;
(
  way["highway"~"^(services|rest_area)$"](area.fr);
  relation["highway"~"^(services|rest_area)$"](area.fr);
  node["highway"~"^(services|rest_area)$"](area.fr);
);
out geom tags;
"""


def centre_de(element: dict) -> tuple[float, float] | None:
    """Position d'un objet : ses coordonnées s'il est un nœud, le centre de sa géométrie sinon."""
    if element.get("type") == "node":
        return element.get("lat"), element.get("lon")
    if "center" in element:
        return element["center"]["lat"], element["center"]["lon"]
    points = element.get("geometry") or []
    points = [p for p in points if p.get("lat") is not None]
    if not points:
        return None
    return (
        sum(p["lat"] for p in points) / len(points),
        sum(p["lon"] for p in points) / len(points),
    )


def tags_retenus(element: dict) -> dict:
    return {c: v for c, v in (element.get("tags") or {}).items() if c in TAGS_UTILES}


def extraire_aires(autoroutes: list[str] | None) -> list[dict]:
    print("Extraction des aires…")
    reponse = interroger(requete_aires(autoroutes))
    aires = []
    for element in reponse.get("elements", []):
        centre = centre_de(element)
        if centre is None or centre[0] is None:
            continue
        contour = [
            [round(p["lat"], 6), round(p["lon"], 6)]
            for p in (element.get("geometry") or [])
            if p.get("lat") is not None
        ]
        aires.append({
            "osm": f"{element['type']}/{element['id']}",
            "lat": round(centre[0], 6),
            "lon": round(centre[1], 6),
            "contour": contour,
            "tags": tags_retenus(element),
        })
    print(f"  {len(aires)} aires trouvées")
    return aires


def extraire_equipements(aires: list[dict]) -> list[dict]:
    """Objets d'intérêt situés à proximité des aires, interrogés par lots."""
    equipements: dict[str, dict] = {}
    lots = [aires[i:i + LOT_AIRES] for i in range(0, len(aires), LOT_AIRES)]
    print(f"Extraction des équipements en {len(lots)} lots…")

    for numero, lot in enumerate(lots, start=1):
        coordonnees = ",".join(f"{a['lat']},{a['lon']}" for a in lot)
        filtres = "\n  ".join(
            f"nwr(around:{RAYON_EQUIPEMENTS_M},{coordonnees}){filtre};"
            for filtre in FILTRES_EQUIPEMENTS
        )
        requete = f"[out:json][timeout:300];\n(\n  {filtres}\n);\nout center tags;"

        reponse = interroger(requete, delai_s=300)
        nouveaux = 0
        for element in reponse.get("elements", []):
            centre = centre_de(element)
            if centre is None or centre[0] is None:
                continue
            cle = f"{element['type']}/{element['id']}"
            if cle in equipements:
                continue
            equipements[cle] = {
                "osm": cle,
                "lat": round(centre[0], 6),
                "lon": round(centre[1], 6),
                "tags": tags_retenus(element),
            }
            nouveaux += 1
        print(f"  lot {numero}/{len(lots)} : {nouveaux} objets")
        time.sleep(DELAI_ENTRE_REQUETES_S)

    return list(equipements.values())


def statistiques(aires: list[dict], equipements: list[dict]) -> list[str]:
    def compte(condition) -> int:
        return sum(1 for e in equipements if condition(e.get("tags", {})))

    types_aires = {}
    for aire in aires:
        types_aires.setdefault(aire["tags"].get("highway", "?"), 0)
        types_aires[aire["tags"].get("highway", "?")] += 1

    nommees = sum(1 for a in aires if a["tags"].get("name"))
    avec_contour = sum(1 for a in aires if len(a["contour"]) > 2)
    marques = {
        (e["tags"].get("brand") or e["tags"].get("name"))
        for e in equipements
        if e["tags"].get("brand") or e["tags"].get("name")
    }

    return [
        f"aires : {len(aires)} ({types_aires})",
        f"  dont nommées : {nommees}",
        f"  dont avec un contour exploitable : {avec_contour}",
        f"équipements : {len(equipements)}",
        f"  toilettes : {compte(lambda t: t.get('amenity') == 'toilets')}",
        f"    dont table à langer renseignée : "
        f"{compte(lambda t: t.get('amenity') == 'toilets' and t.get('changing_table'))}",
        f"  stations-service : {compte(lambda t: t.get('amenity') == 'fuel')}",
        f"  aires de jeux : {compte(lambda t: t.get('leisure') == 'playground')}",
        f"    dont couvertes ou intérieures : "
        f"{compte(lambda t: t.get('leisure') == 'playground' and (t.get('indoor') == 'yes' or t.get('covered') == 'yes'))}",
        f"    dont avec âges renseignés : "
        f"{compte(lambda t: t.get('leisure') == 'playground' and (t.get('min_age') or t.get('max_age')))}",
        f"  restauration : "
        f"{compte(lambda t: t.get('amenity') in {'fast_food', 'restaurant', 'cafe', 'ice_cream'})}",
        f"  boutiques : {compte(lambda t: bool(t.get('shop')))}",
        f"marques distinctes relevées : {len(marques)}",
    ]


def main() -> int:
    analyseur = argparse.ArgumentParser(description=__doc__)
    analyseur.add_argument(
        "--autoroutes",
        help="liste d'autoroutes séparées par des virgules, pour une extraction partielle",
    )
    arguments = analyseur.parse_args()
    autoroutes = [a.strip() for a in arguments.autoroutes.split(",")] if arguments.autoroutes else None

    aires = extraire_aires(autoroutes)
    if not aires:
        print("aucune aire extraite : rien n'est écrit", file=sys.stderr)
        return 1

    equipements = extraire_equipements(aires)

    os.makedirs(os.path.dirname(SORTIE), exist_ok=True)
    with open(SORTIE, "w", encoding="utf-8") as fichier:
        json.dump(
            {"aires": aires, "equipements": equipements},
            fichier,
            ensure_ascii=False,
            indent=1,
            sort_keys=True,
        )
        fichier.write("\n")

    lignes = statistiques(aires, equipements)
    with open(RAPPORT, "w", encoding="utf-8") as fichier:
        fichier.write("# Ce qu'OpenStreetMap contient\n\n")
        fichier.write("Relevé par `python3 tools/scrapper_osm.py`. Données © les contributeurs\n")
        fichier.write("OpenStreetMap, sous licence ODbL.\n\n")
        if autoroutes:
            fichier.write(f"Extraction partielle, limitée à : {', '.join(autoroutes)}.\n\n")
        fichier.write("\n".join(f"- {ligne.strip()}" for ligne in lignes))
        fichier.write("\n\nCes objets ne sont pas encore rattachés aux aires du référentiel :\n")
        fichier.write("c'est l'étape suivante.\n")

    print()
    for ligne in lignes:
        print(ligne)
    print(f"\nÉcrit dans {SORTIE} ({os.path.getsize(SORTIE) / 1_048_576:.1f} Mo)")
    print(f"Rapport : {RAPPORT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
