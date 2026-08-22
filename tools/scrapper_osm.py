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
import math
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

RACINE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SORTIE = os.path.join(RACINE, "donnees", "sources", "osm_aires.json")
RAPPORT = os.path.join(RACINE, "donnees", "rapport_osm.md")

# Instances publiques, essayées dans l'ordre. Ce sont des services bénévoles : on s'annonce,
# on espace les requêtes et on ne demande que ce dont on a besoin.
SERVEURS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
    "https://overpass.osm.jp/api/interpreter",
]

AGENT = "AireAutoroute/0.1 (import de donnees ; https://github.com/fedia76/AireAutoroute)"

# Les instances comptent les requêtes par adresse IP, et celles des runners GitHub sont
# partagées : espacer les requêtes coûte quelques minutes et évite les refus.
DELAI_ENTRE_REQUETES_S = 15
DELAI_REPONSE_S = 240
ATTENTE_APRES_REFUS_S = 45
ESSAIS_PAR_SERVEUR = 2
ATTENTE_AVANT_REPRISE_S = 90
LOT_AIRES = 150
RAYON_EQUIPEMENTS_M = 250

# Boîte englobant la France métropolitaine. Beaucoup plus rapide qu'un filtre sur le polygone du
# pays, qu'Overpass met un temps considérable à appliquer. Les quelques aires frontalières
# récoltées au passage seront écartées faute de tracé français à proximité.
BOITE_FRANCE = "41.3,-5.2,51.1,9.6"

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


def interroger(requete: str, minimum: int = 0, delai_s: int = DELAI_REPONSE_S) -> dict:
    """Envoie une requête Overpass, en réessayant puis en changeant de serveur.

    `minimum` est le nombre d'éléments en dessous duquel la réponse est jugée douteuse. Une
    instance qui ne détient pas la zone interrogée répond en effet poliment, et une liste vide
    se lit alors comme « il n'y a rien ici » au lieu de « ce serveur ne sait pas ».
    """
    donnees = urllib.parse.urlencode({"data": requete}).encode("utf-8")
    dernier_echec = None

    for serveur in SERVEURS:
        for essai in range(1, ESSAIS_PAR_SERVEUR + 1):
            demande = urllib.request.Request(
                serveur,
                data=donnees,
                headers={"User-Agent": AGENT, "Accept": "application/json"},
            )
            try:
                with urllib.request.urlopen(demande, timeout=delai_s) as reponse:
                    resultat = json.loads(reponse.read().decode("utf-8"))
                # Overpass ne renvoie un code d'erreur que pour les requêtes mal écrites : un
                # délai dépassé ou une mémoire saturée arrivent dans un HTTP 200, signalés par
                # « remark ». Sans ce contrôle, l'échec se lit comme un résultat vide.
                remarque = resultat.get("remark")
                if remarque:
                    raise ValueError(f"le serveur signale : {remarque}")
                if len(resultat.get("elements", [])) < minimum:
                    raise ValueError("réponse vide, ce serveur ne couvre sans doute pas la zone")
                return resultat
            except (urllib.error.URLError, urllib.error.HTTPError,
                    TimeoutError, ValueError) as echec:
                dernier_echec = f"{serveur} : {echec}"
                reessayable = (
                    isinstance(echec, urllib.error.HTTPError)
                    and echec.code in {429, 500, 502, 503, 504}
                    and essai < ESSAIS_PAR_SERVEUR
                )
                if reessayable:
                    print(f"  {serveur} refuse ({echec}), nouvel essai dans "
                          f"{ATTENTE_APRES_REFUS_S} s", file=sys.stderr, flush=True)
                    time.sleep(ATTENTE_APRES_REFUS_S)
                    continue
                print(f"  échec sur {serveur} ({echec}), essai du serveur suivant",
                      file=sys.stderr, flush=True)
                time.sleep(DELAI_ENTRE_REQUETES_S)
                break

    raise RuntimeError(f"aucun serveur Overpass n'a répondu — dernier échec : {dernier_echec}")


# Le relevé porte toujours sur la France entière : filtrer côté Overpass supposerait de deviner
# comment les autoroutes y sont nommées — le tag `ref` s'écrit « A 13 » avec une espace, et pas
# toujours. Le tri par autoroute se fait donc ensuite, sur nos propres tracés, qui font foi.
# Deux valeurs exactes plutôt qu'une expression régulière : Overpass indexe les couples
# clé=valeur, et `highway~"^(services|rest_area)$"` lui interdit cet index — il doit alors
# parcourir tous les objets « highway » de la boîte, c'est-à-dire l'essentiel du réseau
# routier de six pays. C'est ce qui faisait dépasser le délai de 180 s.
REQUETE_AIRES = f"""
[out:json][timeout:180][bbox:{BOITE_FRANCE}];
(
  nwr["highway"="services"];
  nwr["highway"="rest_area"];
);
out center tags;
"""


# Large à dessein : le rattachement fin viendra ensuite. Une grande aire de service s'étend loin
# de l'axe, et les tracés du bornage ne suivent qu'une des deux chaussées.
DISTANCE_FILTRE_KM = 1.5

KM_PAR_DEGRE = 111.32


def _boite(trace, marge_km: float) -> tuple[float, float, float, float]:
    """Rectangle englobant un tracé, élargi de la marge, pour écarter vite les aires lointaines."""
    lats = [b.lat for b in trace.bornes]
    lons = [b.lon for b in trace.bornes]
    marge_lat = marge_km / KM_PAR_DEGRE
    milieu = math.radians((min(lats) + max(lats)) / 2)
    marge_lon = marge_km / (KM_PAR_DEGRE * max(0.1, math.cos(milieu)))
    return (min(lats) - marge_lat, max(lats) + marge_lat,
            min(lons) - marge_lon, max(lons) + marge_lon)


def filtrer_par_autoroute(
    aires: list[dict],
    autoroutes: list[str] | None,
    distance_max_km: float = DISTANCE_FILTRE_KM,
) -> list[dict]:
    """Ne garde que les aires longeant nos tracés, et note sur chacune l'axe et le PK trouvés.

    La boîte englobante récoltée chez Overpass déborde sur l'Espagne, l'Italie, l'Allemagne et le
    Benelux : c'est ici que ces aires-là disparaissent, faute de tracé français à proximité. Le
    tri se fait sur nos propres tracés parce qu'eux font foi — le tag `ref` d'OSM s'écrit tantôt
    « A13 », tantôt « A 13 », et manque parfois.
    """
    from importlib import import_module

    bornes = import_module("import.bornes")
    traces = bornes.lire_traces(os.path.join(RACINE, "donnees", "sources", "bornes2025.csv"))

    if autoroutes:
        inconnues = [a for a in autoroutes if a not in traces]
        if inconnues:
            print(f"  autoroutes inconnues du bornage, ignorées : {inconnues}", file=sys.stderr)
        traces = {n: t for n, t in traces.items() if n in autoroutes}

    boites = {n: _boite(t, distance_max_km) for n, t in traces.items()}

    retenus = []
    for aire in aires:
        lat, lon = aire["lat"], aire["lon"]
        meilleur = None
        for numero, trace in traces.items():
            lat_min, lat_max, lon_min, lon_max = boites[numero]
            if not (lat_min <= lat <= lat_max and lon_min <= lon <= lon_max):
                continue
            distance, pk = bornes.projeter_sur_trace(trace, lat, lon)
            if distance <= distance_max_km and (meilleur is None or distance < meilleur[1]):
                meilleur = (numero, distance, pk)
        if meilleur is not None:
            numero, distance, pk = meilleur
            retenus.append({**aire,
                            "autoroute": numero,
                            "pk": round(pk, 3),
                            "distance_km": round(distance, 3)})
    return retenus


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
    print("Extraction des aires (France entière)…", flush=True)
    reponse = interroger(REQUETE_AIRES, minimum=1)
    elements = reponse.get("elements", [])
    if not elements:
        print("  le serveur a répondu sans aucun élément ; requête envoyée :", file=sys.stderr)
        print(REQUETE_AIRES, file=sys.stderr)
    aires = []
    for element in reponse.get("elements", []):
        centre = centre_de(element)
        if centre is None or centre[0] is None:
            continue
        aires.append({
            "osm": f"{element['type']}/{element['id']}",
            "lat": round(centre[0], 6),
            "lon": round(centre[1], 6),
            "tags": tags_retenus(element),
        })
    print(f"  {len(aires)} aires trouvées dans la boîte englobante", flush=True)

    aires = filtrer_par_autoroute(aires, autoroutes)
    portee = ", ".join(autoroutes) if autoroutes else "nos autoroutes"
    print(f"  {len(aires)} retenues le long de {portee}", flush=True)
    return aires


def requete_equipements(lot: list[dict]) -> str:
    """Requête relevant les équipements autour d'un lot d'aires, désignées par leur identifiant.

    Attention au piège : `around:rayon,lat,lon,lat,lon,…` ne veut *pas* dire « autour de chacun
    de ces points » mais « autour de la ligne brisée qui les joint ». Sur un lot d'aires
    dispersées dans toute la France, ce couloir traverse le pays, la requête devient
    ingérable et Overpass la refuse — en répondant, ce qui achève de tromper, un résultat vide.
    On désigne donc les aires par leur identifiant OSM et on demande `around.aires`, qui lui
    travaille bien objet par objet.
    """
    par_type: dict[str, list[str]] = {}
    for aire in lot:
        type_osm, identifiant = aire["osm"].split("/")
        par_type.setdefault(type_osm, []).append(identifiant)

    ensemble = "\n  ".join(
        f"{type_osm}(id:{','.join(identifiants)});"
        for type_osm, identifiants in sorted(par_type.items())
    )
    filtres = "\n  ".join(
        f"nwr(around.aires:{RAYON_EQUIPEMENTS_M}){filtre};" for filtre in FILTRES_EQUIPEMENTS
    )
    return (
        f"[out:json][timeout:180];\n"
        f"(\n  {ensemble}\n)->.aires;\n"
        f"(\n  {filtres}\n);\nout center tags;"
    )


def extraire_equipements(aires: list[dict]) -> tuple[list[dict], int]:
    """Objets d'intérêt situés à proximité des aires, interrogés par lots.

    Renvoie les objets relevés et le nombre de lots perdus : Overpass est un service bénévole
    qui refuse parfois de répondre, et un lot manqué ne doit pas coûter tout le relevé.
    """
    equipements: dict[str, dict] = {}
    lots = [aires[i:i + LOT_AIRES] for i in range(0, len(aires), LOT_AIRES)]
    print(f"Extraction des équipements en {len(lots)} lots…", flush=True)

    restants = list(enumerate(lots, start=1))
    for tentative in (1, 2):
        if not restants:
            break
        if tentative > 1:
            print(f"Reprise des {len(restants)} lot(s) manqués, après une pause de "
                  f"{ATTENTE_AVANT_REPRISE_S} s…", flush=True)
            time.sleep(ATTENTE_AVANT_REPRISE_S)

        echoues = []
        for numero, lot in restants:
            # Un lot couvre plus de cent aires : il en ressort toujours quelque chose. Une
            # réponse vide dénonce le serveur, pas le terrain.
            try:
                reponse = interroger(requete_equipements(lot), minimum=1)
            except RuntimeError as echec:
                echoues.append((numero, lot))
                print(f"  lot {numero}/{len(lots)} : perdu ({echec})", file=sys.stderr, flush=True)
                continue

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
            print(f"  lot {numero}/{len(lots)} : {nouveaux} objets", flush=True)
            time.sleep(DELAI_ENTRE_REQUETES_S)

        restants = echoues

    return list(equipements.values()), len(restants)


def statistiques(aires: list[dict], equipements: list[dict]) -> list[str]:
    def compte(condition) -> int:
        return sum(1 for e in equipements if condition(e.get("tags", {})))

    types_aires = {}
    for aire in aires:
        types_aires.setdefault(aire["tags"].get("highway", "?"), 0)
        types_aires[aire["tags"].get("highway", "?")] += 1

    nommees = sum(1 for a in aires if a["tags"].get("name"))
    marques = {
        (e["tags"].get("brand") or e["tags"].get("name"))
        for e in equipements
        if e["tags"].get("brand") or e["tags"].get("name")
    }

    axes = {a["autoroute"] for a in aires if a.get("autoroute")}

    return [
        f"aires : {len(aires)} ({types_aires})",
        f"  dont nommées : {nommees}",
        f"  réparties sur {len(axes)} autoroutes",
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

    equipements, lots_perdus = extraire_equipements(aires)

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
        if lots_perdus:
            fichier.write(
                f"**Relevé incomplet** : {lots_perdus} lot(s) d'équipements n'ont pas pu être\n"
                "interrogés. Les chiffres ci-dessous sont donc sous-estimés.\n\n"
            )
        fichier.write("\n".join(f"- {ligne.strip()}" for ligne in lignes))
        fichier.write(
            "\n\nChaque aire porte l'autoroute et le point kilométrique déduits de nos tracés, mais\n"
            "n'est pas encore appariée à une aire du référentiel — ni à un sens de circulation :\n"
            "c'est l'étape suivante.\n"
        )

    print()
    for ligne in lignes:
        print(ligne)
    print(f"\nÉcrit dans {SORTIE} ({os.path.getsize(SORTIE) / 1_048_576:.1f} Mo)")
    print(f"Rapport : {RAPPORT}")

    if lots_perdus:
        print(
            f"\n{lots_perdus} lot(s) d'équipements perdus : le relevé est incomplet.",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
