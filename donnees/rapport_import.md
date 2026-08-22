# Rapport d'import des données

Généré par `python3 tools/generer_donnees.py`.

## Ce qui a été retenu

- **89 autoroutes**, 10998 km de tracé
- **1376 aires** — une par chaussée : les deux côtés d'un même lieu sont deux aires distinctes, avec leurs propres équipements et leurs propres avis
- 16 redites de la source regroupées (même nom, même sens, même point)

## Ce qui a été écarté

- 275 km de tracé, là où le kilométrage repart en arrière (l'autoroute est décrite en plusieurs tronçons dans le bornage, on garde le plus long)
- 35 aires dont le PK ne tombe pas sur le tracé retenu
- 3 autoroutes citées par WikiSara sans tracé dans le bornage : A35, A38, A701
- 36 autoroutes bornées sans aire répertoriée

### Aires écartées

- A104 · Beauvert (PK 57, tracé 0-28)
- A104 · Fleury (PK 42, tracé 0-28)
- A104 · Fond des Prés (PK 56, tracé 0-28)
- A104 · La Pointe Ringale (PK 31, tracé 0-28)
- A104 · Les Chevreaux (PK 31, tracé 0-28)
- A16 · Ghyvelde (PK 338, tracé 0-246)
- A16 · Grande-Synthe (PK 319, tracé 0-246)
- A16 · L'Épitre (PK 285, tracé 0-246)
- A16 · Le Beau Marais (PK 287, tracé 0-246)
- A16 · Les Deux Caps (PK 268, tracé 0-246)
- A16 · Les Moëres (PK 338, tracé 0-246)
- A16 · Offekerque (PK 292, tracé 0-246)
- A16 · Saint-Georges-sur-l'Aa (PK 304, tracé 0-246)
- A16 · Téteghem Nord (PK 330, tracé 0-246)
- A16 · Téteghem Sud (PK 328, tracé 0-246)
- A20 · Montauban-Sud (PK 437, tracé 0-429)
- A20 · Montauban-Sud (PK 437, tracé 0-429)
- A28 · Behen (PK 13, tracé 17-285)
- A28 · Behen (PK 13, tracé 17-285)
- A34 · Taissy (PK 1, tracé 17-115)
- A34 · Taissy (PK 1, tracé 17-115)
- A43 · Plate-forme du tunnel du Fréjus (PK 195 hors bornes)
- A43 · Plate-forme du tunnel du Fréjus (PK 195 hors bornes)
- A430 · Sainte-Hélène-sur-Isère (PK 143, tracé 125-139)
- A47 · La Chabure ( RN88 ) (PK 32, tracé 0-29)
- A51 · La Saulce (PK 153 hors bornes)
- A51 · La Saulce (PK 153 hors bornes)
- A570 · Sans nom (PK 10, tracé 0-7)
- A6 · Dardilly (PK 450, tracé 0-445)
- A6 · Les Bruyères - Paisy (PK 450, tracé 0-445)
- A630 · Fontbelleau (PK 43, tracé 0-33)
- A630 · Lormont (PK 43, tracé 0-33)
- A7 · Vitrolles (PK 258, tracé 6-253)
- A81 · Erbrée (PK 4, tracé 175-268)
- A81 · Mondevert (PK 4, tracé 175-268)

## Ce qu'OpenStreetMap a apporté

- **1111 aires rattachées** sur 1369 relevées (1060 par le nom, 51 par la seule position)
- 3044 objets rattachés à une aire (164 trop loin de toute aire, ignorés)
- 990 liens aire/enseigne posés

### Équipements désormais annoncés

- TOILETTES : 621 aires
- AIRE_JEUX_EXTERIEURE : 322 aires
- TABLE_A_LANGER : 94 aires
- STATION_SERVICE : 8 aires
- AIRE_JEUX_INTERIEURE : 2 aires

### 48 enseignes entrées au catalogue

Une marque n'y entre qu'à partir de trois aires : en deçà, c'est plus probablement
une saisie isolée qu'une enseigne.

- Bonjour (BOUTIQUE, 39 aires)
- Eni (CARBURANT, 37 aires)
- Franprix (BOUTIQUE, 35 aires)
- E.Leclerc (CARBURANT, 31 aires)
- Casino Shop (BOUTIQUE, 25 aires)
- Dyneff (CARBURANT, 19 aires)
- Shell Select (BOUTIQUE, 16 aires)
- Pomme de Pain (RESTAURATION, 15 aires)
- Stratto (RESTAURATION, 15 aires)
- Philéas (RESTAURATION, 14 aires)
- Total Access (CARBURANT, 14 aires)
- Fulli (BOUTIQUE, 12 aires)
- Columbus Café & Co (RESTAURATION, 11 aires)
- Lunch Grill (RESTAURATION, 11 aires)
- À Table ! (RESTAURATION, 10 aires)
- Courtepaille (RESTAURATION, 10 aires)
- Casino (BOUTIQUE, 9 aires)
- Leo Resto (RESTAURATION, 9 aires)
- Honiby Market (BOUTIQUE, 8 aires)
- Territoires de France (RESTAURATION, 8 aires)
- L'atelier Charal (RESTAURATION, 8 aires)
- Les Comptoirs Casino (RESTAURATION, 7 aires)
- Flunch (RESTAURATION, 6 aires)
- Pizza Hut (RESTAURATION, 6 aires)
- Tasty (RESTAURATION, 6 aires)
- La Mie Câline (BOUTIQUE, 6 aires)
- Deli by Shell (RESTAURATION, 5 aires)
- Maison Pradier (RESTAURATION, 5 aires)
- Subway (RESTAURATION, 5 aires)
- Casino Express (BOUTIQUE, 5 aires)
- Ciao (RESTAURATION, 5 aires)
- Origin'R (RESTAURATION, 5 aires)
- Casino everyday (BOUTIQUE, 5 aires)
- Buffalo Grill (RESTAURATION, 4 aires)
- L'Arche Cafétéria (RESTAURATION, 4 aires)
- Hippopotamus (RESTAURATION, 4 aires)
- Léo (RESTAURATION, 4 aires)
- Mezzo Di Pasta (RESTAURATION, 4 aires)
- AS 24 (CARBURANT, 4 aires)
- Cœur de Blé (RESTAURATION, 4 aires)
- Mezzoday (RESTAURATION, 4 aires)
- Monop' (BOUTIQUE, 4 aires)
- Proxi (BOUTIQUE, 3 aires)
- Steak 'n Shake (RESTAURATION, 3 aires)
- Honiby (BOUTIQUE, 3 aires)
- L'Arche Comptoir (RESTAURATION, 3 aires)
- Go Johnny Go (RESTAURATION, 3 aires)
- Crep'Eat (RESTAURATION, 3 aires)

### 258 aires relevées sans correspondance

Ni le nom ni la position n'ont permis de les rattacher à une ligne de WikiSara.
Elles sont laissées de côté plutôt que rattachées au petit bonheur.

- A1 · (sans nom) (PK 102)
- A1 · (sans nom) (PK 131)
- A1 · (sans nom) (PK 148)
- A1 · (sans nom) (PK 18)
- A1 · (sans nom) (PK 19)
- A1 · (sans nom) (PK 190)
- A1 · (sans nom) (PK 201)
- A10 · (sans nom) (PK 2)
- A10 · (sans nom) (PK 300)
- A10 · (sans nom) (PK 300)
- A10 · (sans nom) (PK 304)
- A10 · Aire de Chermignac Ouest (PK 445)
- A10 · Secure Truck Parking Isoparc Sorgina (PK 223)
- A11 · Aire Le Mans Nord (PK 172)
- A11 · Aire de Villaines-la-Gonais (PK 138)
- A115 · (sans nom) (PK 11)
- A115 · (sans nom) (PK 7)
- A12 · (sans nom) (PK 8)
- A13 · (sans nom) (PK 214)
- A13 · (sans nom) (PK 39)
- A13 · (sans nom) (PK 82)
- A13 · Aire Sud de Bord (PK 102)
- A13 · Aire de Lirose (PK 218)
- A131 · (sans nom) (PK 16)
- A139 · (sans nom) (PK 2)
- A14 · (sans nom) (PK 8)
- A14 · (sans nom) (PK 8)
- A15 · (sans nom) (PK 22)
- A15 · Aire de Cergy-Pontoise (PK 18)
- A150 · (sans nom) (PK 21)
- A150 · (sans nom) (PK 21)
- A150 · (sans nom) (PK 26)
- A150 · (sans nom) (PK 29)
- A150 · (sans nom) (PK 29)
- A16 · (sans nom) (PK 16)
- A16 · (sans nom) (PK 239)
- A16 · Aire d'Attainville (PK 20)
- A16 · Aire de La Courneuve Est (PK 2)
- A16 · Aire de La Courneuve Ouest (PK 2)
- A16 · Aire des Falaises de Widehem Ouest (PK 228)
- A19 · (sans nom) (PK 11)
- A2 · Aire d'Emblise (Closed) (PK 75)
- A2 · H Trucks Park de Tilloy-lez-Cambrai (PK 32)
- A20 · (sans nom) (PK 94)
- A20 · Aire du Belvédère (PK 311)
- A20 · DIR (PK 148)
- A21 · (sans nom) (PK 44)
- A23 · Aire de Petite Forêt (PK 39)
- A26 · (sans nom) (PK 176)
- A26 · (sans nom) (PK 176)
- A26 · (sans nom) (PK 180)
- A26 · (sans nom) (PK 235)
- A26 · (sans nom) (PK 316)
- A26 · (sans nom) (PK 316)
- A26 · (sans nom) (PK 348)
- A26 · (sans nom) (PK 348)
- A27 · Aire de Camphin-en-Pévèle (PK 10)
- A27 · Aire de Lamain (PK 10)
- A28 · (sans nom) (PK 177)
- A28 · (sans nom) (PK 256)
- … et 198 autres

## Contrôles

Aucune anomalie bloquante.
