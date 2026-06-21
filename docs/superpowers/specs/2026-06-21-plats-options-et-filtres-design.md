# Design — Options de plats (#2) & Filtres gérables (#5)

Date : 2026-06-21
Statut : validé (brainstorming), en attente de revue du spec
Repos concernés : `MySuguClientApp` (backend Spring), `mysugu-admin` (dashboard), `mysugu-frontend` (app users), app mobile restaurateur (consommatrice de l'API)

## Contexte

Deux corrections demandées (PDF « Corrections-pour-l-application-des-Users ») :

- **#2 — Sections/options de plats** : pouvoir définir, par plat, des sections personnalisées (accompagnements, suppléments, sauces…) avec des produits, certains inclus (gratuits) et d'autres payants ; le client les sélectionne avant de passer à la caisse.
- **#5 — Filtres gérables** : pouvoir lister, ajouter, modifier, supprimer les filtres de l'app users (Mieux notés, Promotions, etc.) depuis le dashboard.

État actuel :
- `Plat` ne possède que `ingredients` (List<String>), `prix`, `categoriePlat`. Aucun modèle d'options. L'édition d'un plat n'est pas implémentée (le bouton crayon de `RestaurantMenu.tsx` est inactif).
- Les filtres sont codés en dur dans l'app users (`QUICK_FILTERS` dans `RestaurantsPage.tsx`, `AlimentairesPage.tsx`, `CosmetiquesPage.tsx`) et portent une **logique** (tri par note, filtre promos, distance, délai, ouverts). Aucune entité backend.

## Décisions de cadrage (validées)

| Sujet | Décision |
|------|----------|
| #2 — Qui gère les options | Admin (dashboard) **et** restaurateur (app mobile, sur ses propres plats) |
| #2 — Modèle | Riche par section : mode SINGLE/MULTIPLE, obligatoire/optionnel, min/max, items avec prix (0 = inclus) |
| #2 — Périmètre | Complet : définition + sélection client à la caisse + prix répercuté dans panier **et** commande |
| #5 — Modèle | Comportements prédéfinis (en code) + filtres `CATEGORIE` créables ; admin active/désactive/renomme/réordonne/icône/supprime |
| #5 — Portée | Par verticale/contexte : RESTAURANT, ALIMENTAIRE, COSMETIQUE (3 barres distinctes) |

## Non-objectifs (YAGNI)

- Pas de quantité par item d'option (ex : 2× fromage) dans cette itération.
- Pas de moteur de requête générique pour les filtres (les comportements restent du code).
- Pas de réorganisation non liée du code existant.

---

## Feature #2 — Options de plats

### Modèle de données (backend `MySuguClientApp`)

Deux nouvelles entités, rattachées à `Plat` :

**`OptionGroup`** (table `plat_option_groups`)
- `id` (Long, PK)
- `plat` (ManyToOne `Plat`, FK `plat_id`, not null)
- `nom` (String, ex « Accompagnement »)
- `selectionMode` (enum `OptionSelectionMode` : `SINGLE` | `MULTIPLE`)
- `obligatoire` (boolean, défaut false)
- `minSelections` (Integer, défaut 0)
- `maxSelections` (Integer, nullable ; `SINGLE` ⇒ 1)
- `ordre` (Integer)
- `items` (OneToMany `OptionItem`, cascade ALL, orphanRemoval)

**`OptionItem`** (table `plat_option_items`)
- `id` (Long, PK)
- `group` (ManyToOne `OptionGroup`, FK `group_id`, not null)
- `nom` (String, ex « Frites »)
- `prixSupplement` (BigDecimal, défaut 0 — 0 = inclus/gratuit)
- `disponible` (boolean, défaut true)
- `ordre` (Integer)

Ajout sur `Plat` : `@OneToMany(mappedBy="plat", cascade=ALL, orphanRemoval=true) List<OptionGroup> optionGroups`.

Invariants : `SINGLE` ⇒ `maxSelections = 1` ; `obligatoire` ⇒ `minSelections ≥ 1` ; `minSelections ≤ maxSelections` (si max défini).

### DTOs
- `OptionGroupDTO { id, nom, selectionMode, obligatoire, minSelections, maxSelections, ordre, items: [OptionItemDTO] }`
- `OptionItemDTO { id, nom, prixSupplement, disponible, ordre }`
- `PlatDTO` enrichi de `optionGroups: [OptionGroupDTO]`.

### API de gestion des options
Endpoints JSON dédiés (le `POST/PUT /api/plats` reste en multipart pour l'image ; les options imbriquées passent par JSON) :

- `GET /api/plats/{platId}/options` → `[OptionGroupDTO]` — **public** (affichage fiche plat).
- `PUT /api/plats/{platId}/options` → remplace **toute** la structure (groupes + items) d'un plat à partir d'un body JSON `[OptionGroupDTO]`. Sémantique *replace-all* (robuste pour l'édition imbriquée).
  - Sécurité : `hasRole('ADMIN')` **ou** `RESTAURANT_OWNER` propriétaire du restaurant du plat (vérification d'appartenance dans le service).
  - Validation : invariants ci-dessus ; rejet 400 sinon.

### Intégration Panier & Commande (périmètre complet)

**Ajout au panier** — `POST /api/panier/items` accepte désormais `optionItemIds: [Long]` (en plus de `platId`, `quantite`).
Le backend :
1. Charge les `OptionGroup` du plat.
2. **Valide** la sélection : pour chaque groupe obligatoire, `min ≤ nb sélectionnés ≤ max` ; `SINGLE` ⇒ exactement 1 (si obligatoire) ou 0/1 ; items doivent appartenir au plat et être `disponible`.
3. **Calcule** le prix unitaire de ligne = `plat.prix + Σ prixSupplement des items sélectionnés`.

**Snapshot** (traçabilité) — la ligne de panier et la **ligne de commande** stockent un instantané des options choisies, indépendant des entités d'options (qui peuvent évoluer) :
- Nouvelle entité fille `LigneCommandeOption` (table `ligne_commande_options`) : `{ id, ligneCommande_id, nomGroupe, nomItem, prixSupplement }`.
- Équivalent côté panier : `PanierItemOption` (table `panier_item_options`) `{ id, panierItem_id, optionItemId, nomGroupe, nomItem, prixSupplement }`.
- À la création de commande, le snapshot panier est recopié dans la commande.
- Le total de ligne = `prixUnitaireLigne × quantite` (le prix unitaire inclut déjà les suppléments).

DTOs panier/commande enrichis pour exposer les options choisies + le prix de ligne.

### UI

**Dashboard admin + app restaurateur**
- Éditeur de sections sur l'écran d'édition de plat : ajouter/supprimer une section, définir nom/mode/obligatoire/min/max, ajouter/supprimer des items avec prix et disponibilité, réordonner.
- **Pré-requis** : créer l'écran/drawer d'**édition de plat** côté `mysugu-admin` (`RestaurantMenu.tsx` — le crayon est aujourd'hui inactif). L'app restaurateur consomme les mêmes endpoints.

**App users (`mysugu-frontend`)**
- Sur la fiche plat (avant caisse) : afficher les sections, appliquer les règles (sélection unique/multiple, obligatoire, min/max), recalculer le prix en direct, et envoyer `optionItemIds` à l'ajout au panier.
- Panier/caisse : afficher les options choisies et le prix de ligne incluant les suppléments.

---

## Feature #5 — Filtres gérables

### Modèle de données (backend)

**`Filtre`** (table `filtres`)
- `id` (Long, PK)
- `contexte` (enum `FiltreContexte` : `RESTAURANT` | `ALIMENTAIRE` | `COSMETIQUE`)
- `comportement` (enum `FiltreComportement` : `TOUS` | `PROMOTIONS` | `MIEUX_NOTES` | `PLUS_PROCHES` | `PLUS_RAPIDES` | `OUVERTS` | `CATEGORIE`)
- `categorieId` (Long, nullable — utilisé seulement si `comportement = CATEGORIE`)
- `libelle` (String, renommable)
- `icone` (String, clé d'icône Lucide, ex « Star »)
- `ordre` (Integer)
- `actif` (boolean, défaut true)

Principe : **le comportement reste implémenté côté app** (mappé par `comportement`). L'admin gère présentation (libellé, icône, ordre, actif), peut **ajouter** des filtres `CATEGORIE` (vraies créations data) et **supprimer**. Ajouter un comportement totalement nouveau nécessite du code (assumé).

### API
- `GET /api/filtres?contexte=RESTAURANT` → `[FiltreDTO]` **public**, uniquement `actif`, triés par `ordre` (app users).
- `GET /api/admin/filtres?contexte=…` → admin (tous, inactifs inclus).
- `POST /api/admin/filtres` · `PUT /api/admin/filtres/{id}` · `DELETE /api/admin/filtres/{id}` → admin.
- `FiltreDTO { id, contexte, comportement, categorieId, libelle, icone, ordre, actif }`.

### Seed initial
Au démarrage (initializer conditionnel, exécuté une seule fois si la table est vide), insérer les filtres actuels par contexte pour éviter toute régression :
- RESTAURANT : Tous, Promotions, Mieux notés, Plus proches, Plus rapides, Ouverts (+ « Subsahariens » → filtre CATEGORIE si une catégorie correspond).
- ALIMENTAIRE : Tous, Fruits & légumes, Épicerie, Boissons, Promotions, Plus proches, Plus rapides (les catégories → `CATEGORIE`).
- COSMETIQUE : idem adapté.

### UI

**Dashboard admin (`mysugu-admin`)** — nouvelle page « Filtres » :
- Onglets par contexte (Restaurants / Alimentaire / Cosmétique).
- Liste des filtres ordonnés ; ajout (choisir un comportement du catalogue **ou** une catégorie), édition (libellé, icône, ordre, actif), suppression, réordonnancement.
- Entrée de menu dans la `Sidebar` + route dans `App.tsx`.

**App users (`mysugu-frontend`)**
- Remplacer les `QUICK_FILTERS` codés en dur par `GET /api/filtres?contexte=…`.
- Mapping `comportement → logique de tri/filtre existante` (déjà en code) ; `icone → composant Lucide` (table de correspondance) ; `CATEGORIE` ⇒ filtrage par catégorie.
- Repli : si l'appel échoue, conserver une liste par défaut (pas de barre vide).

---

## Sécurité (récapitulatif)
- `GET /api/plats/{id}/options`, `GET /api/filtres` : publics.
- `PUT /api/plats/{id}/options` : ADMIN ou RESTAURANT_OWNER propriétaire (contrôle d'appartenance).
- `POST /api/panier/items` (avec options) : utilisateur authentifié (CLIENT).
- `/api/admin/filtres/**` : ADMIN (couvert par la règle `/api/admin/**`).

## Migration / compatibilité
- `ddl-auto=update` crée les nouvelles tables ; les plats existants ont simplement zéro `OptionGroup` (comportement inchangé).
- Le seed des filtres ne s'exécute que si la table `filtres` est vide.
- Les lignes de panier/commande existantes sans options restent valides (listes vides).

## Tests
- Backend : validation des sélections (obligatoire/min/max/SINGLE), calcul de prix de ligne, snapshot recopié en commande, contrôle d'appartenance sur `PUT options`, seed filtres idempotent, `GET /api/filtres` public.
- Front : éditeur d'options (admin), sélection + prix en direct (app users), barre de filtres pilotée par l'API + repli.

## Phasage
1. **#5 Filtres** — entité + API + page dashboard + branchement app users (plus petit, faible risque).
2. **#2 — définition** — modèle d'options + gestion (dashboard/restaurateur, écran d'édition de plat) + affichage fiche plat.
3. **#2 — intégration panier/commande** — validation + prix + snapshots.

Chaque phase est livrable et testable indépendamment.
