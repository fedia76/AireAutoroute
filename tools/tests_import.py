#!/usr/bin/env python3
"""Tests de la chaîne d'import. Usage : python3 tools/tests_import.py"""

import json
import os
import re
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from importlib import import_module

lambert93 = import_module("import.lambert93")
bornes = import_module("import.bornes")
wikisara = import_module("import.wikisara")
osm = import_module("import.osm")
enseignes_icones = import_module("import.enseignes")

RACINE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCES = os.path.join(RACINE, "donnees", "sources")
SEED = os.path.join(RACINE, "app", "src", "main", "assets", "seed")
MODELES = os.path.join(
    RACINE, "app", "src", "main", "java", "com", "aireautoroute", "app", "data", "Modeles.kt"
)


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

    def test_les_equipements_annonces_sont_des_criteres_connus(self):
        connus = {
            "AIRE_JEUX_INTERIEURE", "AIRE_JEUX_EXTERIEURE",
            "TOILETTES", "TABLE_A_LANGER", "STATION_SERVICE",
        }
        for aire in self.aires:
            self.assertLessEqual(set(aire["equipements"]), connus, aire["id"])
            self.assertEqual(len(aire["equipements"]), len(set(aire["equipements"])), aire["id"])

    def test_les_liens_d_enseigne_pointent_sur_des_lignes_existantes(self):
        with open(os.path.join(SEED, "enseignes.json"), encoding="utf-8") as fichier:
            enseignes = json.load(fichier)
        with open(os.path.join(SEED, "aire_enseignes.json"), encoding="utf-8") as fichier:
            liens = json.load(fichier)
        identifiants_aire = {a["id"] for a in self.aires}
        identifiants_enseigne = [e["id"] for e in enseignes]
        self.assertEqual(len(identifiants_enseigne), len(set(identifiants_enseigne)))
        for lien in liens:
            self.assertIn(lien["aireId"], identifiants_aire)
            self.assertIn(lien["enseigneId"], set(identifiants_enseigne))


class TestIconesEnseignes(unittest.TestCase):
    """Le jeu de pictogrammes, et son application au catalogue livré."""

    @classmethod
    def setUpClass(cls):
        cls.table = enseignes_icones.lire_table(os.path.join(SOURCES, "icones_enseignes.json"))
        with open(os.path.join(SEED, "enseignes.json"), encoding="utf-8") as fichier:
            cls.enseignes = json.load(fichier)

    def test_le_jeu_est_celui_de_l_application(self):
        """Une icône que l'application ne sait pas dessiner ne doit pas entrer dans les données."""
        source = open(MODELES, encoding="utf-8").read()
        corps = source.split("enum class IconeEnseigne(val libelle: String) {", 1)[1]
        corps = corps.split("}", 1)[0]
        declarees = re.findall(r"^\s{4}([A-Z_]+)\(", corps, re.MULTILINE)
        self.assertEqual(list(enseignes_icones.ICONES), declarees)

    def test_la_table_ne_range_que_des_enseignes_du_catalogue(self):
        catalogue = {e["id"] for e in self.enseignes}
        self.assertEqual([], sorted(set(self.table) - catalogue))

    def test_toute_enseigne_livree_porte_une_icone_connue(self):
        for enseigne in self.enseignes:
            self.assertIn("icone", enseigne, enseigne["id"])
            self.assertIn(enseigne["icone"], enseignes_icones.ICONES, enseigne["id"])

    def test_une_enseigne_hors_table_suit_sa_categorie(self):
        catalogue = [{"id": "inconnue", "nom": "Inconnue", "categorie": "RESTAURATION"}]
        enseignes_icones.appliquer(catalogue, {})
        self.assertEqual("RESTAURANT", catalogue[0]["icone"])

    def test_une_enseigne_que_rien_ne_range_reste_sans_icone(self):
        catalogue = [{"id": "inconnue", "nom": "Inconnue", "categorie": "AUTRE"}]
        self.assertEqual([], enseignes_icones.appliquer(catalogue, {}))
        self.assertNotIn("icone", catalogue[0])

    def test_une_icone_hors_du_jeu_fixe_est_refusee(self):
        """Le jeu est fixe : une valeur inventée doit arrêter l'import, pas le traverser."""
        with tempfile.NamedTemporaryFile("w", suffix=".json", encoding="utf-8") as fichier:
            json.dump({"x": "GARAGE"}, fichier)
            fichier.flush()
            with self.assertRaises(ValueError):
                enseignes_icones.lire_table(fichier.name)


class TestOsm(unittest.TestCase):
    """Le rattachement d'OpenStreetMap au référentiel."""

    def test_normalisation_des_noms(self):
        self.assertEqual(osm.normaliser_nom("Aire de Vironvay Nord"), "vironvay nord")
        self.assertEqual(osm.normaliser_nom("Vironvay-Nord"), "vironvay nord")
        self.assertEqual(osm.normaliser_nom("Aire de repos de Serres-Morlaàs (Sud)"),
                         "serres morlaas sud")

    def test_les_variantes_de_graphie_partagent_une_cle(self):
        self.assertEqual(osm.identifiant_enseigne("L'Atelier Charal"),
                         osm.identifiant_enseigne("L'atelier Charal"))

    def test_un_renommage_range_la_marque_sous_son_nom_actuel(self):
        self.assertEqual(osm.identifiant_enseigne("Total"),
                         osm.identifiant_enseigne("TotalEnergies"))

    def test_le_cote_de_la_chaussee_suit_le_sens_des_pk_croissants(self):
        """Sur un tracé plein est, une aire au sud dessert le sens des PK croissants."""
        trace = bornes.TraceAutoroute(numero="A1", bornes=[
            bornes.Borne(pk=0.0, lat=48.0, lon=2.0, cumul_m=0),
            bornes.Borne(pk=10.0, lat=48.0, lon=2.134, cumul_m=10_000),
        ])
        au_sud = osm.situer(trace, 47.998, 2.067)
        au_nord = osm.situer(trace, 48.002, 2.067)
        self.assertEqual(au_sud[1], "CROISSANT")
        self.assertEqual(au_nord[1], "DECROISSANT")
        self.assertAlmostEqual(au_sud[0], 5.0, delta=0.5)

    def test_les_equipements_sont_traduits_dans_le_vocabulaire_de_l_application(self):
        objets = [
            {"tags": {"amenity": "toilets", "changing_table": "yes"}},
            {"tags": {"amenity": "fuel", "brand": "Esso"}},
            {"tags": {"leisure": "playground"}},
        ]
        declares = osm.equipements_declares(objets)
        self.assertIn("TOILETTES", declares)
        self.assertIn("TABLE_A_LANGER", declares)
        self.assertIn("STATION_SERVICE", declares)
        self.assertIn("AIRE_JEUX_EXTERIEURE", declares)
        self.assertNotIn("AIRE_JEUX_INTERIEURE", declares)

    def test_une_aire_de_jeux_couverte_est_interieure(self):
        declares = osm.equipements_declares([{"tags": {"leisure": "playground", "covered": "yes"}}])
        self.assertEqual(declares, ["AIRE_JEUX_INTERIEURE"])

    def test_un_objet_sans_marque_ne_donne_pas_d_enseigne(self):
        self.assertEqual(osm.marques_relevees([{"tags": {"amenity": "toilets"}}]), [])
        self.assertEqual(
            osm.marques_relevees([{"tags": {"amenity": "fuel", "brand": "Avia"}}]),
            [("Avia", "CARBURANT")],
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
