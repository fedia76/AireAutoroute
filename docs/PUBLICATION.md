# Publier l'application

Ce document décrit la chaîne de publication : la clé de signature, les binaires produits par
l'intégration continue, puis le parcours Play Store avec un **compte développeur personnel**.

## 1. La clé de signature

Google Play signe lui-même les binaires distribués (*Play App Signing*). Ce que tu fournis est une
**clé d'upload** : elle prouve que c'est bien toi qui téléverses. Elle ne peut pas être remplacée
sans passer par le support Google, donc **sa perte est un incident** : sauvegarde-la hors du dépôt,
et hors de la machine qui sert à construire.

Génération, une fois pour toutes :

```sh
keytool -genkeypair -v \
  -keystore upload.jks \
  -storetype PKCS12 \
  -alias aireautoroute-upload \
  -keyalg RSA -keysize 4096 \
  -validity 10000
```

`-validity 10000` (≈ 27 ans) est la valeur recommandée par Google : une clé qui expire avant la fin
de vie de l'application bloque les mises à jour.

Le `.gitignore` exclut déjà `*.jks`, `*.keystore` et `keystore.properties`. Vérifie-le avant tout
commit : une clé d'upload publiée est une clé à révoquer.

## 2. Construire en local

Renseigne les quatre propriétés dans `~/.gradle/gradle.properties` (jamais dans le dépôt) :

```properties
aireautoroute.keystore.file=/chemin/absolu/vers/upload.jks
aireautoroute.keystore.password=…
aireautoroute.key.alias=aireautoroute-upload
aireautoroute.key.password=…
```

Puis :

```sh
./gradlew bundleRelease    # app/build/outputs/bundle/release/app-release.aab → Play Console
./gradlew assembleRelease  # app/build/outputs/apk/release/app-release.apk    → installation directe
```

Sans ces propriétés, la construction reste possible et produit un binaire **non signé** — utile
pour vérifier que le mode release compile, inutilisable pour publier.

## 3. Construire via l'intégration continue

Le workflow `Build APK` lit les mêmes secrets depuis les variables d'environnement. À déclarer dans
*Settings → Secrets and variables → Actions* du dépôt :

| Secret | Contenu |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | le keystore encodé : `base64 -w0 upload.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | mot de passe du keystore |
| `ANDROID_KEY_ALIAS` | `aireautoroute-upload` |
| `ANDROID_KEY_PASSWORD` | mot de passe de la clé |

Chaque exécution publie l'APK de debug, l'APK de release, le bundle `.aab` et un fichier
d'empreintes SHA-256. Tant que les secrets sont absents, la construction continue de fonctionner
et signale dans le résumé que les binaires ne sont pas signés.

Un tag `v*` crée en plus une release GitHub portant l'APK **et** le bundle. L'APK est la voie de
distribution directe, indépendante du Play Store ; le bundle y figure parce qu'une release offre un
lien de téléchargement direct, là où un artefact de CI est une archive zip qu'il faut d'abord
décompresser — ce qui est pénible depuis un téléphone.

### Publier sans ordinateur

Tout le parcours Play Store se fait depuis un téléphone : création du compte et vérification
d'identité (qui réclame des photos d'une pièce d'identité), fiche du magasin, captures d'écran,
déclarations, gestion du test fermé. Demande la **version bureau** dans le navigateur : l'interface
mobile de la Play Console masque certaines sections.

Le seul point qui résiste est la génération de la clé d'upload, `keytool` n'étant pas disponible sur
Android par défaut. Deux contournements : [Termux](https://f-droid.org/packages/com.termux/)
(installé depuis F-Droid, puis `pkg install openjdk-17`), ou la génération sur une autre machine.
Ne jamais la produire dans un job d'intégration continue sur un dépôt public : l'artefact serait
téléchargeable par n'importe qui.

## 4. Le parcours Play Store, compte personnel

Un compte personnel créé après le 13 novembre 2023 doit passer par un test fermé avant d'accéder à
la production.

1. **Compte développeur** — 25 $ une fois, puis vérification d'identité (pièce d'identité, adresse,
   téléphone). Compter quelques jours à deux semaines.
2. **Fiche du magasin** — icône 512×512, bandeau 1024×500, 2 à 8 captures d'écran, titre ≤ 30
   caractères, description courte ≤ 80, description longue ≤ 4000.
3. **Politique de confidentialité** — obligatoire, à une URL publique. Elle est rédigée dans
   [`docs/confidentialite.html`](confidentialite.html) et publiée par GitHub Pages, ce qui évite
   d'avoir à louer un hébergement : *Settings → Pages → Source : Deploy from a branch → `main` /
   `docs`*. L'URL à donner à la Play Console devient alors
   `https://fedia76.github.io/AireAutoroute/confidentialite.html`.

   Google exige une page accessible sans authentification et non modifiable par ses lecteurs — un
   document partagé en écriture est refusé. La page doit rester exacte : c'est un engagement
   opposable, à corriger dès que l'application change ce qu'elle transmet.
4. **Sécurité des données** — répondre **oui** : l'application publie les avis sur un service
   partagé (Supabase, hébergé en Europe). La correspondance exacte avec les types de données de
   Play, relevée sur `ServiceContributions.kt` et `serveur/schema.sql` :

   | Type Play | Champ | Collectée | Partagée | Obligatoire |
   | --- | --- | --- | --- | --- |
   | Informations personnelles → ID utilisateur | `auteur_id` | oui | **oui** | obligatoire |
   | Informations personnelles → Nom | `auteur`, la signature facultative | oui | **oui** | facultatif |
   | Activité dans l'application → Autre contenu généré par l'utilisateur | note, critère, tranche d'âge, commentaire, aire | oui | **oui** | facultatif |

   Finalité unique : *fonctionnalités de l'application*. Ni publicité, ni analyse, ni
   personnalisation.

   « Partagée » vaut oui partout : la requête de lecture sélectionne toutes les colonnes, donc
   `auteur_id` circule aussi, même si le client Kotlin l'ignore à la désérialisation.

   La **position n'est pas à déclarer comme collectée**. Play définit la collecte comme une
   transmission hors de l'appareil, et aucune coordonnée ne quitte le téléphone : le service réseau
   n'en manipule aucune. L'aire citée dans un avis est du contenu que l'utilisateur choisit de
   publier, pas un relevé de sa position. La déclarer contredirait la politique de confidentialité
   et la description de la fiche — une incohérence entre les trois est autrement plus risquée qu'une
   omission supposée.

   Le *commentaire libre* reste le point sensible : un utilisateur peut y écrire des informations
   personnelles. C'est ce qui justifie de le déclarer comme contenu généré par l'utilisateur, et ce
   qui impose à terme un mécanisme de signalement (voir la politique Play sur le contenu généré par
   les utilisateurs).

   Pratiques de sécurité : **chiffrement en transit** oui ; **suppression sur demande** oui, par
   l'adresse de contact de la politique de confidentialité. L'obligation de suppression de compte
   depuis l'application ne s'applique pas : il n'y a pas de compte, seulement une session anonyme
   sans inscription.
5. **Test fermé** — canal *closed testing*, pas *internal testing* qui ne compte pas. Il faut
   **12 testeurs inscrits en continu pendant 14 jours**. Le compteur démarre au 12ᵉ inscrit
   effectif : une adresse listée mais jamais confirmée ne compte pas, et une désinscription remet
   le compteur de la personne à zéro. Prévois 15 à 18 inscrits pour absorber les défections.
6. **Demande d'accès à la production** — examinée manuellement. Google regarde si le test a été
   réel ; un refus renvoie à l'étape 5.

## 5. Nom de package

L'identifiant de publication est `com.airesautoroute.myapp` (`applicationId` dans
`app/build.gradle.kts`). Il est **figé** : la Play Console l'arrête à la création de la fiche, et
l'application ne peut plus en changer une fois publiée sous peine de ne plus pouvoir se mettre à
jour. Il diffère volontairement du `namespace`, `com.aireautoroute.app`, qui désigne le paquet des
sources et n'a pas à lui correspondre.

## 6. Cible d'API

Depuis le 31 août 2026, toute soumission doit cibler **API 36** (Android 16). Le projet est aligné :
`compileSdk` et `targetSdk` valent 36. Cette contrainte se renouvelle chaque année à la fin août —
il faudra remonter d'un niveau annuellement pour continuer à publier des mises à jour.

## 7. Licences des données

Les catalogues embarqués proviennent du bornage du réseau routier national (Licence Ouverte), de
WikiSara (CC BY-SA) et d'OpenStreetMap (ODbL). L'attribution est affichée dans l'écran « Sources et
licences » de l'application, ce qui satisfait ces licences. Les fichiers dérivés de WikiSara restent
sous CC BY-SA.

Le code de l'application est sous licence MIT (voir `LICENSE`).
