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

Le fichier est brut : chaque aire y porte l'autoroute et le point kilométrique déduits de nos
tracés, mais pas encore d'identité. Le rattachement au référentiel se fait hors ligne, dans
`tools/import/osm.py`, sur le nom, le point kilométrique et le côté de la chaussée.

Le fichier est facultatif : la chaîne d'import tourne sans lui, les aires n'annonçant alors que
ce que leur type implique.

## `enseignes.json`

Référentiel des enseignes tenu à la main. Il sert de liste de saisie dans l'application, et
l'import l'augmente des marques relevées dans OpenStreetMap — à partir de trois aires, en deçà
de quoi une marque est plus probablement une saisie isolée qu'une enseigne. Ce fichier-ci n'est
jamais réécrit par l'import : c'est le catalogue livré dans `assets/seed/` qui porte l'ajout.
