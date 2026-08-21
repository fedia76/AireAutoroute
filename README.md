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

### Données livrées

`tools/generer_donnees.py` régénère les fichiers JSON à partir des tracés et des listes d'aires
qu'il contient :

```bash
python3 tools/generer_donnees.py
```

Le script calcule les PK cumulés le long du tracé (mis à l'échelle de la longueur officielle de
l'autoroute) puis projette chaque aire sur ce tracé pour en déduire son PK. Géométrie et PK restent
ainsi toujours cohérents.

⚠️ Le jeu de données actuel (A13, A10, A6, A7, A1) est une **amorce indicative** : les tracés sont
simplifiés à une vingtaine de points et les PK sont donc approchés (quelques kilomètres d'écart avec
les bornes réelles). Il est fait pour être corrigé fichier par fichier, ou remplacé par un import
de données ouvertes.

## Du GPS au point kilométrique

C'est la question centrale du mode automatique. La conversion se fait dans
[`LocalisateurPk`](app/src/main/java/com/aireautoroute/app/geo/LocalisateurPk.kt) :

1. Chaque autoroute est décrite par une **poly-ligne** de points de référence portant chacun leur
   PK (table `autoroute`, champ `geometrie`).
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

Deux limites assumées : la précision du PK dépend de la finesse du tracé, et deux autoroutes
parallèles à moins de quelques centaines de mètres peuvent être confondues. Un tracé plus fin (par
exemple issu d'OpenStreetMap) améliore les deux points sans changer une ligne d'algorithme.

## Construction

Le projet se construit avec le wrapper Gradle, sans Android Studio :

```bash
./gradlew testDebugUnitTest   # tests unitaires (JVM)
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # APK de release non signé
```

- `minSdk` 26 (Android 8.0), `targetSdk`/`compileSdk` 35, JDK 17.
- Aucune dépendance aux services Google : la localisation passe par le `LocationManager` du
  système, l'application fonctionne donc aussi sans Play Services.

### Intégration continue

Le workflow [`.github/workflows/android.yml`](.github/workflows/android.yml) s'exécute **à chaque
push** (toutes branches), sur les pull requests et à la demande :

1. tests unitaires,
2. `assembleDebug` + `assembleRelease`,
3. publication des deux APK en artefacts (`apk-debug`, `apk-release-non-signe`), téléchargeables
   depuis l'onglet *Actions* du dépôt.

L'APK de debug est directement installable sur un téléphone. L'APK de release est **non signé** :
il faut le signer avec sa propre clé (`apksigner`) avant installation. Un push de tag `v*` crée en
plus une release GitHub contenant les APK.

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
│   └── SuiviPosition.kt     flux de positions (LocationManager)
└── ui/                      écrans Compose et composants (étoiles)
```

## Suites possibles

- Remplacer les fichiers par Room, en gardant les mêmes tables.
- Partager les notations entre utilisateurs (API + synchronisation).
- Importer des tracés et des aires depuis OpenStreetMap pour couvrir tout le réseau.
- Filtrer la liste (« seulement les aires avec jeux intérieurs notés 4+ pour les 3-6 ans »).
- Pondérer le consensus par l'ancienneté des déclarations (un équipement peut fermer).
