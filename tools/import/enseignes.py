"""Pictogrammes des enseignes.

Le jeu d'icônes est **fixe** : il est écrit dans l'application (`IconeEnseigne`, `Modeles.kt`)
et repris ici à l'identique. Une icône dit la nature du commerce — de quoi faire le plein, de
quoi manger, de quoi acheter, de quoi dormir — jamais la marque : aucun logo n'entre dans le
catalogue, et la même image sert à toutes les enseignes de même nature.

Deux niveaux, dans cet ordre :

  1. la table `donnees/sources/icones_enseignes.json`, tenue à la main, qui range chaque
     enseigne connue ;
  2. à défaut, la catégorie relevée par l'import, qui ne distingue pas la boulangerie du
     restaurant mais vaut mieux qu'une case vide.

Une enseigne dont la catégorie elle-même ne dit rien (« AUTRE ») reste sans icône : mieux vaut
aucune image qu'une image fausse.
"""

import json

# Le jeu fixe, dans l'ordre où l'application le présente.
ICONES = (
    "CARBURANT",
    "RECHARGE_ELECTRIQUE",
    "RESTAURATION_RAPIDE",
    "RESTAURANT",
    "BOULANGERIE",
    "CAFE",
    "PIZZERIA",
    "SUPERETTE",
    "BOUTIQUE",
    "HOTEL",
)

# Ce que la catégorie seule permet d'affirmer.
DEFAUTS_PAR_CATEGORIE = {
    "CARBURANT": "CARBURANT",
    "RESTAURATION": "RESTAURANT",
    "BOUTIQUE": "BOUTIQUE",
    "HOTEL": "HOTEL",
}


def lire_table(chemin: str) -> dict[str, str]:
    """Lit la table des icônes et refuse tout nom hors du jeu fixe."""
    with open(chemin, encoding="utf-8") as fichier:
        table = json.load(fichier)
    inconnues = sorted({icone for icone in table.values() if icone not in ICONES})
    if inconnues:
        raise ValueError(f"icônes hors du jeu fixe : {', '.join(inconnues)}")
    return table


def appliquer(enseignes: list[dict], table: dict[str, str]) -> list[str]:
    """Pose l'icône de chaque enseigne du catalogue. Renvoie les entrées inutiles de la table.

    Le catalogue est modifié sur place : c'est lui qui est écrit dans `assets/seed/`.
    """
    for enseigne in enseignes:
        icone = table.get(enseigne["id"]) or DEFAUTS_PAR_CATEGORIE.get(enseigne.get("categorie"))
        if icone:
            enseigne["icone"] = icone
        else:
            enseigne.pop("icone", None)
    connues = {enseigne["id"] for enseigne in enseignes}
    return sorted(identifiant for identifiant in table if identifiant not in connues)
