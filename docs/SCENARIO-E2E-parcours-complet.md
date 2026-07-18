# Scénario E2E — Parcours complet Client → Vendeur → Livreur

Playbook de bout en bout du backend MySugu, **fidèle aux appels réels des 3 apps mobiles** :

| Rôle | App (Flutter) | Namespace backend |
|------|---------------|-------------------|
| Client | `MySuKu` | `/auth/*`, `/api/*` (natif Spring) |
| Vendeur | `Tiktak-vendor-app-moso` | `/api/v3/seller/*` (shim 6valley) |
| Livreur | `Tiktak-deliverer-app` | `/api/v2/delivery-man/*` (shim 6valley) |

Les trois shims tapent le **même backend** et le **même store** (commandes, chat unifié, users). Ce document couvre : création & connexion des comptes → catalogue → commande → préparation → dispatch → livraison → chats croisés → stats/GET/mises à jour.

> Légende : ✅ = **vérifié en prod** pendant la rédaction · 📱 = tel que l'app mobile l'envoie · ⚠️ = piège à connaître.

---

## Phase 0 — Prérequis & conventions

- **Base URL paramétrable** (défaut prod) :
  ```bash
  BASE_URL="${BASE_URL:-https://api.mysukuapp.com}"
  ```
- **Auth** : tout appel authentifié porte l'en-tête `Authorization: Bearer <token>`. Le token s'obtient au login (clé `token` dans la réponse). Les 3 namespaces acceptent le **même JWT MySugu** (le rôle est encodé dedans).
- **Récupérer un token** (helper) :
  ```bash
  tok() { curl -s -X POST "$1" -H 'Content-Type: application/json' -d "$2" \
    | python3 -c "import json,sys;print(json.load(sys.stdin).get('token',''))"; }
  ```
- ⚠️ **Method-spoofing Laravel** : les apps vendeur/livreur envoient beaucoup de **POST** avec un champ `"_method":"put"` (ou `"delete"`) dans le corps. Le backend l'accepte. Ce n'est pas un vrai PUT/PATCH HTTP.
- ⚠️ **Chat client sur un autre host dans l'app** : l'app client pointe historiquement le chat vers `dashboard.atlantique21service.one`. Le backend MySugu implémente le **même contrat** sous `BASE_URL/api/v1/customer/chat/*` (chat unifié). Ce playbook teste MySugu → on utilise `BASE_URL`.

### Comptes de démonstration (déjà en prod)
| Rôle | Identifiant | Mot de passe | Login |
|------|-------------|--------------|-------|
| Client | `client.demo@mysugu.ma` | `Client2026!` | email |
| Vendeur | `gerant.demo@mysugu.ma` (resto « Le Gourmet Démo » id 11) | `Gerant2026!` | email |
| Livreur | tél `+212600778899` | `Livreur2026!` | tél |
| Admin | `newadmin@mysugu.ma` | `Mysugu2024!` | email |

### Tables d'enum (chaînes exactes envoyées par les apps)
- **Statut commande — natif** (`statut`, app client) : `EN_ATTENTE, CONFIRMEE, EN_PREPARATION, PRETE, ASSIGNEE_LIVREUR, EN_COURS, LIVREE, ANNULEE, NON_FINALISEE`.
- **Statut commande — 6valley** (`order_status`, apps vendeur/livreur) : `pending, confirmed, processing, out_for_delivery, delivered, canceled, returned, failed`.
- **Correspondance** (utilisée par `orders/list` ET `order-statistics`) :
  `EN_ATTENTE→pending`, `CONFIRMEE→confirmed`, `EN_PREPARATION|PRETE→processing`, `ASSIGNEE_LIVREUR|EN_COURS→out_for_delivery`, `LIVREE→delivered`, `ANNULEE→canceled`, `NON_FINALISEE→failed`.
- **Paiement** : `ESPECES` (alias acceptés : `CASH_ON_DELIVERY`, `COD`, `CASH`) · `CARTE_BANCAIRE` (alias `CARD`, `CREDIT_CARD`, `DIGITAL_PAYMENT`, `STRIPE`).
- **Mode réception** : `LIVRAISON` (adresse requise) · `RETRAIT_SUR_PLACE`.

---

## Phase 1 — Création & connexion des comptes

### 1.1 Client (app MySuKu) ✅
1. **Inscription** — `POST /auth/register`
   ```json
   {"email":"client1@demo.ma","password":"Passw0rd!","nom":"Konaté","prenom":"Awa","telephone":"+212600112233","role":"CLIENT"}
   ```
   → `201`, renvoie le `UserModel` (id, email, role=CLIENT). Pas d'OTP, l'app renvoie ensuite vers l'écran de login.
2. **Login** — `POST /auth/login` `{"email":"client1@demo.ma","password":"Passw0rd!"}` → `{token, user}`.
3. **Profil** — `GET /users/profile` (Bearer) → l'`id` renvoyé sert de `clientId` pour panier/commande.

### 1.2 Vendeur (app Tiktak-vendor)
**Login = email** (⚠️ pas téléphone) :
- `POST /api/v3/seller/auth/login` `{"email":"gerant.demo@mysugu.ma","password":"Gerant2026!"}` → `{token}` ✅ (role RESTAURANT_OWNER).
- Après login l'app enregistre le token FCM : `POST /api/v3/seller/cm-firebase-token` `{"_method":"put","cm_firebase_token":"<fcm>"}`.

**Inscription vendeur** (self-register, 📱) — `POST /api/v3/seller/registration` **multipart/form-data** :
- champs : `f_name, l_name, phone` (= indicatif+numéro, ex. `+212XXXXXXXXX`), `email, password, confirm_password, shop_name, shop_address`
- fichiers : `image` (profil), `logo`, `banner`, `bottom_banner`
- → le compte/boutique est créé **en attente d'approbation admin** (inactif tant que non approuvé).

**Variante native + approbation admin** (parcours onboarding MySugu, utile pour valider la boutique) :
1. `POST /api/restaurateur/register` (public) — crée le compte RESTAURANT_OWNER.
2. `POST /api/restaurateur/restaurant` (Bearer) — soumet le restaurant (passe en `EN_ATTENTE`).
3. `POST /api/restaurateur/restaurant/justificatifs` — pièces jointes éventuelles.
4. **Admin** : `GET /api/admin/restaurants/a-reviser` → puis `POST /api/admin/restaurants/{id}/approuver` (ou `/rejeter`, `/demander-complement`). Le restaurant devient `APPROUVE` + actif.
5. Le vendeur voit alors sa boutique : `GET /api/restaurateur/mon-restaurant` ✅ / `GET /api/v3/seller/shop-info` ✅.

### 1.3 Livreur (app Tiktak-deliverer)
⚠️ **L'app livreur n'a AUCUNE inscription** (login uniquement) — les comptes sont créés par l'admin/back-office.
- **Création (côté admin)** — `POST /auth/register` avec `role=LIVREUR` ✅ :
  ```json
  {"email":"livreur1@demo.ma","password":"Livreur2026!","nom":"Traoré","prenom":"Ibrahim","telephone":"+212600778899","role":"LIVREUR"}
  ```
  (Le `telephone` doit être au format E.164 `+212…` : c'est ce que l'app comparera au login.)
- **Login (app, par téléphone)** — `POST /api/v2/delivery-man/auth/login` 📱 :
  ```json
  {"country_code":"212","phone":"600778899","password":"Livreur2026!"}
  ```
  ⚠️ L'app envoie `country_code` **sans `+`** et `phone` = **numéro local tel que saisi** (le backend reconstruit `+212600778899`). → `{token}` ✅.
- Après login : `POST /api/v2/delivery-man/update-fcm-token` `{"_method":"put","fcm_token":"<token>"}`.

---

## Phase 2 — Client : catalogue & panier ✅

Tous en Bearer client.
1. **Home / services** — `GET /api/services`
2. **Catégories** — `GET /api/categories` · `GET /api/categories/{id}/restaurants`
3. **Restaurants** — `GET /api/restaurants?page=0&size=20` (lit `content[]`) · détail `GET /api/restaurants/{id}` · proches `GET /api/restaurants/nearby?latitude=..&longitude=..&radiusKm=5` · recherche `GET /api/restaurants/search?keyword=Gourmet`
4. **Plats** — `GET /api/restaurants/{id}/plats` ou `GET /api/plats?restaurantId=11` · détail `GET /api/plats/{id}` · recherche `GET /api/plats/search?keyword=Poulet`
5. **Filtres** — `GET /api/filtres?contexte=RESTAURANT`
6. **Panier (serveur)** :
   - Voir : `GET /api/panier`
   - Ajouter : `POST /api/panier/items` `{"platId":5,"quantite":1}`
   - Modifier qté : `PATCH /api/panier/items/{itemId}?quantite=2` (⚠️ qté en **query param**)
   - Vider : `DELETE /api/panier`

---

## Phase 3 — Client : passage de commande ✅

**`POST /api/commandes`** (Bearer client) — corps exact envoyé par l'app (`cart_screen.dart`) :
```json
{
  "clientId": 30,
  "restaurantId": 11,
  "lignes": [{"platId": 5, "quantite": 1}],
  "methodePaiement": "ESPECES",
  "modeReception": "LIVRAISON",
  "adresseLivraison": {
    "latitude": 31.63, "longitude": -8.0,
    "adresse": "Av Mohammed V", "ville": "Marrakech",
    "pays": "Maroc", "codePostal": "40000"
  },
  "commentaire": "",
  "montantTotal": 30
}
```
→ `201`, renvoie la commande (`id`, `numeroCommande`, statut `EN_ATTENTE`). ⚠️ L'app n'envoie que `ESPECES` + `LIVRAISON` (codés en dur au checkout) ; le backend accepte aussi `RETRAIT_SUR_PLACE` (sans adresse) et les alias de paiement.
- **Code promo** (optionnel, avant commande) : `GET /api/codes-promo/disponibles` ✅ puis `POST /api/codes-promo/valider` `{"code":"XXX","montantCommande":30}` ✅.

---

## Phase 4 — Vendeur : réception & préparation ✅

Bearer vendeur.
1. **Boutique** — `GET /api/v3/seller/shop-info` · `GET /api/v3/seller/seller-info`
2. **Commandes** — `GET /api/v3/seller/orders/list?limit=10&offset=1&status=all`
   (onglets `status` : `all, pending, confirmed, processing, out_for_delivery, delivered, returned, failed, canceled`)
3. **Détail** — `GET /api/v3/seller/orders/{id}` · suivi `GET /api/v3/seller/orders/{id}/live-tracking`
4. **Avancer le statut** — `POST /api/v3/seller/orders/order-detail-status/{id}` `{"_method":"put","order_status":"confirmed"}` puis `"processing"` ✅
   ⚠️ **Workflow séquentiel strict** : `pending → confirmed → processing → out_for_delivery → delivered`. On ne saute pas d'étape (ex. `pending → processing` = **400**).
5. **Paiement** — `POST /api/v3/seller/orders/update-payment-status` `{"order_id":23,"payment_status":"paid"}` (`paid`/`unpaid`).

---

## Phase 5 — Dispatch & Livraison ✅ (chaîne prouvée en prod)

1. **Vendeur assigne un livreur** — `POST /api/v3/seller/orders/assign-delivery-man`
   `{"_method":"put","order_id":23,"delivery_man_id":28}` → `200` ✅
   (Modèle alternatif « FCFS » : la commande est diffusée aux livreurs disponibles, aucun `assign` préalable, le livreur la revendique via `/accept`.)
2. **Livreur en ligne** — `POST /api/v2/delivery-man/is-online` `{"is_online":1,"_method":"put"}`
   ⚠️ L'app utilise **`is-online`**, PAS `change-status`.
3. **Livreur voit ses commandes** — `GET /api/v2/delivery-man/current-orders` (contient la #23) ✅ · historique `GET /api/v2/delivery-man/all-orders?status=&limit=10&offset=1`
4. **Accepter** — `POST /api/v2/delivery-man/{orderId}/accept` `{"order_id":23}` → `200` ✅ (⚠️ `409` si un autre livreur l'a déjà prise).
5. **En route** — `POST /api/v2/delivery-man/update-order-status` `{"order_id":23,"status":"out_for_delivery","_method":"put"}` → `200` ✅
6. **Livraison (photo + OTP)** :
   - Photo (si config `imageUpload==1`) — `POST /api/v2/delivery-man/order-delivery-verification` **multipart** : `order_id` + parts fichier `image[0]`, `image[1]`…
   - Code de livraison — le client reçoit un `verification_code` (notification/FCM) ; le livreur le saisit :
     `POST /api/v2/delivery-man/verify-order-delivery-otp` `{"order_id":23,"verification_code":"1234"}`
     (renvoi du code : `POST /api/v2/delivery-man/resend-verification-code` `{"order_id":23}`)
   - Puis paiement + livré : `POST .../update-payment-status {"order_id":23,"payment_status":"paid"}` → `POST .../update-order-status {"order_id":23,"status":"delivered","_method":"put"}`.
7. **Distance / position** — `GET /api/v2/delivery-man/seller-location?seller_id=..&order_id=23` · `POST /api/v2/delivery-man/distance-api {origin_lat,origin_lng,destination_lat,destination_lng}`.

---

## Phase 6 — Chats croisés (chat unifié, 3 façades)

Les 3 façades lisent/écrivent la **même conversation** : l'identité vendeur = `(RESTAURANT, restaurantId)`, client = `(CUSTOMER, userId)`, livreur = `(LIVREUR, userId)`.

### 6.1 Côté client (app MySuKu) — `/api/v1/customer/chat/*`
`{type}` : `seller` (restaurant) ou `delivery-man`.
- Liste : `GET /api/v1/customer/chat/list/seller?offset=1&limit=50` (lit `chat[]`)
- Messages : `GET /api/v1/customer/chat/get-messages/seller/{recipientId}?offset=1&limit=50` (`message[]`, `sent_by_customer==1` = mien)
- Envoyer : `POST /api/v1/customer/chat/send-message/seller` — **multipart** champs `id` (destinataire) + `message` ⚠️ (pas de JSON : envoyé en form-data)
- Vu : `POST /api/v1/customer/chat/seen-message/seller` — form-data `{"id":<recipientId>}`

### 6.2 Côté vendeur (app Tiktak-vendor) — `/api/v3/seller/messages/*` ✅
`{type}` : `customer` ou `delivery-man`.
- Liste : `GET /api/v3/seller/messages/list/customer?limit=30&offset=1` ✅ (`{total_size,limit,offset,chat[]}`)
- Messages : `GET /api/v3/seller/messages/get-message/customer/{id}?limit=30&offset=1`
- Envoyer : `POST /api/v3/seller/messages/send/customer` — **multipart** : `id` (=id client), `message`, fichiers part `image[]` ✅
- Recherche : `GET /api/v3/seller/messages/search/customer?search=..`

### 6.3 Côté livreur (app Tiktak-deliverer) — `/api/v2/delivery-man/messages/*` ✅
`{type}` : `customer` ou `seller`.
- Liste : `GET /api/v2/delivery-man/messages/list/customer?limit=10&offset=1`
- Messages : `GET /api/v2/delivery-man/messages/get-message/customer/{id}?limit=10&offset=1`
- Envoyer : `POST /api/v2/delivery-man/messages/send-message/customer` — **multipart** : `message`, `id`, fichiers `image[0]`, `image[1]`…

---

## Phase 7 — Stats, GET & mises à jour

### 7.1 Vendeur ✅
- **Statistiques commandes** — `GET /api/v3/seller/order-statistics?statistics_type=overall` (`today`/`this_month`).
  Renvoie `pending, confirmed, processing, out_for_delivery, delivered, canceled, returned, failed, total`. ✅ **`total` = somme des buckets = réconcilie avec `orders/list`.**
- **Revenus** — `GET /api/v3/seller/get-earning-statitics?type=yearEarn` (⚠️ orthographe `statitics`, types `yearEarn/MonthEarn/WeekEarn`) → clés `seller_earn`, `commission_earn`.
- **Produits** — liste `GET /api/v3/seller/products/{sellerId}/all-products?limit=20&offset=0` · détail `.../products/details/{id}` · ajout `POST /api/v3/seller/products/add` (JSON) · statut `POST /api/v3/seller/products/status-update {"id","status","_method":"put"}` · stock `.../products/quantity-update`.
  (Côté natif, création d'un plat : `POST /api/plats` **multipart à plat** — champs `nom, prix, restaurantId, categoriePlat, description, ingredients…` + fichier `image`. ⚠️ pas un part JSON.)
- **Livreurs** — `GET /api/v3/seller/delivery-man/list?limit=10&offset=1` · encaissement `POST /api/v3/seller/delivery-man/cash-receive {"deliveryman_id","amount"}` · top `GET /api/v3/seller/top-delivery-man`.
- **Transactions** — `GET /api/v3/seller/transactions?status=..`.

### 7.2 Livreur ✅
- Profil — `GET /api/v2/delivery-man/info` · config `GET /api/v1/config`.
- Gains — `GET /api/v2/delivery-man/delivery-wise-earned?limit=10&offset=1` · caisse `GET /api/v2/delivery-man/collected_cash_history?limit=10&offset=1`.
- Commission — `GET /api/v2/delivery-man/commission/{today|week|month|all}` (custom : `?start_date=&end_date=`) · marquer payé `POST .../commission/{type} {"transaction_ref":".."}`.
- Retrait — `POST /api/v2/delivery-man/withdraw-request {"amount","note"}` · `GET /api/v2/delivery-man/withdraw-list-by-approved`.
- Notifications — `GET /api/v2/delivery-man/notifications?limit=20&offset=1`.

### 7.3 Client ✅
- Mes commandes — `GET /api/commandes/client/{clientId}` · détail `GET /api/commandes/{id}` · suivi `GET /api/commandes/{id}/tracking` · en cours `GET /api/commandes/en-cours`.
- Annuler — `PATCH /api/commandes/{id}/status {"statut":"ANNULEE"}`.
- Avis — `POST /api/avis {"commandeId","noteRestaurant","noteLivreur","commentaire"}` (après livraison) · `GET /api/avis/mes-avis`.
- Adresses — `GET/POST /api/users/adresses` · `PUT /api/users/adresses/{id}` · défaut `PATCH /api/users/adresses/{id}/default`.
- Favoris — `GET /api/favoris` · toggle `POST /api/favoris/{restaurantId}/toggle`.
- Portefeuille — `GET /api/wallet` ✅ · recharger `POST /api/wallet/recharger {"montant":50}` · transactions `GET /api/wallet/transactions`.
- Fidélité — `GET /api/fidelite` ✅ · historique `GET /api/fidelite/historique`.
- Notifications — `GET /api/notifications` · tout lu `POST /api/notifications/lire-toutes` · token FCM `POST /api/device-tokens/register {"userId","token","platform"}`.

---

## Phase 8 — Règles & pièges à retenir

1. **Workflow de statut = sens unique et séquentiel** : `pending → confirmed → processing → out_for_delivery → delivered`. Sauter une étape ou revenir en arrière → `400`. `out_for_delivery` exige une commande **assignée à un livreur** (via `assign-delivery-man` ou `/accept`).
2. **Login livreur** : indicatif `212` (sans `+`) + numéro local ; le `telephone` en base doit être `+212…`.
3. **Login vendeur = email** (pas téléphone).
4. **Paiement** : l'app client envoie `ESPECES` ; les alias `CASH_ON_DELIVERY`/`CARD`/`DIGITAL_PAYMENT` sont acceptés.
5. **Autorisations** : `GET /api/commandes` (liste globale) = **ADMIN only** ; le client utilise `/api/commandes/client/{id}`, le vendeur `/api/v3/seller/*`, le livreur `/api/v2/delivery-man/*`.
6. **`_method` spoofing** : la plupart des mutations vendeur/livreur sont des `POST` avec `"_method":"put"|"delete"` dans le corps.
7. **Entrées invalides** : paramètre requis manquant / mauvais content-type → `400` (plus de `500` opaque).
8. **Chat** : `id` = id de l'**interlocuteur** (pas de la conversation) ; part fichier `image[]` (vendeur/livreur v2 : `image[0]`, `image[1]`).

---

## Annexe — Récap des créations de compte

| Rôle | Endpoint de création | Login | Note |
|------|----------------------|-------|------|
| Client | `POST /auth/register` (role CLIENT) | `POST /auth/login` (email) | self-service |
| Vendeur | `POST /api/v3/seller/registration` **ou** `POST /api/restaurateur/register` + approbation admin | `POST /api/v3/seller/auth/login` (email) | approbation admin requise pour activer la boutique |
| Livreur | `POST /auth/register` (role LIVREUR, tél +212…) — action admin | `POST /api/v2/delivery-man/auth/login` (tél) | pas de self-register dans l'app |

> Ce playbook reflète l'état déployé le 2026-07-18 (backend `origin/dev`). Sources : apps `MySuKu`, `Tiktak-vendor-app-moso`, `Tiktak-deliverer-app` (constantes de routes + repositories) ; nombreuses étapes vérifiées en prod contre `https://api.mysukuapp.com`.
