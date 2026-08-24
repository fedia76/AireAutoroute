# Textes de la fiche Play Store

Les chiffres cités doivent correspondre aux fichiers de `app/src/main/assets/seed/`. Après une
régénération des données, les recompter avant de mettre la fiche à jour :

```sh
python3 -c "import json;[print(f,len(json.load(open(f)))) for f in ['app/src/main/assets/seed/autoroutes.json','app/src/main/assets/seed/aires.json']]"
```

## Description brève (80 caractères maximum)

```
Les prochaines aires sur votre route, notées par les voyageurs. Sans réseau.
```

## Description complète (4000 caractères maximum)

```
Aires d'autoroute affiche les prochaines aires sur votre trajet, dans votre sens de circulation, avec les avis laissés par les voyageurs qui s'y sont arrêtés.

Comment ça marche

L'application se sert de votre position pour vous situer sur l'autoroute et calculer le point kilométrique, celui qui est inscrit sur les bornes. Elle en déduit votre sens de circulation, et ne liste que les aires accessibles depuis votre chaussée.

89 autoroutes, 11 000 km de tracé et 1 376 aires sont incluses dans l'application.

Les avis

Vous pouvez noter une aire sur plusieurs critères :

• propreté des sanitaires
• aires de jeux, avec les tranches d'âge concernées
• tables à langer
• restauration et enseignes présentes

Vous pouvez aussi signaler qu'un équipement annoncé n'est pas là, ou en ajouter un qui manque au catalogue. Les avis de tous les utilisateurs sont recoupés pour donner l'état de chaque aire.

Sans réseau

Le catalogue des autoroutes et des aires est stocké dans l'application. Elle reste utilisable sans connexion : vous gardez la liste des prochaines aires et les derniers avis téléchargés.

Trois habillages

Signalétique, Carnet de route et Copilote, au choix. Le mode Copilote est prévu pour la conduite de nuit.

Vie privée

• Pas de compte à créer, pas d'adresse e-mail demandée
• Pas de publicité
• Pas d'outil de mesure d'audience
• Votre position n'est pas transmise : elle reste sur le téléphone

Les avis que vous publiez sont visibles par les autres utilisateurs.

Données et code

Le tracé des autoroutes vient du bornage du réseau routier national (Licence Ouverte), la liste des aires de WikiSara (CC BY-SA), le relevé des équipements d'OpenStreetMap (ODbL). Les sources sont créditées dans l'application.

Le code est libre, sous licence MIT :
github.com/fedia76/AireAutoroute

À savoir

Les informations viennent de sources ouvertes et des contributions des utilisateurs. Il y a des erreurs et des manques. Si une aire est absente ou une information fausse, vous pouvez la corriger depuis l'application.

Application indépendante, sans lien avec un exploitant autoroutier.
```
