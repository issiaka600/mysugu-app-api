# Verticales boutiques (alimentaire & cosmétique) — design

Date : 2026-08-09
Statut : validé, prêt pour découpage en plans d'implémentation

## 1. Objectif

Ouvrir l'achat en boutique alimentaire et cosmétique sur la plateforme, en réutilisant
le parcours restaurant existant. Contrainte structurante : le travail se fait côté
backend ; les apps mobiles changent le moins possible.

## 2. État des lieux

Une partie du socle est déjà en place, manifestement dans cette intention :

- `Vertical { RESTAURANT, ALIMENTAIRE, COSMETIQUE }` porté par `Restaurant.vertical`
- `GET /api/restaurants?vertical=…` filtre déjà, avec RESTAURANT par défaut
- `Plat.categorieProduit` (catégorie libre pour les verticales non-restaurant)
- `GET /api/plats/categories-produit?vertical=…`, mais les valeurs sont en dur
- `Filtre` par `FiltreContexte` (les 3 contextes sont semés en base, 2 inutilisés)
- `OptionGroup` / `OptionItem` sur les plats, avec suppléments de prix et snapshot
  au panier et à la commande

Côté app cliente (`MySuKu`), rien n'est branché : `_openService`
(`home_categories_screen.dart:309`) n'ouvre la liste que si le libellé de la tuile
contient « restaurant », sinon il affiche « sera disponible bientôt » ; le datasource
n'envoie jamais `vertical`.

## 3. Approche retenue

**Réutilisation totale du modèle existant.** Une boutique est une ligne `restaurants`
avec `vertical = ALIMENTAIRE | COSMETIQUE`. Un produit est une ligne `plats`. Aucune
table de commerce nouvelle, aucun endpoint de commande nouveau.

Écarté : des entités dédiées `Boutique` / `Produit`. `Commande.restaurant_id` est
`nullable = false` ; rendre la commande polymorphe toucherait la caisse livreur, les
gains, la facturation, les deux shims legacy et les trois apps mobiles — exactement ce
que la contrainte du projet interdit.

Écarté également : une façade `/api/commerces/*` en alias des mêmes tables. Elle
corrigerait le vocabulaire au prix d'une seconde surface d'API à maintenir, pour zéro
gain fonctionnel.

Conséquence assumée : le vocabulaire interne reste « restaurant / plat ». C'est une
dette de nommage, pas un défaut fonctionnel.

## 4. Décisions verrouillées

| Sujet | Décision |
|---|---|
| Stock | `quantiteStock` **en plus** de `isAvailable`, pas à la place |
| Déclinaisons | Réutiliser `OptionGroup` / `OptionItem` |
| Panier | Un seul panier, mono-commerçant — comportement actuel inchangé |
| Découverte | La verticale est portée par `/api/services`, l'app la relaie |
| Cycle de commande | Machine à états identique ; l'app livreur ne bouge pas |
| Frais de livraison | Inchangés : calculés par `ZoneDeploiement` selon la distance |
| Commission | Inchangée : `Restaurant.commissionPourcentage`, par commerçant |
| Multi-établissements | Hors périmètre — un compte propriétaire = un établissement |

## 5. Modèle de domaine : trois deltas

### 5.1 `Plat.quantiteStock` et `Plat.seuilAlerteStock`

Deux `Integer` nullables. `null` = stock non géré, ce qui est le cas de tous les plats
existants : migration nulle, aucune régression.

- Décrément à la création de commande, dans la transaction qui écrit `Commande`, sous
  verrou pessimiste sur la ligne produit. Les lignes dont `quantiteStock` est `null`
  sont ignorées : un plat de restaurant ne déclenche ni verrou ni décrément.
- Ré-incrément à l'annulation, quelle qu'en soit l'origine (client, commerçant, admin,
  commande restée `NON_FINALISEE`), symétriquement au refund Stripe existant, et une
  seule fois par commande — une commande déjà re-créditée ne l'est pas deux fois.
- Les DTO exposent une disponibilité effective :
  `isAvailable && (quantiteStock == null || quantiteStock > 0)`.
- `isAvailable` reste l'interrupteur maître du vendeur et n'est jamais écrasé par le
  stock : un réapprovisionnement rend le produit visible sans réintervention.
- Stock insuffisant à la validation du panier → 400 avec message métier explicite.

### 5.2 `CategorieRestaurant.vertical`

La table contient aujourd'hui des types de cuisine (« subsahariens », « marocains »).
Sans ce champ, un boutiquier doit ranger sa parapharmacie sous « marocains », et le
client voit des filtres de cuisine sur la barre boutique. `null` = RESTAURANT : les
données existantes restent correctes sans migration.

### 5.3 `ServiceCategorie.vertical`

La tuile d'accueil porte sa verticale. Remplace l'heuristique textuelle de
`home_service.dart:34`. Ajouter une verticale (pharmacie, etc.) devient une ligne en
base, sans release mobile.

### 5.4 Ce qui ne bouge pas

`Commande`, `LigneCommande`, `Panier`, `PanierItem`, les options et leurs snapshots,
`CaisseLivreur`, `GainsLivreur`, `FacturationRestaurant`, les zones de déploiement et
le calcul de frais, le chat unifié, les avis, les codes promo, la fidélité, le wallet,
le shim livreur, le shim vendeur.

## 6. Surface API

### 6.1 Fuites de verticale à colmater

Dès qu'une boutique existe en base, ces endpoints mélangent les univers :

- `GET /api/restaurants/search`
- `GET /api/restaurants/top-rated`
- `GET /api/restaurants/nearby`
- `GET /api/plats`
- `GET /api/plats/search`

On y ajoute `vertical`, avec **exactement la même convention que le listing existant** :
absent ⇒ RESTAURANT, `ALL` ⇒ toutes verticales, valeur inconnue ⇒ 400.

C'est ce qui garantit qu'une app non mise à jour continue de voir strictement
l'univers restaurant. La compatibilité descendante est structurelle.

### 6.2 Navigation par rayon

Un restaurant a une vingtaine de plats rangés par `CategoriePlat` ; une supérette a
plusieurs centaines de références.

- Paramètre `categorieProduit` sur `GET /api/plats` et `GET /api/restaurants/{id}/plats`
- `RestaurantDTO` expose la liste des rayons non vides de l'établissement
- `RestaurantDTO` expose `vertical`
- Les catégories produit sortent du code en dur de `CategorieProduitController` et
  passent en base, derrière la même URL `GET /api/plats/categories-produit?vertical=`

### 6.3 Dette de performance à traiter

`PlatServiceImpl.getAllPlats` fait `platRepository.findAll()`, filtre en mémoire puis
pagine en mémoire. Tolérable sur des cartes de restaurant, intenable sur des catalogues
de supérette. À basculer sur une requête paginée en base, filtres poussés dans le SQL,
avec index sur `plats(restaurant_id, categorie_produit)` et
`restaurants(vertical, is_active)`.

## 7. Delta app cliente (MySuKu)

### Ce que l'app n'a pas à faire

- Connaître le stock : la disponibilité exposée est déjà pondérée par le stock ; une
  app qui ignore `quantiteStock` se comporte correctement.
- Gérer les déclinaisons : elles passent par les options, que l'écran de détail rend déjà.
- Toucher au paiement, au suivi, au chat, aux avis, aux codes promo.

### Les cinq points de contact

1. `home_service.dart` — champ `vertical` ; `opensRestaurants` devient `vertical != null`
2. `home_categories_screen.dart:309` — `_openService` passe `service.vertical` à
   l'écran de liste au lieu du message « bientôt disponible »
3. `restaurants_screen.dart` — accepte et relaie `vertical` ; titre = `service.nom`
4. `restaurant_remote_datasource.dart` — `vertical` en query sur listing, search,
   nearby, top-rated
5. Écran commerce — onglets par rayon alimentés par les rayons du `RestaurantDTO`

### Vocabulaire

`RestaurantDTO.vertical` permet à l'app une table de correspondance locale
(« Menu » → « Catalogue », « plat » → « produit »). Un endpoint de libellés servi par
le backend a été écarté : il n'économise aucune plomberie côté app et ajoute une
surface d'API pour du texte stable.

### Les autres apps

L'app livreur ne voit qu'une commande de plus, dans la même machine à états : aucun
changement.

## 8. Côté commerçant

L'app vendeur Tiktak lit et écrit déjà `current_stock` et `minimum_order_qty`
(`product_model.dart:211`, `add_product_repository.dart:165`). Le shim vendeur mappe
`Plat.quantiteStock` ↔ `current_stock` et `categorieProduit` sur le champ catégorie
existant : le boutiquier gère son stock depuis l'app vendeur **sans une ligne de Dart**.

Commandes, statuts, assignation livreur, coupons, stats, finances et chat vendeur sont
déjà servis par le shim et ignorent la verticale.

### Contrainte : un compte propriétaire = un établissement

`SellerContext.currentRestaurant` s'appuie sur `findByOwnerId`, qui renvoie un seul
établissement, et `soumettreOnboarding` refuse explicitement un second. La base de
production contient déjà des propriétaires à deux établissements (cause des 500 de
juillet), et la parade posée alors est un `ORDER BY … LIMIT 1` : le second établissement
est invisible et ingérable.

Le multi-établissements reste **hors périmètre** : le contrat 6valley de l'app vendeur
est mono-boutique de bout en bout, sans sélecteur d'établissement. Le supporter
imposerait de modifier l'app vendeur. Règle produit explicite : un commerçant à deux
enseignes ouvre deux comptes. Le comportement doit être honnête (refus clair) plutôt
que silencieux. Le vrai multi-établissements est une initiative séparée.

## 9. Côté admin (mysugu-admin)

Aucun écran à créer, une dimension à ajouter.

- `Restaurants` et `RestaurantOnboarding` : filtre et colonne verticale, champ
  `vertical` au formulaire de création
- `Categories` : distinguer lisiblement catégories d'**établissement**
  (`CategorieRestaurant`, portées par une verticale) et catégories de **produit**
  (les rayons)
- `RestaurantMenu` : stock et seuil d'alerte affichés pour une boutique, masqués pour
  un restaurant
- `Filtres` : rien à faire, les trois contextes sont déjà gérés

## 10. Découpage en sous-projets

Séquentiels, chacun avec son spec et son plan, chacun livrable et vérifiable seul.

1. **Socle domaine et API** (backend seul) — les trois champs, le stock verrouillé, la
   verticale sur les cinq endpoints, les rayons en base, la requête paginée.
   Vérifiable sans aucune app : boutique de démo, catalogue interrogeable, commande
   passée par API jusqu'à la livraison.
2. **Admin** — rend le socle exploitable par un humain et permet de peupler des données
   réelles pour la suite.
3. **Commerçant** — mapping du stock dans le shim vendeur, validation sur l'app vendeur
   existante.
4. **Client mobile** — les cinq points de contact, puis smoke test du parcours complet.
   Seul sous-projet embarquant du Dart, volontairement en dernier.

## 11. Tests

Deux exigences non négociables :

- **Non-régression par endpoint touché** : en l'absence du paramètre `vertical`, la
  réponse est identique à aujourd'hui. Garantie contractuelle exécutable que les apps
  non mises à jour ne bougent pas.
- **Concurrence sur le stock** : deux commandes simultanées sur le dernier article, une
  seule passe. Les surventes ne se voient jamais en test manuel.

Plus la couverture ordinaire : décrément et ré-incrément symétriques, disponibilité
effective, filtrage par rayon, catégories d'établissement par verticale, refus du
second établissement.

## 12. Prérequis et pièges d'environnement

- Le dépôt local `MySuguClientApp` est sur `dev` à `9accf13`, en retard sur `origin`.
  Le shim vendeur (`legacy/seller`) vit sur `feat/backend-shims`. **Vérifier avant de
  démarrer** que cette branche est fusionnée dans `dev`, sinon le mapping du stock côté
  vendeur n'a pas de socle.
- `mvn test` en local échoue faussement à cause de `.class` périmés dans `target/` :
  `rm -rf target` d'abord.
- Agent Mockito explicite sous JDK 21 :
  `-DargLine="-javaagent:…/mockito-core-5.20.0.jar -Xshare:off"`.
- `git` sur `/mnt/c` est lent (status/diff > 2 min) : prévoir des timeouts longs.
