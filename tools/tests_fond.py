"""
Tests de la génération du fond cartographique.

L'extraction est vérifiée contre une archive PMTiles construite sur mesure,
dont on connaît le contenu exact : on compare tuile par tuile ce que
`generer_fond.py` retient et écrit avec ce qu'un calcul indépendant prévoit.

    python3 tools/tests_fond.py
"""

import os
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from pmtiles.reader import Reader, MmapSource, zxy_to_tileid
from pmtiles.tile import Compression, TileType
from pmtiles.writer import Writer

from generer_fond import extraire, plage_tuiles, recenser, source_fichier

ZMAX_SOURCE = 8
ZMAX_EXTRAIT = 6
BBOX = (-5.3, 41.3, 9.7, 51.2)


def contenu(z, x, y):
    """Charge déterministe. Une tuile sur trois est « de l'océan » : partagée."""
    if (x + y) % 3 == 0:
        return b"OCEAN" * 4
    return bytes([(x * 31 + y * 17 + z) % 251]) * (20 + (x * 7 + y * 5 + z) % 40)


def construire_source(chemin):
    with open(chemin, "wb") as fichier:
        redacteur = Writer(fichier)
        for z in range(ZMAX_SOURCE + 1):
            cote = 1 << z
            for x in range(cote):
                for y in range(cote):
                    redacteur.write_tile(zxy_to_tileid(z, x, y), contenu(z, x, y))
        redacteur.finalize(
            {
                "tile_type": TileType.MVT,
                "tile_compression": Compression.GZIP,
                "min_lon_e7": -1800000000, "min_lat_e7": -850000000,
                "max_lon_e7": 1800000000, "max_lat_e7": 850000000,
                "center_zoom": 0, "center_lon_e7": 0, "center_lat_e7": 0,
            },
            {"name": "source de test", "vector_layers": [{"id": "roads"}]},
        )


def tuiles_attendues():
    """Les (z, x, y) que l'extrait doit contenir, calculés indépendamment."""
    attendues = []
    for z in range(ZMAX_EXTRAIT + 1):
        x_min, x_max, y_min, y_max = plage_tuiles(BBOX, z)
        for x in range(x_min, x_max + 1):
            for y in range(y_min, y_max + 1):
                attendues.append((z, x, y))
    return attendues


def main():
    echecs = []
    with tempfile.TemporaryDirectory() as dossier:
        source = os.path.join(dossier, "source.pmtiles")
        sortie = os.path.join(dossier, "extrait.pmtiles")
        construire_source(source)

        lire = source_fichier(source)
        entete, retenues, octets, tuiles = recenser(lire, BBOX, ZMAX_EXTRAIT)
        extraire(lire, entete, retenues, BBOX, sortie)

        attendues = tuiles_attendues()

        # 1. Le recensement retient exactement le bon nombre de tuiles par niveau.
        for z in range(ZMAX_EXTRAIT + 1):
            prevu = sum(1 for (zz, _, _) in attendues if zz == z)
            if tuiles[z] != prevu:
                echecs.append(f"z{z} : {tuiles[z]} tuiles recensées, {prevu} attendues")

        # 2. L'extrait contient chaque tuile attendue, avec le bon contenu.
        with open(sortie, "r+b") as fichier:
            lecteur = Reader(MmapSource(fichier))
            for (z, x, y) in attendues:
                obtenu = lecteur.get(z, x, y)
                if obtenu is None:
                    echecs.append(f"z{z}/{x}/{y} absente de l'extrait")
                elif obtenu != contenu(z, x, y):
                    echecs.append(f"z{z}/{x}/{y} : contenu incorrect")

            # 3. Rien au-delà du zoom demandé.
            if lecteur.get(ZMAX_EXTRAIT + 1, 0, 0) is not None:
                echecs.append(f"une tuile z{ZMAX_EXTRAIT + 1} a fuité dans l'extrait")

            # 4. Rien en dehors de l'emprise.
            x_min, _, y_min, _ = plage_tuiles(BBOX, ZMAX_EXTRAIT)
            if lecteur.get(ZMAX_EXTRAIT, x_min - 3, y_min) is not None:
                echecs.append("une tuile hors emprise a fuité dans l'extrait")

            # 5. Les métadonnées de schéma sont conservées : sans elles, le style
            #    ne sait pas quelles couches la source expose.
            meta = lecteur.metadata()
            if "vector_layers" not in meta:
                echecs.append("métadonnées de schéma perdues à l'extraction")

        poids = os.path.getsize(sortie)
        print(f"tuiles attendues : {len(attendues)}")
        print(f"extrait          : {poids} octets")

    if echecs:
        print("\nECHECS :")
        for echec in echecs:
            print(" -", echec)
        return 1
    print("\nRESULTAT : OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
