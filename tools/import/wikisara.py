"""Lecture du catalogue des aires extrait de WikiSara.

Source : WikiSara (routes.fandom.com), sous licence CC BY-SA. Le fichier CSV est produit par
`tools/scrapper_wikisara.py` et versionné dans `donnees/sources/` : la chaîne d'import ne
retourne pas sur le site à chaque exécution.

Le CSV donne, pour chaque aire, son autoroute, son sens de circulation (« Sens Paris - Caen »),
son nom, son type et son point kilométrique. Le sens de circulation croissant se déduit de
l'ordre du tableau d'origine : WikiSara liste les aires dans l'ordre du parcours, donc le sens
dont les PK augmentent au fil des lignes est celui des PK croissants.
"""

import csv
import re
import unicodedata
from dataclasses import dataclass, field

SEPARATEUR_TERMINUS = re.compile(r"\s+-\s+")
PREFIXE_SENS = re.compile(r"^Sens\s+", re.IGNORECASE)
NUMERO_AUTOROUTE = re.compile(r"^A\d+$")


@dataclass
class AireWikisara:
    autoroute: str
    nom: str
    type_aire: str
    pk: float
    sens_libelle: str


@dataclass
class SensWikisara:
    libelle: str
    depart: str
    arrivee: str
    croissant: bool


@dataclass
class CatalogueWikisara:
    aires: list[AireWikisara] = field(default_factory=list)
    sens: dict[tuple[str, str], SensWikisara] = field(default_factory=dict)
    """Autoroutes dont le nom ne suit pas la forme « A13 » : hors périmètre."""
    autoroutes_ignorees: set[str] = field(default_factory=set)


def sans_accents(texte: str) -> str:
    decompose = unicodedata.normalize("NFKD", texte)
    return "".join(c for c in decompose if not unicodedata.combining(c))


def slug(texte: str) -> str:
    """Identifiant lisible et stable : « Vironvay Nord » -> « vironvay-nord »."""
    base = sans_accents(texte).lower()
    base = re.sub(r"[''`]", "-", base)
    base = re.sub(r"[^a-z0-9]+", "-", base)
    return base.strip("-")


def _terminus(libelle: str) -> tuple[str, str]:
    """« Sens Paris - Caen » -> (« Paris », « Caen »)."""
    corps = PREFIXE_SENS.sub("", libelle).strip()
    morceaux = SEPARATEUR_TERMINUS.split(corps, maxsplit=1)
    if len(morceaux) != 2:
        # Certains libellés n'ont pas d'espace autour du tiret (« Sens Rouen-Le Havre »), et
        # d'autres ne nomment pas de terminus du tout (« Sens intérieur » du périphérique).
        morceaux = corps.split("-", 1) if "-" in corps else [corps, corps]
    return morceaux[0].strip(), morceaux[1].strip()


def lire_catalogue(chemin: str) -> CatalogueWikisara:
    catalogue = CatalogueWikisara()
    groupes: dict[tuple[str, str], list[AireWikisara]] = {}

    with open(chemin, encoding="utf-8-sig", newline="") as fichier:
        for ligne in csv.DictReader(fichier, delimiter=";"):
            autoroute = ligne["autoroute"].strip()
            if not NUMERO_AUTOROUTE.match(autoroute):
                catalogue.autoroutes_ignorees.add(autoroute)
                continue
            if not ligne["pk_km"]:
                continue
            aire = AireWikisara(
                autoroute=autoroute,
                nom=ligne["nom_aire"].strip(),
                type_aire="SERVICE" if ligne["type"].strip().lower().startswith("serv") else "REPOS",
                pk=float(ligne["pk_km"]),
                sens_libelle=ligne["sens"].strip(),
            )
            catalogue.aires.append(aire)
            groupes.setdefault((autoroute, aire.sens_libelle), []).append(aire)

    for (autoroute, libelle), aires in groupes.items():
        pks = [a.pk for a in aires]
        hausses = sum(1 for i in range(len(pks) - 1) if pks[i + 1] > pks[i])
        baisses = sum(1 for i in range(len(pks) - 1) if pks[i + 1] < pks[i])
        depart, arrivee = _terminus(libelle)
        catalogue.sens[(autoroute, libelle)] = SensWikisara(
            libelle=libelle,
            depart=depart,
            arrivee=arrivee,
            croissant=hausses >= baisses,
        )

    return catalogue
