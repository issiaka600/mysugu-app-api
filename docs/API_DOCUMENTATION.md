# Documentation API — MySugu Backend

**Version** : 1.0 — Mars 2026
**Backend** : Spring Boot 4 · Java 21 · PostgreSQL
**Base URL** : `http://localhost:8083` (dev) · `https://api.mysugu.ma` (prod)
**Authentification** : JWT Bearer Token

---

## Table des matières

1. [Vue d'ensemble de l'architecture](#1-vue-densemble)
2. [Authentification & Utilisateurs](#2-authentification--utilisateurs)
3. [Catalogue](#3-catalogue-restaurants-plats-catégories-menus)
4. [Commandes & Panier](#4-commandes--panier)
5. [Livraison & Tracking](#5-livraison--tracking-websocket)
6. [Finance](#6-finance)
7. [Marketing](#7-marketing)
8. [Notifications & Push](#8-notifications--push)
9. [Administration](#9-administration)
10. [Fichiers](#10-fichiers)
11. [Référence rapide par rôle](#11-référence-rapide-par-rôle)
12. [Codes d'erreur](#12-codes-derreur)

---

## 1. Vue d'ensemble

### 1.1 Architecture générale

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          CLIENTS                                        │
│                                                                         │
│   Web (React)        Android App        iOS App        Admin Panel      │
└──────────┬───────────────┬─────────────────┬──────────────┬────────────┘
           │               │                 │              │
           └───────────────┴─────────────────┴──────────────┘
                                    │
                           HTTPS + JWT Bearer
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      BACKEND MYSUGU  :8083                              │
│                                                                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌────────────┐ │
│  │    Auth &    │  │   Catalogue  │  │  Commandes   │  │  Finance   │ │
│  │  Utilisateurs│  │ Restaurants  │  │   & Panier   │  │  & Wallet  │ │
│  └──────────────┘  └──────────────┘  └──────────────┘  └────────────┘ │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌────────────┐ │
│  │ Notifications│  │   Tracking   │  │  Marketing   │  │   Admin    │ │
│  │  & Push FCM  │  │  WebSocket   │  │  & Promos    │  │    Stats   │ │
│  └──────────────┘  └──────────────┘  └──────────────┘  └────────────┘ │
└──────────┬────────────────┬───────────────────────────────────────────┘
           │                │
     ┌─────▼─────┐   ┌──────▼──────┐
     │ PostgreSQL │   │   Firebase  │
     │     DB    │   │  FCM Push   │
     └───────────┘   └─────────────┘
                            │
              ┌─────────────┼──────────────┐
              ▼             ▼              ▼
           Android         iOS            Web
```

### 1.2 Rôles utilisateurs

| Rôle | Description | Accès |
|---|---|---|
| `CLIENT` | Utilisateur final qui passe des commandes | Catalogue, Commandes, Panier, Wallet, Favoris |
| `LIVREUR` | Livreur qui effectue les livraisons | Commandes assignées, Gains, Caisse, Disponibilité |
| `RESTAURANT_OWNER` | Propriétaire d'un restaurant | Gestion restaurant, Commandes, Dashboard, Facturation |
| `RESTAURANT_STAFF` | Employé d'un restaurant | Accès limité au restaurant employeur |
| `ADMIN` | Administrateur plateforme | Accès total + statistiques + campagnes |

### 1.3 Format des requêtes et réponses

Toutes les requêtes et réponses utilisent **JSON** sauf indication contraire (upload de fichiers → `multipart/form-data`).

**Header obligatoire pour les endpoints protégés :**
```
Authorization: Bearer <jwt_token>
```

**Format de date** : ISO 8601 — `2026-03-28T14:30:00`

**Devise** : MAD (Dirham marocain) — symbole `Dh`

---

## 2. Authentification & Utilisateurs

### 2.1 Flux d'authentification

```
┌─────────┐                          ┌─────────────┐
│ Client  │                          │   Backend   │
└────┬────┘                          └──────┬──────┘
     │                                      │
     │  POST /auth/register                 │
     │  { email, password, nom, role… }     │
     │─────────────────────────────────────►│
     │                                      │── Crée l'utilisateur
     │◄─────────────────────────────────────│── Envoie email vérification
     │  201 { UserDTO }                     │
     │                                      │
     │  POST /api/auth/verify-email         │
     │  { token }                           │
     │─────────────────────────────────────►│
     │◄─────────────────────────────────────│
     │  200 OK                              │
     │                                      │
     │  POST /auth/login                    │
     │  { email, password }                 │
     │─────────────────────────────────────►│
     │◄─────────────────────────────────────│
     │  200 { token, user: UserDTO }        │
     │                                      │
     │  (utilise le token JWT dans tous     │
     │   les appels suivants)               │
     │                                      │
     │  POST /api/auth/refresh              │
     │  { refreshToken }                    │
     │─────────────────────────────────────►│
     │◄─────────────────────────────────────│
     │  200 { accessToken, refreshToken }   │
     │                                      │
     │  POST /api/auth/logout               │
     │  Authorization: Bearer <token>       │
     │─────────────────────────────────────►│
     │◄─────────────────────────────────────│
     │  200 OK (token blacklisté)           │
└────┴────────────────────────────────────── ┘
```

### 2.2 Endpoints Auth

#### `POST /auth/register` — Inscription
Public · Aucune authentification requise

**Corps :**
```json
{
  "email":     "client@example.com",
  "password":  "MonMotDePasse123!",
  "nom":       "Traoré",
  "prenom":    "Issiaka",
  "telephone": "+212612345678",
  "role":      "CLIENT",
  "consentRgpd": true
}
```

**Réponse 201 :**
```json
{
  "id": 42,
  "email": "client@example.com",
  "nom": "Traoré",
  "prenom": "Issiaka",
  "role": "CLIENT",
  "isActive": true,
  "createdAt": "2026-03-28T14:30:00"
}
```

---

#### `POST /auth/login` — Connexion
Public

**Corps :**
```json
{ "email": "client@example.com", "password": "MonMotDePasse123!" }
```

**Réponse 200 :**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "user": {
    "id": 42,
    "email": "client@example.com",
    "nom": "Traoré",
    "prenom": "Issiaka",
    "telephone": "+212612345678",
    "role": "CLIENT",
    "avatar": "https://...",
    "localisation": { "latitude": 33.57, "longitude": -7.58, "ville": "Casablanca" },
    "isActive": true,
    "livreurDisponible": false,
    "createdAt": "2026-03-01T10:00:00"
  }
}
```

> **Important** : stocker le `token` et l'`id` utilisateur dès la connexion. Le token est requis dans le header `Authorization: Bearer <token>` pour tous les appels suivants.

---

#### `POST /auth/google` — Connexion Google
Public

**Corps :**
```json
{ "idToken": "google_id_token_string..." }
```

**Réponse 200 :** même format que `/auth/login`

---

#### `POST /api/auth/refresh` — Rafraîchir le token
Public

**Corps :**
```json
{ "refreshToken": "refresh_token_string..." }
```

**Réponse 200 :**
```json
{ "accessToken": "nouveau_jwt...", "refreshToken": "nouveau_refresh..." }
```

---

#### `POST /api/auth/logout` — Déconnexion
`isAuthenticated()`

**Corps (optionnel) :**
```json
{ "refreshToken": "refresh_token_string..." }
```

---

#### `POST /api/auth/forgot-password` — Mot de passe oublié
Public

```json
{ "email": "client@example.com" }
```

---

#### `POST /api/auth/reset-password` — Réinitialiser le mot de passe
Public

```json
{ "token": "reset_token", "newPassword": "NouveauMdp123!" }
```

---

#### `POST /api/auth/change-password` — Changer le mot de passe
`isAuthenticated()`

```json
{ "oldPassword": "AncienMdp", "newPassword": "NouveauMdp123!" }
```

---

#### `POST /api/auth/send-verification` — Renvoyer l'email de vérification
`isAuthenticated()`

---

#### `POST /api/auth/verify-email` — Vérifier l'email
Public

```json
{ "token": "verification_token_from_email" }
```

---

### 2.3 Profil utilisateur

#### `GET /users/profile` — Mon profil
`isAuthenticated()`

**Réponse 200 :** `UserDTO` (voir section 2.2)

---

#### `PUT /users/profile` — Modifier mon profil
`isAuthenticated()` · `multipart/form-data`

| Champ form | Type | Description |
|---|---|---|
| `nom` | String | Nom de famille |
| `prenom` | String | Prénom |
| `telephone` | String | Numéro de téléphone |
| `avatar` | File | Photo de profil (optionnel) |

---

#### `PATCH /users/location` — Mettre à jour ma position GPS
`isAuthenticated()`

```json
{ "latitude": 33.5731, "longitude": -7.5898 }
```

---

#### `PATCH /api/users/livreur/disponibilite` — Définir ma disponibilité
`LIVREUR` uniquement

```json
{ "disponible": true }
```

> Activer `true` pour signaler sa disponibilité à recevoir des commandes.
> Le système d'auto-assignation utilise ce flag pour choisir le meilleur livreur.

---

### 2.4 Adresses de livraison

#### `GET /api/users/adresses`
`isAuthenticated()` — Liste mes adresses sauvegardées

#### `POST /api/users/adresses`
`isAuthenticated()`

```json
{
  "libelle": "Domicile",
  "adresse": "123 Rue des Fleurs",
  "complement": "Appt 4B",
  "ville": "Casablanca",
  "codePostal": "20000",
  "latitude": 33.5731,
  "longitude": -7.5898,
  "isDefault": true
}
```

#### `PUT /api/users/adresses/{adresseId}` — Modifier
#### `DELETE /api/users/adresses/{adresseId}` — Supprimer
#### `PATCH /api/users/adresses/{adresseId}/default` — Définir comme adresse par défaut

---

### 2.5 Suppression de compte (RGPD)

#### `DELETE /api/users/compte`
`isAuthenticated()` — Suppression irréversible du compte

---

## 3. Catalogue (Restaurants, Plats, Catégories, Menus)

### 3.1 Restaurants

#### `GET /api/restaurants` — Liste des restaurants
Public · Paramètres optionnels :

| Paramètre | Type | Description |
|---|---|---|
| `categorieId` | Long | Filtrer par catégorie |
| `latitude` | Double | Latitude client (pour tri par distance) |
| `longitude` | Double | Longitude client |
| `maxDistance` | Double | Distance max en km |
| `page` | Int | Numéro de page (0-based) |
| `size` | Int | Éléments par page (défaut 20) |

**Réponse 200 :** Page de `RestaurantDTO`
```json
{
  "content": [{
    "id": 1,
    "nom": "Pizza Palace",
    "description": "Les meilleures pizzas de Casablanca",
    "logoUrl": "https://...",
    "appreciation": 4.5,
    "nombreAvis": 128,
    "tempsLivraisonMoyen": 35,
    "localisation": { "latitude": 33.5880, "longitude": -7.6114, "adresse": "…", "ville": "Casablanca" },
    "categorie": { "id": 2, "nom": "Pizza" },
    "promotion": { "pourcentage": 20, "description": "-20% ce weekend" },
    "isActive": true,
    "openNow": true,
    "heureOuverture": "11:00",
    "heureFermeture": "23:00",
    "distance": 2.3
  }],
  "totalElements": 47
}
```

---

#### `GET /api/restaurants/{id}` — Restaurant par ID · Public
#### `GET /api/restaurants/search?keyword=pizza` — Recherche · Public
#### `GET /api/restaurants/top-rated?limit=10` — Top restaurants · Public
#### `GET /api/restaurants/nearby?latitude=33.57&longitude=-7.58&radiusKm=5` — Restaurants proches · Public

---

#### `POST /api/restaurants` — Créer un restaurant
`RESTAURANT_OWNER` ou `ADMIN` · `multipart/form-data`

| Champ | Type | Obligatoire |
|---|---|---|
| `nom` | String | Oui |
| `description` | String | Non |
| `categorieId` | Long | Oui |
| `adresse` | String | Oui |
| `ville` | String | Oui |
| `latitude` | Double | Oui |
| `longitude` | Double | Oui |
| `heureOuverture` | String (HH:mm) | Oui |
| `heureFermeture` | String (HH:mm) | Oui |
| `logo` | File | Non |

#### `PUT /api/restaurants/{id}` — Modifier · `RESTAURANT_OWNER` ou `ADMIN`
#### `DELETE /api/restaurants/{id}` — Supprimer · `RESTAURANT_OWNER` ou `ADMIN`
#### `PATCH /api/restaurants/{id}/activate` — Activer/désactiver · `RESTAURANT_OWNER` ou `ADMIN`
#### `GET /api/restaurants/{id}/plats` — Plats d'un restaurant · Public

---

### 3.2 Plats

#### `GET /api/plats` — Liste des plats · Public

| Paramètre | Type | Description |
|---|---|---|
| `restaurantId` | Long | Filtrer par restaurant |
| `categorie` | String | Filtrer par catégorie plat |
| `available` | Boolean | Uniquement disponibles |
| `topVente` | Boolean | `true` = uniquement les plats sélectionnés pour la rubrique « Top des ventes » |

#### `GET /api/plats/{id}` · `GET /api/plats/restaurant/{restaurantId}` · `GET /api/plats/search?keyword=…` — Public

---

#### `POST /api/plats` — Créer un plat
`RESTAURANT_OWNER` ou `ADMIN` · `multipart/form-data`

| Champ | Type | Description |
|---|---|---|
| `nom` | String | Nom du plat |
| `description` | String | Description |
| `prix` | BigDecimal | Prix en MAD |
| `restaurantId` | Long | Restaurant propriétaire |
| `categoriePlat` | String | Catégorie (ENTREE, PLAT_PRINCIPAL, DESSERT, BOISSON…) |
| `tempsPreparation` | Int | En minutes |
| `ingredients` | String[] | Liste des ingrédients |
| `availabilityMode` | String | ALWAYS / SCHEDULED / MANUAL |
| `topVente` | Boolean | Marque le plat pour la rubrique « Top des ventes » de l'appli client (`GET /api/plats?topVente=true`). Rien d'automatique : seul un plat marqué ici y apparaît. |
| `image` | File | Photo du plat |

#### `PUT /api/plats/{id}` — Modifier · `RESTAURANT_OWNER` ou `ADMIN`
#### `PATCH /api/plats/{id}/image` — Changer uniquement l'image
#### `PATCH /api/plats/{id}/availability` — Changer la disponibilité
#### `DELETE /api/plats/{id}` — Supprimer

---

### 3.3 Catégories

| Méthode | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/categories` | Public | Toutes les catégories |
| `GET` | `/api/categories/{id}` | Public | Par ID |
| `GET` | `/api/categories/{id}/restaurants` | Public | Restaurants de cette catégorie |
| `POST` | `/api/categories` | `ADMIN` | Créer (multipart) |
| `PUT` | `/api/categories/{id}` | `ADMIN` | Modifier (multipart) |
| `DELETE` | `/api/categories/{id}` | `ADMIN` | Supprimer |

---

### 3.4 Menus

Un menu regroupe plusieurs plats (offre du jour, menu enfant, etc.).

| Méthode | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/menus/{id}` | Public | Menu par ID |
| `GET` | `/api/menus/restaurant/{restaurantId}` | Public | Menus d'un restaurant |
| `POST` | `/api/menus` | `RESTAURANT_OWNER`/`ADMIN` | Créer |
| `PATCH` | `/api/menus/{id}/activer` | `RESTAURANT_OWNER`/`ADMIN` | Activer/désactiver |
| `DELETE` | `/api/menus/{id}` | `RESTAURANT_OWNER`/`ADMIN` | Supprimer |

---

### 3.5 Zones de livraison

Zones géographiques dans lesquelles un restaurant accepte de livrer.

| Méthode | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/zones-livraison/restaurant/{restaurantId}` | Public | Zones d'un restaurant |
| `POST` | `/api/zones-livraison` | `RESTAURANT_OWNER`/`ADMIN` | Créer une zone |
| `PUT` | `/api/zones-livraison/{id}` | `RESTAURANT_OWNER`/`ADMIN` | Modifier |
| `DELETE` | `/api/zones-livraison/{id}` | `RESTAURANT_OWNER`/`ADMIN` | Supprimer |

---

### 3.6 Employés de restaurant

| Méthode | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/restaurants/{restaurantId}/employes` | `RESTAURANT_OWNER`/`ADMIN` | Liste |
| `POST` | `/api/restaurants/{restaurantId}/employes` | `RESTAURANT_OWNER`/`ADMIN` | Ajouter |
| `DELETE` | `/api/restaurants/{restaurantId}/employes/{employeId}` | `RESTAURANT_OWNER`/`ADMIN` | Retirer |

---

## 4. Commandes & Panier

### 4.1 Cycle de vie d'une commande

```
              CLIENT                    RESTAURANT               LIVREUR
                │                           │                       │
   ┌────────────▼──────────────┐            │                       │
   │  Ajouter items au panier  │            │                       │
   │  POST /api/panier/items   │            │                       │
   └────────────┬──────────────┘            │                       │
                │                           │                       │
   ┌────────────▼──────────────┐            │                       │
   │   Créer la commande       │            │                       │
   │   POST /api/commandes     │            │                       │
   └────────────┬──────────────┘            │                       │
                │                           │                       │
           EN_ATTENTE ─────────────────────►│ (notification push)   │
                │                           │                       │
                │           ┌───────────────▼──────────────┐        │
                │           │  Restaurant confirme          │        │
                │           │  PATCH /commandes/{id}/status │        │
                │           │  { statut: "CONFIRMEE" }      │        │
                │           └───────────────┬──────────────┘        │
                │                           │                       │
           CONFIRMEE ◄── push client        │         ┌─────────────▼───────────┐
                │                           │         │  Auto-assignation        │
                │                           │         │  du meilleur livreur     │
                │                           │         │  (proximité + note)      │
                │                           │         └─────────────┬───────────┘
                │                           │                       │
                │           EN_PREPARATION ◄┘                       │ push livreur
                │                ▼                                   │
                │     Restaurant prépare...                          │
                │                ▼                                   │
                │           PRETE                                    │
                │                ▼                                   │
                │     ┌──────────────────────────────────────────────▼────────┐
                │     │       Livreur prend la commande                       │
                │     │  PATCH /commandes/{id}/assign-livreur/{livreurId}     │
                │     │  ou PATCH /commandes/{id}/status { statut: "EN_COURS"}│
                │     └──────────────────────────────────────────────┬────────┘
                │                                                     │
           EN_COURS ◄── push client (livraison en cours)             │
                │                │ tracking GPS temps réel            │
                │         WebSocket /ws (topic /topic/tracking/{id})  │
                │                                                     │
                │     ┌───────────────────────────────────────────────▼──────┐
                │     │  Livraison effectuée                                  │
                │     │  PATCH /commandes/{id}/status { statut: "LIVREE" }   │
                │     └───────────────────────────────────────────────┬──────┘
                │                                                      │
           LIVREE ◄── push client (livraison terminée)                │
                │                                                      │
   ┌────────────▼──────────────┐              Livreur redevient disponible
   │  Client laisse un avis    │
   │  POST /api/avis           │
   └───────────────────────────┘

   ANNULEE : possible depuis EN_ATTENTE, CONFIRMEE, EN_PREPARATION, PRETE
             → livreur libéré automatiquement si déjà assigné
```

### 4.2 Statuts de commande

| Statut | Description | Transitions possibles |
|---|---|---|
| `EN_ATTENTE` | Commande créée, attend confirmation | → CONFIRMEE, ANNULEE |
| `CONFIRMEE` | Restaurant a confirmé | → EN_PREPARATION, ANNULEE |
| `EN_PREPARATION` | Restaurant prépare | → PRETE, ANNULEE |
| `PRETE` | Prête à être récupérée | → EN_COURS, ANNULEE |
| `EN_COURS` | En cours de livraison | → LIVREE |
| `LIVREE` | Livrée au client | — (terminal) |
| `ANNULEE` | Annulée | — (terminal) |

### 4.3 Panier

> Le panier est limité à un seul restaurant à la fois. Ajouter un plat d'un autre restaurant vide le panier.

#### `GET /api/panier` — Mon panier
`CLIENT`

**Réponse 200 :**
```json
{
  "id": 5,
  "restaurantId": 1,
  "restaurantNom": "Pizza Palace",
  "items": [
    { "id": 10, "platId": 3, "platNom": "Margherita", "quantite": 2, "prixUnitaire": 65.00, "total": 130.00 }
  ],
  "montantTotal": 130.00,
  "nombreItems": 2,
  "updatedAt": "2026-03-28T14:00:00"
}
```

---

#### `POST /api/panier/items` — Ajouter un item
`CLIENT`

```json
{ "platId": 3, "quantite": 2 }
```

---

#### `PATCH /api/panier/items/{itemId}?quantite=3` — Modifier la quantité
`CLIENT`

---

#### `DELETE /api/panier` — Vider le panier
`CLIENT`

---

### 4.4 Commandes

#### `POST /api/commandes` — Créer une commande
`CLIENT` ou `ADMIN`

```json
{
  "clientId": 42,
  "restaurantId": 1,
  "modeReception": "LIVRAISON",
  "methodePaiement": "CARTE_BANCAIRE",
  "commentaire": "Sonnette en panne, appeler SVP",
  "adresseLivraison": {
    "latitude": 33.5731,
    "longitude": -7.5898,
    "adresse": "123 Rue des Fleurs",
    "ville": "Casablanca"
  },
  "lignes": [
    { "platId": 3, "quantite": 2, "remarque": "Sans oignons" },
    { "platId": 7, "quantite": 1 }
  ]
}
```

| Champ `modeReception` | Description |
|---|---|
| `LIVRAISON` | Livraison à domicile (adresseLivraison obligatoire) |
| `RETRAIT_SUR_PLACE` | Le client vient chercher |

| Champ `methodePaiement` | Description |
|---|---|
| `CARTE_BANCAIRE` | Paiement par carte |
| `ESPECES` | Paiement en espèces à la livraison |
| `MOBILE_MONEY` | Paiement mobile |
| `PAYPAL` | PayPal |

**Réponse 201 :** `CommandeDTO` complet

---

#### `GET /api/commandes` — Toutes les commandes (admin)
`Authentifié` · Paramètres : `clientId`, `restaurantId`, `statut`, `page`, `size`

#### `GET /api/commandes/{id}` — Par ID
#### `GET /api/commandes/numero/{numeroCommande}` — Par numéro (ex: `CMD-20260328-001`)
#### `GET /api/commandes/client/{clientId}` — Commandes d'un client · `CLIENT`/`ADMIN`
#### `GET /api/commandes/restaurant/{restaurantId}` — Commandes d'un restaurant · `RESTAURANT_OWNER`/`ADMIN`
#### `GET /api/commandes/livreur/{livreurId}` — Commandes d'un livreur · `LIVREUR`/`ADMIN`
#### `GET /api/commandes/en-cours` — Commandes actives · `Authentifié`

---

#### `PATCH /api/commandes/{id}/status` — Changer le statut
`RESTAURANT_OWNER`, `LIVREUR` ou `ADMIN`

```json
{
  "statut": "EN_PREPARATION",
  "raisonAnnulation": null
}
```

> Lors du passage à `CONFIRMEE` : le système auto-assigne le meilleur livreur disponible dans un rayon de 15 km (score = 60% distance + 40% note moyenne).

---

#### `PATCH /api/commandes/{id}/assign-livreur/{livreurId}` — Assigner manuellement un livreur
`ADMIN` ou `RESTAURANT_OWNER`

> Si un livreur était déjà assigné, il est remis disponible automatiquement.

---

#### `DELETE /api/commandes/{id}` — Annuler une commande
`Authentifié`

---

#### `GET /api/commandes/{id}/tracking` — Suivi de commande
`Authentifié`

**Réponse 200 :**
```json
{
  "numeroCommande": "CMD-20260328-001",
  "statut": "EN_COURS",
  "trackingStatut": "En cours de livraison",
  "modeReception": "LIVRAISON",
  "tempsEstime": 25,
  "createdAt": "2026-03-28T14:00:00",
  "restaurant": { "id": 1, "nom": "Pizza Palace", "localisation": {…} },
  "client": { "id": 42, "nom": "Traoré", "telephone": "+212…" },
  "livreur": { "id": 7, "nom": "Hassan", "telephone": "+212…", "localisation": {…} },
  "destination": { "latitude": 33.57, "longitude": -7.58, "adresse": "…" }
}
```

---

### 4.5 Avis

#### `POST /api/avis` — Soumettre un avis
`CLIENT`

```json
{
  "commandeId": 15,
  "restaurantId": 1,
  "livreurId": 7,
  "noteRestaurant": 5,
  "noteLivreur": 4,
  "commentaire": "Excellent repas, livraison rapide !"
}
```

#### `GET /api/avis/restaurant/{restaurantId}` — Avis d'un restaurant · Public
#### `GET /api/avis/livreur/{livreurId}` — Avis d'un livreur · Public
#### `GET /api/avis/mes-avis` — Mes avis · `CLIENT`
#### `PATCH /api/avis/{id}/moderation` — Modérer un avis · `ADMIN`
#### `GET /api/avis/admin/en-attente` — Avis à modérer · `ADMIN`
#### `DELETE /api/avis/{id}` · `CLIENT`/`ADMIN`

---

## 5. Livraison & Tracking WebSocket

### 5.1 Disponibilité livreur

Avant de recevoir des commandes, le livreur doit se déclarer disponible :

```
PATCH /api/users/livreur/disponibilite
Authorization: Bearer <jwt_livreur>

{ "disponible": true }
```

#### `GET /livreurs/disponibles` — Livreurs disponibles proches
Public

Paramètres : `latitude`, `longitude`, `radiusKm` (défaut 10)

---

### 5.2 Tracking GPS en temps réel (WebSocket)

Le tracking de position utilise **STOMP over WebSocket**.

```
Endpoint WebSocket : ws://localhost:8083/ws
```

#### Connexion STOMP

```javascript
const client = new Client({
  brokerURL: 'ws://localhost:8083/ws',
  onConnect: () => {
    // Le client s'abonne au topic de sa commande
    client.subscribe(`/topic/tracking/${commandeId}`, (message) => {
      const position = JSON.parse(message.body);
      // { latitude, longitude, commandeId, timestamp }
      updateMapMarker(position);
    });
  }
});
client.activate();
```

#### Envoi de position (livreur)

```javascript
client.publish({
  destination: '/app/tracking.update',
  body: JSON.stringify({
    latitude:   33.5880,
    longitude:  -7.6114,
    commandeId: 15,
    timestamp:  new Date().toISOString()
  })
});
```

```
Flux tracking :

Livreur App                  Backend                    Client App
    │                           │                           │
    │  WS connect + publish     │                           │
    │  /app/tracking.update     │                           │
    │  { lat, lon, cmdId }      │                           │
    │──────────────────────────►│                           │
    │                           │── broadcast               │
    │                           │  /topic/tracking/{cmdId} ►│
    │                           │                           │── met à jour la carte
```

---

## 6. Finance

### 6.1 Wallet (Portefeuille électronique)

#### `GET /api/wallet` — Mon wallet · `CLIENT`/`ADMIN`

**Réponse 200 :**
```json
{
  "id": 3,
  "userId": 42,
  "userNom": "Traoré",
  "userPrenom": "Issiaka",
  "solde": 250.00,
  "createdAt": "2026-01-15T10:00:00"
}
```

---

#### `POST /api/wallet/recharger` · `CLIENT`/`ADMIN`
```json
{ "montant": 100.00, "description": "Rechargement carte" }
```

#### `POST /api/wallet/payer` · `CLIENT`/`ADMIN`
```json
{ "montant": 65.00, "commandeId": 15 }
```

#### `GET /api/wallet/transactions` — Historique · `CLIENT`/`ADMIN`
#### `GET /api/wallet/admin/{userId}` — Wallet d'un utilisateur · `ADMIN`

---

### 6.2 Fidélité (Points)

#### `GET /api/fidelite` — Mes points · `CLIENT`/`ADMIN`
#### `GET /api/fidelite/historique` — Historique des points · `CLIENT`/`ADMIN`
#### `GET /api/fidelite/admin/{userId}` — Points d'un utilisateur · `ADMIN`

---

### 6.3 Gains Livreur

#### `GET /api/livreurs/gains` — Mes gains (paginé) · `LIVREUR`
#### `GET /api/livreurs/gains/summary` — Résumé de mes gains · `LIVREUR`

**Réponse summary :**
```json
{
  "totalGains": 1250.00,
  "nombreLivraisons": 87,
  "moyenneParLivraison": 14.37,
  "gainsMoisEnCours": 320.00
}
```

#### `GET /api/livreurs/{livreurId}/gains` — Gains d'un livreur · `ADMIN`
#### `GET /api/livreurs/{livreurId}/gains/summary` · `ADMIN`

---

### 6.4 Caisse Livreur

La caisse gère la collecte des paiements espèces par les livreurs.

#### Endpoints LIVREUR

| Méthode | Path | Description |
|---|---|---|
| `GET` | `/api/caisse/ma-position` | Solde et position de ma caisse |
| `GET` | `/api/caisse/mon-historique` | Mes transactions (paginé) |
| `GET` | `/api/caisse/info-commande/{commandeId}` | Info paiement d'une commande |
| `POST` | `/api/caisse/paiement-restaurant/{commandeId}` | Confirmer paiement au restaurant |

#### Endpoints ADMIN

| Méthode | Path | Description |
|---|---|---|
| `GET` | `/api/caisse/bord-admin` | Tableau de bord de toutes les caisses |
| `GET` | `/api/caisse/{livreurId}/position` | Position d'un livreur |
| `GET` | `/api/caisse/{livreurId}/historique` | Historique d'un livreur |
| `POST` | `/api/caisse/avance/{livreurId}` | Accorder une avance |
| `POST` | `/api/caisse/reconcilier` | Réconciliation globale |
| `GET`/`PUT` | `/api/caisse/parametres` | Paramètres caisse |
| `PUT` | `/api/caisse/{livreurId}/plafond?plafond=500` | Définir plafond |

---

### 6.5 Facturation Restaurant

Gestion du reversement aux restaurants partenaires.

#### Endpoints `RESTAURANT_OWNER`/`ADMIN`

| Méthode | Path | Description |
|---|---|---|
| `GET` | `/api/facturation-restaurant/{restaurantId}/parametres` | Paramètres de paiement |
| `PUT` | `/api/facturation-restaurant/{restaurantId}/parametres` | Modifier les paramètres |

#### Endpoints `ADMIN` uniquement

| Méthode | Path | Description |
|---|---|---|
| `GET` | `/api/facturation-restaurant/{restaurantId}/dettes` | Dettes en cours |
| `GET` | `/api/facturation-restaurant/{restaurantId}/dettes/total` | Montant total dû |
| `POST` | `/api/facturation-restaurant/{restaurantId}/payer` | Créer un paiement |
| `GET` | `/api/facturation-restaurant/{restaurantId}/paiements` | Historique paiements |
| `GET` | `/api/facturation-restaurant/paiements/{id}/dettes` | Dettes d'un paiement |

---

## 7. Marketing

### 7.1 Promotions Restaurant

Réductions automatiques appliquées aux commandes d'un restaurant.

> **Validité** : une promo est « active » (feed `GET /api/promotions`, badge
> restaurant, remise en commande) UNIQUEMENT si elle est `isActive=true`, que la date
> courante est dans `[dateDebut, dateFin]` ET que son plafond `usageMax` n'est pas
> atteint — règle unique portée par `Promotion.isActiveNow()`. Une promo expirée
> reste stockée (`isActive=true`) mais n'apparaît plus nulle part.

| Méthode | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/promotions` | Public | Promotions actives (dans leur fenêtre de dates) |
| `GET` | `/api/promotions/flash` | Public | Promotions flash |
| `GET` | `/api/promotions/{id}` | Public | Par ID |
| `GET` | `/api/promotions/restaurant/{restaurantId}` | Public | D'un restaurant |
| `POST` | `/api/promotions` | `ADMIN` | Créer (valide `pourcentage>0` et `dateFin > dateDebut`) |
| `PATCH` | `/api/promotions/{id}/activer` | `ADMIN` | Activer/désactiver |
| `DELETE` | `/api/promotions/{id}` | `ADMIN` | Supprimer |

**Corps création :**
```json
{
  "restaurantId": 1,
  "pourcentage": 20,
  "description": "Weekend spécial -20%",
  "dateDebut": "2026-03-29T00:00:00",
  "dateFin":   "2026-03-31T23:59:59",
  "estFlash":  false
}
```

---

### 7.2 Codes Promo

Codes à saisir par le client pour obtenir une réduction.

| Méthode | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/codes-promo` | `ADMIN` | Créer un code |
| `GET` | `/api/codes-promo` | `ADMIN` | Tous les codes |
| `GET` | `/api/codes-promo/{id}` | `ADMIN` | Par ID |
| `PATCH` | `/api/codes-promo/{id}/activer?actif=true` | `ADMIN` | Activer/désactiver |
| `DELETE` | `/api/codes-promo/{id}` | `ADMIN` | Supprimer |
| `POST` | `/api/codes-promo/valider` | `CLIENT`/`ADMIN` | Valider un code |

**Valider un code (côté client) :**
```json
{
  "code":        "WEEKEND20",
  "montantTotal": 150.00,
  "restaurantId": 1
}
```

**Réponse :**
```json
{
  "valide": true,
  "reduction": 30.00,
  "montantFinal": 120.00,
  "message": "Code valide — réduction de 20%"
}
```

---

### 7.3 Favoris

| Méthode | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/favoris` | `CLIENT` | Mes restaurants favoris |
| `POST` | `/api/favoris/{restaurantId}` | `CLIENT` | Ajouter |
| `DELETE` | `/api/favoris/{restaurantId}` | `CLIENT` | Retirer |
| `POST` | `/api/favoris/{restaurantId}/toggle` | `CLIENT` | Basculer (add/remove) |
| `GET` | `/api/favoris/{restaurantId}/status` | `CLIENT` | Est-ce un favori ? |
| `GET` | `/api/favoris/{restaurantId}/count` | Public | Nombre de favoris |

---

## 8. Notifications & Push

### 8.1 Architecture FCM

```
Backend Spring Boot
        │
        │  Firebase Admin SDK
        │  (firebase-service-account.json)
        ▼
Firebase Cloud Messaging
        │
   ┌────┼────────────┐
   ▼    ▼            ▼
Android  iOS        Web
```

> Le `firebase-service-account.json` côté serveur est universel : il permet d'envoyer
> vers Android, iOS et Web sans modification.

### 8.2 Tokens FCM

#### `POST /api/device-tokens/register` — Enregistrer un token
`Authentifié` · À appeler à **chaque démarrage de l'application** et au login

```json
{
  "userId":   42,
  "token":    "fcm_token_string...",
  "platform": "ANDROID"
}
```

Valeurs `platform` : `ANDROID` · `IOS` · `WEB`

#### `DELETE /api/device-tokens/{token}` — Désactiver · À appeler au logout
#### `DELETE /api/device-tokens/user/{userId}` — Désactiver tous les tokens d'un utilisateur

---

### 8.3 Notifications in-app

| Méthode | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/notifications?page=0&size=20` | `Authentifié` | Mes notifications paginées |
| `GET` | `/api/notifications/non-lues` | `Authentifié` | Non lues uniquement |
| `GET` | `/api/notifications/count` | `Authentifié` | `{ "nonLues": 5 }` |
| `PATCH` | `/api/notifications/{id}/lire` | `Authentifié` | Marquer comme lue |
| `POST` | `/api/notifications/lire-toutes` | `Authentifié` | Tout marquer comme lu |

**Structure `NotificationDTO` :**
```json
{
  "id": 101,
  "destinataireId": 42,
  "titre": "Commande confirmée",
  "message": "Votre commande CMD-001 a été confirmée.",
  "type": "COMMANDE_CONFIRMEE",
  "lue": false,
  "lueAt": null,
  "entityId": 15,
  "entityType": "COMMANDE",
  "createdAt": "2026-03-28T14:30:00"
}
```

---

### 8.4 Déclencheurs automatiques de notifications push

| Événement | Destinataires | Type |
|---|---|---|
| Commande confirmée | Client | `COMMANDE_CONFIRMEE` |
| Commande confirmée | Restaurant (owner) | `COMMANDE_CONFIRMEE` |
| Auto-assignation livreur | Livreur assigné | `LIVREUR_ASSIGNE` |
| Auto-assignation livreur | Admins | `COMMANDE_CONFIRMEE` |
| Aucun livreur disponible | Tous livreurs dispo | `COMMANDE_CONFIRMEE` |
| Assignation manuelle | Livreur | `LIVREUR_ASSIGNE` |
| Assignation manuelle | Client | `COMMANDE_EN_COURS` |
| Assignation manuelle | Restaurant | `LIVREUR_ASSIGNE` |

---

### 8.5 Campagnes de notification (Admin)

#### `POST /api/admin/notifications/campagne`
`ADMIN` uniquement

```json
{
  "titre":     "🎉 Offre spéciale !",
  "message":   "Profitez de -20% ce weekend. Code : WEEKEND20",
  "type":      "PROMOTION",
  "cibleRole": "CLIENT",
  "entityId":  7,
  "entityType": "PROMOTION"
}
```

| Champ `type` | Description |
|---|---|
| `PROMOTION` | Campagne marketing / offre commerciale |
| `SYSTEME` | Message d'information système |

| Champ `cibleRole` | Description |
|---|---|
| `CLIENT` | Tous les clients actifs (défaut) |
| `LIVREUR` | Tous les livreurs actifs |
| `RESTAURANT_OWNER` | Tous les propriétaires actifs |
| `ADMIN` | Tous les admins |
| `ALL` | Tous les utilisateurs actifs |

**Réponse 200 :**
```json
{
  "destinatairesCount": 1250,
  "notificationsCreees": 1250,
  "pushEnvoyees": 1250,
  "envoyeeAt": "2026-03-28T14:30:00"
}
```

---

### 8.6 Types de notifications — action recommandée côté app

| `type` | Action UI recommandée |
|---|---|
| `COMMANDE_CONFIRMEE` | Naviguer vers le détail de la commande (`entityId` = commandeId) |
| `COMMANDE_EN_PREPARATION` | Naviguer vers le détail |
| `COMMANDE_PRETE` | Naviguer vers le détail |
| `COMMANDE_EN_COURS` | Ouvrir le tracking GPS |
| `COMMANDE_LIVREE` | Proposer de laisser un avis |
| `COMMANDE_ANNULEE` | Naviguer vers le détail + raison |
| `LIVREUR_ASSIGNE` | Ouvrir le détail de la livraison (livreur) |
| `PROMOTION` | Naviguer vers la page promotions (`entityId` = promotionId si fourni) |
| `SYSTEME` | Ouvrir le centre de notifications |
| `AVIS_MODERE` | Naviguer vers l'avis concerné |

---

## 9. Administration

### 9.1 Gestion des utilisateurs

#### `GET /api/admin/users` — Liste paginée des utilisateurs
`ADMIN`

Paramètres : `role`, `search`, `page`, `size`

#### `GET /api/admin/users/{id}` — Détail d'un utilisateur · `ADMIN`
#### `PATCH /api/admin/users/{id}/toggle` — Activer/désactiver un compte · `ADMIN`

---

### 9.2 Dashboard Restaurant

#### `GET /api/restaurant-dashboard/mon-restaurant`
`RESTAURANT_OWNER`

**Réponse :**
```json
{
  "commandesAujourdhui": 12,
  "commandesEnAttente": 3,
  "chiffreAffairesAujourdhui": 780.00,
  "noteMovenne": 4.3,
  "commandesParStatut": { "EN_ATTENTE": 3, "EN_PREPARATION": 2, "LIVREE": 7 }
}
```

---

### 9.3 Statistiques Admin

> Tous les endpoints sont sous `/api/admin/statistiques` et requièrent le rôle `ADMIN`.

#### Dashboard global

```
GET /api/admin/statistiques/dashboard
```

**Réponse :**
```json
{
  "commandesTotalAujourdhui": 47,
  "commandesTotalSemaine": 312,
  "commandesTotalMois": 1248,
  "chiffreAffairesAujourdhui": 4350.00,
  "chiffreAffairesSemaine": 28700.00,
  "chiffreAffairesMois": 112500.00,
  "tauxAnnulation": 3.2,
  "valeurMoyenneCommande": 90.15,
  "nouveauxUsersAujourdhui": 5,
  "nouveauxUsersSemaine": 34,
  "nouveauxUsersMois": 128,
  "commandesEnCours": 8,
  "restaurantsActifs": 23,
  "livreursActifs": 41,
  "livreursDisponibles": 12,
  "livreursEnLivraison": 8
}
```

---

#### Évolution commandes

| Endpoint | Paramètres | Description |
|---|---|---|
| `GET /commandes/evolution` | `debut`, `fin`, `periode` (DAY/MONTH) | Courbe d'évolution |
| `GET /commandes/par-statut` | `debut`, `fin` | Répartition par statut |
| `GET /commandes/par-mode` | `debut`, `fin` | Livraison vs Retrait |
| `GET /commandes/par-paiement` | `debut`, `fin` | Répartition par paiement |
| `GET /commandes/heures-pointe` | `debut`, `fin` | Heures de pointe |

---

#### Restaurants

| Endpoint | Paramètres | Description |
|---|---|---|
| `GET /restaurants/top` | `limit`, `debut`, `fin`, `tri` | Top restaurants par CA ou commandes |
| `GET /restaurants/performance` | `debut`, `fin` | Performance globale |

---

#### Clients

| Endpoint | Paramètres | Description |
|---|---|---|
| `GET /clients/top` | `limit`, `debut`, `fin` | Top clients (montant ou fréquence) |
| `GET /clients/retention` | — | Taux de rétention |
| `GET /clients/par-ville` | `debut`, `fin` | Répartition géographique |

---

#### Livreurs

| Endpoint | Paramètres | Description |
|---|---|---|
| `GET /livreurs` | `debut`, `fin` | Performance de tous les livreurs |
| `GET /livreurs/top` | `limit`, `debut`, `fin` | Top livreurs |

---

#### Plats

| Endpoint | Paramètres | Description |
|---|---|---|
| `GET /plats/top` | `limit`, `debut`, `fin` | Plats les plus commandés |
| `GET /plats/jamais-commandes` | — | Plats jamais commandés (catalogue mort) |
| `GET /plats/par-categorie` | `debut`, `fin` | Ventes par catégorie |

---

#### Finance & Monitoring

| Endpoint | Description |
|---|---|
| `GET /financier?debut=…&fin=…` | Rapport financier complet |
| `GET /monitoring` | Métriques temps réel |
| `GET /alertes` | Alertes actives |

---

## 10. Fichiers

Gestion des médias (logos restaurants, photos plats, avatars).

#### `POST /api/files/upload` — Uploader un fichier
`Authentifié` · `multipart/form-data`

| Champ | Description |
|---|---|
| `file` | Le fichier à uploader |
| `folder` | Dossier cible (ex: `restaurants`, `plats`, `avatars`) |

**Réponse :**
```json
{ "objectName": "restaurants/logo-abc123.jpg", "url": "https://…" }
```

---

#### `GET /api/files/{*objectName}` — Récupérer un fichier · Public

```
GET /api/files/restaurants/logo-abc123.jpg
GET /api/files/restaurants/logo-abc123.jpg?download=true  ← force le téléchargement
```

#### `GET /api/files/url/{*objectName}` — Obtenir l'URL directe · Public
#### `GET /api/files/metadata/{*objectName}` — Métadonnées · Public

---

## 11. Référence rapide par rôle

### CLIENT

```
Auth       POST /auth/register · /auth/login · /api/auth/logout
Profil     GET/PUT /users/profile · PATCH /users/location
Adresses   GET/POST/PUT/DELETE /api/users/adresses/**
Catalogue  GET /api/restaurants/** · /api/plats/** · /api/categories/**
Panier     GET/POST/PATCH/DELETE /api/panier/**
Commandes  POST /api/commandes · GET /api/commandes/client/{id}
           GET /api/commandes/{id}/tracking
Avis       POST /api/avis · GET /api/avis/mes-avis
Wallet     GET/POST /api/wallet/**
Fidélité   GET /api/fidelite · /api/fidelite/historique
Codes      POST /api/codes-promo/valider
Favoris    GET/POST/DELETE /api/favoris/**
Notifs     GET/PATCH /api/notifications/**
FCM        POST /api/device-tokens/register
           DELETE /api/device-tokens/{token}
```

### LIVREUR

```
Auth        POST /auth/login · /api/auth/logout
Profil      GET/PUT /users/profile · PATCH /users/location
Dispo       PATCH /api/users/livreur/disponibilite
Commandes   GET /api/commandes/livreur/{id}
            PATCH /api/commandes/{id}/status
Tracking    WebSocket /ws → publish /app/tracking.update
Gains       GET /api/livreurs/gains · /gains/summary
Caisse      GET /api/caisse/ma-position · /mon-historique
            GET /api/caisse/info-commande/{id}
            POST /api/caisse/paiement-restaurant/{id}
Notifs      GET/PATCH /api/notifications/**
FCM         POST /api/device-tokens/register
```

### RESTAURANT_OWNER

```
Auth          POST /auth/login
Profil        GET/PUT /users/profile
Restaurant    POST /api/restaurants · GET/PUT/DELETE /api/restaurants/{id}
Plats         POST/PUT/PATCH/DELETE /api/plats/**
Menus         POST/PATCH/DELETE /api/menus/**
Zones         POST/PUT/DELETE /api/zones-livraison/**
Employés      GET/POST/DELETE /api/restaurants/{id}/employes/**
Commandes     GET /api/commandes/restaurant/{id}
              PATCH /api/commandes/{id}/status
Dashboard     GET /api/restaurant-dashboard/mon-restaurant
Facturation   GET/PUT /api/facturation-restaurant/{id}/parametres
Notifs        GET/PATCH /api/notifications/**
FCM           POST /api/device-tokens/register
```

### ADMIN

```
Tout ce que les autres rôles peuvent faire, plus :

Users         GET/PATCH /api/admin/users/**
Stats         GET /api/admin/statistiques/**
Campagnes     POST /api/admin/notifications/campagne
Codes promo   GET/POST/PATCH/DELETE /api/codes-promo/**
Promotions    POST/PATCH/DELETE /api/promotions/**
Catégories    POST/PUT/DELETE /api/categories/**
Caisse admin  GET /api/caisse/bord-admin
              GET/PUT /api/caisse/{livreurId}/**
              POST /api/caisse/avance/** · /reconcilier · /parametres
Facturation   POST /api/facturation-restaurant/{id}/payer
              GET /api/facturation-restaurant/{id}/dettes/**
Avis          PATCH /api/avis/{id}/moderation · GET /api/avis/admin/en-attente
```

---

## 12. Codes d'erreur

| Code HTTP | Signification | Cas courants |
|---|---|---|
| `200` | Succès | — |
| `201` | Créé | Inscription, création commande/restaurant/plat |
| `400` | Requête invalide | Champ manquant, validation échouée, transition de statut invalide |
| `401` | Non authentifié | Token JWT absent ou expiré |
| `403` | Accès interdit | Rôle insuffisant pour l'endpoint |
| `404` | Ressource introuvable | ID inexistant |
| `409` | Conflit | Email déjà utilisé, token FCM déjà enregistré |
| `500` | Erreur serveur | Erreur inattendue côté backend |

**Format d'erreur standard :**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Le plat Pizza Margherita n'est pas disponible",
  "timestamp": "2026-03-28T14:30:00"
}
```

---

*Documentation générée depuis le code source de MySuguClientApp — Mars 2026*
