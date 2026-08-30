-- =============================================================================
-- Schéma des contributions — Aires d'autoroute
--
-- À coller dans l'éditeur SQL de Supabase (menu « SQL Editor »), puis exécuter.
-- Le script est idempotent : on peut le rejouer sans casser l'existant.
--
-- Ce qui est ici : ce que les utilisateurs produisent (notes, déclarations de
-- présence, enseignes). Le référentiel — autoroutes, tracés, aires — reste
-- embarqué dans l'application : il change une fois par an et doit s'afficher
-- sans réseau.
--
-- Les identifiants d'aire (« a13-vironvay-nord ») sont ceux des fichiers livrés
-- avec l'application. Le serveur ne les connaît pas : il ne peut donc pas les
-- valider par clé étrangère, c'est le prix de ce partage.
-- =============================================================================

-- --- Domaines de valeurs ------------------------------------------------------
-- Écrits en dur plutôt qu'en table de référence : ces listes changent avec le
-- code de l'application, pas avec les données.

do $$ begin
  create type critere as enum (
    'AIRE_JEUX_INTERIEURE',
    'AIRE_JEUX_EXTERIEURE',
    'TOILETTES',
    'STATION_SERVICE',
    'TABLE_A_LANGER',
    'APPRECIATION_GENERALE'
  );
exception when duplicate_object then null; end $$;

do $$ begin
  create type tranche_age as enum ('ANS_0_3', 'ANS_3_6', 'ANS_6_12');
exception when duplicate_object then null; end $$;

do $$ begin
  create type presence as enum ('OUI', 'NON');
exception when duplicate_object then null; end $$;

-- --- Table notation -----------------------------------------------------------

create table if not exists notation (
  id            uuid primary key default gen_random_uuid(),
  auteur_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
  aire_id       text not null check (aire_id ~ '^[a-z0-9-]{3,80}$'),
  critere       critere not null,
  tranche_age   tranche_age,
  note          smallint not null check (note between 1 and 5),
  commentaire   text check (length(commentaire) <= 500),
  auteur        text check (length(auteur) <= 40),
  -- Posée par le serveur : un client ne peut pas antidater sa contribution.
  creee_le      timestamptz not null default now(),
  modifiee_le   timestamptz not null default now(),
  -- Permet de masquer un contenu abusif sans le détruire.
  masquee       boolean not null default false,

  -- Une tranche d'âge n'a de sens que pour les aires de jeux.
  constraint tranche_age_coherente check (
    (critere in ('AIRE_JEUX_INTERIEURE', 'AIRE_JEUX_EXTERIEURE') and tranche_age is not null)
    or (critere not in ('AIRE_JEUX_INTERIEURE', 'AIRE_JEUX_EXTERIEURE') and tranche_age is null)
  ),

  -- Un contributeur ne note qu'une fois chaque critère d'une aire : revenir sur
  -- son avis le remplace, au lieu de gonfler la moyenne.
  -- « nulls not distinct » est indispensable : sans lui, deux lignes dont la
  -- tranche d'âge est nulle ne se ressembleraient pas aux yeux de l'index, et
  -- l'on pourrait noter les mêmes toilettes autant de fois qu'on le veut.
  constraint notation_unique_par_auteur
    unique nulls not distinct (auteur_id, aire_id, critere, tranche_age)
);

-- Rattrapage pour une base créée avec la première version du script.
do $$ begin
  alter table notation drop constraint if exists notation_unique_par_auteur;
  alter table notation add constraint notation_unique_par_auteur
    unique nulls not distinct (auteur_id, aire_id, critere, tranche_age);
end $$;

create index if not exists notation_par_aire on notation (aire_id) where not masquee;

-- --- Table declaration_equipement ---------------------------------------------

create table if not exists declaration_equipement (
  id            uuid primary key default gen_random_uuid(),
  auteur_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
  aire_id       text not null check (aire_id ~ '^[a-z0-9-]{3,80}$'),
  critere       critere not null check (critere <> 'APPRECIATION_GENERALE'),
  presence      presence not null,
  auteur        text check (length(auteur) <= 40),
  creee_le      timestamptz not null default now(),
  modifiee_le   timestamptz not null default now(),
  masquee       boolean not null default false,

  constraint declaration_unique_par_auteur unique (auteur_id, aire_id, critere)
);

create index if not exists declaration_par_aire on declaration_equipement (aire_id) where not masquee;

-- --- Enseignes ----------------------------------------------------------------

create table if not exists enseigne (
  id         uuid primary key default gen_random_uuid(),
  nom        text not null check (length(trim(nom)) between 2 and 60),
  categorie  text not null default 'AUTRE'
             check (categorie in ('CARBURANT', 'RESTAURATION', 'BOUTIQUE', 'HOTEL', 'AUTRE')),
  -- Pictogramme, facultatif : le contributeur qui signale une enseigne sur le bord de la route
  -- n'est pas tenu de la ranger. Le jeu est fixe et écrit en dur, comme les autres domaines de
  -- valeurs : il change avec le code de l'application, pas avec les données.
  icone      text constraint enseigne_icone_connue check (icone in (
               'CARBURANT', 'RECHARGE_ELECTRIQUE', 'RESTAURATION_RAPIDE', 'RESTAURANT',
               'BOULANGERIE', 'CAFE', 'PIZZERIA', 'SUPERETTE', 'BOUTIQUE', 'HOTEL'
             )),
  creee_le   timestamptz not null default now()
);

-- Rattrapage pour une base créée avant l'arrivée des pictogrammes.
alter table enseigne add column if not exists icone text;
alter table enseigne drop constraint if exists enseigne_icone_connue;
alter table enseigne add constraint enseigne_icone_connue check (icone in (
  'CARBURANT', 'RECHARGE_ELECTRIQUE', 'RESTAURATION_RAPIDE', 'RESTAURANT',
  'BOULANGERIE', 'CAFE', 'PIZZERIA', 'SUPERETTE', 'BOUTIQUE', 'HOTEL'
));

-- « McDonald's » et « mcdonalds » doivent désigner la même enseigne.
create unique index if not exists enseigne_nom_unique on enseigne (lower(trim(nom)));

create table if not exists aire_enseigne (
  aire_id      text not null check (aire_id ~ '^[a-z0-9-]{3,80}$'),
  enseigne_id  uuid not null references enseigne (id) on delete cascade,
  auteur_id    uuid not null default auth.uid() references auth.users (id) on delete cascade,
  creee_le     timestamptz not null default now(),
  primary key (aire_id, enseigne_id, auteur_id)
);

create index if not exists aire_enseigne_par_aire on aire_enseigne (aire_id);

-- --- Règles d'accès -----------------------------------------------------------
-- Tout le monde lit, chacun n'écrit que ses propres lignes. `auth.uid()` est
-- posé par le serveur à partir du jeton : un client ne peut pas s'en réclamer
-- un autre.

alter table notation enable row level security;
alter table declaration_equipement enable row level security;
alter table enseigne enable row level security;
alter table aire_enseigne enable row level security;

drop policy if exists notation_lecture on notation;
create policy notation_lecture on notation
  for select using (not masquee);

drop policy if exists notation_ajout on notation;
create policy notation_ajout on notation
  for insert with check (auteur_id = auth.uid());

drop policy if exists notation_modification on notation;
create policy notation_modification on notation
  for update using (auteur_id = auth.uid()) with check (auteur_id = auth.uid());

drop policy if exists notation_suppression on notation;
create policy notation_suppression on notation
  for delete using (auteur_id = auth.uid());

drop policy if exists declaration_lecture on declaration_equipement;
create policy declaration_lecture on declaration_equipement
  for select using (not masquee);

drop policy if exists declaration_ajout on declaration_equipement;
create policy declaration_ajout on declaration_equipement
  for insert with check (auteur_id = auth.uid());

drop policy if exists declaration_modification on declaration_equipement;
create policy declaration_modification on declaration_equipement
  for update using (auteur_id = auth.uid()) with check (auteur_id = auth.uid());

drop policy if exists declaration_suppression on declaration_equipement;
create policy declaration_suppression on declaration_equipement
  for delete using (auteur_id = auth.uid());

drop policy if exists enseigne_lecture on enseigne;
create policy enseigne_lecture on enseigne for select using (true);

-- Le catalogue s'enrichit des ajouts de chacun, mais personne ne le corrige :
-- renommer une enseigne changerait le sens des contributions des autres.
drop policy if exists enseigne_ajout on enseigne;
create policy enseigne_ajout on enseigne
  for insert with check (auth.uid() is not null);

drop policy if exists aire_enseigne_lecture on aire_enseigne;
create policy aire_enseigne_lecture on aire_enseigne for select using (true);

drop policy if exists aire_enseigne_ajout on aire_enseigne;
create policy aire_enseigne_ajout on aire_enseigne
  for insert with check (auteur_id = auth.uid());

drop policy if exists aire_enseigne_suppression on aire_enseigne;
create policy aire_enseigne_suppression on aire_enseigne
  for delete using (auteur_id = auth.uid());

-- --- Horodatage des modifications ---------------------------------------------

create or replace function marquer_modification() returns trigger
language plpgsql as $$
begin
  new.modifiee_le := now();
  return new;
end $$;

drop trigger if exists notation_modifiee on notation;
create trigger notation_modifiee before update on notation
  for each row execute function marquer_modification();

drop trigger if exists declaration_modifiee on declaration_equipement;
create trigger declaration_modifiee before update on declaration_equipement
  for each row execute function marquer_modification();

-- --- Vues de synthèse ---------------------------------------------------------
-- L'application n'a pas besoin de toutes les notations : pour dresser sa liste,
-- il lui faut des moyennes. Ces vues lui évitent de rapatrier des milliers de
-- lignes pour en calculer trois chiffres.

create or replace view synthese_notation
with (security_invoker = true) as
  select
    aire_id,
    critere,
    tranche_age,
    round(avg(note)::numeric, 2) as moyenne,
    count(*)                     as nombre
  from notation
  where not masquee
  group by aire_id, critere, tranche_age;

create or replace view synthese_equipement
with (security_invoker = true) as
  select
    aire_id,
    critere,
    count(*) filter (where presence = 'OUI') as declarations_oui,
    count(*) filter (where presence = 'NON') as declarations_non
  from declaration_equipement
  where not masquee
  group by aire_id, critere;

-- ---------------------------------------------------------------------------
-- Signalements
-- ---------------------------------------------------------------------------
-- Les règles du Play Store imposent, pour toute application publiant du contenu écrit par ses
-- utilisateurs, un moyen de signaler ce contenu depuis l'application et son retrait rapide.
-- Le commentaire libre d'une notation est le seul contenu concerné : les notes et les
-- déclarations de présence sont des valeurs contraintes, elles ne peuvent rien véhiculer.

-- Un signalement vise soit un avis précis, soit un contributeur dans son ensemble. Le second
-- cas existe parce que signaler vingt commentaires publicitaires un par un n'a pas de sens.
create table if not exists signalement (
  id              uuid primary key default gen_random_uuid(),
  notation_id     uuid references notation (id) on delete cascade,
  auteur_signale  uuid references auth.users (id) on delete cascade,
  auteur_id       uuid not null default auth.uid() references auth.users (id) on delete cascade,
  motif           text not null default 'AUTRE'
                  check (motif in ('INJURIEUX', 'PERSONNEL', 'INEXACT', 'HORS_SUJET', 'AUTRE')),
  creee_le        timestamptz not null default now()
);

-- Rattrapage pour les bases où la première version de la table a déjà été appliquée.
alter table signalement add column if not exists auteur_signale uuid references auth.users (id) on delete cascade;
alter table signalement alter column notation_id drop not null;

-- Une cible et une seule : un signalement qui ne désigne rien, ou qui désigne les deux, est
-- une erreur d'appel qu'il vaut mieux refuser que stocker.
alter table signalement drop constraint if exists signalement_une_seule_cible;
alter table signalement add constraint signalement_une_seule_cible
  check ((notation_id is null) <> (auteur_signale is null));

-- Un signalement par personne et par cible : sans quoi un seul contributeur suffirait à
-- atteindre le seuil de masquage. `nulls not distinct` est indispensable — les deux colonnes
-- de cible étant alternativement nulles, l'unicité par défaut ne verrait aucun doublon.
alter table signalement drop constraint if exists signalement_unique_par_auteur;
alter table signalement add constraint signalement_unique_par_auteur
  unique nulls not distinct (auteur_id, notation_id, auteur_signale);

create index if not exists signalement_par_notation on signalement (notation_id);
create index if not exists signalement_par_auteur_signale on signalement (auteur_signale);

alter table signalement enable row level security;

drop policy if exists signalement_ajout on signalement;
create policy signalement_ajout on signalement
  for insert with check (auteur_id = auth.uid());

-- Chacun relit ses propres signalements, personne ne lit ceux des autres : savoir qui a
-- signalé quoi inviterait aux représailles.
drop policy if exists signalement_lecture on signalement;
create policy signalement_lecture on signalement
  for select using (auteur_id = auth.uid());

-- Au-delà du seuil, l'avis est masqué sans attendre d'intervention humaine. La fonction est
-- « security definer » parce que celui qui signale n'est pas l'auteur : les règles d'accès de
-- `notation` lui interdiraient la mise à jour. `search_path` est fixé, faute de quoi une table
-- homonyme placée dans un autre schéma détournerait la fonction.
create or replace function masquer_notation_signalee() returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  total integer;
begin
  -- Seul le signalement d'un avis masque. Celui d'un contributeur remonte pour examen : le
  -- masquage automatique de tout ce qu'une personne a écrit serait disproportionné, et
  -- offrirait à trois comptes le pouvoir de l'effacer.
  if new.notation_id is null then
    return new;
  end if;
  select count(*) into total from signalement where notation_id = new.notation_id;
  if total >= 3 then
    update notation set masquee = true, modifiee_le = now() where id = new.notation_id;
  end if;
  return new;
end;
$$;

drop trigger if exists signalement_masque_notation on signalement;
create trigger signalement_masque_notation
  after insert on signalement
  for each row execute function masquer_notation_signalee();

-- Le masquage est réversible : `update notation set masquee = false where id = '…';` depuis la
-- console. À relire régulièrement, le seuil ne remplaçant pas l'examen des signalements :
--   select n.id, n.commentaire, count(s.*) as signalements
--   from notation n join signalement s on s.notation_id = n.id
--   group by n.id, n.commentaire order by signalements desc;

-- Revue des contributeurs signalés, qu'aucun automatisme ne traite :
--   select auteur_signale, count(*) as signalements, max(creee_le) as dernier
--   from signalement where auteur_signale is not null
--   group by auteur_signale order by signalements desc;
