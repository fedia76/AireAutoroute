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

Un tag `v*` crée en plus une release GitHub avec l'APK de release — c'est la voie de distribution
directe, indépendante du Play Store.

## 4. Le parcours Play Store, compte personnel

Un compte personnel créé après le 13 novembre 2023 doit passer par un test fermé avant d'accéder à
la production.

1. **Compte développeur** — 25 $ une fois, puis vérification d'identité (pièce d'identité, adresse,
   téléphone). Compter quelques jours à deux semaines.
2. **Fiche du magasin** — icône 512×512, bandeau 1024×500, 2 à 8 captures d'écran, titre ≤ 30
   caractères, description courte ≤ 80, description longue ≤ 4000.
3. **Politique de confidentialité** — obligatoire, à une URL publique. L'application ne demande
   aucun accès réseau et ne transmet rien : le texte peut être court, mais il doit exister.
4. **Sécurité des données** — déclarer *aucune donnée collectée ni partagée*. La position est
   utilisée pour situer l'utilisateur sur l'autoroute, elle ne quitte jamais l'appareil ; les
   notations sont écrites dans `filesDir/donnees_utilisateur.json`. L'application n'a pas la
   permission `INTERNET`, ce qui rend cette déclaration vérifiable.
5. **Test fermé** — canal *closed testing*, pas *internal testing* qui ne compte pas. Il faut
   **12 testeurs inscrits en continu pendant 14 jours**. Le compteur démarre au 12ᵉ inscrit
   effectif : une adresse listée mais jamais confirmée ne compte pas, et une désinscription remet
   le compteur de la personne à zéro. Prévois 15 à 18 inscrits pour absorber les défections.
6. **Demande d'accès à la production** — examinée manuellement. Google regarde si le test a été
   réel ; un refus renvoie à l'étape 5.

## 5. Cible d'API

Depuis le 31 août 2026, toute soumission doit cibler **API 36** (Android 16). Le projet est aligné :
`compileSdk` et `targetSdk` valent 36. Cette contrainte se renouvelle chaque année à la fin août —
il faudra remonter d'un niveau annuellement pour continuer à publier des mises à jour.

## 6. Licences des données

Les catalogues embarqués proviennent du bornage du réseau routier national (Licence Ouverte) et de
WikiSara (CC BY-SA). L'attribution est affichée dans l'écran « Sources et licences » de
l'application, ce qui satisfait les deux licences. Les fichiers dérivés de WikiSara restent sous
CC BY-SA.
