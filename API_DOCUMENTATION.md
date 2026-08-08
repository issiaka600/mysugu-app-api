# MySugu — Documentation API

> **Base URL :** `http://localhost:8083`
> **Authentification :** `Authorization: Bearer <JWT>`
> **Format :** `application/json` sauf indication contraire
> **Pagination Spring Boot 4.x :** `{ content: [...], page: { size, number, totalElements, totalPages } }`

---

## Légende des rôles

| Rôle | Description |
|---|---|
| `ADMIN` | Administrateur plateforme |
| `CLIENT` | Client final |
| `LIVREUR` | Livreur |
| `RESTAURANT_OWNER` | Propriétaire de restaurant |
| `AUTH` | Tout utilisateur authentifié |
| *(public)* | Aucune authentification requise |

---

## Table des matières

1. [Authentification](#1-authentification)
2. [Utilisateurs & Profil](#2-utilisateurs--profil)
3. [Adresses de livraison](#3-adresses-de-livraison)
4. [Administration des utilisateurs](#4-administration-des-utilisateurs)
5. [Restaurants](#5-restaurants)
6. [Catégories de restaurants](#6-catégories-de-restaurants)
7. [Plats](#7-plats)
8. [Menus](#8-menus)
9. [Panier](#9-panier)
10. [Commandes](#10-commandes)
11. [Zones de déploiement](#11-zones-de-déploiement)
12. [Promotions](#12-promotions)
13. [Codes promo](#13-codes-promo)
14. [Avis](#14-avis)
15. [Favoris](#15-favoris)
16. [Wallet](#16-wallet)
17. [Fidélité](#17-fidélité)
18. [Notifications](#18-notifications)
19. [Device Tokens (FCM)](#19-device-tokens-fcm)
20. [Caisse livreur](#20-caisse-livreur)
21. [Gains livreur](#21-gains-livreur)
22. [Facturation restaurant](#22-facturation-restaurant)
23. [Dashboard restaurant](#23-dashboard-restaurant)
24. [Employés restaurant](#24-employés-restaurant)
25. [Statistiques admin](#25-statistiques-admin)
26. [Fichiers (MinIO)](#26-fichiers-minio)
27. [Tracking WebSocket](#27-tracking-websocket)
28. [Campagnes de notification](#28-campagnes-de-notification)

---

## 1. Authentification

**Base :** `/auth`

| Méthode | Endpoint | Rôle | Description |
|---|---|---|---|
| `POST` | `/auth/register` | *(public)* | Créer un compte |
| `POST` | `/auth/login` | *(public)* | Connexion email/mot de passe |
| `POST` | `/auth/google` | *(public)* | Connexion via Google OAuth |
| `POST` | `/api/auth/verify-email` | *(public)* | Vérifier l'adresse e-mail |
| `POST` | `/api/auth/forgot-password` | *(public)* | Demander une réinitialisation de mot de passe |
| `POST` | `/api/auth/reset-password` | *(public)* | Réinitialiser le mot de passe avec le token reçu |
| `POST` | `/api/auth/refresh` | *(public)* | Rafraîchir le JWT via un refresh token |
| `POST` | `/api/auth/send-verification` | `AUTH` | Renvoyer l'e-mail de vérification |
| `POST` | `/api/auth/logout` | `AUTH` | Invalider le refresh token |
| `POST` | `/api/auth/change-password` | `AUTH` | Changer son mot de passe |

### POST /auth/register
```json
{
  "email": "user@example.com",
  "password": "motdepasse",
  "nom": "Traoré",
  "prenom": "Issiaka",
  "telephone": "+212600000000",
  "role": "CLIENT"
}
```
**Réponse 201 :** `UserDTO`

Un e-mail de vérification est envoyé automatiquement. Le compte ne peut pas se connecter
avec le mot de passe tant que l'e-mail n'a pas été vérifié.

### POST /auth/login
```json
{ "email": "user@example.com", "password": "motdepasse" }
```
**Réponse 200 :** `{ "token": "...", "refreshToken": "...", "user": UserDTO }`

Un compte avec `emailVerified: false` reçoit `403 Forbidden` et doit vérifier son e-mail.

### PUT /users/profile — changement d'e-mail

`Content-Type: multipart/form-data`, JWT requis. Si le champ `email` change, le backend met
`emailVerified` à `false` et envoie automatiquement un lien de vérification à la nouvelle
adresse. Le mobile ne doit pas appeler manuellement `/api/auth/send-verification` dans ce cas.

### POST /api/auth/refresh
```json
{ "refreshToken": "..." }
```
**Réponse 200 :** `{ "token": "...", "refreshToken": "..." }`

### POST /api/auth/forgot-password
```json
{ "email": "user@example.com" }
```

### POST /api/auth/reset-password
```json
{ "token": "...", "newPassword": "nouveauMotDePasse" }
```

### POST /api/auth/change-password
```json
{ "ancienMotDePasse": "...", "nouveauMotDePasse": "..." }
```

---

## 2. Utilisateurs & Profil

**Base :** `/users`

| Méthode | Endpoint | Rôle | Description |
|---|---|---|---|
| `GET` | `/users/profile` | `AUTH` | Récupérer son profil |
| `PUT` | `/users/profile` | `AUTH` | Mettre à jour son profil (multipart/form-data) |
| `PATCH` | `/users/location` | `AUTH` | Mettre à jour sa position GPS |
| `PATCH` | `/api/users/livreur/disponibilite` | `LIVREUR` | Mettre à jour sa disponibilité |
| `DELETE` | `/api/users/compte` | `AUTH` | Supprimer son compte |
| `GET` | `/livreurs/disponibles` | *(public)* | Lister les livreurs disponibles à proximité |

### GET /users/profile
**Réponse :** `UserDTO`

### PUT /users/profile
`Content-Type: multipart/form-data`

| Champ | Type | Description |
|---|---|---|
| `nom` | string | Nom |
| `prenom` | string | Prénom |
| `telephone` | string | Téléphone |
| `avatar` | file | Photo de profil |

### PATCH /users/location
```json
{ "latitude": 14.6928, "longitude": -17.4467 }
```

### PATCH /api/users/livreur/disponibilite
```json
{ "disponible": true }
```

### GET /livreurs/disponibles
**Params :** `latitude`, `longitude`, `radiusKm`

---

## 3. Adresses de livraison

**Base :** `/api/users/adresses` — `AUTH`

| Méthode | Endpoint | Description |
|---|---|---|
| `GET` | `/api/users/adresses` | Lister mes adresses |
| `POST` | `/api/users/adresses` | Ajouter une adresse |
| `PUT` | `/api/users/adresses/{adresseId}` | Modifier une adresse |
| `DELETE` | `/api/users/adresses/{adresseId}` | Supprimer une adresse |
| `PATCH` | `/api/users/adresses/{adresseId}/default` | Définir comme adresse par défaut |

### POST /api/users/adresses
```json
{
  "adresse": "Rue des Acacias",
  "ville": "Marrakech",
  "codePostal": "40000",
  "pays": "Maroc",
  "latitude": 31.6295,
  "longitude": -7.9811,
  "estParDefaut": true
}
```
**Réponse 201 :** `AdresseLivraisonDTO`

---

## 4. Administration des utilisateurs

**Base :** `/api/admin/users` — `ADMIN`

| Méthode | Endpoint | Description |
|---|---|---|
| `GET` | `/api/admin/users` | Lister les utilisateurs paginés |
| `GET` | `/api/admin/users/{id}` | Récupérer un utilisateur par ID |
| `PATCH` | `/api/admin/users/{id}/toggle` | Activer / désactiver un utilisateur |

### GET /api/admin/users
**Params :** `role` (CLIENT/LIVREUR/RESTAURANT_OWNER/ADMIN), `page`, `size`, `search`
**Réponse :** `Page<UserDTO>`

---

## 5. Restaurants

**Base :** `/api/restaurants`

| Méthode | Endpoint | Rôle | Description |
|---|---|---|---|
| `GET` | `/api/restaurants` | *(public)* | Lister les restaurants paginés |
| `GET` | `/api/restaurants/{id}` | *(public)* | Récupérer un restaurant par ID |
| `GET` | `/api/restaurants/search` | *(public)* | Rechercher par mot-clé |
| `GET` | `/api/restaurants/top-rated` | *(public)* | Top restaurants par note |
| `GET` | `/api/restaurants/nearby` | *(public)* | Restaurants à proximité |
| `GET` | `/api/restaurants/{id}/plats` | *(public)* | Plats d'un restaurant |
| `POST` | `/api/restaurants` | `RESTAURANT_OWNER`, `ADMIN` | Créer un restaurant |
| `PUT` | `/api/restaurants/{id}` | `RESTAURANT_OWNER`, `ADMIN` | Modifier un restaurant |
| `DELETE` | `/api/restaurants/{id}` | `RESTAURANT_OWNER`, `ADMIN` | Supprimer un restaurant |
| `PATCH` | `/api/restaurants/{id}/activate` | `RESTAURANT_OWNER`, `ADMIN` | Activer / désactiver |
| `PATCH` | `/api/restaurants/{id}/commission` | `ADMIN` | Définir le taux de commission négocié |

### GET /api/restaurants
**Params :** `categorieId`, `latitude`, `longitude`, `maxDistance`, `page`, `size`
**Réponse :** `Page<RestaurantDTO>`

### GET /api/restaurants/nearby
**Params :** `latitude`, `longitude`, `radiusKm`
**Réponse :** `List<RestaurantDTO>`

### POST /api/restaurants
`Content-Type: multipart/form-data`

| Champ | Type | Description |
|---|---|---|
| `nom` | string | Nom du restaurant (requis) |
| `description` | string | Description |
| `categorieId` | number | ID catégorie |
| `zoneDeploiementId` | number | ID zone de déploiement |
| `tempsLivraisonMoyen` | number | Temps moyen en minutes |
| `heureOuverture` | string | Format `HH:mm` |
| `heureFermeture` | string | Format `HH:mm` |
| `localisation.adresse` | string | Adresse |
| `localisation.ville` | string | Ville |
| `logo` | file | Logo du restaurant |
| `removeLogo` | boolean | Supprimer le logo existant |

**Réponse 201 :** `RestaurantDTO`

### PATCH /api/restaurants/{id}/commission
**Param :** `pourcentage` (0–100, ex: `15.5`)
**Réponse :** `RestaurantDTO`

> Ce taux s'applique aux plats dont le prix dépasse le seuil global (`seuilPrixCommission`).
> En dessous du seuil, c'est la `commissionMinPourcentage` globale (paramètres caisse) qui s'applique.

---

## 6. Catégories de restaurants

**Base :** `/api/categories`

| Méthode | Endpoint | Rôle | Description |
|---|---|---|---|
| `GET` | `/api/categories` | *(public)* | Lister toutes les catégories |
| `GET` | `/api/categories/{id}` | *(public)* | Récupérer une catégorie |
| `GET` | `/api/categories/{id}/restaurants` | *(public)* | Restaurants d'une catégorie |
| `POST` | `/api/categories` | `ADMIN` | Créer une catégorie |
| `PUT` | `/api/categories/{id}` | `ADMIN` | Modifier une catégorie |
| `DELETE` | `/api/categories/{id}` | `ADMIN` | Supprimer une catégorie |

### POST /api/categories
`Content-Type: multipart/form-data` — champs : `nom`, `description`, `image`
**Réponse 201 :** `CategorieRestaurantDTO`

---

## 7. Plats

**Base :** `/api/plats`

| Méthode | Endpoint | Rôle | Description |
|---|---|---|---|
| `GET` | `/api/plats` | *(public)* | Lister les plats paginés |
| `GET` | `/api/plats/{id}` | *(public)* | Récupérer un plat |
| `GET` | `/api/plats/restaurant/{restaurantId}` | *(public)* | Plats d'un restaurant |
| `GET` | `/api/plats/search` | *(public)* | Rechercher par mot-clé |
| `POST` | `/api/plats` | `RESTAURANT_OWNER`, `ADMIN` | Créer un plat |
| `PUT` | `/api/plats/{id}` | `RESTAURANT_OWNER`, `ADMIN` | Modifier un plat |
| `PATCH` | `/api/plats/{id}/image` | `RESTAURANT_OWNER`, `ADMIN` | Changer l'image |
| `PATCH` | `/api/plats/{id}/availability` | `RESTAURANT_OWNER`, `ADMIN` | Changer la disponibilité |
| `DELETE` | `/api/plats/{id}` | `RESTAURANT_OWNER`, `ADMIN` | Supprimer un plat |

### GET /api/plats
**Params :** `restaurantId`, `categorie`, `available` (boolean), `page`, `size`
**Réponse :** `Page<PlatDTO>`

### POST /api/plats
`Content-Type: multipart/form-data`

| Champ | Type | Description |
|---|---|---|
| `nom` | string | Nom du plat (requis) |
| `description` | string | Description |
| `prix` | number | Prix (requis) |
| `restaurantId` | number | ID restaurant (requis) |
| `categoriePlat` | string | ENTREE / PLAT_PRINCIPAL / DESSERT / BOISSON |
| `tempsPreparation` | number | En minutes |
| `ingredients` | string[] | Liste d'ingrédients |
| `image` | file | Image du plat |

**Réponse 201 :** `PlatDTO`

### PATCH /api/plats/{id}/availability
```json
{ "available": false, "indisponibleJusqua": "2026-04-01T10:00:00" }
```

---

## 8. Menus

**Base :** `/api/menus`

| Méthode | Endpoint | Rôle | Description |
|---|---|---|---|
| `GET` | `/api/menus/{id}` | *(public)* | Récupérer un menu |
| `GET` | `/api/menus/restaurant/{restaurantId}` | *(public)* | Menus d'un restaurant |
| `POST` | `/api/menus` | `RESTAURANT_OWNER`, `ADMIN` | Créer un menu |
| `PATCH` | `/api/menus/{id}/activer` | `RESTAURANT_OWNER`, `ADMIN` | Activer / désactiver |
| `DELETE` | `/api/menus/{id}` | `RESTAURANT_OWNER`, `ADMIN` | Supprimer |

### POST /api/menus
```json
{
  "restaurantId": 1,
  "nom": "Menu du jour",
  "description": "Plat + boisson",
  "platsIds": [1, 2, 3],
  "prix": 45.00,
  "actif": true
}
```
**Réponse 201 :** `MenuDTO`

---

## 9. Panier

**Base :** `/api/panier` — `CLIENT`

| Méthode | Endpoint | Description |
|---|---|---|
| `GET` | `/api/panier` | Consulter mon panier |
| `POST` | `/api/panier/items` | Ajouter un article |
| `PATCH` | `/api/panier/items/{itemId}` | Modifier la quantité d'un article |
| `DELETE` | `/api/panier` | Vider le panier |

### POST /api/panier/items
```json
{ "platId": 1, "quantite": 2, "remarque": "Sans piment" }
```
**Réponse 201 :** `PanierDTO`

### PATCH /api/panier/items/{itemId}
**Param :** `quantite` (integer)
**Réponse :** `PanierDTO`

---

## 10. Commandes

### GET /api/v3/seller/orders/list

Liste des commandes du restaurant Vendor authentifié.

- `status` accepte `pending`, `confirmed`, `processing`, `ready`, `out_for_delivery`, `delivered`, `canceled`, `returned`, `failed` et `all`.
- Seul `sort=createdAt` est accepté.
- `order=asc|desc` est optionnel. Sans `order`, `pending`, `confirmed`, `processing` et `ready` sont triés ancien → récent ; les autres vues récent → ancien.

**Base :** `/api/commandes`

| Méthode | Endpoint | Rôle | Description |
|---|---|---|---|
| `GET` | `/api/commandes` | `AUTH` | Lister les commandes paginées |
| `GET` | `/api/commandes/{id}` | `AUTH` | Récupérer une commande |
| `GET` | `/api/commandes/numero/{numeroCommande}` | `AUTH` | Récupérer par numéro |
| `GET` | `/api/commandes/client/{clientId}` | `CLIENT`, `ADMIN` | Commandes d'un client |
| `GET` | `/api/commandes/restaurant/{restaurantId}` | `RESTAURANT_OWNER`, `ADMIN` | Commandes d'un restaurant |
| `GET` | `/api/commandes/livreur/{livreurId}` | `LIVREUR`, `ADMIN` | Commandes d'un livreur |
| `GET` | `/api/commandes/en-cours` | `AUTH` | Commandes en cours |
| `GET` | `/api/commandes/{id}/tracking` | `AUTH` | Suivi en temps réel |
| `POST` | `/api/commandes` | `CLIENT`, `ADMIN` | Créer une commande |
| `PATCH` | `/api/commandes/{id}/status` | `RESTAURANT_OWNER`, `LIVREUR`, `ADMIN` | Changer le statut |
| `PATCH` | `/api/commandes/{id}/assign-livreur/{livreurId}` | `RESTAURANT_OWNER`, `ADMIN` | Assigner un livreur |
| `DELETE` | `/api/commandes/{id}` | `AUTH` | Annuler une commande |

### GET /api/commandes
**Params :** `clientId`, `restaurantId`, `statut`, `page`, `size`
**Réponse :** `Page<CommandeDTO>`

### POST /api/commandes
```json
{
  "clientId": 4,
  "restaurantId": 1,
  "lignes": [
    { "platId": 1, "quantite": 2, "remarque": "Sans piment" }
  ],
  "adresseLivraison": {
    "latitude": 14.6928,
    "longitude": -17.4467,
    "adresse": "Djour Marjane",
    "ville": "Marrakech"
  },
  "commentaire": "Livraison urgente",
  "methodePaiement": "ESPECES",
  "modeReception": "LIVRAISON",
  "codePromo": "PROMO10"
}
```
**Réponse 201 :** `CommandeDTO`

> **Commission :** à la création, le système applique automatiquement :
> - Si `prixUnitaire ≤ seuilPrixCommission` → `commissionMinPourcentage` globale (ex: 20%)
> - Si `prixUnitaire > seuilPrixCommission` → taux négocié du restaurant (`commissionPourcentage`)
>
> Les champs `commissionPourcentage` et `montantCommission` sont renseignés sur chaque `LigneCommande`.
> Le total est disponible dans `CommandeDTO.montantCommissionTotal`.

### PATCH /api/commandes/{id}/status
```json
{ "statut": "EN_PREPARATION" }
```
**Statuts possibles :** `EN_ATTENTE` → `CONFIRMEE` → `EN_PREPARATION` → `PRETE` → `EN_COURS` → `LIVREE` / `ANNULEE`

### CommandeDTO — Structure complète
```json
{
  "id": 1,
  "numeroCommande": "CMD-20260221062843-7941",
  "statut": "LIVREE",
  "trackingStatut": "COMMANDE_LIVREE",
  "client": { "id": 4, "nom": "TRAORE", "prenom": "Client", "email": "...", "telephone": "...", "role": "CLIENT", "avatar": null },
  "restaurant": { "id": 1, "nom": "Chez Fatou", "commissionPourcentage": 15.0, "..." : "..." },
  "livreur": { "id": 3, "nom": "TRAORE", "prenom": "Livreur", "telephone": "...", "..." : "..." },
  "lignesCommande": [
    {
      "id": 1, "quantite": 2,
      "prixUnitaire": 5000.00, "montantTotal": 10000.00,
      "commissionPourcentage": 15.0, "montantCommission": 1500.00,
      "remarque": "Sans piment",
      "plat": { "id": 1, "nom": "Thiéboudienne", "prix": 50.00, "..." : "..." }
    }
  ],
  "montantTotal": 10500.00,
  "montantRemise": 0,
  "montantFinal": 10500.00,
  "montantCommissionTotal": 1500.00,
  "fraisLivraison": 500.00,
  "codePromoUtilise": null,
  "methodePaiement": "ESPECES",
  "statutPaiement": "PAYE",
  "modeReception": "LIVRAISON",
  "adresseLivraison": { "adresse": "Djour Marjane", "ville": "Marrakech", "latitude": 14.6928, "longitude": -17.4467 },
  "tempsLivraisonEstime": 30,
  "commentaire": "Livraison urgente",
  "raisonAnnulation": null,
  "currency": "MAD", "currencySymbol": "DH",
  "createdAt": "2026-02-21T06:28:43",
  "updatedAt": "2026-03-22T23:03:52",
  "livreeAt": "2026-03-22T23:03:52",
  "scheduledAt": null
}
```

---

## 11. Zones de déploiement

**Base :** `/api/zones-deploiement`

| Méthode | Endpoint | Rôle | Description |
|---|---|---|---|
| `GET` | `/api/zones-deploiement/actives` | *(public)* | Lister les zones actives |
| `GET` | `/api/zones-deploiement` | `ADMIN` | Lister toutes les zones |
| `GET` | `/api/zones-deploiement/{id}` | `ADMIN` | Récupérer une zone |
| `POST` | `/api/zones-deploiement` | `ADMIN` | Créer une zone |
| `PUT` | `/api/zones-deploiement/{id}` | `ADMIN` | Modifier une zone |
| `PATCH` | `/api/zones-deploiement/{id}/activer` | `ADMIN` | Activer / désactiver |
| `DELETE` | `/api/zones-deploiement/{id}` | `ADMIN` | Supprimer |

### POST /api/zones-deploiement
```json
{
  "nom": "Marrakech Centre",
  "description": "Zone centre-ville",
  "centreLatitude": 31.6295,
  "centreLongitude": -7.9811,
  "rayonKm": 20,
  "fraisLivraisonMin": 10.00,
  "distanceMinKm": 3.0,
  "prixExtraParKm": 2.0,
  "isActive": true
}
```

> **Calcul des frais de livraison :**
> - Si `distance ≤ distanceMinKm` → `fraisLivraisonMin`
> - Sinon → `fraisLivraisonMin + (distance − distanceMinKm) × prixExtraParKm`
>
> *Exemple : fraisMin=10 DH, distMin=3 km, prixExtra=2 DH/km — commande à 7 km → 10 + (7−3)×2 = 18 DH*

---

## 12. Promotions

**Base :** `/api/promotions`

| Méthode | Endpoint | Rôle | Description |
|---|---|---|---|
| `GET` | `/api/promotions` | *(public)* | Promotions actives |
| `GET` | `/api/promotions/{id}` | *(public)* | Récupérer une promotion |
| `GET` | `/api/promotions/flash` | *(public)* | Promotions flash |
| `GET` | `/api/promotions/restaurant/{restaurantId}` | *(public)* | Promotions d'un restaurant |
| `POST` | `/api/promotions` | `ADMIN` | Créer une promotion |
| `PATCH` | `/api/promotions/{id}/activer` | `ADMIN` | Activer / désactiver |
| `DELETE` | `/api/promotions/{id}` | `ADMIN` | Supprimer |

### POST /api/promotions
```json
{
  "restaurantId": 1,
  "nom": "Happy Hour",
  "pourcentage": 20,
  "montantMinCommande": 50.00,
  "montantMaxReduction": 30.00,
  "dateDebut": "2026-04-01T00:00:00",
  "dateFin": "2026-04-30T23:59:59",
  "isFlash": false,
  "usageMax": 100
}
```
**Réponse 201 :** `PromotionDTO`

---

## 13. Codes promo

**Base :** `/api/codes-promo`

| Méthode | Endpoint | Rôle | Description |
|---|---|---|---|
| `GET` | `/api/codes-promo` | `ADMIN` | Lister tous les codes |
| `GET` | `/api/codes-promo/{id}` | `ADMIN` | Récupérer un code |
| `POST` | `/api/codes-promo` | `ADMIN` | Créer un code promo |
| `PATCH` | `/api/codes-promo/{id}/activer` | `ADMIN` | Activer / désactiver |
| `DELETE` | `/api/codes-promo/{id}` | `ADMIN` | Supprimer |
| `POST` | `/api/codes-promo/valider` | `CLIENT`, `ADMIN` | Valider et simuler l'application d'un code |

### POST /api/codes-promo
```json
{
  "code": "BIENVENUE10",
  "typeReduction": "POURCENTAGE",
  "valeur": 10,
  "montantMinCommande": 30.00,
  "montantMaxReduction": 20.00,
  "dateExpiration": "2026-12-31T23:59:59",
  "usageMax": 500,
  "actif": true
}
```

### POST /api/codes-promo/valider
```json
{ "code": "BIENVENUE10", "montantCommande": 75.00 }
```
**Réponse :** `{ "valide": true, "remise": 7.50, "message": "..." }`

---

## 14. Avis

**Base :** `/api/avis`

| Méthode | Endpoint | Rôle | Description |
|---|---|---|---|
| `GET` | `/api/avis/{id}` | *(public)* | Récupérer un avis |
| `GET` | `/api/avis/restaurant/{restaurantId}` | *(public)* | Avis d'un restaurant |
| `GET` | `/api/avis/livreur/{livreurId}` | *(public)* | Avis sur un livreur |
| `GET` | `/api/avis/mes-avis` | `AUTH` | Mes avis |
| `GET` | `/api/avis/admin/en-attente` | `ADMIN` | Avis en attente de modération |
| `POST` | `/api/avis` | `CLIENT` | Soumettre un avis |
| `PATCH` | `/api/avis/{id}/moderation` | `ADMIN` | Modérer un avis |
| `DELETE` | `/api/avis/{id}` | `AUTH` | Supprimer un avis |

### POST /api/avis
```json
{
  "commandeId": 1,
  "restaurantId": 1,
  "livreurId": 3,
  "noteRestaurant": 4,
  "noteLivreur": 5,
  "commentaire": "Excellent service !"
}
```

---

## 15. Favoris

**Base :** `/api/favoris` — `CLIENT`

| Méthode | Endpoint | Description |
|---|---|---|
| `GET` | `/api/favoris` | Mes restaurants favoris |
| `POST` | `/api/favoris/{restaurantId}` | Ajouter un favori |
| `DELETE` | `/api/favoris/{restaurantId}` | Retirer un favori |
| `POST` | `/api/favoris/{restaurantId}/toggle` | Toggle favori |
| `GET` | `/api/favoris/{restaurantId}/status` | Vérifier si en favori |
| `GET` | `/api/favoris/{restaurantId}/count` | Nombre de fois mis en favori |

---

## 16. Wallet

**Base :** `/api/wallet` — `CLIENT`, `ADMIN`

| Méthode | Endpoint | Description |
|---|---|---|
| `GET` | `/api/wallet` | Consulter son wallet |
| `POST` | `/api/wallet/recharger` | Recharger son wallet |
| `POST` | `/api/wallet/payer` | Payer une commande via wallet |
| `GET` | `/api/wallet/transactions` | Historique paginé |
| `GET` | `/api/wallet/admin/{userId}` | Wallet d'un utilisateur (ADMIN) |

### POST /api/wallet/recharger
```json
{ "montant": 100.00, "methodePaiement": "CARTE_BANCAIRE" }
```

### POST /api/wallet/payer
```json
{ "commandeId": 1, "montant": 75.00 }
```

---

## 17. Fidélité

**Base :** `/api/fidelite` — `CLIENT`, `ADMIN`

| Méthode | Endpoint | Rôle | Description |
|---|---|---|---|
| `GET` | `/api/fidelite` | `CLIENT`, `ADMIN` | Mes points de fidélité |
| `GET` | `/api/fidelite/historique` | `CLIENT`, `ADMIN` | Historique des transactions |
| `GET` | `/api/fidelite/admin/{userId}` | `ADMIN` | Points d'un utilisateur |

---

## 18. Notifications

**Base :** `/api/notifications` — `AUTH`

| Méthode | Endpoint | Description |
|---|---|---|
| `GET` | `/api/notifications` | Mes notifications paginées |
| `GET` | `/api/notifications/non-lues` | Notifications non lues |
| `GET` | `/api/notifications/count` | Nombre de non lues |
| `PATCH` | `/api/notifications/{id}/lire` | Marquer comme lue |
| `POST` | `/api/notifications/lire-toutes` | Marquer toutes comme lues |

### GET /api/notifications/count
**Réponse :** `{ "nonLues": 3, "total": 12 }`

### Push client — changement de statut de commande
À chaque transition de commande, le client reçoit une notification FCM avec les données :

```json
{
  "type": "order_status",
  "event": "order_status_changed",
  "order_id": "123",
  "status": "processing",
  "badge": "2",
  "screen": "order_tracking"
}
```

Les statuts exposés sont `pending`, `confirmed`, `processing`, `ready`, `assigned`,
`out_for_delivery`, `delivered` et `canceled`. Le titre et le message sont adaptés au
statut ; Android utilise `order_alert` et iOS `order_alert.wav`. Les canaux sont
`mysuku_customer_notifications_v1` (Customer), `mysuku_seller_orders_v1` (Vendor) et
`mysuku_delivery_orders_v2` (Delivery). Le clic doit ouvrir le
suivi de la commande à partir de `order_id` (`GET /api/commandes/{id}/tracking`).

---

## 19. Device Tokens (FCM)

**Base :** `/api/device-tokens` — `AUTH`

| Méthode | Endpoint | Description |
|---|---|---|
| `POST` | `/api/device-tokens/register` | Enregistrer un token FCM |
| `DELETE` | `/api/device-tokens/{token}` | Désactiver un token |
| `DELETE` | `/api/device-tokens/user/{userId}` | Désactiver tous les tokens d'un utilisateur |

### POST /api/device-tokens/register
```json
{ "userId": 4, "token": "fcm_token_here", "platform": "ANDROID" }
```

### POST /api/mobile/notification-acks
Enregistre un accusé de réception envoyé par l'application mobile. Authentification JWT
obligatoire ; l'utilisateur est toujours déterminé depuis le JWT, jamais depuis le corps.

Événements autorisés : `RECEIVED`, `DISPLAYED`, `OPENED`, `ACCEPTED`, `REJECTED`.
L'envoi Firebase (`tokensSent` / identifiants Firebase) reste une trace serveur et ne doit
pas être déclaré comme un accusé mobile.

```json
{
  "event": "OPENED",
  "notificationId": 987,
  "orderId": 123,
  "deliveryOfferId": 456,
  "firebaseMessageId": "projects/.../messages/0:...",
  "deviceToken": "fcm_token_here",
  "occurredAt": "2026-08-07T12:30:00"
}
```

`notificationId`, `orderId`, `deliveryOfferId`, `firebaseMessageId`, `deviceToken` et
`occurredAt` sont facultatifs selon le contexte ; `occurredAt` vaut l'heure serveur s'il
est absent. Réponse `202 Accepted` : `{ "id": 1, "event": "OPENED", "receivedAt": "..." }`.

---

## 20. Caisse livreur

**Base :** `/api/caisse`

| Méthode | Endpoint | Rôle | Description |
|---|---|---|---|
| `GET` | `/api/caisse/ma-position` | `LIVREUR` | Consulter sa caisse |
| `GET` | `/api/caisse/mon-historique` | `LIVREUR` | Historique de ses transactions |
| `GET` | `/api/caisse/info-commande/{commandeId}` | `LIVREUR` | Infos paiement d'une commande |
| `POST` | `/api/caisse/paiement-restaurant/{commandeId}` | `LIVREUR` | Confirmer le paiement au restaurant |
| `GET` | `/api/caisse/bord-admin` | `ADMIN` | Tableau de bord caisse admin |
| `GET` | `/api/caisse/{livreurId}/position` | `ADMIN` | Caisse d'un livreur |
| `GET` | `/api/caisse/{livreurId}/historique` | `ADMIN` | Historique d'un livreur |
| `POST` | `/api/caisse/avance/{livreurId}` | `ADMIN` | Accorder une avance |
| `POST` | `/api/caisse/reconcilier` | `ADMIN` | Réconcilier la caisse |
| `GET` | `/api/caisse/parametres` | `ADMIN` | Récupérer les paramètres globaux |
| `PUT` | `/api/caisse/parametres` | `ADMIN` | Mettre à jour les paramètres globaux |
| `PUT` | `/api/caisse/{livreurId}/plafond` | `ADMIN` | Définir un plafond personnalisé |

### GET /api/caisse/parametres — Réponse
```json
{
  "plafondCaisseLivreur": 500.00,
  "seuilAlertePourcentage": 80,
  "intervalleReconciliationHeures": 48,
  "tauxCommissionPlateforme": 0.1500,
  "periodicitePaiementRestaurantJours": 7,
  "seuilPrixCommission": 10.00,
  "commissionMinPourcentage": 20.00
}
```

> **`seuilPrixCommission` :** prix en DH en-dessous duquel la commission minimum globale s'applique.
> **`commissionMinPourcentage` :** taux (%) appliqué aux plats dont le prix ≤ seuil.
> Au-delà du seuil, c'est le taux négocié avec le restaurant qui s'applique.

### PUT /api/caisse/parametres
```json
{
  "plafondCaisseLivreur": 600.00,
  "seuilAlertePourcentage": 85,
  "seuilPrixCommission": 15.00,
  "commissionMinPourcentage": 25.00
}
```
*(Seuls les champs fournis sont mis à jour)*

### POST /api/caisse/avance/{livreurId}
```json
{ "montant": 200.00, "note": "Avance exceptionnelle" }
```

### POST /api/caisse/reconcilier
```json
{ "livreurId": 3, "montantRemis": 350.00, "note": "Réconciliation hebdomadaire" }
```

### PUT /api/caisse/{livreurId}/plafond
**Param :** `plafond` (number)

---

## 21. Gains livreur

**Base :** `/api/livreurs`

| Méthode | Endpoint | Rôle | Description |
|---|---|---|---|
| `GET` | `/api/livreurs/gains` | `LIVREUR` | Mes gains paginés |
| `GET` | `/api/livreurs/gains/summary` | `LIVREUR` | Résumé de mes gains |
| `GET` | `/api/livreurs/{livreurId}/gains` | `ADMIN` | Gains d'un livreur |
| `GET` | `/api/livreurs/{livreurId}/gains/summary` | `ADMIN` | Résumé gains d'un livreur |

---

## 22. Facturation restaurant

**Base :** `/api/facturation-restaurant` — `RESTAURANT_OWNER`, `ADMIN`

| Méthode | Endpoint | Rôle | Description |
|---|---|---|---|
| `GET` | `/api/facturation-restaurant/{restaurantId}/parametres` | `RESTAURANT_OWNER`, `ADMIN` | Paramètres de paiement |
| `PUT` | `/api/facturation-restaurant/{restaurantId}/parametres` | `RESTAURANT_OWNER`, `ADMIN` | Mettre à jour les paramètres |
| `GET` | `/api/facturation-restaurant/{restaurantId}/dettes` | `ADMIN` | Dettes en attente |
| `GET` | `/api/facturation-restaurant/{restaurantId}/dettes/total` | `ADMIN` | Total des dettes |
| `POST` | `/api/facturation-restaurant/{restaurantId}/payer` | `ADMIN` | Enregistrer un paiement |
| `GET` | `/api/facturation-restaurant/{restaurantId}/paiements` | `RESTAURANT_OWNER`, `ADMIN` | Historique des paiements |
| `GET` | `/api/facturation-restaurant/paiements/{paiementId}/dettes` | `RESTAURANT_OWNER`, `ADMIN` | Dettes couvertes par un paiement |

### PUT /api/facturation-restaurant/{restaurantId}/parametres
```json
{
  "modePaiement": "PERIODIQUE",
  "periodicitéJours": 7,
  "modeVersement": "VIREMENT_BANCAIRE",
  "rib": "MA12345678901234567890",
  "nomBeneficiaire": "Chez Fatou SARL"
}
```

### POST /api/facturation-restaurant/{restaurantId}/payer
**Params :** `periodeDebut` (datetime), `periodeFin` (datetime), `note`
**Réponse 201 :** `PaiementRestaurantDTO`

---

## 23. Dashboard restaurant

**Base :** `/api/restaurant-dashboard`

| Méthode | Endpoint | Rôle | Description |
|---|---|---|---|
| `GET` | `/api/restaurant-dashboard/{restaurantId}` | `RESTAURANT_OWNER`, `ADMIN` | Dashboard d'un restaurant |
| `GET` | `/api/restaurant-dashboard/mon-restaurant` | `RESTAURANT_OWNER` | Mon dashboard |

**Réponse :** `RestaurantDashboardDTO` — statistiques du jour, de la semaine, commandes en cours, top plats, etc.

---

## 24. Employés restaurant

**Base :** `/api/restaurants/{restaurantId}/employes` — `RESTAURANT_OWNER`, `ADMIN`

| Méthode | Endpoint | Description |
|---|---|---|
| `GET` | `/api/restaurants/{restaurantId}/employes` | Lister les employés |
| `POST` | `/api/restaurants/{restaurantId}/employes` | Ajouter un employé |
| `DELETE` | `/api/restaurants/{restaurantId}/employes/{employeId}` | Retirer un employé |

### POST /api/restaurants/{restaurantId}/employes
```json
{ "userId": 10, "role": "CAISSIER" }
```

---

## 25. Statistiques admin

**Base :** `/api/admin/statistiques` — `ADMIN`

| Méthode | Endpoint | Description |
|---|---|---|
| `GET` | `/api/admin/statistiques/dashboard` | Tableau de bord global |
| `GET` | `/api/admin/statistiques/commandes/evolution` | Évolution des commandes |
| `GET` | `/api/admin/statistiques/commandes/par-statut` | Commandes par statut |
| `GET` | `/api/admin/statistiques/commandes/par-mode` | Commandes par mode de réception |
| `GET` | `/api/admin/statistiques/commandes/par-paiement` | Commandes par méthode de paiement |
| `GET` | `/api/admin/statistiques/commandes/heures-pointe` | Heures de pointe |
| `GET` | `/api/admin/statistiques/restaurants/top` | Top restaurants |
| `GET` | `/api/admin/statistiques/restaurants/performance` | Performance de tous les restaurants |
| `GET` | `/api/admin/statistiques/clients/top` | Top clients |
| `GET` | `/api/admin/statistiques/clients/retention` | Taux de rétention |
| `GET` | `/api/admin/statistiques/clients/par-ville` | Commandes par ville |
| `GET` | `/api/admin/statistiques/livreurs` | Performance des livreurs |
| `GET` | `/api/admin/statistiques/livreurs/top` | Top livreurs |
| `GET` | `/api/admin/statistiques/plats/top` | Top plats commandés |
| `GET` | `/api/admin/statistiques/plats/jamais-commandes` | Plats jamais commandés |
| `GET` | `/api/admin/statistiques/plats/par-categorie` | Plats par catégorie |
| `GET` | `/api/admin/statistiques/financier` | Rapport financier |
| `GET` | `/api/admin/statistiques/monitoring` | Monitoring temps réel |
| `GET` | `/api/admin/statistiques/alertes` | Alertes actives |

**Params communs :** `debut` (date), `fin` (date), `periode` (JOUR/SEMAINE/MOIS)

### GET /api/admin/statistiques/dashboard — Réponse
```json
{
  "commandesTotalAujourdhui": 42,
  "commandesTotalSemaine": 280,
  "commandesTotalMois": 1150,
  "chiffreAffairesAujourdhui": 4200.00,
  "chiffreAffairesSemaine": 28000.00,
  "chiffreAffairesMois": 115000.00,
  "tauxAnnulation": 3.2,
  "valeurMoyenneCommande": 100.00,
  "nouveauxUsersAujourdhui": 5,
  "commandesEnCours": 8,
  "restaurantsActifs": 12,
  "livreursActifs": 7
}
```

---

## 26. Fichiers (MinIO)

**Base :** `/api/files`

| Méthode | Endpoint | Rôle | Description |
|---|---|---|---|
| `POST` | `/api/files/upload` | `AUTH` | Uploader un fichier |
| `GET` | `/api/files` | *(public)* | Télécharger via query param |
| `GET` | `/api/files/{*objectName}` | *(public)* | Télécharger via path |
| `GET` | `/api/files/metadata/{*objectName}` | *(public)* | Métadonnées d'un fichier |
| `GET` | `/api/files/url/{*objectName}` | *(public)* | URL publique d'un fichier |

### POST /api/files/upload
`Content-Type: multipart/form-data`
**Champs :** `file` (requis), `folder` (optionnel, ex: `plats`, `logos`)
**Réponse 201 :** `{ "objectName": "plats/uuid.jpg", "url": "http://..." }`

### GET /api/files
**Params :** `objectName` (ex: `plats/uuid.jpg`), `download` (boolean)

---

## 27. Tracking WebSocket

**Protocole :** STOMP over WebSocket
**Endpoint :** `ws://localhost:8083/ws`

| Type | Destination | Description |
|---|---|---|
| Subscribe | `/topic/tracking/{commandeId}` | Recevoir les mises à jour GPS d'une commande |
| Send | `/app/tracking.update` | Envoyer sa position GPS (livreur) |

### Message /app/tracking.update
```json
{ "commandeId": 1, "latitude": 14.6928, "longitude": -17.4467 }
```

### Message reçu sur /topic/tracking/{commandeId}
```json
{ "commandeId": 1, "latitude": 14.6932, "longitude": -17.4471, "timestamp": "2026-03-29T10:15:00" }
```

---

## 28. Campagnes de notification

**Base :** `/api/admin/notifications` — `ADMIN`

| Méthode | Endpoint | Description |
|---|---|---|
| `POST` | `/api/admin/notifications/campagne` | Envoyer une campagne push |
| `GET` | `/api/admin/notifications/promotion/{promotionId}` | Utilisateurs notifiés pour une promo |
| `GET` | `/api/admin/notifications/promotions` | Toutes les promotions |

### POST /api/admin/notifications/campagne
```json
{
  "titre": "Offre spéciale !",
  "corps": "−20% ce week-end sur toutes les commandes.",
  "cible": "TOUS",
  "promotionId": 5
}
```
**Réponse :** `{ "envoyes": 1200, "echecs": 3 }`

---

## Codes d'erreur communs

| Code HTTP | Signification |
|---|---|
| `400 Bad Request` | Données invalides ou règle métier non respectée |
| `401 Unauthorized` | JWT absent ou expiré |
| `403 Forbidden` | Rôle insuffisant |
| `404 Not Found` | Ressource introuvable |
| `409 Conflict` | Contrainte d'unicité violée |
| `500 Internal Server Error` | Erreur serveur inattendue |

**Format d'erreur standard :**
```json
{
  "timestamp": "2026-03-29T10:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Le plat Thiéboudienne n'est pas disponible"
}
```

---

*Dernière mise à jour : 2026-03-29*
