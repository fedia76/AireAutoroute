# Sources des données

Ces fichiers sont les **entrées** de la chaîne d'import (`tools/generer_donnees.py`). Ils sont
versionnés pour que la génération soit reproductible et ne dépende d'aucun service en ligne.

## `bornes2025.csv`

Bornage du réseau routier national, millésime 2025, publié sur data.gouv.fr sous
**Licence Ouverte / Open Licence** :
<https://www.data.gouv.fr/datasets/bornage-du-reseau-routier-national/>

Chaque ligne décrit une borne : ses coordonnées en Lambert 93, sa distance depuis l'origine du
tracé (`cumul`) et le numéro qui y est inscrit (`pr`). Seuls les codes route courts (`A0013`)
décrivent un itinéraire complet ; les codes longs (`27A801315CD`) sont des bretelles d'échangeur.

## `wikisara_aires.csv`

Catalogue des aires extrait de [WikiSara](https://routes.fandom.com), sous **CC BY-SA**. Produit
par `tools/scrapper_wikisara.py`. Le CSV versionné fait foi : le script n'est rejoué que pour le
rafraîchir, à la demande.

## `osm_aires.json`

Aires d'autoroute et équipements relevés dans [OpenStreetMap](https://www.openstreetmap.org),
sous **licence ODbL** — les données doivent être attribuées à ses contributeurs, et toute base
qui en dérive reste sous la même licence. Produit par `tools/scrapper_osm.py`, qui interroge
l'API Overpass.

Le fichier est brut : les objets n'y sont pas encore rattachés aux aires du référentiel. Ce
rattachement se fait hors ligne, dans la chaîne d'import.

## `enseignes.json`

Référentiel des enseignes tenu à la main. Il sert de liste de saisie dans l'application ; aucune
enseigne n'est rattachée à une aire tant que la passe OpenStreetMap n'est pas branchée.
