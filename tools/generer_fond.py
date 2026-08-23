#!/usr/bin/env python3
"""
Génère le fond cartographique embarqué dans l'application.

Extrait d'une archive PMTiles distante le sous-ensemble qui couvre la France
jusqu'à un niveau de zoom donné, et l'écrit dans `app/src/main/assets/fond/`.
Aucune tuile n'est téléchargée à l'exécution de l'application : ce script est
rejoué à la demande, comme `generer_donnees.py`, et son résultat est versionné.

Le zoom maximal du jeu de tuiles n'est pas le zoom maximal d'affichage :
MapLibre survole les niveaux supérieurs en redessinant la géométrie du dernier
niveau disponible. On perd du détail, pas de la netteté.

Usage :
    python3 tools/generer_fond.py --estimer     # pèse sans rien télécharger
    python3 tools/generer_fond.py               # génère l'asset

Options :
    --url=...       archive source (défaut : dernier build Protomaps connu)
    --bbox=O,S,E,N  emprise (défaut : France métropolitaine + Corse)
    --maxzoom=N     niveau de zoom maximal embarqué (défaut : 8)
    --sortie=...    chemin du fichier produit
    --estimer       n'écrit rien, affiche seulement le poids par niveau
    --ua=...        User-Agent, si l'hébergeur refuse celui par défaut

Dépendance :  pip install pmtiles
"""

import math
import os
import sys
import urllib.error
import urllib.request

try:
    from pmtiles.reader import deserialize_header, deserialize_directory, tileid_to_zxy
    from pmtiles.writer import Writer
    from pmtiles.tile import Compression, TileType
except ImportError:
    sys.exit("Dépendance manquante :  pip install pmtiles")

# France métropolitaine, Corse comprise, avec un peu de marge.
BBOX_DEFAUT = (-5.3, 41.3, 9.7, 51.2)
MAXZOOM_DEFAUT = 8
URL_DEFAUT = "https://build.protomaps.com/20260823.pmtiles"
SORTIE_DEFAUT = "app/src/main/assets/fond/france-z8.pmtiles"

# Les hébergeurs derrière Cloudflare refusent couramment l'agent par défaut de
# urllib. On se présente comme un client ordinaire.
AGENT_DEFAUT = (
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"
)


def premier_id(z):
    """Identifiant de la première tuile du niveau de zoom z."""
    return ((1 << (z * 2)) - 1) // 3


def source_http(url, agent=AGENT_DEFAUT):
    """Renvoie une fonction (offset, longueur) -> octets, via HTTP Range."""
    def lire(offset, longueur):
        requete = urllib.request.Request(
            url,
            headers={
                "Range": f"bytes={offset}-{offset + longueur - 1}",
                "User-Agent": agent,
                "Accept": "*/*",
            },
        )
        try:
            with urllib.request.urlopen(requete, timeout=120) as reponse:
                donnees = reponse.read()
        except urllib.error.HTTPError as erreur:
            corps = ""
            try:
                corps = erreur.read()[:400].decode("utf-8", "replace").strip()
            except Exception:
                pass
            raise SystemExit(
                f"HTTP {erreur.code} sur l'archive."
                + (f"\nRéponse du serveur : {corps}" if corps else "")
                + "\n\nVérifie que le build visé par --url est toujours publié "
                "(la liste est sur maps.protomaps.com/builds/), et réessaie au "
                "besoin avec --ua='...'."
            ) from None
        except urllib.error.URLError as erreur:
            raise SystemExit(f"Archive injoignable : {erreur.reason}") from None
        if len(donnees) != longueur:
            raise SystemExit(
                f"Le serveur a renvoyé {len(donnees)} octets au lieu de {longueur} : "
                "il ignore les requêtes Range, l'extraction est impossible."
            )
        return donnees
    return lire


def plage_tuiles(bbox, z):
    """Bornes x/y des tuiles couvrant la bbox à ce zoom (schéma XYZ)."""
    ouest, sud, est, nord = bbox
    cote = 1 << z

    def x(lon):
        return min(cote - 1, max(0, int((lon + 180.0) / 360.0 * cote)))

    def y(lat):
        lat = max(-85.05112878, min(85.05112878, lat))
        rad = math.radians(lat)
        proportion = (1.0 - math.log(math.tan(rad) + 1 / math.cos(rad)) / math.pi) / 2.0
        return min(cote - 1, max(0, int(proportion * cote)))

    # y croît vers le sud : le nord donne la borne basse.
    return x(ouest), x(est), y(nord), y(sud)


def recenser(lire, bbox, maxzoom):
    """
    Parcourt l'index de l'archive et retient les tuiles de l'emprise.

    Ne télécharge que les répertoires d'index, jamais les tuiles elles-mêmes :
    c'est ce qui permet de peser un extrait avant de décider de le produire.
    """
    entete = deserialize_header(lire(0, 127))
    limite = premier_id(maxzoom + 1)
    plages = {z: plage_tuiles(bbox, z) for z in range(maxzoom + 1)}

    retenues = []                      # (tile_id, offset, longueur)
    octets = {z: 0 for z in range(maxzoom + 1)}
    tuiles = {z: 0 for z in range(maxzoom + 1)}
    # Une même tuile peut être partagée par plusieurs identifiants (océan,
    # forêt) : l'extrait n'en garde qu'une copie, on ne la compte qu'une fois.
    blocs_vus = set()

    def parcourir(offset, longueur):
        for entree in deserialize_directory(lire(offset, longueur)):
            # Les entrées sont triées : au-delà de la limite, tout le reste l'est.
            if entree.tile_id >= limite:
                return
            if entree.run_length == 0:
                parcourir(entete["leaf_directory_offset"] + entree.offset, entree.length)
                continue
            for i in range(entree.run_length):
                identifiant = entree.tile_id + i
                if identifiant >= limite:
                    break
                z, x, y = tileid_to_zxy(identifiant)
                x_min, x_max, y_min, y_max = plages[z]
                if x_min <= x <= x_max and y_min <= y <= y_max:
                    tuiles[z] += 1
                    retenues.append((identifiant, entree.offset, entree.length))
                    if entree.offset not in blocs_vus:
                        blocs_vus.add(entree.offset)
                        octets[z] += entree.length

    parcourir(entete["root_offset"], entete["root_length"])
    return entete, retenues, octets, tuiles


def afficher_poids(octets, tuiles):
    print(f"{'zoom':>5} {'tuiles':>10} {'niveau':>12} {'cumulé':>12}")
    print("-" * 42)
    cumul = 0
    for z in sorted(octets):
        cumul += octets[z]
        print(f"{z:>5} {tuiles[z]:>10} {octets[z] / 1e6:>10.2f} Mo {cumul / 1e6:>10.2f} Mo")
    print("-" * 42)
    return cumul


def extraire(lire, entete, retenues, bbox, sortie):
    """Télécharge les tuiles retenues et écrit l'archive de sortie."""
    os.makedirs(os.path.dirname(sortie) or ".", exist_ok=True)
    # Plusieurs identifiants peuvent viser le même bloc : on ne le télécharge
    # qu'une fois, le writer se chargera de refaire les séries.
    cache = {}
    total = len(retenues)

    with open(sortie, "wb") as fichier:
        redacteur = Writer(fichier)
        for rang, (identifiant, offset, longueur) in enumerate(sorted(retenues), 1):
            if offset not in cache:
                cache[offset] = lire(entete["tile_data_offset"] + offset, longueur)
                if rang % 20 == 0 or rang == total:
                    print(f"  {rang}/{total} tuiles", end="\r", flush=True)
            redacteur.write_tile(identifiant, cache[offset])

        ouest, sud, est, nord = bbox
        redacteur.finalize(
            {
                "tile_type": entete.get("tile_type", TileType.MVT),
                "tile_compression": entete.get("tile_compression", Compression.GZIP),
                "min_lon_e7": int(ouest * 1e7),
                "min_lat_e7": int(sud * 1e7),
                "max_lon_e7": int(est * 1e7),
                "max_lat_e7": int(nord * 1e7),
                "center_lon_e7": int((ouest + est) / 2 * 1e7),
                "center_lat_e7": int((sud + nord) / 2 * 1e7),
                "center_zoom": 6,
            },
            metadonnees(lire, entete),
        )
    print()


def metadonnees(lire, entete):
    """Reprend les métadonnées de l'archive source (elles décrivent le schéma)."""
    import gzip
    import json

    longueur = entete.get("metadata_length", 0)
    if not longueur:
        return {"name": "fond France"}
    brut = lire(entete["metadata_offset"], longueur)
    if entete.get("internal_compression") == Compression.GZIP:
        brut = gzip.decompress(brut)
    return json.loads(brut)


def main():
    url = URL_DEFAUT
    bbox = BBOX_DEFAUT
    maxzoom = MAXZOOM_DEFAUT
    sortie = SORTIE_DEFAUT
    agent = AGENT_DEFAUT
    estimer = False

    for argument in sys.argv[1:]:
        if argument.startswith("--url="):
            url = argument.split("=", 1)[1]
        elif argument.startswith("--bbox="):
            bbox = tuple(float(v) for v in argument.split("=", 1)[1].split(","))
        elif argument.startswith("--maxzoom="):
            maxzoom = int(argument.split("=", 1)[1])
        elif argument.startswith("--sortie="):
            sortie = argument.split("=", 1)[1]
        elif argument.startswith("--ua="):
            agent = argument.split("=", 1)[1]
        elif argument == "--estimer":
            estimer = True
        else:
            sys.exit(__doc__)

    lire = source_http(url, agent) if url.startswith("http") else source_fichier(url)

    print(f"archive  : {url}")
    print(f"emprise  : {bbox[0]},{bbox[1]},{bbox[2]},{bbox[3]}")
    print(f"zoom max : {maxzoom}\n")

    entete, retenues, octets, tuiles = recenser(lire, bbox, maxzoom)
    cumul = afficher_poids(octets, tuiles)

    if estimer:
        print("\nEstimation seule : rien n'a été téléchargé ni écrit.")
        return

    print(f"\nTéléchargement de {len(retenues)} tuiles ({cumul / 1e6:.1f} Mo)…")
    extraire(lire, entete, retenues, bbox, sortie)
    print(f"Écrit : {sortie}  ({os.path.getsize(sortie) / 1e6:.1f} Mo)")


def source_fichier(chemin):
    """Variante locale, pour rejouer l'extraction depuis une archive du disque."""
    def lire(offset, longueur):
        with open(chemin, "rb") as fichier:
            fichier.seek(offset)
            return fichier.read(longueur)
    return lire


if __name__ == "__main__":
    main()
