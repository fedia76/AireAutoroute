#!/usr/bin/env python3
"""Génère les fichiers JSON livrés dans app/src/main/assets/seed/.

Les tracés d'autoroute sont décrits par une poly-ligne de points GPS ; le script
calcule les points kilométriques (PK) cumulés le long de ce tracé, puis projette
chaque aire sur la poly-ligne pour en déduire son PK. Les PK de l'application et
ceux de la géométrie sont ainsi toujours cohérents entre eux.

Usage : python3 tools/generer_donnees.py
"""

import json
import math
import os

R_TERRE_KM = 6371.0088


def distance_km(a, b):
    lat1, lon1 = math.radians(a[0]), math.radians(a[1])
    lat2, lon2 = math.radians(b[0]), math.radians(b[1])
    dlat, dlon = lat2 - lat1, lon2 - lon1
    h = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 2 * R_TERRE_KM * math.asin(math.sqrt(h))


def projection_locale(point, origine):
    """Coordonnées planes approchées (km) autour d'une origine (équirectangulaire)."""
    lat0 = math.radians(origine[0])
    x = math.radians(point[1] - origine[1]) * math.cos(lat0) * R_TERRE_KM
    y = math.radians(point[0] - origine[0]) * R_TERRE_KM
    return x, y


def pk_cumules(trace, longueur_reelle_km=None):
    """PK cumulés le long du tracé, éventuellement mis à l'échelle de la longueur officielle."""
    pks = [0.0]
    for i in range(1, len(trace)):
        pks.append(pks[-1] + distance_km(trace[i - 1], trace[i]))
    if longueur_reelle_km:
        facteur = longueur_reelle_km / pks[-1]
        pks = [pk * facteur for pk in pks]
    return pks


def pk_de_l_aire(trace, pks, position):
    """Projette une aire sur le tracé et renvoie son PK interpolé."""
    meilleur_pk, meilleure_distance = 0.0, float("inf")
    for i in range(1, len(trace)):
        a, b = trace[i - 1], trace[i]
        ax, ay = projection_locale(a, a)
        bx, by = projection_locale(b, a)
        px, py = projection_locale(position, a)
        dx, dy = bx - ax, by - ay
        longueur2 = dx * dx + dy * dy
        t = 0.0 if longueur2 == 0 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / longueur2))
        proj_x, proj_y = ax + t * dx, ay + t * dy
        distance = math.hypot(px - proj_x, py - proj_y)
        if distance < meilleure_distance:
            meilleure_distance = distance
            meilleur_pk = pks[i - 1] + t * (pks[i] - pks[i - 1])
    return round(meilleur_pk, 1)


# --- Catalogue des enseignes -------------------------------------------------

ENSEIGNES = [
    ("totalenergies", "TotalEnergies", "CARBURANT"),
    ("esso", "Esso", "CARBURANT"),
    ("shell", "Shell", "CARBURANT"),
    ("bp", "BP", "CARBURANT"),
    ("avia", "Avia", "CARBURANT"),
    ("agip", "Agip", "CARBURANT"),
    ("mcdonalds", "McDonald's", "RESTAURATION"),
    ("burger_king", "Burger King", "RESTAURATION"),
    ("kfc", "KFC", "RESTAURATION"),
    ("paul", "Paul", "RESTAURATION"),
    ("brioche_doree", "Brioche Dorée", "RESTAURATION"),
    ("la_croissanterie", "La Croissanterie", "RESTAURATION"),
    ("starbucks", "Starbucks", "RESTAURATION"),
    ("columbus_cafe", "Columbus Café", "RESTAURATION"),
    ("marie_blachere", "Marie Blachère", "RESTAURATION"),
    ("le_kiosque_a_pizzas", "Le Kiosque à Pizzas", "RESTAURATION"),
    ("autogrill", "Autogrill", "RESTAURATION"),
    ("arche", "L'Arche", "RESTAURATION"),
    ("cote_france", "Côté France", "RESTAURATION"),
    ("relais", "Relais", "BOUTIQUE"),
    ("carrefour_express", "Carrefour Express", "BOUTIQUE"),
    ("vival", "Vival", "BOUTIQUE"),
    ("daily_monop", "Daily Monop'", "BOUTIQUE"),
    ("ibis_budget", "Ibis Budget", "HOTEL"),
    ("b_and_b", "B&B Hôtels", "HOTEL"),
]

TOUS = ["TOILETTES", "STATION_SERVICE", "TABLE_A_LANGER", "APPRECIATION_GENERALE"]
JEUX_EXT = ["AIRE_JEUX_EXTERIEURE"]
JEUX_INT = ["AIRE_JEUX_INTERIEURE"]

# --- Autoroutes --------------------------------------------------------------
# Chaque autoroute : identifiant, nom, libellé, terminus, longueur officielle,
# tracé (lat, lon) du terminus de départ vers le terminus d'arrivée, et aires.
# Les aires sont décrites par (id, nom, lat, lon, sens, type, enseignes, équipements).
# CROISSANT = chaussée qui va vers le terminus d'arrivée.

AUTOROUTES = [
    {
        "id": "A13",
        "nom": "A13",
        "libelle": "Autoroute de Normandie",
        "terminusDebut": "Paris",
        "terminusFin": "Caen",
        "longueurKm": 227.0,
        "trace": [
            (48.8470, 2.2560), (48.8420, 2.1650), (48.8330, 2.1030), (48.8720, 2.0300),
            (48.9200, 1.9700), (48.9600, 1.8500), (48.9880, 1.7200), (49.0250, 1.6100),
            (49.0800, 1.4900), (49.1350, 1.3400), (49.1800, 1.2000), (49.2400, 1.0600),
            (49.3100, 0.9200), (49.3500, 0.7400), (49.3700, 0.5600), (49.3450, 0.3600),
            (49.3000, 0.2000), (49.2500, 0.0100), (49.2100, -0.1400), (49.1800, -0.3300),
        ],
        "aires": [
            ("a13-guerville", "Aire de Guerville", 48.9660, 1.7900, "DECROISSANT", "SERVICE",
             ["totalenergies", "paul", "relais"], TOUS + JEUX_EXT),
            ("a13-rosny", "Aire de Rosny-sur-Seine", 48.9930, 1.6800, "CROISSANT", "SERVICE",
             ["totalenergies", "brioche_doree", "relais", "mcdonalds"], TOUS + JEUX_EXT + JEUX_INT),
            ("a13-bouafles", "Aire de Bouafles", 49.1600, 1.3600, "DECROISSANT", "REPOS",
             [], ["TOILETTES", "TABLE_A_LANGER", "APPRECIATION_GENERALE"] + JEUX_EXT),
            ("a13-heudebouville-n", "Aire de Heudebouville Nord", 49.2050, 1.1900, "DECROISSANT", "SERVICE",
             ["esso", "la_croissanterie", "relais"], TOUS),
            ("a13-vironvay", "Aire de Vironvay", 49.1950, 1.1750, "CROISSANT", "SERVICE",
             ["totalenergies", "paul", "relais", "burger_king"], TOUS + JEUX_EXT),
            ("a13-bosgouet-s", "Aire de Bosgouet Sud", 49.3300, 0.8500, "CROISSANT", "SERVICE",
             ["shell", "mcdonalds", "carrefour_express"], TOUS + JEUX_EXT + JEUX_INT),
            ("a13-bosgouet-n", "Aire de Bosgouet Nord", 49.3350, 0.8550, "DECROISSANT", "SERVICE",
             ["shell", "la_croissanterie", "relais"], TOUS + JEUX_EXT),
            ("a13-bourneville", "Aire de Bourneville", 49.3650, 0.6000, "LES_DEUX", "REPOS",
             [], ["TOILETTES", "APPRECIATION_GENERALE"] + JEUX_EXT),
            ("a13-beuzeville", "Aire de Beuzeville", 49.3400, 0.3700, "LES_DEUX", "SERVICE",
             ["totalenergies", "brioche_doree", "relais"], TOUS + JEUX_EXT),
            ("a13-le-torquesne", "Aire du Pays d'Auge", 49.2700, 0.1400, "CROISSANT", "SERVICE",
             ["avia", "cote_france", "vival"], TOUS + JEUX_EXT),
            ("a13-dozule", "Aire de Dozulé", 49.2300, -0.0400, "DECROISSANT", "REPOS",
             [], ["TOILETTES", "TABLE_A_LANGER", "APPRECIATION_GENERALE"]),
            ("a13-giberville", "Aire de Giberville", 49.1850, -0.3000, "LES_DEUX", "SERVICE",
             ["esso", "arche", "relais"], TOUS + JEUX_EXT),
        ],
    },
    {
        "id": "A10",
        "nom": "A10",
        "libelle": "L'Aquitaine",
        "terminusDebut": "Paris",
        "terminusFin": "Bordeaux",
        "longueurKm": 557.0,
        "trace": [
            (48.7700, 2.2900), (48.6700, 2.2000), (48.5700, 2.0800), (48.4200, 1.9800),
            (48.2500, 1.9500), (48.0500, 1.9000), (47.9000, 1.8500), (47.8200, 1.7500),
            (47.6000, 1.4000), (47.4300, 1.1000), (47.3000, 0.7000), (47.0000, 0.5000),
            (46.6000, 0.3500), (46.3000, 0.3000), (45.9000, 0.1000), (45.6500, -0.1000),
            (45.2000, -0.3000), (44.8500, -0.5600),
        ],
        "aires": [
            ("a10-limours", "Aire de Limours", 48.6300, 2.1300, "CROISSANT", "SERVICE",
             ["totalenergies", "brioche_doree", "relais"], TOUS + JEUX_EXT),
            ("a10-orleans-saran", "Aire d'Orléans-Saran", 47.9500, 1.8800, "LES_DEUX", "SERVICE",
             ["totalenergies", "mcdonalds", "relais", "starbucks"], TOUS + JEUX_EXT + JEUX_INT),
            ("a10-beaugency", "Aire de Beaugency", 47.7700, 1.6300, "DECROISSANT", "SERVICE",
             ["esso", "la_croissanterie", "vival"], TOUS + JEUX_EXT),
            ("a10-blois-villerbon", "Aire de Blois-Villerbon", 47.6500, 1.3800, "CROISSANT", "SERVICE",
             ["shell", "paul", "relais"], TOUS + JEUX_EXT),
            ("a10-poitiers-chincé", "Aire de Poitiers-Chincé", 46.6300, 0.3300, "LES_DEUX", "SERVICE",
             ["totalenergies", "burger_king", "relais", "ibis_budget"], TOUS + JEUX_EXT + JEUX_INT),
            ("a10-saintes", "Aire de Saintes", 45.7500, -0.0500, "CROISSANT", "SERVICE",
             ["avia", "cote_france", "vival"], TOUS + JEUX_EXT),
        ],
    },
    {
        "id": "A6",
        "nom": "A6",
        "libelle": "Autoroute du Soleil",
        "terminusDebut": "Paris",
        "terminusFin": "Lyon",
        "longueurKm": 446.0,
        "trace": [
            (48.8000, 2.4000), (48.6500, 2.4500), (48.4500, 2.5500), (48.2500, 2.6800),
            (48.1000, 2.7200), (47.9000, 2.9000), (47.7000, 3.1500), (47.4500, 3.4500),
            (47.2500, 3.7000), (47.0500, 4.1000), (46.9000, 4.6000), (46.7800, 4.8500),
            (46.5000, 4.8500), (46.2000, 4.8000), (45.9000, 4.7500), (45.7600, 4.8600),
        ],
        "aires": [
            ("a6-nemours", "Aire de Nemours", 48.2600, 2.6600, "CROISSANT", "SERVICE",
             ["totalenergies", "paul", "relais"], TOUS + JEUX_EXT),
            ("a6-venoy-soleil", "Aire de Venoy-Grosse Pierre", 47.8100, 3.6100, "LES_DEUX", "SERVICE",
             ["esso", "mcdonalds", "relais"], TOUS + JEUX_EXT + JEUX_INT),
            ("a6-beaune-tailly", "Aire de Beaune-Tailly", 46.9700, 4.7700, "LES_DEUX", "SERVICE",
             ["totalenergies", "burger_king", "relais", "ibis_budget"], TOUS + JEUX_EXT + JEUX_INT),
            ("a6-dommartin", "Aire de Dommartin (Poulet de Bresse)", 46.3000, 4.8500, "DECROISSANT", "SERVICE",
             ["shell", "cote_france", "vival"], TOUS + JEUX_EXT),
            ("a6-dracé", "Aire de Dracé", 46.1300, 4.7500, "CROISSANT", "SERVICE",
             ["avia", "brioche_doree", "relais"], TOUS + JEUX_EXT),
        ],
    },
    {
        "id": "A7",
        "nom": "A7",
        "libelle": "Autoroute du Soleil",
        "terminusDebut": "Lyon",
        "terminusFin": "Marseille",
        "longueurKm": 313.0,
        "trace": [
            (45.7300, 4.8300), (45.5200, 4.8200), (45.3200, 4.8000), (45.0500, 4.8300),
            (44.9300, 4.8900), (44.5600, 4.7500), (44.2500, 4.7000), (44.0500, 4.8100),
            (43.9000, 4.8300), (43.6800, 4.9500), (43.5000, 5.1000), (43.3000, 5.3700),
        ],
        "aires": [
            ("a7-vienne-reventin", "Aire de Vienne-Reventin", 45.4800, 4.8100, "CROISSANT", "SERVICE",
             ["totalenergies", "brioche_doree", "relais"], TOUS + JEUX_EXT),
            ("a7-latitude-45", "Aire de Latitude 45", 45.0300, 4.8300, "LES_DEUX", "SERVICE",
             ["esso", "cote_france", "vival"], TOUS + JEUX_EXT),
            ("a7-montelimar", "Aire de Montélimar", 44.5300, 4.7600, "LES_DEUX", "SERVICE",
             ["totalenergies", "mcdonalds", "relais"], TOUS + JEUX_EXT + JEUX_INT),
            ("a7-mornas-village", "Aire de Mornas-Village", 44.2000, 4.7300, "DECROISSANT", "SERVICE",
             ["shell", "paul", "relais"], TOUS + JEUX_EXT),
            ("a7-lancon", "Aire de Lançon", 43.6000, 5.1300, "CROISSANT", "SERVICE",
             ["avia", "burger_king", "carrefour_express"], TOUS + JEUX_EXT),
        ],
    },
    {
        "id": "A1",
        "nom": "A1",
        "libelle": "Autoroute du Nord",
        "terminusDebut": "Paris",
        "terminusFin": "Lille",
        "longueurKm": 211.0,
        "trace": [
            (48.9000, 2.3800), (49.0000, 2.5000), (49.1500, 2.6000), (49.3500, 2.7500),
            (49.5500, 2.8500), (49.7500, 2.9000), (49.9000, 2.8500), (50.1000, 2.9500),
            (50.2900, 2.7800), (50.4500, 2.9500), (50.6300, 3.0600),
        ],
        "aires": [
            ("a1-vemars", "Aire de Vémars", 49.0700, 2.5600, "LES_DEUX", "SERVICE",
             ["totalenergies", "mcdonalds", "relais"], TOUS + JEUX_EXT),
            ("a1-ressons", "Aire de Ressons", 49.5500, 2.8600, "LES_DEUX", "SERVICE",
             ["esso", "paul", "relais", "b_and_b"], TOUS + JEUX_EXT + JEUX_INT),
            ("a1-assevillers", "Aire d'Assevillers", 49.9000, 2.8300, "LES_DEUX", "SERVICE",
             ["totalenergies", "burger_king", "relais", "starbucks"], TOUS + JEUX_EXT + JEUX_INT),
            ("a1-phalempin", "Aire de Phalempin", 50.5200, 3.0200, "CROISSANT", "SERVICE",
             ["shell", "brioche_doree", "vival"], TOUS + JEUX_EXT),
        ],
    },
]


def main():
    racine = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    dossier = os.path.join(racine, "app", "src", "main", "assets", "seed")
    os.makedirs(dossier, exist_ok=True)

    autoroutes, aires, liens = [], [], []
    for auto in AUTOROUTES:
        trace = auto["trace"]
        pks = pk_cumules(trace, auto["longueurKm"])
        autoroutes.append({
            "id": auto["id"],
            "nom": auto["nom"],
            "libelle": auto["libelle"],
            "terminusDebut": auto["terminusDebut"],
            "terminusFin": auto["terminusFin"],
            "longueurKm": auto["longueurKm"],
            "geometrie": [
                {"pk": round(pk, 1), "lat": p[0], "lon": p[1]} for p, pk in zip(trace, pks)
            ],
        })
        for aire_id, nom, lat, lon, sens, type_aire, enseignes, equipements in auto["aires"]:
            aires.append({
                "id": aire_id,
                "autorouteId": auto["id"],
                "nom": nom,
                "pk": pk_de_l_aire(trace, pks, (lat, lon)),
                "sens": sens,
                "type": type_aire,
                "lat": lat,
                "lon": lon,
                "equipements": equipements,
            })
            for enseigne_id in enseignes:
                liens.append({"aireId": aire_id, "enseigneId": enseigne_id, "ajoutParUtilisateur": False})

    aires.sort(key=lambda a: (a["autorouteId"], a["pk"]))

    ecrire(os.path.join(dossier, "autoroutes.json"), autoroutes)
    ecrire(os.path.join(dossier, "aires.json"), aires)
    ecrire(os.path.join(dossier, "enseignes.json"),
           [{"id": i, "nom": n, "categorie": c} for i, n, c in ENSEIGNES])
    ecrire(os.path.join(dossier, "aire_enseignes.json"), liens)
    print(f"{len(autoroutes)} autoroutes, {len(aires)} aires, {len(ENSEIGNES)} enseignes, {len(liens)} liens")


def ecrire(chemin, contenu):
    with open(chemin, "w", encoding="utf-8") as f:
        json.dump(contenu, f, ensure_ascii=False, indent=2)
        f.write("\n")


if __name__ == "__main__":
    main()
