"""Lecture du bornage du réseau routier national.

Source : « Bornage du réseau routier national », data.gouv.fr, Licence Ouverte.

Le fichier décrit des points de repère (les bornes du bord de route) par leurs coordonnées
Lambert 93. Chaque borne porte deux abscisses :

  * `cumul` — la distance en mètres depuis l'origine du tracé ;
  * `pr`    — le numéro inscrit sur la borne, c'est-à-dire le point kilométrique que lit
              l'automobiliste, qui ne coïncide pas toujours avec le cumul (une autoroute peut
              prolonger le kilométrage d'une autre, ou décaler son origine).

C'est le `pr` qui nous intéresse : l'application doit parler le même langage que les panneaux.
Le `cumul` sert uniquement à ordonner les bornes le long du tracé.

Deux familles de codes cohabitent dans le fichier ; seuls les codes courts (« A0013 ») décrivent
un itinéraire complet. Les codes longs (« 27A801315CD ») sont des bretelles d'échangeur de
quelques centaines de mètres, sans usage pour nous.
"""

import csv
import math
import re
from dataclasses import dataclass, field

from .lambert93 import vers_wgs84

# « A0013 » -> A13. Les codes longs et les routes nationales sont écartés.
CODE_AUTOROUTE = re.compile(r"^A(\d{4})$")

KM_PAR_DEGRE = 111.32


@dataclass
class Borne:
    pk: float
    lat: float
    lon: float
    cumul_m: int


@dataclass
class TraceAutoroute:
    numero: str
    bornes: list[Borne] = field(default_factory=list)
    """Kilomètres écartés parce que le kilométrage y repart en arrière."""
    km_ecartes: float = 0.0

    @property
    def pk_min(self) -> float:
        return self.bornes[0].pk

    @property
    def pk_max(self) -> float:
        return self.bornes[-1].pk

    @property
    def longueur_km(self) -> float:
        return round(self.pk_max - self.pk_min, 1)


def _nombre(valeur: str) -> float:
    return float(valeur.replace(",", "."))


def _plus_longue_section_croissante(bornes: list[Borne]) -> tuple[list[Borne], float]:
    """
    Découpe le tracé aux endroits où le kilométrage repart en arrière et garde le plus long
    morceau. Un PK qui recule casserait le calcul des « prochaines aires » : mieux vaut couvrir
    une partie de l'autoroute correctement que sa totalité de travers.
    """
    sections: list[list[Borne]] = [[]]
    for borne in bornes:
        if sections[-1] and borne.pk < sections[-1][-1].pk:
            sections.append([])
        sections[-1].append(borne)

    # On compare les sections sur la distance réellement parcourue, pas sur l'étendue des PK :
    # une poignée de bornes éparses peut couvrir une large plage de numéros sans rien décrire.
    def parcours_km(section: list[Borne]) -> float:
        return (section[-1].cumul_m - section[0].cumul_m) / 1000 if len(section) > 1 else 0.0

    gardee = max(sections, key=parcours_km)
    ecartes = sum(parcours_km(s) for s in sections if s is not gardee)
    return gardee, round(ecartes, 1)


def lire_traces(chemin: str) -> dict[str, TraceAutoroute]:
    """Renvoie, pour chaque numéro d'autoroute, son tracé ordonné du PK le plus petit au plus grand."""
    brut: dict[str, list[dict]] = {}
    with open(chemin, encoding="utf-8-sig", newline="") as fichier:
        for ligne in csv.DictReader(fichier, delimiter=";"):
            correspondance = CODE_AUTOROUTE.match(ligne["route"])
            if not correspondance:
                continue
            numero = "A" + correspondance.group(1).lstrip("0")
            brut.setdefault(numero, []).append(ligne)

    traces: dict[str, TraceAutoroute] = {}
    for numero, lignes in brut.items():
        # Les deux chaussées sont bornées séparément et se suivent à quelques dizaines de mètres :
        # une seule suffit à décrire l'itinéraire.
        comptes: dict[str, int] = {}
        for ligne in lignes:
            comptes[ligne["cote"]] = comptes.get(ligne["cote"], 0) + 1
        cote_majoritaire = max(comptes, key=comptes.get)
        retenues = [l for l in lignes if l["cote"] == cote_majoritaire]

        retenues.sort(key=lambda l: int(l["cumul"]))
        bornes: list[Borne] = []
        for ligne in retenues:
            lat, lon = vers_wgs84(_nombre(ligne["x"]), _nombre(ligne["y"]))
            borne = Borne(
                pk=float(ligne["pr"]),
                lat=round(lat, 5),
                lon=round(lon, 5),
                cumul_m=int(ligne["cumul"]),
            )
            # Deux bornes au même PK n'apportent rien et gêneraient l'interpolation.
            if bornes and borne.pk == bornes[-1].pk:
                continue
            bornes.append(borne)

        if len(bornes) < 2:
            continue

        gardees, ecartes = _plus_longue_section_croissante(bornes)
        if len(gardees) < 2:
            continue
        traces[numero] = TraceAutoroute(numero=numero, bornes=gardees, km_ecartes=ecartes)

    return traces


def projeter_sur_trace(trace: TraceAutoroute, lat: float, lon: float) -> tuple[float, float]:
    """
    Projette un point sur le tracé et renvoie (distance en km, point kilométrique).

    Sert à reconnaître les objets qui appartiennent à une autoroute donnée, puis à les situer
    dessus. Le calcul se fait dans un repère plan local, comme pour la localisation GPS.
    """
    meilleure_distance = float("inf")
    meilleur_pk = trace.bornes[0].pk

    for depart, arrivee in zip(trace.bornes, trace.bornes[1:]):
        # Repère plan centré sur le début du segment, en kilomètres.
        cos_lat = math.cos(math.radians(depart.lat))
        bx = (arrivee.lon - depart.lon) * KM_PAR_DEGRE * cos_lat
        by = (arrivee.lat - depart.lat) * KM_PAR_DEGRE
        px = (lon - depart.lon) * KM_PAR_DEGRE * cos_lat
        py = (lat - depart.lat) * KM_PAR_DEGRE

        longueur2 = bx * bx + by * by
        part = 0.0 if longueur2 == 0 else max(0.0, min(1.0, (px * bx + py * by) / longueur2))
        distance = math.hypot(px - part * bx, py - part * by)
        if distance < meilleure_distance:
            meilleure_distance = distance
            meilleur_pk = depart.pk + part * (arrivee.pk - depart.pk)

    return meilleure_distance, meilleur_pk


def ecart_lateral(trace: TraceAutoroute, lat: float, lon: float) -> float:
    """
    Distance signée entre un point et le tracé, en kilomètres.

    Positive à gauche du sens des points kilométriques croissants, négative à droite. Comme on
    roule à droite en France, une aire à droite du tracé dessert la chaussée croissante. Le
    bornage ne relève qu'une des deux chaussées, décalée d'une vingtaine de mètres : le signe
    départage bien les aires qui se font face, mais reste indicatif tout près de l'axe.
    """
    meilleure_distance = float("inf")
    meilleur_ecart = 0.0

    for depart, arrivee in zip(trace.bornes, trace.bornes[1:]):
        cos_lat = math.cos(math.radians(depart.lat))
        bx = (arrivee.lon - depart.lon) * KM_PAR_DEGRE * cos_lat
        by = (arrivee.lat - depart.lat) * KM_PAR_DEGRE
        px = (lon - depart.lon) * KM_PAR_DEGRE * cos_lat
        py = (lat - depart.lat) * KM_PAR_DEGRE

        longueur2 = bx * bx + by * by
        part = 0.0 if longueur2 == 0 else max(0.0, min(1.0, (px * bx + py * by) / longueur2))
        distance = math.hypot(px - part * bx, py - part * by)
        if distance < meilleure_distance:
            longueur = math.sqrt(longueur2)
            meilleure_distance = distance
            meilleur_ecart = 0.0 if longueur == 0 else (bx * py - by * px) / longueur

    return meilleur_ecart


def position_du_pk(trace: TraceAutoroute, pk: float) -> tuple[float, float] | None:
    """Coordonnées d'un point kilométrique, interpolées entre les deux bornes qui l'encadrent."""
    bornes = trace.bornes
    if pk < bornes[0].pk or pk > bornes[-1].pk:
        return None
    for precedente, suivante in zip(bornes, bornes[1:]):
        if precedente.pk <= pk <= suivante.pk:
            ecart = suivante.pk - precedente.pk
            part = 0.0 if ecart == 0 else (pk - precedente.pk) / ecart
            return (
                round(precedente.lat + part * (suivante.lat - precedente.lat), 5),
                round(precedente.lon + part * (suivante.lon - precedente.lon), 5),
            )
    return None
