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

## 5. Symboles de débogage natifs

La carte s'appuie sur MapLibre, qui embarque son moteur de rendu OpenGL sous forme de
bibliothèques natives. La Play Console signale leur absence de symboles :

> Cet App Bundle contient du code natif, et vous n'avez pas importé de symboles de débogage.

C'est un **avertissement, pas un refus** : le bundle est acceptable en l'état. Sans les symboles,
un plantage venu du code natif n'apparaît toutefois dans la Play Console que sous forme d'adresses
mémoire, sans nom de fonction ni numéro de ligne.

`debugSymbolLevel = "SYMBOL_TABLE"` dans le type de build `release` demande à Gradle de les
empaqueter. Ils voyagent dans le bundle et sont retirés avant livraison : l'application installée
ne grossit pas. `FULL` existe aussi, mais n'apporte rien de plus sur une dépendance fournie
précompilée.

Cela suppose que les `.so` livrés par MapLibre aient conservé leur table des symboles. Si
l'avertissement persiste après un nouveau téléversement, c'est qu'ils ont été dépouillés à la
source, et il n'y a alors rien à en tirer.

## 6. Contenu généré par les utilisateurs

Le commentaire libre d'une notation est publié tel quel et lu par tout le monde. Les règles du
Play Store imposent alors de pouvoir signaler un contenu **et** un auteur, de bloquer un
contributeur, et de retirer effectivement ce qui est signalé.

Deux axes se croisent : ce que le geste vise — un avis, ou la personne — et pour qui il agit —
tout le monde, ou le seul lecteur. Trois des quatre combinaisons sont utiles ; masquer un avis
isolé pour soi seul n'en fait pas partie, il suffit de ne pas le lire.

- **Signalement** — un bouton sur chaque commentaire, avec un motif à choisir. Il est posé sur le
  contenu lui-même : relégué dans un écran de réglages, il ne serait pas trouvé au moment où l'on
  en a besoin.
- **Retrait** — au troisième signalement, un déclencheur de base de données bascule `masquee` à
  vrai. Les règles de lecture filtrant déjà `not masquee`, l'avis disparaît pour tout le monde sans
  intervention. Le seuil est une constante unique dans `serveur/schema.sql`.
- **Effacement** — l'écran « Sources et confidentialité » permet de supprimer d'un coup toutes ses
  contributions. C'est le droit à l'effacement du RGPD, exercé sans avoir à écrire à qui que ce
  soit.
- **Signalement d'un contributeur** — pour la personne plutôt que pour une de ses phrases.
  Signaler vingt commentaires publicitaires un par un n'aurait pas de sens. Aucun masquage
  automatique n'en découle : effacer d'un coup tout ce qu'une personne a écrit sur la foi de trois
  signalements serait disproportionné, et donnerait à trois comptes le pouvoir de l'effacer. Ces
  lignes remontent pour examen.
- **Masquage d'un contributeur** — le « block users » exigé par Google. Immédiat, et pour le seul
  lecteur qui l'a demandé. Ses avis, ses notes et ses déclarations sortent de l'affichage **et des
  moyennes** : masquer quelqu'un en le laissant peser sur les étoiles serait cosmétique. Deux
  lecteurs peuvent donc voir des moyennes différentes, c'est le prix d'un masquage qui fait ce
  qu'il annonce. La liste se défait depuis « Sources et confidentialité » — sans quoi le geste
  serait sans retour, l'avis qui permettrait de l'annuler étant justement celui qu'on ne voit plus.

Une limite à connaître : « une personne » désigne ici **une session anonyme sur un appareil**.
Réinstaller l'application donne une nouvelle identité, donc démasquée et désignalée. C'est inhérent
au choix de ne demander aucun compte, et Google l'admet pour les applications anonymes ; mais
contre un nuisible déterminé, ces mécanismes ralentissent plus qu'ils n'empêchent.

Le seuil automatique ne dispense pas de relire les signalements. Depuis la console du service :

```sql
select n.id, n.commentaire, count(s.*) as signalements
from notation n join signalement s on s.notation_id = n.id
group by n.id, n.commentaire
order by signalements desc;
```

Un masquage se défait par `update notation set masquee = false where id = '…';`.

## 7. Numéro de version

`versionCode` est le numéro que la Play Console utilise pour ordonner les versions. **Il doit
augmenter à chaque téléversement** : un bundle réutilisant un numéro déjà vu est refusé, même si la
version correspondante a été retirée depuis. Il ne redescend jamais.

`versionName` n'a aucun rôle technique — c'est la chaîne montrée aux utilisateurs.

Corriger quoi que ce soit dans une version déjà téléversée impose donc de reconstruire avec un
`versionCode` supérieur : on ne remplace pas un bundle, on en publie un nouveau.

## 8. Nom de package

L'identifiant de publication est `com.aireautoroute.app` (`applicationId` dans
`app/build.gradle.kts`), identique au `namespace`.

Il doit correspondre **exactement** au nom de package de la fiche Play Store, sans quoi le
téléversement est refusé. Or ce nom est arrêté à la création de la fiche et n'y est plus
modifiable : la Play Console en propose un par défaut, de la forme `com.<nom>.myapp`, qu'il faut
penser à corriger à ce moment-là. La seule façon de le rattraper ensuite est de recréer la fiche,
tant que rien n'a été publié.

Côté application, l'identifiant est modifiable jusqu'à la première publication acceptée, et figé
après : en changer ferait perdre la capacité de mettre à jour les installations existantes.

## 9. Cible d'API

Depuis le 31 août 2026, toute soumission doit cibler **API 36** (Android 16). Le projet est aligné :
`compileSdk` et `targetSdk` valent 36. Cette contrainte se renouvelle chaque année à la fin août —
il faudra remonter d'un niveau annuellement pour continuer à publier des mises à jour.

## 10. Licences des données

Les catalogues embarqués proviennent du bornage du réseau routier national (Licence Ouverte), de
WikiSara (CC BY-SA) et d'OpenStreetMap (ODbL). L'attribution est affichée dans l'écran « Sources et
licences » de l'application, ce qui satisfait ces licences. Les fichiers dérivés de WikiSara restent
sous CC BY-SA.

Le code de l'application est sous licence MIT (voir `LICENSE`).
