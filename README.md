# Aires d'autoroute

Application Android (Kotlin / Jetpack Compose) pour trouver les aires situées **devant soi** sur
l'autoroute et les **noter**, en particulier quand on voyage avec des enfants.

## Ce que fait l'application

1. **Je dis où je suis.**
   - *À la main* : « je suis sur l'A13, PK 55, en direction de Caen ».
   - *Bouton « Me localiser »* : une localisation ponctuelle déduit l'autoroute, le PK et le sens
     du GPS du téléphone (voir « Du GPS au point kilométrique » plus bas), et remplit le
     formulaire manuel, qui reste corrigeable.
2. **Les prochaines aires sont listées** (200 km max) avec une vue résumée : distance, note
   générale, notes des aires de jeux par tranche d'âge, équipements, enseignes.
   Seules les aires accessibles depuis la chaussée empruntée sont affichées.
3. **Un clic ouvre le détail** : statut de chaque équipement, notes par critère, notes par tranche
   d'âge, commentaires, enseignes (qu'on peut compléter), et un formulaire de contribution.
4. **Une vue carte** montre l'itinéraire et les aires autour de soi : la portion qui reste à
   parcourir se détache de celle qui est derrière, un appui sur une aire l'ouvre. Elle est dessinée
   à partir des tracés embarqués — **aucune tuile n'est téléchargée**, donc elle fonctionne sans
   réseau, ce qui est la moindre des choses en voiture.

### Trois habillages au choix

L'icône palette, en haut de l'écran d'accueil, ouvre le choix du thème. Le réglage est conservé
d'une session à l'autre (préférences Android, pas le fichier de données).

| Thème | Parti pris | Style de liste |
| --- | --- | --- |
| **Signalétique** (par défaut) | le bleu des panneaux d'autoroute, angles francs, distances en cartouche | cartes bordées d'un liseré coloré |
| **Carnet de route** | fond papier, titres en serif, avis mis en avant | cartes souples, notes des jeux en barres |
| **Copilote** | tableau de bord sombre, chiffres en chasse fixe, lecture de nuit | lignes denses séparées par des filets |

Un thème regroupe une palette (claire et sombre), une typographie, des formes et un style de liste
— la structure des écrans, elle, ne change pas. *Signalétique* et *Carnet* suivent le réglage
clair/sombre du téléphone ; *Copilote* n'existe qu'en sombre, c'est son propos. Tout est dans
`ui/theme/ThemeApp.kt` : ajouter un quatrième thème revient à ajouter une entrée à l'énumération.

Faute de pouvoir embarquer les fontes des maquettes (Barlow, Source Serif, IBM Plex) sans les
télécharger, les thèmes s'appuient sur les familles du système — sans-serif, serif et monospace —
en gardant la structure des trois ramps typographiques.

### Critères

| Critère | Noté | Détail |
| --- | --- | --- |
| Enseignes | non | simplement listées, l'utilisateur peut en ajouter |
| Aire de jeux intérieure | 1 à 5 ★ | par tranche d'âge : 0-3 ans, 3-6 ans, 6-12 ans |
| Aire de jeux extérieure | 1 à 5 ★ | par tranche d'âge : 0-3 ans, 3-6 ans, 6-12 ans |
| Toilettes | 1 à 5 ★ | |
| Station-service | 1 à 5 ★ | |
| Table à langer | 1 à 5 ★ | |
| Appréciation générale | 1 à 5 ★ | |

Chaque note peut porter un commentaire libre et un nom d'auteur.

Pour chaque **équipement** (donc tout sauf l'appréciation générale), le formulaire demande d'abord
« cet équipement existe-t-il ? » — **oui / non / ne sais pas**. Les étoiles n'apparaissent que si le
contributeur répond *oui* : on ne note pas ce qu'on n'a pas vu.

### L'algorithme de présence

Une réponse isolée ne fait pas foi. L'application ne déclare un équipement présent que si
**au moins 2 déclarations concordantes** (`SEUIL_CONFIRMATION`) le disent, et qu'elles sont
majoritaires. Les « ne sais pas » ne comptent pas. D'où cinq statuts :

| Statut | Quand |
| --- | --- |
| **Confirmé par les visiteurs** | ≥ 2 déclarations « présent », majoritaires — c'est le seul cas où l'app affirme la présence (coche ✓) |
| **À confirmer** | au moins une déclaration « présent », mais pas encore le seuil |
| **Annoncé, non vérifié** | présent dans les données livrées, jamais constaté sur place |
| **Absent** | ≥ 2 déclarations « absent », majoritaires (ou une seule si rien ne le contredit) |
| **Non renseigné** | ni donnée livrée, ni déclaration |

Un équipement *Absent* disparaît de la vue résumée et ses notes ne sont plus mises en avant, même
si l'exploitant l'annonçait : les visiteurs ont le dernier mot. Le seuil est une constante unique
(`SEUIL_CONFIRMATION` dans `data/Vues.kt`), à relever quand il y aura plus de contributeurs.

## Modèle de données

Pas de base de données pour l'instant : les données sont dans des fichiers JSON, mais **structurées
comme des tables**, pour que le passage à Room ne touche que `DepotDonnees`.

| Table | Fichier | Contenu |
| --- | --- | --- |
| `autoroute` | `assets/seed/autoroutes.json` | id, nom, libellé, terminus de départ et d'arrivée, longueur, géométrie (points GPS + PK) |
| `aire` | `assets/seed/aires.json` | id, autoroute, nom, PK, sens desservi, type (service/repos), coordonnées, équipements annoncés |
| `enseigne` | `assets/seed/enseignes.json` | id, nom, catégorie (carburant, restauration, boutique, hôtel) |
| `aire_enseigne` | `assets/seed/aire_enseignes.json` | table de liaison **many-to-many** aire ↔ enseigne |
| `notation` | `filesDir/donnees_utilisateur.json` | id, aire, critère, tranche d'âge, note 1-5, commentaire, auteur, date |
| `declaration_equipement` | `filesDir/donnees_utilisateur.json` | id, aire, critère, présence (oui/non), auteur, date |

Les quatre premiers fichiers sont livrés dans l'APK (lecture seule). Le cinquième est écrit dans le
stockage privé de l'application et contient aussi les enseignes ajoutées par l'utilisateur et leurs
liaisons (`liensEnseignes`), avec le drapeau `ajoutParUtilisateur` qui permet de les distinguer du
catalogue livré.

Le **sens** est modélisé par le PK : `CROISSANT` = les PK augmentent = on va vers le terminus
d'arrivée (pour l'A13 : vers Caen), `DECROISSANT` = l'inverse, `LES_DEUX` pour une aire accessible
depuis les deux chaussées.

### D'où viennent les données

**89 autoroutes, 11 000 km de tracé et 1 092 aires**, importés de deux sources ouvertes
versionnées dans `donnees/sources/` :

| Source | Licence | Ce qu'elle apporte |
| --- | --- | --- |
| [Bornage du réseau routier national](https://www.data.gouv.fr/datasets/bornage-du-reseau-routier-national/) (data.gouv.fr) | Licence Ouverte | le tracé de chaque autoroute — une borne par kilomètre — et le PK **affiché sur les panneaux** |
| [WikiSara](https://routes.fandom.com) | CC BY-SA | la liste des aires : nom, sens, type, point kilométrique |
| `donnees/sources/enseignes.json` | — | référentiel de saisie des enseignes, tenu à la main |

Le bornage donne, pour chaque borne, deux abscisses : la distance depuis l'origine du tracé et le
numéro inscrit sur la borne. Les deux ne coïncident pas — une autoroute peut prolonger le
kilométrage d'une autre, ou décaler son origine — et c'est le **numéro de borne** qui est retenu,
puisque c'est celui que lit l'automobiliste. Chaque aire est ensuite placée sur le tracé en
interpolant son PK entre les deux bornes qui l'encadrent : les deux sources parlent la même langue,
celle des panneaux.

Pour régénérer :

```bash
python3 tools/generer_donnees.py            # écrit les fichiers et donnees/rapport_import.md
python3 tools/generer_donnees.py --verifier  # contrôle sans rien écrire
python3 tools/tests_import.py                # tests de la chaîne d'import
```

Le script refuse d'écrire si un contrôle échoue : identifiants en double, aire sans position, PK
hors tracé, kilométrage non croissant, ou chute de plus de 20 % du nombre d'aires. Ce qui est
écarté est listé dans [`donnees/rapport_import.md`](donnees/rapport_import.md) — aujourd'hui
275 km de tracé (là où le bornage décrit une autoroute en tronçons dont le kilométrage repart) et
33 aires dont le PK ne tombe pas sur le tracé retenu.

`tools/scrapper_wikisara.py` sert à rafraîchir le CSV des aires ; il n'est pas rejoué à chaque
import, le CSV versionné fait foi.

**Ce que les données ne disent pas encore** : aucune enseigne n'est rattachée à une aire, et les
seuls équipements annoncés sont la station-service et les sanitaires des aires de service — parce
que c'est ce qui définit ce type d'aire, pas parce qu'on les a vérifiés. Ils restent « annoncés »
jusqu'à ce que deux visiteurs les confirment. La passe OpenStreetMap viendra combler ce manque.

## Du GPS au point kilométrique

C'est la question centrale du mode automatique. La conversion se fait dans
[`LocalisateurPk`](app/src/main/java/com/aireautoroute/app/geo/LocalisateurPk.kt) :

1. Chaque autoroute est décrite par une **poly-ligne** de points de référence portant chacun leur
   PK — ce sont les bornes du bord de route, une par kilomètre (table `autoroute`, champ
   `geometrie`).
2. Le point GPS est **projeté orthogonalement** sur chaque segment de chaque poly-ligne. Le calcul
   se fait dans un repère plan local (projection équirectangulaire centrée sur le segment) : à
   l'échelle de quelques kilomètres, l'erreur est négligeable et c'est beaucoup moins coûteux
   qu'un calcul géodésique.
3. On garde le segment le plus proche. Si l'écart dépasse **3 km**, on considère qu'on n'est sur
   aucune autoroute connue.
4. Le **PK est interpolé** linéairement entre les deux extrémités du segment retenu.
5. Le **sens** vient du cap GPS : on le compare à l'azimut du segment orienté dans le sens des PK
   croissants. Moins de 90° d'écart → sens croissant, sinon décroissant. Le cap n'étant fiable
   qu'en mouvement, il est ignoré sous ~10 km/h ; dans ce cas l'application le signale et le sens
   reste corrigeable d'un bouton (« Inverser »).

Comme le PK de l'utilisateur et celui des aires sont mesurés sur le même ruban et avec la même
méthode, la distance affichée (« dans 5 km ») reste juste même là où le tracé est grossier. Et
puisque l'échelle est celle des bornes officielles, la saisie manuelle « je suis au PK 55 »
correspond bien au panneau que l'automobiliste a sous les yeux.

Deux limites assumées : entre deux bornes, la position est interpolée en ligne droite, donc
imprécise de quelques dizaines de mètres dans les courbes ; et deux autoroutes parallèles séparées
de moins de quelques centaines de mètres peuvent être confondues.

## Construction

Le projet se construit avec le wrapper Gradle, sans Android Studio :

```bash
./gradlew testDebugUnitTest   # tests unitaires (JVM)
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # APK de release (signé si la clé est configurée)
./gradlew bundleRelease       # app/build/outputs/bundle/release/app-release.aab
```

- `minSdk` 26 (Android 8.0), `targetSdk`/`compileSdk` 36, JDK 17.
- La release est minifiée (R8) et réduite ; les règles de conservation couvrent
  kotlinx.serialization, seul mécanisme réflexif du projet.
- La signature est lue dans l'environnement ou dans `~/.gradle/gradle.properties` ; sans clé, la
  construction aboutit à un binaire non signé. Voir [docs/PUBLICATION.md](docs/PUBLICATION.md).
- Aucune dépendance aux services Google : la localisation passe par le `LocationManager` du
  système, l'application fonctionne donc aussi sans Play Services.

### Intégration continue

Le workflow [`.github/workflows/android.yml`](.github/workflows/android.yml) s'exécute **à chaque
push** (toutes branches), sur les pull requests et à la demande :

1. tests unitaires,
2. `assembleDebug` + `assembleRelease` + `bundleRelease`,
3. publication des binaires en artefacts (`apk-debug`, `apk-release`, `aab-release`, `empreintes`),
   téléchargeables depuis l'onglet *Actions* du dépôt.

Un second workflow, [`donnees.yml`](.github/workflows/donnees.yml), régénère les données **à la
demande** (déclenchement manuel, avec une option pour rejouer d'abord le scraping de WikiSara) et
ouvre une pull request portant le rapport d'import en description : le diff est ce qui permet de
refuser un import qui aurait mal tourné. Les données ne sont jamais régénérées pendant un build.

L'APK de debug est directement installable sur un téléphone. L'APK de release l'est aussi dès que
les secrets de signature sont configurés dans le dépôt ; sinon la CI le produit non signé et le
signale dans le résumé du run. Un push de tag `v*` crée en plus une release GitHub contenant l'APK
de release.

Le fichier `.aab` est le format attendu par la Play Console — il ne s'installe pas directement sur
un téléphone. La marche à suivre pour la publication est décrite dans
[docs/PUBLICATION.md](docs/PUBLICATION.md).

## Structure du dépôt

```
donnees/
├── sources/                 les données d'entrée, versionnées (bornage, WikiSara, enseignes)
└── rapport_import.md        ce que le dernier import a retenu et écarté
tools/
├── generer_donnees.py       point d'entrée de l'import
├── tests_import.py          tests de la chaîne d'import
├── scrapper_wikisara.py     rafraîchissement du catalogue des aires
└── import/                  lecture des sources, conversion Lambert 93, construction
```

## Structure du code

```
app/src/main/java/com/aireautoroute/app/
├── MainActivity.kt          navigation (accueil → détail → notation)
├── AppViewModel.kt          état de l'écran, position, enregistrement des notes
├── data/
│   ├── Modeles.kt           les « tables » et les énumérations (critères, tranches d'âge, sens)
│   ├── DepotDonnees.kt      lecture des assets, lecture/écriture du fichier utilisateur
│   └── Vues.kt              consensus de présence, prochaines aires, moyennes par tranche d'âge
├── geo/
│   ├── LocalisateurPk.kt    GPS → autoroute + PK + sens
│   ├── ProjectionCarte.kt   projection locale et fenêtre d'affichage de la carte
│   └── SuiviPosition.kt     flux de positions (LocationManager)
└── ui/                      écrans Compose et composants (étoiles)
```

## Suites possibles

- Remplacer les fichiers par Room, en gardant les mêmes tables.
- Partager les notations entre utilisateurs (API + synchronisation).
- Enrichir les aires depuis OpenStreetMap : équipements réellement présents, aires de jeux,
  tables à langer et enseignes.
- Filtrer la liste (« seulement les aires avec jeux intérieurs notés 4+ pour les 3-6 ans »).
- Pondérer le consensus par l'ancienneté des déclarations (un équipement peut fermer).
