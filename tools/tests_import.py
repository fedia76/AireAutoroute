#!/usr/bin/env python3
"""Tests de la chaîne d'import. Usage : python3 tools/tests_import.py"""

import json
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from importlib import import_module

lambert93 = import_module("import.lambert93")
bornes = import_module("import.bornes")
wikisara = import_module("import.wikisara")

RACINE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCES = os.path.join(RACINE, "donnees", "sources")
SEED = os.path.join(RACINE, "app", "src", "main", "assets", "seed")


class TestLambert93(unittest.TestCase):
    def test_origine_de_la_projection(self):
        """Sur le méridien de référence, la longitude vaut exactement 3° est."""
        _, longitude = lambert93.vers_wgs84(700000.0, 6600000.0)
        self.assertAlmostEqual(3.0, longitude, places=6)

    def test_point_connu(self):
        """La première borne de l'A13 tombe porte d'Auteuil, à Paris."""
        latitude, longitude = lambert93.vers_wgs84(645322.63, 6860966.26)
        self.assertAlmostEqual(48.846, latitude, delta=0.01)
        self.assertAlmostEqual(2.255, longitude, delta=0.01)


class TestBornes(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.traces = bornes.lire_traces(os.path.join(SOURCES, "bornes2025.csv"))

    def test_l_a13_est_complete(self):
        a13 = self.traces["A13"]
        self.assertGreater(len(a13.bornes), 200)
        self.assertAlmostEqual(0.0, a13.pk_min, delta=1)
        self.assertAlmostEqual(222.0, a13.pk_max, delta=2)

    def test_les_points_kilometriques_sont_croissants(self):
        for numero, trace in self.traces.items():
            pks = [b.pk for b in trace.bornes]
            self.assertEqual(sorted(pks), pks, f"{numero} : PK non croissants")

    def test_interpolation_d_un_pk(self):
        a13 = self.traces["A13"]
        latitude, longitude = bornes.position_du_pk(a13, 93.0)
        # Aire de Vironvay, entre Louviers et Vernon.
        self.assertAlmostEqual(49.21, latitude, delta=0.05)
        self.assertAlmostEqual(1.22, longitude, delta=0.05)

    def test_pk_hors_trace(self):
        self.assertIsNone(bornes.position_du_pk(self.traces["A13"], 9_999.0))


class TestWikisara(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.catalogue = wikisara.lire_catalogue(os.path.join(SOURCES, "wikisara_aires.csv"))

    def test_sens_croissant_deduit_de_l_ordre_du_tableau(self):
        sens = self.catalogue.sens[("A13", "Sens Paris - Caen")]
        self.assertTrue(sens.croissant)
        self.assertEqual(("Paris", "Caen"), (sens.depart, sens.arrivee))
        self.assertFalse(self.catalogue.sens[("A13", "Sens Caen - Paris")].croissant)

    def test_slug(self):
        self.assertEqual("vironvay-nord", wikisara.slug("Vironvay Nord"))
        self.assertEqual("beuzeville-bpv", wikisara.slug("Beuzeville (BPV)"))
        self.assertEqual("l-epitre", wikisara.slug("L'Épitre"))


class TestFichiersLivres(unittest.TestCase):
    """Contrôles sur les fichiers réellement embarqués dans l'application."""

    @classmethod
    def setUpClass(cls):
        with open(os.path.join(SEED, "autoroutes.json"), encoding="utf-8") as f:
            cls.autoroutes = json.load(f)
        with open(os.path.join(SEED, "aires.json"), encoding="utf-8") as f:
            cls.aires = json.load(f)

    def test_volume(self):
        self.assertGreater(len(self.autoroutes), 70)
        self.assertGreater(len(self.aires), 1_000)

    def test_identifiants_uniques(self):
        identifiants = [a["id"] for a in self.aires]
        self.assertEqual(len(identifiants), len(set(identifiants)))

    def test_chaque_aire_est_placee_sur_son_autoroute(self):
        longueurs = {a["id"]: a["longueurKm"] for a in self.autoroutes}
        for aire in self.aires:
            self.assertIn(aire["autorouteId"], longueurs, aire["id"])
            self.assertIsNotNone(aire["lat"], aire["id"])
            self.assertLessEqual(aire["pk"], longueurs[aire["autorouteId"]] + 1, aire["id"])

    def test_sens_connus(self):
        for aire in self.aires:
            self.assertIn(aire["sens"], {"CROISSANT", "DECROISSANT", "LES_DEUX"})

    def test_les_aires_de_service_annoncent_leurs_equipements(self):
        service = [a for a in self.aires if a["type"] == "SERVICE"]
        self.assertGreater(len(service), 300)
        for aire in service:
            self.assertIn("STATION_SERVICE", aire["equipements"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
