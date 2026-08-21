# Le serveur des contributions

Les autoroutes, les tracés et les aires restent **embarqués dans l'application** : ce référentiel
change une fois par an et doit s'afficher sans attendre le réseau. Seules les contributions — notes,
déclarations de présence, enseignes — sont partagées entre les utilisateurs, et c'est l'objet de ce
dossier.

## Mise en place

1. Créer un compte sur [supabase.com](https://supabase.com) et un projet, **en région européenne**
   (Frankfurt ou Paris) : les contributions sont des données personnelles au sens du RGPD.
2. Ouvrir le *SQL Editor* du projet, y coller [`schema.sql`](schema.sql) et l'exécuter. Le script
   est rejouable sans dommage.
3. Dans *Authentication → Sign In / Providers*, activer les **connexions anonymes**
   (*Anonymous sign-ins*). C'est ce qui permet de contribuer sans jamais créer de compte.
4. Relever dans *Project Settings → API* l'**URL du projet** et la **clé publique** (`anon`), puis
   les reporter dans `app/src/main/assets/supabase.properties` (voir le modèle du dépôt).

## Sur la clé embarquée dans l'application

La clé `anon` est publique par construction : elle est faite pour être distribuée dans un client.
Ce qu'elle autorise n'est pas décidé par sa confidentialité mais par les règles d'accès du schéma —
tout le monde lit, chacun n'écrit que ses propres lignes, et `auth.uid()` est posé par le serveur à
partir du jeton, donc personne ne peut se faire passer pour un autre.

**La clé `service_role`, elle, ne doit jamais quitter le tableau de bord Supabase** : celle-là
contourne toutes les règles.

## Modération

Chaque contribution porte une colonne `masquee`. La passer à `true` depuis le *Table Editor* retire
la ligne de la vue des utilisateurs sans la détruire, et les moyennes se recalculent aussitôt.

## Ce que le serveur ne peut pas vérifier

Les identifiants d'aire (`a13-vironvay-nord`) viennent des fichiers livrés avec l'application ; le
serveur ne connaît pas le référentiel et ne peut donc pas les valider par clé étrangère. Une faute
de frappe côté client produirait des contributions orphelines — d'où le contrôle de forme sur la
colonne.
