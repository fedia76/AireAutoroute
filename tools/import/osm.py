"""Rattachement du relevé OpenStreetMap au référentiel des aires.

Le référentiel, c'est WikiSara : une aire par chaussée, avec son nom et son point kilométrique.
OpenStreetMap ne sait pas cela ; il connaît des polygones et des objets posés dedans. Ce module
fait le pont — et quand le pont ne tient pas, il ne devine pas : l'aire reste sans équipement et
le rapport le dit.

L'appariement repose sur trois indices, du plus sûr au moins sûr :

  1. le nom, normalisé (« Aire de Vironvay Nord » et « Vironvay-Nord » sont le même lieu) ;
  2. le point kilométrique, obtenu en projetant le point OSM sur notre propre tracé ;
  3. le côté de la chaussée, déduit du signe du produit vectoriel avec le tracé.

Le troisième n'arbitre que les cas où les deux premiers ne suffisent pas — c'est-à-dire les 574
aires du référentiel qui vont par paires homonymes, une par sens. Mesuré sur les aires au nom
non ambigu, il tombe juste dans 93 % des cas : bon départage, mauvaise vérité.
"""

import json
import math
import re
import unicodedata
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from difflib import SequenceMatcher

from .bornes import KM_PAR_DEGRE, TraceAutoroute, ecart_lateral, projeter_sur_trace

SENS_CROISSANT = "CROISSANT"
SENS_DECROISSANT = "DECROISSANT"

# Un objet appartient à l'aire dont le centre est le plus proche, dans ce rayon. C'est aussi le
# rayon du relevé : au-delà, rien n'a été collecté.
RAYON_RATTACHEMENT_KM = 0.25

# Deux aires qui se font face sont à quelques centaines de mètres l'une de l'autre ; le PK d'une
# ligne WikiSara est arrondi. Quatre kilomètres laissent de la marge sans mélanger deux aires
# successives, qui sont rarement si proches.
ECART_PK_MAX_KM = 4.0

# En deçà, deux noms ne désignent pas le même lieu.
SIMILARITE_MINIMALE = 0.55
# Un nom qui concorde autorise un écart de PK ; sans nom, seule la position parle.
ECART_TOLERE_AVEC_NOM_KM = 1.0
ECART_TOLERE_SANS_NOM_KM = 1.5

POIDS_NOM = 0.65
POIDS_PK = 0.35
POIDS_COTE = 0.15
# Sous cet écart à l'axe, le côté n'est plus qu'une présomption : son poids décroît d'autant.
ECART_LATERAL_FIABLE_KM = 0.05

# Une marque vue trop peu souvent est plus probablement une erreur de saisie qu'une enseigne.
OCCURRENCES_MINIMALES_ENSEIGNE = 3

# Noms génériques que des contributeurs emploient faute de mieux : ce sont des descriptions,
# pas des enseignes. Comparés après normalisation.
NOMS_GENERIQUES = {
    identifiant
    for identifiant in (
        "boutique", "la boutique", "le market", "market", "corner cafe", "restaurant",
        "cafeteria", "cafe", "snack", "sandwicherie", "superette", "station service",
        "aire de service", "restauration", "espace boutique", "brasserie", "self",
    )
}

# Renommages de marque : OpenStreetMap garde longtemps l'ancien nom à côté du nouveau, et deux
# entrées pour la même enseigne se verraient dans l'application.
RENOMMAGES = {"total": "totalenergies"}

AMENITES_RESTAURATION = {"fast_food", "restaurant", "cafe", "ice_cream"}

# Mots vidés du nom avant comparaison : ils décorent, ils ne désignent pas.
_PREFIXES = re.compile(r"^(aire|halte|relais)\s+(de\s+la\s+|de\s+l'|des\s+|du\s+|de\s+|d')?")
_OUTILS = re.compile(r"\b(aire|de|du|des|la|le|les|l|d|service|repos|autoroute)\b")


@dataclass
class RapportOsm:
    aires_relevees: int = 0
    aires_appariees: int = 0
    objets_rattaches: int = 0
    objets_orphelins: int = 0
    equipements_ajoutes: Counter = field(default_factory=Counter)
    enseignes_ajoutees: list[str] = field(default_factory=list)
    liens_poses: int = 0
    apparies_par_le_nom: int = 0
    apparies_par_la_position: int = 0
    cote_contredit_le_referentiel: int = 0
    sans_correspondance: list[str] = field(default_factory=list)


def lire_releve(chemin: str) -> dict:
    with open(chemin, encoding="utf-8") as fichier:
        return json.load(fichier)


def normaliser_nom(nom: str | None) -> str:
    """Réduit un nom à ce qui le distingue : sans accents, sans article, sans ponctuation."""
    texte = unicodedata.normalize("NFKD", (nom or "").lower())
    texte = "".join(c for c in texte if not unicodedata.combining(c))
    texte = _PREFIXES.sub(" ", texte)
    texte = _OUTILS.sub(" ", texte)
    return " ".join(re.sub(r"[^a-z0-9]+", " ", texte).split())


def _distance_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    return math.hypot(
        (lat1 - lat2) * KM_PAR_DEGRE,
        (lon1 - lon2) * KM_PAR_DEGRE * math.cos(math.radians(lat1)),
    )


def situer(trace: TraceAutoroute, lat: float, lon: float) -> tuple[float, str, float]:
    """Renvoie (point kilométrique, sens desservi, confiance du sens entre 0 et 1)."""
    _, pk = projeter_sur_trace(trace, lat, lon)
    ecart = ecart_lateral(trace, lat, lon)
    sens = SENS_DECROISSANT if ecart > 0 else SENS_CROISSANT
    return pk, sens, min(1.0, abs(ecart) / ECART_LATERAL_FIABLE_KM)


def rattacher_les_objets(releve: dict) -> tuple[dict[str, list[dict]], int]:
    """Attribue chaque objet relevé à l'aire OSM dont le centre est le plus proche."""
    aires = releve["aires"]
    cases: dict[tuple[float, float], list[int]] = defaultdict(list)
    for rang, aire in enumerate(aires):
        cases[(round(aire["lat"], 2), round(aire["lon"], 2))].append(rang)

    par_aire: dict[str, list[dict]] = defaultdict(list)
    orphelins = 0
    for objet in releve["equipements"]:
        voisines: list[int] = []
        for dlat in (-0.01, 0.0, 0.01):
            for dlon in (-0.01, 0.0, 0.01):
                voisines += cases.get(
                    (round(objet["lat"] + dlat, 2), round(objet["lon"] + dlon, 2)), []
                )
        candidates = [
            (_distance_km(aires[r]["lat"], aires[r]["lon"], objet["lat"], objet["lon"]), r)
            for r in voisines
        ]
        candidates = [c for c in candidates if c[0] <= RAYON_RATTACHEMENT_KM]
        if not candidates:
            orphelins += 1
            continue
        par_aire[aires[min(candidates)[1]]["osm"]].append(objet)

    return par_aire, orphelins


def apparier(
    aires_referentiel: list[dict],
    traces: dict[str, TraceAutoroute],
    releve: dict,
    rapport: RapportOsm,
) -> dict[str, str]:
    """Associe à chaque aire OSM une aire du référentiel, au plus une de chaque côté.

    Les couples sont notés puis attribués du meilleur au moins bon : une aire déjà prise ne
    l'est plus, ce qui empêche deux aires OSM de revendiquer la même ligne de WikiSara.
    """
    par_axe: dict[str, list[dict]] = defaultdict(list)
    for aire in aires_referentiel:
        par_axe[aire["autorouteId"]].append(aire)

    couples: list[tuple[float, float, str, str]] = []
    for aire in releve["aires"]:
        trace = traces.get(aire["autoroute"])
        if trace is None:
            continue
        pk, sens, confiance = situer(trace, aire["lat"], aire["lon"])
        nom = normaliser_nom(aire["tags"].get("name"))

        for reference in par_axe.get(aire["autoroute"], []):
            ecart_pk = abs(pk - reference["pk"])
            if ecart_pk > ECART_PK_MAX_KM:
                continue
            similarite = (
                SequenceMatcher(None, nom, normaliser_nom(reference["nom"])).ratio() if nom else 0.0
            )
            if nom and similarite < SIMILARITE_MINIMALE and ecart_pk > ECART_TOLERE_AVEC_NOM_KM:
                continue
            if not nom and ecart_pk > ECART_TOLERE_SANS_NOM_KM:
                continue

            score = POIDS_NOM * similarite + POIDS_PK * (1 - ecart_pk / ECART_PK_MAX_KM)
            accord = sens == reference["sens"]
            score += (POIDS_COTE if accord else -POIDS_COTE) * confiance
            couples.append((score, similarite, aire["osm"], reference["id"]))

    couples.sort(reverse=True)
    pris_osm: set[str] = set()
    pris_reference: set[str] = set()
    appariement: dict[str, str] = {}
    for score, similarite, identifiant_osm, identifiant_reference in couples:
        if identifiant_osm in pris_osm or identifiant_reference in pris_reference:
            continue
        pris_osm.add(identifiant_osm)
        pris_reference.add(identifiant_reference)
        appariement[identifiant_osm] = identifiant_reference
        if similarite >= SIMILARITE_MINIMALE:
            rapport.apparies_par_le_nom += 1
        else:
            rapport.apparies_par_la_position += 1

    rapport.aires_relevees = len(releve["aires"])
    rapport.aires_appariees = len(appariement)
    rapport.sans_correspondance = sorted(
        f"{a['autoroute']} · {a['tags'].get('name') or '(sans nom)'} (PK {a['pk']:.0f})"
        for a in releve["aires"]
        if a["osm"] not in pris_osm
    )
    return appariement


def equipements_declares(objets: list[dict]) -> list[str]:
    """Traduit les objets relevés dans le vocabulaire des cinq critères de l'application."""
    tags = [o.get("tags", {}) for o in objets]
    declares: list[str] = []

    jeux = [t for t in tags if t.get("leisure") == "playground"]
    if any(t.get("indoor") == "yes" or t.get("covered") == "yes" for t in jeux):
        declares.append("AIRE_JEUX_INTERIEURE")
    if any(t.get("indoor") != "yes" and t.get("covered") != "yes" for t in jeux):
        declares.append("AIRE_JEUX_EXTERIEURE")
    if any(t.get("amenity") == "toilets" for t in tags):
        declares.append("TOILETTES")
    # OSM note la table à langer sur les toilettes qui en portent une ; « limited » signale
    # un équipement présent mais d'accès restreint, ce qui reste une table à langer.
    if any(
        t.get("changing_table") in {"yes", "limited"} or t.get("changing_table:location")
        for t in tags
    ):
        declares.append("TABLE_A_LANGER")
    if any(t.get("amenity") == "fuel" for t in tags):
        declares.append("STATION_SERVICE")
    return declares


def _categorie(tags: dict) -> str | None:
    if tags.get("amenity") == "fuel":
        return "CARBURANT"
    if tags.get("amenity") in AMENITES_RESTAURATION:
        return "RESTAURATION"
    if tags.get("shop"):
        return "BOUTIQUE"
    return None


def marques_relevees(objets: list[dict]) -> list[tuple[str, str]]:
    """Marques portées par les objets d'une aire, avec leur catégorie."""
    trouvees = []
    for objet in objets:
        tags = objet.get("tags", {})
        categorie = _categorie(tags)
        marque = tags.get("brand") or tags.get("name")
        if categorie and marque:
            trouvees.append((marque.strip(), categorie))
    return trouvees


def identifiant_enseigne(nom: str) -> str:
    """Clé d'une enseigne : le nom réduit à sa forme comparable, renommages appliqués.

    C'est ce qui réunit « L'Atelier Charal » et « L'atelier Charal », et ce qui range « Total »
    sous « TotalEnergies ».
    """
    cle = re.sub(r"_+", "_", re.sub(r"[^a-z0-9]+", "_", normaliser_nom(nom))).strip("_")
    return RENOMMAGES.get(cle, cle)


def enrichir(
    aires: list[dict],
    traces: dict[str, TraceAutoroute],
    releve: dict,
    enseignes: list[dict],
) -> tuple[list[dict], list[dict], list[dict], RapportOsm]:
    """Complète les aires par les équipements et les enseignes relevés dans OpenStreetMap.

    Renvoie les aires enrichies, le catalogue d'enseignes augmenté, les liens aire/enseigne, et
    le rapport. Rien n'est affirmé : les équipements ajoutés rejoignent ceux qu'annonce le
    référentiel, et l'application les présente comme annoncés tant qu'aucun visiteur ne les a
    confirmés.
    """
    rapport = RapportOsm()
    objets_par_aire, rapport.objets_orphelins = rattacher_les_objets(releve)
    rapport.objets_rattaches = sum(len(v) for v in objets_par_aire.values())

    appariement = apparier(aires, traces, releve, rapport)
    par_identifiant = {a["id"]: a for a in aires}

    # Une marque n'entre au catalogue que si elle revient assez souvent pour être une enseigne.
    # Le décompte se fait sur la clé, pas sur le libellé : les variantes de casse et de graphie
    # comptent ensemble, et c'est la graphie la plus répandue qui l'emporte.
    occurrences: Counter = Counter()
    graphies: dict[str, Counter] = defaultdict(Counter)
    categories: dict[str, str] = {}
    for identifiant_osm in appariement:
        for marque, categorie in marques_relevees(objets_par_aire.get(identifiant_osm, [])):
            cle = identifiant_enseigne(marque)
            if not cle or cle in NOMS_GENERIQUES:
                continue
            occurrences[cle] += 1
            graphies[cle][marque] += 1
            categories.setdefault(cle, categorie)

    connues = {identifiant_enseigne(e["nom"]): e["id"] for e in enseignes}
    catalogue = list(enseignes)
    for cle, nombre in occurrences.most_common():
        if nombre < OCCURRENCES_MINIMALES_ENSEIGNE or cle in connues:
            continue
        libelle = graphies[cle].most_common(1)[0][0]
        connues[cle] = cle
        catalogue.append({"id": cle, "nom": libelle, "categorie": categories[cle]})
        rapport.enseignes_ajoutees.append(f"{libelle} ({categories[cle]}, {nombre} aires)")

    liens: set[tuple[str, str]] = set()
    for identifiant_osm, identifiant_reference in appariement.items():
        aire = par_identifiant.get(identifiant_reference)
        if aire is None:
            continue
        objets = objets_par_aire.get(identifiant_osm, [])

        annonces = list(aire["equipements"])
        for equipement in equipements_declares(objets):
            if equipement not in annonces:
                annonces.append(equipement)
                rapport.equipements_ajoutes[equipement] += 1
        aire["equipements"] = annonces

        for marque, _ in marques_relevees(objets):
            enseigne = connues.get(identifiant_enseigne(marque))
            if enseigne:
                liens.add((identifiant_reference, enseigne))

    rapport.liens_poses = len(liens)
    liaisons = [
        {"aireId": aire, "enseigneId": enseigne} for aire, enseigne in sorted(liens)
    ]
    return aires, catalogue, liaisons, rapport
