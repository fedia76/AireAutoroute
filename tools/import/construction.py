"""Construction des fichiers livrés à l'application à partir des deux sources ouvertes.

Le bornage donne le tracé et l'échelle des points kilométriques ; WikiSara donne le catalogue
des aires avec leur PK. Chaque aire est placée sur le tracé en interpolant son PK entre les deux
bornes qui l'encadrent : les deux sources parlent la même langue, celle des panneaux.
"""

from collections import defaultdict
from dataclasses import dataclass, field

from .bornes import TraceAutoroute, position_du_pk
from .wikisara import AireWikisara, CatalogueWikisara, slug

SENS_CROISSANT = "CROISSANT"
SENS_DECROISSANT = "DECROISSANT"
SENS_LES_DEUX = "LES_DEUX"

# Une aire de service se définit par la présence de carburant et de sanitaires : on les annonce,
# sans les affirmer — l'application les présentera comme « annoncés, non vérifiés » tant qu'aucun
# visiteur ne les aura confirmés.
EQUIPEMENTS_AIRE_DE_SERVICE = ["STATION_SERVICE", "TOILETTES"]

# Tolérance de rapprochement entre les deux chaussées d'une même aire, en kilomètres.
TOLERANCE_FUSION_KM = 2.0


@dataclass
class Rapport:
    autoroutes_retenues: int = 0
    aires_retenues: int = 0
    aires_deux_sens: int = 0
    km_traces: float = 0.0
    km_ecartes: float = 0.0
    autoroutes_sans_trace: list[str] = field(default_factory=list)
    autoroutes_sans_aire: list[str] = field(default_factory=list)
    aires_hors_trace: list[str] = field(default_factory=list)
    sens_indetermine: list[str] = field(default_factory=list)

    @property
    def aires_ecartees(self) -> int:
        return len(self.aires_hors_trace)


def _sens_de(catalogue: CatalogueWikisara, aire: AireWikisara) -> str | None:
    sens = catalogue.sens.get((aire.autoroute, aire.sens_libelle))
    if sens is None:
        return None
    return SENS_CROISSANT if sens.croissant else SENS_DECROISSANT


def _terminus(catalogue: CatalogueWikisara, autoroute: str) -> tuple[str, str] | None:
    """Terminus de l'autoroute, lus sur le libellé du sens des PK croissants."""
    for (numero, _), sens in catalogue.sens.items():
        if numero == autoroute and sens.croissant:
            return sens.depart, sens.arrivee
    for (numero, _), sens in catalogue.sens.items():
        if numero == autoroute:
            return sens.arrivee, sens.depart
    return None


def construire(
    traces: dict[str, TraceAutoroute],
    catalogue: CatalogueWikisara,
) -> tuple[list[dict], list[dict], Rapport]:
    rapport = Rapport()
    aires_par_autoroute: dict[str, list[AireWikisara]] = defaultdict(list)
    for aire in catalogue.aires:
        aires_par_autoroute[aire.autoroute].append(aire)

    for autoroute in sorted(aires_par_autoroute):
        if autoroute not in traces:
            rapport.autoroutes_sans_trace.append(autoroute)

    autoroutes: list[dict] = []
    aires: list[dict] = []

    for numero in sorted(traces, key=lambda n: (len(n), n)):
        trace = traces[numero]
        candidates = aires_par_autoroute.get(numero, [])
        if not candidates:
            rapport.autoroutes_sans_aire.append(numero)
            continue

        terminus = _terminus(catalogue, numero)
        if terminus is None:
            continue

        aires_autoroute = _aires_de_l_autoroute(numero, trace, candidates, catalogue, rapport)
        if not aires_autoroute:
            continue

        autoroutes.append({
            "id": numero,
            "nom": numero,
            "libelle": f"{terminus[0]} — {terminus[1]}",
            "terminusDebut": terminus[0],
            "terminusFin": terminus[1],
            "longueurKm": round(trace.pk_max, 1),
            "geometrie": [
                {"pk": round(b.pk, 1), "lat": b.lat, "lon": b.lon} for b in trace.bornes
            ],
        })
        aires.extend(aires_autoroute)
        rapport.autoroutes_retenues += 1
        rapport.km_traces += trace.longueur_km
        rapport.km_ecartes += trace.km_ecartes

    rapport.aires_retenues = len(aires)
    rapport.aires_deux_sens = sum(1 for a in aires if a["sens"] == SENS_LES_DEUX)
    rapport.km_traces = round(rapport.km_traces, 1)
    rapport.km_ecartes = round(rapport.km_ecartes, 1)
    return autoroutes, aires, rapport


def _aires_de_l_autoroute(
    numero: str,
    trace: TraceAutoroute,
    candidates: list[AireWikisara],
    catalogue: CatalogueWikisara,
    rapport: Rapport,
) -> list[dict]:
    # Une même aire desservie par les deux chaussées apparaît deux fois, au même nom et à un ou
    # deux kilomètres près : on la fusionne en une seule entrée accessible dans les deux sens.
    par_nom: dict[str, list[tuple[AireWikisara, str]]] = defaultdict(list)
    for aire in candidates:
        sens = _sens_de(catalogue, aire)
        if sens is None:
            rapport.sens_indetermine.append(f"{numero} · {aire.nom}")
            continue
        if not (trace.pk_min - TOLERANCE_FUSION_KM <= aire.pk <= trace.pk_max + TOLERANCE_FUSION_KM):
            rapport.aires_hors_trace.append(
                f"{numero} · {aire.nom} (PK {aire.pk:.0f}, tracé {trace.pk_min:.0f}-{trace.pk_max:.0f})"
            )
            continue
        par_nom[slug(aire.nom)].append((aire, sens))

    # Deux aires homonymes éloignées de plus de la tolérance restent deux aires distinctes.
    groupes: list[tuple[str, list[tuple[AireWikisara, str]]]] = []
    for nom_slug, membres in par_nom.items():
        membres.sort(key=lambda m: m[0].pk)
        courant: list[tuple[AireWikisara, str]] = []
        for membre in membres:
            if courant and membre[0].pk - courant[-1][0].pk > TOLERANCE_FUSION_KM:
                groupes.append((nom_slug, courant))
                courant = []
            courant.append(membre)
        if courant:
            groupes.append((nom_slug, courant))

    # Un nom d'aire peut revenir à deux PK différents sur la même autoroute : dans ce cas
    # seulement, le PK entre dans l'identifiant pour le garder unique.
    occurrences: dict[str, int] = defaultdict(int)
    for nom_slug, _ in groupes:
        occurrences[nom_slug] += 1

    resultat: list[dict] = []
    for nom_slug, membres in sorted(groupes, key=lambda g: g[1][0][0].pk):
        aire = membres[0][0]
        sens_presents = {sens for _, sens in membres}
        sens = SENS_LES_DEUX if len(sens_presents) > 1 else next(iter(sens_presents))

        pk = round(sum(m[0].pk for m in membres) / len(membres), 1)
        position = position_du_pk(trace, pk)
        if position is None:
            rapport.aires_hors_trace.append(f"{numero} · {aire.nom} (PK {pk:.0f} hors bornes)")
            continue

        identifiant = f"{numero.lower()}-{nom_slug}"
        if occurrences[nom_slug] > 1:
            identifiant = f"{identifiant}-{pk:.0f}"

        type_aire = "SERVICE" if any(m[0].type_aire == "SERVICE" for m in membres) else "REPOS"
        resultat.append({
            "id": identifiant,
            "autorouteId": numero,
            "nom": aire.nom,
            "pk": pk,
            "sens": sens,
            "type": type_aire,
            "lat": position[0],
            "lon": position[1],
            "equipements": EQUIPEMENTS_AIRE_DE_SERVICE if type_aire == "SERVICE" else [],
        })

    return resultat
