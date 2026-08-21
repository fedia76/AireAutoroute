#!/usr/bin/env python3
"""Génère les fichiers de données livrés dans app/src/main/assets/seed/.

Deux sources ouvertes, toutes deux versionnées dans `donnees/sources/` :

  * `bornes2025.csv`      — Bornage du réseau routier national (data.gouv.fr, Licence Ouverte).
                            Donne le tracé de chaque autoroute et, borne par borne, le point
                            kilométrique affiché sur les panneaux.
  * `wikisara_aires.csv`  — Catalogue des aires (WikiSara, CC BY-SA), produit par
                            `tools/scrapper_wikisara.py`.
  * `enseignes.json`      — Référentiel des enseignes, tenu à la main : il sert de liste de
                            saisie dans l'application, aucune n'est rattachée à une aire tant
                            que la passe OpenStreetMap n'est pas branchée.

Usage :
    python3 tools/generer_donnees.py            # écrit les fichiers et le rapport
    python3 tools/generer_donnees.py --verifier  # n'écrit rien, contrôle et rapporte
"""

import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from importlib import import_module

bornes = import_module("import.bornes")
wikisara = import_module("import.wikisara")
construction = import_module("import.construction")

RACINE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCES = os.path.join(RACINE, "donnees", "sources")
SORTIE = os.path.join(RACINE, "app", "src", "main", "assets", "seed")
RAPPORT = os.path.join(RACINE, "donnees", "rapport_import.md")

# Seuils de vraisemblance : en dessous, c'est que la lecture d'une source a échoué.
MINIMUM_AUTOROUTES = 70
MINIMUM_AIRES = 1_000
BAISSE_MAXIMALE = 0.20


def controler(autoroutes: list[dict], aires: list[dict]) -> list[str]:
    """Renvoie la liste des anomalies bloquantes. Une liste vide vaut feu vert."""
    anomalies: list[str] = []

    if len(autoroutes) < MINIMUM_AUTOROUTES:
        anomalies.append(f"{len(autoroutes)} autoroutes seulement (minimum {MINIMUM_AUTOROUTES})")
    if len(aires) < MINIMUM_AIRES:
        anomalies.append(f"{len(aires)} aires seulement (minimum {MINIMUM_AIRES})")

    identifiants = [a["id"] for a in aires]
    doublons = {i for i in identifiants if identifiants.count(i) > 1}
    if doublons:
        anomalies.append(f"identifiants d'aire en double : {sorted(doublons)[:5]}")

    longueurs = {a["id"]: a["longueurKm"] for a in autoroutes}
    for aire in aires:
        if aire["lat"] is None or aire["lon"] is None:
            anomalies.append(f"aire sans position : {aire['id']}")
        longueur = longueurs.get(aire["autorouteId"])
        if longueur is None:
            anomalies.append(f"aire rattachée à une autoroute absente : {aire['id']}")
        elif not -1 <= aire["pk"] <= longueur + 1:
            anomalies.append(f"PK hors tracé : {aire['id']} (PK {aire['pk']} / {longueur} km)")

    for autoroute in autoroutes:
        pks = [point["pk"] for point in autoroute["geometrie"]]
        if len(pks) < 2:
            anomalies.append(f"{autoroute['id']} : tracé trop court")
        elif any(b <= a for a, b in zip(pks, pks[1:])):
            anomalies.append(f"{autoroute['id']} : points kilométriques non croissants")

    ancien = os.path.join(SORTIE, "aires.json")
    if os.path.exists(ancien):
        with open(ancien, encoding="utf-8") as fichier:
            precedentes = len(json.load(fichier))
        if precedentes and len(aires) < precedentes * (1 - BAISSE_MAXIMALE):
            anomalies.append(
                f"chute du nombre d'aires : {precedentes} -> {len(aires)}"
            )

    return anomalies[:20]


def ecrire_json(chemin: str, contenu) -> None:
    with open(chemin, "w", encoding="utf-8") as fichier:
        json.dump(contenu, fichier, ensure_ascii=False, indent=2)
        fichier.write("\n")


def ecrire_rapport(rapport, anomalies: list[str]) -> str:
    lignes = [
        "# Rapport d'import des données",
        "",
        "Généré par `python3 tools/generer_donnees.py`.",
        "",
        "## Ce qui a été retenu",
        "",
        f"- **{rapport.autoroutes_retenues} autoroutes**, {rapport.km_traces:.0f} km de tracé",
        f"- **{rapport.aires_retenues} aires**, dont {rapport.aires_deux_sens} accessibles dans les deux sens",
        "",
        "## Ce qui a été écarté",
        "",
        f"- {rapport.km_ecartes:.0f} km de tracé, là où le kilométrage repart en arrière "
        "(l'autoroute est décrite en plusieurs tronçons dans le bornage, on garde le plus long)",
        f"- {rapport.aires_ecartees} aires dont le PK ne tombe pas sur le tracé retenu",
        f"- {len(rapport.autoroutes_sans_trace)} autoroutes citées par WikiSara sans tracé dans le bornage : "
        + (", ".join(rapport.autoroutes_sans_trace) or "aucune"),
        f"- {len(rapport.autoroutes_sans_aire)} autoroutes bornées sans aire répertoriée",
        "",
    ]

    if rapport.aires_hors_trace:
        lignes += ["### Aires écartées", ""]
        lignes += [f"- {ligne}" for ligne in sorted(rapport.aires_hors_trace)]
        lignes.append("")

    if rapport.sens_indetermine:
        lignes += ["### Sens de circulation indéterminé", ""]
        lignes += [f"- {ligne}" for ligne in sorted(rapport.sens_indetermine)]
        lignes.append("")

    lignes += ["## Contrôles", ""]
    if anomalies:
        lignes += ["Anomalies bloquantes :", ""] + [f"- {a}" for a in anomalies]
    else:
        lignes.append("Aucune anomalie bloquante.")
    lignes.append("")

    return "\n".join(lignes)


def main() -> int:
    analyseur = argparse.ArgumentParser(description=__doc__)
    analyseur.add_argument(
        "--verifier",
        action="store_true",
        help="ne rien écrire, se contenter de contrôler les sources",
    )
    arguments = analyseur.parse_args()

    traces = bornes.lire_traces(os.path.join(SOURCES, "bornes2025.csv"))
    catalogue = wikisara.lire_catalogue(os.path.join(SOURCES, "wikisara_aires.csv"))
    autoroutes, aires, rapport = construction.construire(traces, catalogue)
    anomalies = controler(autoroutes, aires)

    print(f"{rapport.autoroutes_retenues} autoroutes · {rapport.aires_retenues} aires "
          f"({rapport.aires_deux_sens} dans les deux sens) · {rapport.km_traces:.0f} km de tracé")
    print(f"écartés : {rapport.aires_ecartees} aires, {rapport.km_ecartes:.0f} km de tracé")

    if anomalies:
        print("\nAnomalies bloquantes :", file=sys.stderr)
        for anomalie in anomalies:
            print(f"  - {anomalie}", file=sys.stderr)
        return 1

    if arguments.verifier:
        print("\nVérification seule : aucun fichier écrit.")
        return 0

    os.makedirs(SORTIE, exist_ok=True)
    ecrire_json(os.path.join(SORTIE, "autoroutes.json"), autoroutes)
    ecrire_json(os.path.join(SORTIE, "aires.json"), aires)
    with open(os.path.join(SOURCES, "enseignes.json"), encoding="utf-8") as fichier:
        enseignes = json.load(fichier)
    ecrire_json(os.path.join(SORTIE, "enseignes.json"), enseignes)
    # Aucune liaison aire/enseigne n'est sourcée tant qu'OpenStreetMap n'est pas branché.
    ecrire_json(os.path.join(SORTIE, "aire_enseignes.json"), [])

    with open(RAPPORT, "w", encoding="utf-8") as fichier:
        fichier.write(ecrire_rapport(rapport, anomalies))
    print(f"\nFichiers écrits dans {SORTIE}")
    print(f"Rapport : {RAPPORT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
