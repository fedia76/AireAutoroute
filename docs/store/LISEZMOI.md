# Visuels de la fiche Play Store

| Fichier | Usage |
| --- | --- |
| `icone-512.png` | icône de la fiche, 512 × 512, PNG 32 bits opaque |
| `bandeau-1024x500.png` | image de présentation, 1024 × 500, sans transparence |
| `generer_visuels.py` | produit les deux, **et** le vecteur du lanceur |

## Pourquoi un script plutôt que des fichiers seuls

L'icône du Store et celle du lanceur montrent le même motif. Les tenir à jour séparément, c'est
les laisser diverger : une retouche d'un côté s'oublie de l'autre, et l'utilisateur qui installe
l'application ne retrouve pas sur son écran d'accueil l'image qu'il a vue sur la fiche.

Le script décrit la géométrie **une fois**, en coordonnées normalisées, puis la décline :

- en PNG pour le Store, par multiplication à la taille de la toile ;
- en `pathData` pour `app/src/main/res/drawable/ic_launcher_foreground.xml`, par projection
  dans les 72 unités centrales du viewport de 108 — la seule zone que le masque de l'icône
  adaptative laisse toujours visible, quelle que soit la forme imposée par le lanceur.

Toute retouche du motif se fait donc dans le script, jamais dans les fichiers produits.

## Le motif

Le pictogramme du panneau d'aire de repos : le sol, le pin, la table de pique-nique. Le tracé est
simplifié — à la taille d'une icône, le détail des branches du panneau réglementaire se referme en
une tache. L'étoile ambre, dans le ciel, dit la notation ; elle occupe la seule zone libre de la
composition, le pin tenant la droite et la table le bas.

Le bandeau ne reprend que le pictogramme : y ajouter l'étoile ferait doublon avec la rangée
d'étoiles du bloc de texte.

## Exécution

```sh
pip install Pillow
# Outfit (SIL OFL) attendu dans le dossier « polices », pour le seul bandeau
python3 docs/store/generer_visuels.py
```

## Textes de la fiche

Ils vivent dans [`textes-fiche.md`](textes-fiche.md), pour que les chiffres annoncés puissent être
recoupés avec les données livrées.
