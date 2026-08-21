"""Conversion Lambert 93 (EPSG:2154) vers WGS84.

Le référentiel de bornage du réseau routier national donne les coordonnées en Lambert 93 ;
l'application, elle, travaille en latitude/longitude. Les constantes sont celles publiées par
l'IGN pour la projection conique conforme sécante sur l'ellipsoïde GRS80.
"""

import math

N = 0.7256077650
C = 11754255.426
XS = 700000.0
YS = 12655612.050
E = 0.0818191910428158
LON0 = math.radians(3.0)


def vers_wgs84(x: float, y: float) -> tuple[float, float]:
    """(x, y) en mètres Lambert 93 -> (latitude, longitude) en degrés."""
    rayon = math.hypot(x - XS, y - YS)
    gamma = math.atan((x - XS) / (YS - y))
    longitude = LON0 + gamma / N
    latitude_iso = -1 / N * math.log(abs(rayon / C))

    # La latitude ne s'obtient pas en forme fermée : quelques itérations suffisent à converger.
    latitude = 2 * math.atan(math.exp(latitude_iso)) - math.pi / 2
    for _ in range(12):
        sin_lat = math.sin(latitude)
        latitude = 2 * math.atan(
            ((1 + E * sin_lat) / (1 - E * sin_lat)) ** (E / 2) * math.exp(latitude_iso)
        ) - math.pi / 2

    return math.degrees(latitude), math.degrees(longitude)
