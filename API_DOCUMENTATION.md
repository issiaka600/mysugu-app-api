# MySugu — Documentation API

> Base URL : `http://localhost:8080`
> Authentification : `Authorization: Bearer <JWT>`
> Format des réponses : `application/json` sauf indication contraire.

---

## Table des matières

1. [Authentification & Compte](#1-authentification--compte)
2. [Gestion des utilisateurs (Admin)](#2-gestion-des-utilisateurs-admin)
3. [Restaurants](#3-restaurants)
4. [Plats](#4-plats)
5. [Catégories de restaurant](#5-catégories-de-restaurant)
6. [Commandes](#6-commandes)
7. [Promotions](#7-promotions)
8. [Codes Promo](#8-codes-promo)
9. [Notifications](#9-notifications)
10. [Campagnes de notification (Admin)](#10-campagnes-de-notification-admin)
11. [FCM Device Tokens](#11-fcm-device-tokens)
12. [Wallet](#12-wallet)
13. [Fidélité](#13-fidélité)
14. [Caisse Livreur](#14-caisse-livreur)
15. [Gains Livreur](#15-gains-livreur)
16. [Facturation Restaurant](#16-facturation-restaurant)
17. [Zones de déploiement](#17-zones-de-déploiement)
18. [Statistiques Admin](#18-statistiques-admin)
19. [Avis](#19-avis)
20. [Favoris](#20-favoris)
21. [Panier](#21-panier)
22. [Menus](#22-menus)
23. [Dashboard Restaurant](#23-dashboard-restaurant)
24. [Employés Restaurant](#24-employés-restaurant)
25. [Fichiers (Minio)](#25-fichiers-minio)
26. [WebSocket Tracking GPS](#26-websocket-tracking-gps)

---

## 1. Authentification & Compte

### POST `/auth/register`
Inscription d'un nouvel utilisateur.

**Accès :** Public
**Body :**
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
Valeurs de `role` : `CLIENT`, `LIVREUR`, `RESTAURANT_OWNER`, `ADMIN`

**Réponse 201 :** `UserDTO`

---

### POST `/auth/login`
Connexion par email/mot de passe.

**Accès :** Public
**Body :**
```json
{ "email": "user@example.com", "password": "motdepasse" }
```
**Réponse 200 :** `LoginResponseDTO`
```json
{ "token": "<JWT>", "user": { ...UserDTO } }
```

---

### POST `/auth/google`
Connexion via Google OAuth.

**Accès :** Public
**Body :**
```json
{ "idToken": "<Google ID Token>" }
```
**Réponse 200 :** `LoginResponseDTO`

---

### GET `/users/profile`
Profil de l'utilisateur connecté.

**Accès :** Authentifié
**Réponse 200 :** `UserDTO`

---

### PUT `/users/profile`
Mise à jour du profil (multipart/form-data).

**Accès :** Authentifié
**Content-Type :** `multipart/form-data`
**Champs form :**
```
nom, prenom, telephone    (champs texte)
avatar                    (fichier image, optionnel)
```
**Réponse 200 :** `UserDTO`

---

### PATCH `/users/location`
Mise à jour de la position GPS de l'utilisateur.

**Accès :** Authentifié
**Body :**
```json
{ "latitude": 31.6295, "longitude": -7.9811 }
```
**Réponse 200 :** `UserDTO`

---

### PATCH `/api/users/livreur/disponibilite`
Déclare la disponibilité d'un livreur.

**Accès :** `LIVREUR`
**Body :**
```json
{ "disponible": true }
```
**Réponse 200 :** `UserDTO`

---

### GET `/livreurs/disponibles`
Liste des livreurs disponibles à proximité.

**Accès :** Authentifié
**Query params :**
| Param | Type | Défaut | Description |
|---|---|---|---|
| `latitude` | double | requis | Latitude du point de référence |
| `longitude` | double | requis | Longitude du point de référence |
| `radiusKm` | double | 10.0 | Rayon de recherche en km |

**Réponse 200 :** `UserDTO[]`

---

### POST `/api/auth/send-verification`
Envoie un email de vérification à l'utilisateur connecté.

**Accès :** Authentifié
**Réponse 200 :**
```json
{ "message": "Email de vérification envoyé" }
```

---

### POST `/api/auth/verify-email`
Vérifie l'email avec le token reçu par mail.

**Accès :** Public
**Body :**
```json
{ "token": "<verification-token>" }
```
**Réponse 200 :**
```json
{ "message": "Email vérifié avec succès" }
```

---

### POST `/api/auth/forgot-password`
Déclenche un email de réinitialisation de mot de passe.

**Accès :** Public
**Body :**
```json
{ "email": "user@example.com" }
```
**Réponse 200 :**
```json
{ "message": "Si votre email est enregistré, vous recevrez un lien de réinitialisation" }
```

---

### POST `/api/auth/reset-password`
Réinitialise le mot de passe via le token reçu par mail.

**Accès :** Public
**Body :**
```json
{ "token": "<reset-token>", "newPassword": "nouveauMotDePasse" }
```
**Réponse 200 :**
```json
{ "message": "Mot de passe réinitialisé avec succès" }
```

---

### POST `/api/auth/refresh`
Rafraîchit le JWT à partir d'un refresh token.

**Accès :** Public
**Body :**
```json
{ "refreshToken": "<refresh-token>" }
```
**Réponse 200 :** `RefreshTokenResponseDTO`
```json
{ "token": "<new-JWT>", "refreshToken": "<new-refresh-token>" }
```

---

### POST `/api/auth/logout`
Invalide le token actuel (et optionnellement le refresh token FCM).

**Accès :** Authentifié
**Body (optionnel) :**
```json
{ "refreshToken": "<refresh-token>", "fcmToken": "<fcm-device-token>" }
```
**Réponse 200 :**
```json
{ "message": "Déconnexion réussie" }
```

---

### POST `/api/auth/change-password`
Change le mot de passe (ancien → nouveau).

**Accès :** Authentifié
**Body :**
```json
{ "ancienMotDePasse": "...", "nouveauMotDePasse": "..." }
```
**Réponse 200 :**
```json
{ "message": "Mot de passe modifié avec succès" }
```

---

### GET `/api/users/adresses`
Liste des adresses de livraison sauvegardées.

**Accès :** Authentifié
**Réponse 200 :** `AdresseLivraisonDTO[]`

---

### POST `/api/users/adresses`
Ajoute une adresse de livraison.

**Accès :** Authentifié
**Body :**
```json
{
  "libelle": "Domicile",
  "adresse": "123 Rue de la Palmeraie",
  "ville": "Marrakech",
  "latitude": 31.6295,
  "longitude": -7.9811,
  "estParDefaut": false
}
```
**Réponse 201 :** `AdresseLivraisonDTO`

---

### PUT `/api/users/adresses/{adresseId}`
Modifie une adresse de livraison.

**Accès :** Authentifié
**Body :** même structure que POST
**Réponse 200 :** `AdresseLivraisonDTO`

---

### DELETE `/api/users/adresses/{adresseId}`
Supprime une adresse de livraison.

**Accès :** Authentifié
**Réponse 204**

---

### PATCH `/api/users/adresses/{adresseId}/default`
Définit une adresse comme adresse par défaut.

**Accès :** Authentifié
**Réponse 200 :** `AdresseLivraisonDTO`

---

### DELETE `/api/users/compte`
Supprime définitivement le compte (conformité RGPD).

**Accès :** Authentifié
**Réponse 200 :**
```json
{ "message": "Votre compte a été supprimé conformément au RGPD" }
```

---

## 2. Gestion des utilisateurs (Admin)

### GET `/api/admin/users`
Liste paginée d'utilisateurs filtrés par rôle.

**Accès :** `ADMIN`
**Query params :**
| Param | Type | Défaut | Description |
|---|---|---|---|
| `role` | string | `CLIENT` | Rôle : `CLIENT`, `LIVREUR`, `RESTAURANT_OWNER`, `ADMIN` |
| `page` | int | 0 | Numéro de page |
| `size` | int | 10 | Taille de page |
| `search` | string | — | Recherche par nom / email (optionnel) |

**Réponse 200 :** `Page<UserDTO>`

---

### GET `/api/admin/users/{id}`
Récupère un utilisateur par son ID.

**Accès :** `ADMIN`
**Réponse 200 :** `UserDTO`

---

### PATCH `/api/admin/users/{id}/toggle`
Active ou désactive un compte utilisateur.

**Accès :** `ADMIN`
**Réponse 200 :** `UserDTO`

---

## 3. Restaurants

### GET `/api/restaurants`
Liste paginée des restaurants, avec filtres optionnels.

**Accès :** Public
**Query params :**
| Param | Type | Description |
|---|---|---|
| `categorieId` | Long | Filtre par catégorie |
| `latitude` | double | Latitude du client (pour tri par distance) |
| `longitude` | double | Longitude du client |
| `maxDistance` | double | Distance max en km |
| `page`, `size`, `sort` | — | Pagination Spring |

**Réponse 200 :** `Page<RestaurantDTO>`

---

### GET `/api/restaurants/{id}`
Détail d'un restaurant.

**Accès :** Public
**Réponse 200 :** `RestaurantDTO`

---

### GET `/api/restaurants/search`
Recherche plein-texte sur le nom du restaurant.

**Accès :** Public
**Query params :**
| Param | Type | Description |
|---|---|---|
| `keyword` | string | Mot-clé de recherche |

**Réponse 200 :** `RestaurantDTO[]`

---

### GET `/api/restaurants/top-rated`
Restaurants les mieux notés.

**Accès :** Public
**Query params :**
| Param | Type | Défaut |
|---|---|---|
| `limit` | int | 10 |

**Réponse 200 :** `RestaurantDTO[]`

---

### GET `/api/restaurants/nearby`
Restaurants à proximité d'un point GPS.

**Accès :** Public
**Query params :**
| Param | Type | Défaut | Description |
|---|---|---|---|
| `latitude` | double | requis | |
| `longitude` | double | requis | |
| `radiusKm` | double | 5.0 | Rayon en km |

**Réponse 200 :** `RestaurantDTO[]`

---

### POST `/api/restaurants`
Crée un restaurant (multipart/form-data).

**Accès :** `RESTAURANT_OWNER`, `ADMIN`
**Content-Type :** `multipart/form-data`
**Champs form :**
```
nom*              string
description       string
categorieId*      Long
zoneDeploiementId Long    (optionnel — zone de livraison rattachée)
adresse           string
ville             string
latitude          double
longitude         double
heureOuverture    string  (ex: "09:00")
heureFermeture    string  (ex: "23:00")
autoCloseEnabled  boolean
logo              fichier image (optionnel)
```
**Réponse 201 :** `RestaurantDTO`

---

### PUT `/api/restaurants/{id}`
Met à jour un restaurant (multipart/form-data).

**Accès :** `RESTAURANT_OWNER`, `ADMIN`
**Content-Type :** `multipart/form-data`
**Champs form :** identiques à POST
**Réponse 200 :** `RestaurantDTO`

---

### DELETE `/api/restaurants/{id}`
Supprime un restaurant.

**Accès :** `RESTAURANT_OWNER`, `ADMIN`
**Réponse 204**

---

### PATCH `/api/restaurants/{id}/activate`
Bascule l'état actif/inactif d'un restaurant.

**Accès :** `RESTAURANT_OWNER`, `ADMIN`
**Réponse 200 :** `RestaurantDTO`

---

## 4. Plats

### GET `/api/plats`
Liste paginée de plats.

**Accès :** Public
**Query params :**
| Param | Type | Description |
|---|---|---|
| `restaurantId` | Long | Filtre par restaurant |
| `categorie` | string | `ENTREE`, `PLAT_PRINCIPAL`, `DESSERT`, `BOISSON` |
| `available` | boolean | Filtre sur disponibilité |
| `page`, `size` | — | Pagination Spring |

**Réponse 200 :** `Page<PlatDTO>`

---

### GET `/api/plats/{id}`
Détail d'un plat.

**Accès :** Public
**Réponse 200 :** `PlatDTO`

---

### GET `/api/plats/restaurant/{restaurantId}`
Tous les plats d'un restaurant.

**Accès :** Public
**Réponse 200 :** `PlatDTO[]`

---

### GET `/api/plats/search`
Recherche plein-texte sur le nom du plat.

**Accès :** Public
**Query params :**
| Param | Type |
|---|---|
| `keyword` | string |

**Réponse 200 :** `PlatDTO[]`

---

### POST `/api/plats`
Crée un plat (multipart/form-data).

**Accès :** `RESTAURANT_OWNER`, `ADMIN`
**Content-Type :** `multipart/form-data`
**Champs form :**
```
nom*              string
description       string
prix*             BigDecimal
restaurantId*     Long
categoriePlat     string   (ENTREE | PLAT_PRINCIPAL | DESSERT | BOISSON)
ingredients       string   (séparés par virgule)
tempsPreparation  int      (minutes)
isAvailable       boolean
image             fichier image (optionnel)
```
**Réponse 201 :** `PlatDTO`

---

### PUT `/api/plats/{id}`
Met à jour un plat (multipart/form-data).

**Accès :** `RESTAURANT_OWNER`, `ADMIN`
**Content-Type :** `multipart/form-data`
**Champs form :** identiques à POST
**Réponse 200 :** `PlatDTO`

---

### PATCH `/api/plats/{id}/image`
Met à jour uniquement l'image d'un plat.

**Accès :** `RESTAURANT_OWNER`, `ADMIN`
**Content-Type :** `multipart/form-data`
**Champs form :**
```
image*    fichier image
```
**Réponse 200 :** `PlatDTO`

---

### DELETE `/api/plats/{id}`
Supprime un plat.

**Accès :** `RESTAURANT_OWNER`, `ADMIN`
**Réponse 204**

---

### PATCH `/api/plats/{id}/availability`
Met à jour la disponibilité d'un plat.

**Accès :** `RESTAURANT_OWNER`, `ADMIN`
**Body :**
```json
{
  "available": true,
  "availabilityMode": "ALWAYS",
  "indisponibleJusqua": "2025-12-31T23:59:59"
}
```
`availabilityMode` : `ALWAYS` | `SCHEDULE` | `MANUAL`
**Réponse 200 :** `PlatDTO`

---

## 5. Catégories de restaurant

### GET `/api/categories`
Liste toutes les catégories.

**Accès :** Public
**Réponse 200 :** `CategorieRestaurantDTO[]`

---

### GET `/api/categories/{id}`
Détail d'une catégorie.

**Accès :** Public
**Réponse 200 :** `CategorieRestaurantDTO`

---

### GET `/api/categories/{id}/restaurants`
Restaurants d'une catégorie.

**Accès :** Public
**Réponse 200 :** `RestaurantDTO[]`

---

### POST `/api/categories`
Crée une catégorie.

**Accès :** `ADMIN`
**Content-Type :** `multipart/form-data`
**Champs form :**
```
nom*          string
description   string
image         fichier image (optionnel)
```
**Réponse 201 :** `CategorieRestaurantDTO`

---

### PUT `/api/categories/{id}`
Met à jour une catégorie.

**Accès :** `ADMIN`
**Content-Type :** `multipart/form-data`
**Champs form :** identiques à POST
**Réponse 200 :** `CategorieRestaurantDTO`

---

### DELETE `/api/categories/{id}`
Supprime une catégorie.

**Accès :** `ADMIN`
**Réponse 204**

---

## 6. Commandes

### Statuts possibles
`EN_ATTENTE` → `CONFIRMEE` → `EN_PREPARATION` → `PRETE` → `EN_COURS` → `LIVREE`
`ANNULEE`, `NON_FINALISEE`

### Modes de réception
`LIVRAISON`, `RETRAIT_SUR_PLACE`

### Méthodes de paiement
`ESPECES`, `CARTE`, `WALLET`, `MOBILE_MONEY`

---

### GET `/api/commandes`
Liste paginée des commandes.

**Accès :** `ADMIN` (tous), `CLIENT` / `RESTAURANT_OWNER` / `LIVREUR` (les leurs)
**Query params :**
| Param | Type | Description |
|---|---|---|
| `clientId` | Long | Filtre par client |
| `restaurantId` | Long | Filtre par restaurant |
| `statut` | string | Filtre par statut |
| `page`, `size` | — | Pagination |

**Réponse 200 :** `Page<CommandeDTO>`

---

### GET `/api/commandes/{id}`
Détail d'une commande.

**Accès :** Authentifié
**Réponse 200 :** `CommandeDTO`

---

### GET `/api/commandes/numero/{numeroCommande}`
Commande par numéro (ex : `CMD-20250401-0001`).

**Accès :** Authentifié
**Réponse 200 :** `CommandeDTO`

---

### GET `/api/commandes/client/{clientId}`
Toutes les commandes d'un client.

**Accès :** `CLIENT` (son propre ID), `ADMIN`
**Réponse 200 :** `CommandeDTO[]`

---

### GET `/api/commandes/restaurant/{restaurantId}`
Toutes les commandes d'un restaurant.

**Accès :** `RESTAURANT_OWNER`, `ADMIN`
**Réponse 200 :** `CommandeDTO[]`

---

### GET `/api/commandes/livreur/{livreurId}`
Commandes assignées à un livreur.

**Accès :** `LIVREUR` (son propre ID), `ADMIN`
**Réponse 200 :** `CommandeDTO[]`

---

### GET `/api/commandes/en-cours`
Commandes en cours (tous statuts actifs).

**Accès :** `ADMIN`
**Réponse 200 :** `CommandeDTO[]`

---

### GET `/api/commandes/{id}/tracking`
Informations de suivi d'une commande.

**Accès :** Authentifié
**Réponse 200 :** `CommandeTrackingDTO`

---

### POST `/api/commandes`
Crée une commande.

**Accès :** `CLIENT`, `ADMIN`
**Body :**
```json
{
  "restaurantId": 1,
  "lignes": [
    { "platId": 10, "quantite": 2, "remarque": "sans piment" }
  ],
  "modeReception": "LIVRAISON",
  "methodePaiement": "ESPECES",
  "adresseLivraison": {
    "adresse": "123 Rue de la Palmeraie",
    "ville": "Marrakech",
    "latitude": 31.6295,
    "longitude": -7.9811
  },
  "commentaire": "Sonner à l'interphone",
  "codePromo": "PROMO10"
}
```

> **Validation zone :** Si le restaurant est rattaché à une zone de déploiement,
> l'adresse de livraison est vérifiée contre le rayon de la zone. Une adresse
> hors-zone retourne une erreur `400` avec un message explicite.

> **Calcul des frais :** Si la zone a une grille tarifaire complète (`fraisLivraisonMin`,
> `distanceMinKm`, `prixExtraParKm`) :
> - `distance ≤ distanceMinKm` → `fraisLivraisonMin`
> - `distance > distanceMinKm` → `fraisLivraisonMin + (distance − distanceMinKm) × prixExtraParKm`

**Réponse 201 :** `CommandeDTO`

---

### PATCH `/api/commandes/{id}/status`
Met à jour le statut d'une commande.

**Accès :** `RESTAURANT_OWNER`, `LIVREUR`, `ADMIN`
**Body :**
```json
{ "statut": "EN_PREPARATION", "raisonAnnulation": "..." }
```
**Réponse 200 :** `CommandeDTO`

---

### PATCH `/api/commandes/{id}/assign-livreur/{livreurId}`
Assigne un livreur à une commande.

**Accès :** `ADMIN`
**Réponse 200 :** `CommandeDTO`

---

### DELETE `/api/commandes/{id}`
Annule une commande.

**Accès :** `CLIENT` (si en attente), `ADMIN`
**Réponse 200 :** `CommandeDTO` (statut `ANNULEE`)

---

## 7. Promotions

### GET `/api/promotions`
Liste des promotions actives.

**Accès :** Public
**Réponse 200 :** `PromotionDTO[]`

---

### GET `/api/promotions/flash`
Promotions flash actives (durée courte).

**Accès :** Public
**Réponse 200 :** `PromotionDTO[]`

---

### GET `/api/promotions/{id}`
Détail d'une promotion.

**Accès :** Public
**Réponse 200 :** `PromotionDTO`

---

### GET `/api/promotions/restaurant/{restaurantId}`
Promotions actives d'un restaurant.

**Accès :** Public
**Réponse 200 :** `PromotionDTO[]`

---

### POST `/api/promotions`
Crée une promotion.

**Accès :** `ADMIN`
**Body :**
```json
{
  "pourcentage": 20,
  "dateDebut": "2025-04-01T00:00:00",
  "dateFin": "2025-04-30T23:59:59",
  "description": "Promotion de printemps",
  "restaurantId": 1,
  "appliquerATousLesRestaurants": false,
  "montantMinCommande": 50.00,
  "usageMax": 100,
  "estFlash": false
}
```
Si `appliquerATousLesRestaurants: true`, la promotion est créée pour tous les restaurants (champ `restaurantId` ignoré).

**Réponse 201 :** `PromotionDTO`

---

### PATCH `/api/promotions/{id}/activer`
Active ou désactive une promotion.

**Accès :** `ADMIN`
**Query params :** `actif=true|false`
**Réponse 200 :** `PromotionDTO`

---

### DELETE `/api/promotions/{id}`
Supprime une promotion.

**Accès :** `ADMIN`
**Réponse 204**

---

## 8. Codes Promo

### GET `/api/codes-promo`
Liste tous les codes promo.

**Accès :** `ADMIN`
**Réponse 200 :** `CodePromoDTO[]`

---

### GET `/api/codes-promo/{id}`
Détail d'un code promo.

**Accès :** `ADMIN`
**Réponse 200 :** `CodePromoDTO`

---

### POST `/api/codes-promo`
Crée un code promo.

**Accès :** `ADMIN`
**Body :**
```json
{
  "code": "BIENVENUE10",
  "description": "10% de réduction pour les nouveaux clients",
  "typeReduction": "POURCENTAGE",
  "valeur": 10,
  "montantMinCommande": 30.00,
  "montantMaxReduction": 50.00,
  "dateDebut": "2025-04-01T00:00:00",
  "dateFin": "2025-12-31T23:59:59",
  "usageMax": 500
}
```
`typeReduction` : `POURCENTAGE` | `MONTANT_FIXE`

**Réponse 201 :** `CodePromoDTO`

---

### PATCH `/api/codes-promo/{id}/activer`
Active ou désactive un code promo.

**Accès :** `ADMIN`
**Query params :** `actif=true|false`
**Réponse 200 :** `CodePromoDTO`

---

### DELETE `/api/codes-promo/{id}`
Supprime un code promo.

**Accès :** `ADMIN`
**Réponse 204**

---

### POST `/api/codes-promo/valider`
Valide un code promo et calcule la remise pour un montant donné.

**Accès :** `CLIENT`, `ADMIN`
**Body :**
```json
{ "code": "BIENVENUE10", "montantCommande": 80.00 }
```
**Réponse 200 :** `ResultatCodePromoDTO`
```json
{
  "valide": true,
  "montantRemise": 8.00,
  "montantFinal": 72.00,
  "message": "Code valide — 10% de réduction appliqué"
}
```

---

## 9. Notifications

### GET `/api/notifications`
Notifications paginées de l'utilisateur connecté.

**Accès :** Authentifié
**Query params :** `page`, `size` (pagination Spring)
**Réponse 200 :** `Page<NotificationDTO>`

---

### GET `/api/notifications/non-lues`
Notifications non lues de l'utilisateur connecté.

**Accès :** Authentifié
**Réponse 200 :** `NotificationDTO[]`

---

### GET `/api/notifications/count`
Nombre de notifications non lues.

**Accès :** Authentifié
**Réponse 200 :**
```json
{ "nonLues": 5 }
```

---

### PATCH `/api/notifications/{id}/lire`
Marque une notification comme lue.

**Accès :** Authentifié
**Réponse 200 :** `NotificationDTO`

---

### POST `/api/notifications/lire-toutes`
Marque toutes les notifications comme lues.

**Accès :** Authentifié
**Réponse 200 :**
```json
{ "message": "Toutes les notifications ont été marquées comme lues." }
```

---

## 10. Campagnes de notification (Admin)

### POST `/api/admin/notifications/campagne`
Envoie une campagne de notification à un segment d'utilisateurs.
Les notifications sont envoyées **en in-app (BDD) ET en push FCM**.

**Accès :** `ADMIN`
**Body :**
```json
{
  "titre": "Promo de Printemps",
  "message": "Profitez de -20% sur toutes vos commandes ce week-end !",
  "type": "PROMOTION",
  "cibleRole": "CLIENT",
  "entityId": 42,
  "entityType": "PROMOTION"
}
```
| Champ | Valeurs | Défaut |
|---|---|---|
| `type` | `PROMOTION`, `SYSTEME` | `PROMOTION` |
| `cibleRole` | `CLIENT`, `LIVREUR`, `RESTAURANT_OWNER`, `ADMIN`, `ALL` | `CLIENT` |
| `entityId` | ID d'une entité liée | — |
| `entityType` | ex. `"PROMOTION"`, `"COMMANDE"` | — |

**Réponse 200 :** `CampagneNotificationResultDTO`
```json
{
  "destinatairesCount": 1234,
  "notificationsCreees": 1234,
  "pushEnvoyees": 987,
  "envoyeeAt": "2025-04-01T10:00:00"
}
```

---

### GET `/api/admin/notifications/promotion/{promotionId}`
Liste des utilisateurs notifiés pour une promotion donnée.

**Accès :** `ADMIN`
**Réponse 200 :** `NotificationDTO[]`

---

### GET `/api/admin/notifications/promotions`
Toutes les promotions (actives + inactives) — vue admin.

**Accès :** `ADMIN`
**Réponse 200 :** `PromotionDTO[]`

---

## 11. FCM Device Tokens

### POST `/api/device-tokens/register`
Enregistre ou met à jour un token FCM.
À appeler au démarrage de l'application mobile ou lors du renouvellement du token.

**Accès :** Authentifié
**Body :**
```json
{
  "userId": 123,
  "token": "<FCM-token>",
  "platform": "ANDROID"
}
```
`platform` : `ANDROID` | `IOS`

**Réponse 200 :**
```json
{ "message": "Token FCM enregistré avec succès" }
```

---

### DELETE `/api/device-tokens/{token}`
Désactive un token FCM (appeler à la déconnexion).

**Accès :** Authentifié
**Réponse 200 :**
```json
{ "message": "Token FCM désactivé" }
```

---

### DELETE `/api/device-tokens/user/{userId}`
Désactive tous les tokens FCM d'un utilisateur.

**Accès :** Authentifié
**Réponse 200 :**
```json
{ "message": "Tous les tokens FCM de l'utilisateur désactivés" }
```

---

## 12. Wallet

### GET `/api/wallet`
Solde et informations du wallet de l'utilisateur connecté.

**Accès :** `CLIENT`, `ADMIN`
**Réponse 200 :** `WalletDTO`
```json
{
  "id": 1,
  "userId": 123,
  "userNom": "Traoré",
  "userPrenom": "Issiaka",
  "solde": 150.00,
  "createdAt": "2025-01-01T00:00:00",
  "updatedAt": "2025-04-01T10:00:00"
}
```

---

### POST `/api/wallet/recharger`
Recharge le wallet.

**Accès :** `CLIENT`, `ADMIN`
**Body :**
```json
{ "montant": 100.00, "reference": "REF-PAIEMENT-XYZ" }
```
**Réponse 200 :** `WalletDTO`

---

### POST `/api/wallet/payer`
Effectue un paiement depuis le wallet.

**Accès :** `CLIENT`, `ADMIN`
**Body :**
```json
{ "montant": 45.00, "description": "Commande CMD-20250401-0001" }
```
**Réponse 200 :** `WalletDTO`

---

### GET `/api/wallet/transactions`
Historique paginé des transactions du wallet.

**Accès :** `CLIENT`, `ADMIN`
**Query params :** `page`, `size`
**Réponse 200 :** `Page<TransactionWalletDTO>`

---

### GET `/api/wallet/admin/{userId}`
Wallet d'un utilisateur spécifique.

**Accès :** `ADMIN`
**Réponse 200 :** `WalletDTO`

---

## 13. Fidélité

### GET `/api/fidelite`
Points de fidélité de l'utilisateur connecté.

**Accès :** `CLIENT`, `ADMIN`
**Réponse 200 :** `PointsFideliteDTO`
```json
{
  "id": 1,
  "userId": 123,
  "userNom": "Traoré",
  "userPrenom": "Issiaka",
  "pointsTotal": 500,
  "pointsDisponibles": 450,
  "pointsUtilises": 50,
  "niveauFidelite": "ARGENT",
  "pointsPourProchainNiveau": 500,
  "prochainNiveau": "OR"
}
```
Niveaux : `BRONZE` → `ARGENT` → `OR` → `PLATINE`

---

### GET `/api/fidelite/historique`
Historique paginé des transactions de points.

**Accès :** `CLIENT`, `ADMIN`
**Query params :** `page`, `size`
**Réponse 200 :** `Page<TransactionPointsDTO>`

---

### GET `/api/fidelite/admin/{userId}`
Points de fidélité d'un utilisateur spécifique.

**Accès :** `ADMIN`
**Réponse 200 :** `PointsFideliteDTO`

---

## 14. Caisse Livreur

### Routes Livreur

#### GET `/api/caisse/ma-position`
Position de caisse de l'utilisateur livreur connecté.

**Accès :** `LIVREUR`
**Réponse 200 :** `CaisseLivreurDTO`

---

#### GET `/api/caisse/mon-historique`
Historique paginé des transactions de caisse du livreur connecté.

**Accès :** `LIVREUR`
**Query params :** `page`, `size` (défaut : 20)
**Réponse 200 :** `Page<TransactionCaisseDTO>`

---

#### GET `/api/caisse/info-commande/{commandeId}`
Informations de paiement d'une commande pour le livreur.

**Accès :** `LIVREUR`
**Réponse 200 :** `InfoPaiementCommandeDTO`

---

#### POST `/api/caisse/paiement-restaurant/{commandeId}`
Confirme le paiement remis au restaurant par le livreur.

**Accès :** `LIVREUR`
**Réponse 200 :** `CaisseLivreurDTO`

---

### Routes Admin

#### GET `/api/caisse/bord-admin`
Tableau de bord global de la caisse (tous les livreurs).

**Accès :** `ADMIN`
**Réponse 200 :** `BordCaisseAdminDTO`

---

#### GET `/api/caisse/{livreurId}/position`
Position de caisse d'un livreur spécifique.

**Accès :** `ADMIN`
**Réponse 200 :** `CaisseLivreurDTO`

---

#### GET `/api/caisse/{livreurId}/historique`
Historique paginé des transactions d'un livreur.

**Accès :** `ADMIN`
**Query params :** `page`, `size`
**Réponse 200 :** `Page<TransactionCaisseDTO>`

---

#### POST `/api/caisse/avance/{livreurId}`
Accorde une avance de liquidités à un livreur.

**Accès :** `ADMIN`
**Body :**
```json
{ "montant": 50.00, "note": "Avance pour frais d'essence" }
```
**Réponse 200 :** `CaisseLivreurDTO`

---

#### POST `/api/caisse/reconcilier`
Réconciliation : le livreur remet les espèces collectées à l'admin.

**Accès :** `ADMIN`
**Body :**
```json
{ "livreurId": 3, "montantRemis": 100.00, "note": "Réconciliation du soir" }
```
**Réponse 200 :** `ReconciliationResultDTO`
```json
{
  "livreurId": 3,
  "livreurNom": "Dupont",
  "soldeCourantAvant": 120.00,
  "gainsDus": 95.00,
  "montantDuCalcule": 95.00,
  "montantRemis": 100.00,
  "ecart": 5.00,
  "dateReconciliation": "2025-04-01T18:00:00",
  "message": "Réconciliation réussie avec écart de +5 DH"
}
```

---

#### GET `/api/caisse/parametres`
Paramètres globaux de la caisse (plafonds, seuils d'alerte).

**Accès :** `ADMIN`
**Réponse 200 :** `ParametresCaisseDTO`

---

#### PUT `/api/caisse/parametres`
Met à jour les paramètres globaux de la caisse.

**Accès :** `ADMIN`
**Body :** `ParametresCaisseDTO`
**Réponse 200 :** `ParametresCaisseDTO`

---

#### PUT `/api/caisse/{livreurId}/plafond`
Définit un plafond personnalisé pour un livreur.

**Accès :** `ADMIN`
**Query params :** `plafond=200.00`
**Réponse 200 :** `CaisseLivreurDTO`

---

## 15. Gains Livreur

### GET `/api/livreurs/gains`
Historique paginé des gains du livreur connecté.

**Accès :** `LIVREUR`
**Query params :** `page`, `size`
**Réponse 200 :** `Page<GainsLivreurDTO>`

---

### GET `/api/livreurs/gains/summary`
Résumé des gains du livreur connecté (total, mois en cours, etc.).

**Accès :** `LIVREUR`
**Réponse 200 :** `GainsSummaryDTO`

---

### GET `/api/livreurs/{livreurId}/gains`
Historique des gains d'un livreur spécifique.

**Accès :** `ADMIN`
**Query params :** `page`, `size`
**Réponse 200 :** `Page<GainsLivreurDTO>`

---

### GET `/api/livreurs/{livreurId}/gains/summary`
Résumé des gains d'un livreur spécifique.

**Accès :** `ADMIN`
**Réponse 200 :** `GainsSummaryDTO`

---

## 16. Facturation Restaurant

### GET `/api/facturation-restaurant/{restaurantId}/parametres`
Paramètres de paiement d'un restaurant (mode de versement, RIB, etc.).

**Accès :** `RESTAURANT_OWNER`, `ADMIN`
**Réponse 200 :** `ParametresPaiementRestaurantDTO`
```json
{
  "id": 1,
  "restaurantId": 10,
  "restaurantNom": "Le Palais",
  "modePaiement": "VIREMENT",
  "periodiciteJours": 7,
  "modeVersement": "VIREMENT",
  "rib": "MA12345678901234567890",
  "nomBeneficiaire": "Le Palais SARL"
}
```

---

### PUT `/api/facturation-restaurant/{restaurantId}/parametres`
Met à jour les paramètres de paiement.

**Accès :** `RESTAURANT_OWNER`, `ADMIN`
**Body :** `ParametresPaiementRestaurantDTO`
**Réponse 200 :** `ParametresPaiementRestaurantDTO`

---

### GET `/api/facturation-restaurant/{restaurantId}/dettes`
Liste des dettes en attente d'un restaurant.

**Accès :** `ADMIN`
**Réponse 200 :** `DetteRestaurantDTO[]`

---

### GET `/api/facturation-restaurant/{restaurantId}/dettes/total`
Montant total des dettes en attente.

**Accès :** `ADMIN`
**Réponse 200 :** `BigDecimal` (montant en DH)

---

### POST `/api/facturation-restaurant/{restaurantId}/payer`
Crée un paiement groupé au restaurant pour une période donnée.

**Accès :** `ADMIN`
**Query params :**
| Param | Type | Description |
|---|---|---|
| `periodeDebut` | ISO date-time | Date de début de la période |
| `periodeFin` | ISO date-time | Date de fin de la période |
| `note` | string | Note interne (optionnel) |

Exemple : `?periodeDebut=2025-04-01T00:00:00&periodeFin=2025-04-30T23:59:59`

**Réponse 201 :** `PaiementRestaurantDTO`

---

### GET `/api/facturation-restaurant/{restaurantId}/paiements`
Historique paginé des paiements d'un restaurant.

**Accès :** `RESTAURANT_OWNER`, `ADMIN`
**Query params :** `page`, `size` (défaut : 20)
**Réponse 200 :** `Page<PaiementRestaurantDTO>`

---

### GET `/api/facturation-restaurant/paiements/{paiementId}/dettes`
Détail des dettes incluses dans un paiement donné.

**Accès :** `RESTAURANT_OWNER`, `ADMIN`
**Réponse 200 :** `DetteRestaurantDTO[]`

---

## 17. Zones de déploiement

Les zones de déploiement définissent les villes / secteurs couverts par MySugu.
Chaque restaurant est rattaché à une zone. À la création d'une commande en livraison,
l'adresse du client est vérifiée contre la zone du restaurant.

**Calcul des frais de livraison** (si la zone a une grille tarifaire complète) :
- `distance ≤ distanceMinKm` → `fraisLivraisonMin`
- `distance > distanceMinKm` → `fraisLivraisonMin + (distance − distanceMinKm) × prixExtraParKm`

---

### GET `/api/zones-deploiement/actives`
Zones de déploiement actives.

**Accès :** Public (aucune authentification requise)
**Utilisé par :** app mobile (sélecteur lors de la création d'un restaurant)
**Réponse 200 :** `ZoneDeploiementDTO[]`

---

### GET `/api/zones-deploiement`
Toutes les zones (actives + inactives).

**Accès :** `ADMIN`
**Réponse 200 :** `ZoneDeploiementDTO[]`

---

### GET `/api/zones-deploiement/{id}`
Détail d'une zone.

**Accès :** `ADMIN`
**Réponse 200 :** `ZoneDeploiementDTO`

---

### POST `/api/zones-deploiement`
Crée une zone de déploiement.

**Accès :** `ADMIN`
**Body :**
```json
{
  "nom": "Marrakech",
  "description": "Grand Marrakech — Médina, Guéliz, Hivernage, Palmeraie",
  "centreLatitude": 31.6295,
  "centreLongitude": -7.9811,
  "rayonKm": 20,
  "fraisLivraisonMin": 10.00,
  "distanceMinKm": 3.0,
  "prixExtraParKm": 2.00,
  "isActive": true
}
```
**Réponse 201 :** `ZoneDeploiementDTO`

---

### PUT `/api/zones-deploiement/{id}`
Met à jour une zone de déploiement.

**Accès :** `ADMIN`
**Body :** identique à POST
**Réponse 200 :** `ZoneDeploiementDTO`

---

### PATCH `/api/zones-deploiement/{id}/activer`
Active ou désactive une zone.

**Accès :** `ADMIN`
**Query params :** `actif=true|false`
**Réponse 200 :** `ZoneDeploiementDTO`

---

### DELETE `/api/zones-deploiement/{id}`
Supprime une zone.

**Accès :** `ADMIN`
> Retourne `400` si des restaurants sont encore rattachés à cette zone.

**Réponse 204**

---

## 18. Statistiques Admin

Tous les endpoints de cette section sont réservés à `ADMIN`.
Base URL : `/api/admin/statistiques`

---

### GET `/api/admin/statistiques/dashboard`
Vue d'ensemble du tableau de bord.

**Réponse 200 :** `DashboardOverviewDTO`
```json
{
  "commandesTotalAujourdhui": 42,
  "commandesTotalSemaine": 284,
  "commandesTotalMois": 1120,
  "chiffreAffairesAujourdhui": 3150.00,
  "chiffreAffairesSemaine": 21400.00,
  "chiffreAffairesMois": 84600.00,
  "tauxAnnulation": 0.032,
  "valeurMoyenneCommande": 75.50,
  "nouveauxUsersAujourdhui": 8,
  "nouveauxUsersSemaine": 53,
  "nouveauxUsersMois": 210,
  "commandesEnCours": 15,
  "restaurantsActifs": 34,
  "livreursActifs": 12
}
```

---

### GET `/api/admin/statistiques/commandes/evolution`
Évolution des commandes par jour ou par mois.

**Query params :**
| Param | Type | Défaut | Description |
|---|---|---|---|
| `debut` | date ISO | 1er du mois | Date de début |
| `fin` | date ISO | Aujourd'hui | Date de fin |
| `periode` | string | `JOUR` | `JOUR` ou `MOIS` |

**Réponse 200 :** `EvolutionCommandesDTO[]`
```json
[{
  "periode": "2025-04-01",
  "nombreCommandes": 42,
  "chiffreAffaires": 3150.00,
  "commandesLivrees": 38,
  "commandesAnnulees": 2
}]
```

---

### GET `/api/admin/statistiques/commandes/par-statut`
Répartition des commandes par statut sur une période.

**Query params :** `debut`, `fin` (défaut : mois en cours)
**Réponse 200 :** `CommandesParStatutDTO[]`
```json
[{ "statut": "LIVREE", "nombre": 1050, "pourcentage": 0.937 }]
```

---

### GET `/api/admin/statistiques/commandes/par-mode`
Répartition par mode de réception (`LIVRAISON` / `RETRAIT_SUR_PLACE`).

**Query params :** `debut`, `fin`
**Réponse 200 :** `CommandesParModeDTO[]`

---

### GET `/api/admin/statistiques/commandes/par-paiement`
Répartition par méthode de paiement.

**Query params :** `debut`, `fin`
**Réponse 200 :** `CommandesParPaiementDTO[]`

---

### GET `/api/admin/statistiques/commandes/heures-pointe`
Heures de pointe (volume de commandes par heure de la journée).

**Query params :** `debut` (défaut : -30j), `fin`
**Réponse 200 :** `HeurePointe[]`
```json
[{ "heure": 12, "nombreCommandes": 185 }]
```

---

### GET `/api/admin/statistiques/restaurants/top`
Top restaurants triés par nombre de commandes ou chiffre d'affaires.

**Query params :**
| Param | Type | Défaut |
|---|---|---|
| `limit` | int | 10 |
| `debut`, `fin` | date ISO | mois en cours |
| `tri` | string | `COMMANDES` → `CA` pour chiffre d'affaires |

**Réponse 200 :** `TopRestaurantDTO[]`
```json
[{
  "restaurantId": 1,
  "nom": "Le Palais",
  "nombreCommandes": 320,
  "chiffreAffaires": 24000.00,
  "appreciation": 4.7,
  "nombreAvis": 145,
  "tauxValidation": 0.96
}]
```

---

### GET `/api/admin/statistiques/restaurants/performance`
Indicateurs de performance de chaque restaurant sur une période.

**Query params :** `debut`, `fin`
**Réponse 200 :** `RestaurantPerformanceDTO[]`

---

### GET `/api/admin/statistiques/clients/top`
Meilleurs clients par dépense.

**Query params :** `limit` (défaut 10), `debut`, `fin` (défaut : -3 mois)
**Réponse 200 :** `ClientAnalyticsDTO[]`

---

### GET `/api/admin/statistiques/clients/retention`
Taux de rétention des clients.

**Réponse 200 :** `RetentionDTO`

---

### GET `/api/admin/statistiques/clients/par-ville`
Répartition des commandes par ville.

**Query params :** `debut`, `fin`
**Réponse 200 :** `ZoneCommandesDTO[]`

---

### GET `/api/admin/statistiques/livreurs`
Performance de tous les livreurs sur une période.

**Query params :** `debut`, `fin`
**Réponse 200 :** `LivreurAnalyticsDTO[]`

---

### GET `/api/admin/statistiques/livreurs/top`
Top livreurs par performance.

**Query params :** `limit` (défaut 10), `debut`, `fin`
**Réponse 200 :** `LivreurAnalyticsDTO[]`

---

### GET `/api/admin/statistiques/plats/top`
Plats les plus commandés.

**Query params :** `limit` (défaut 10), `debut`, `fin`
**Réponse 200 :** `PlatAnalyticsDTO[]`

---

### GET `/api/admin/statistiques/plats/jamais-commandes`
Plats qui n'ont jamais été commandés.

**Réponse 200 :** `PlatAnalyticsDTO[]`

---

### GET `/api/admin/statistiques/plats/par-categorie`
Chiffre d'affaires par catégorie de plat.

**Query params :** `debut`, `fin`
**Réponse 200 :** `PlatAnalyticsDTO[]`

---

### GET `/api/admin/statistiques/financier`
Rapport financier global sur une période (CA, commissions, versements).

**Query params :** `debut`, `fin`
**Réponse 200 :** `FinancierDTO`

---

### GET `/api/admin/statistiques/monitoring`
Données de monitoring en temps réel (commandes actives, livreurs en ligne, etc.).

**Réponse 200 :** `MonitoringTempsReelDTO`

---

### GET `/api/admin/statistiques/alertes`
Alertes actives (plafond caisse dépassé, restaurant fermé avec commandes en cours, etc.).

**Réponse 200 :** `AlerteDTO[]`

---

## 19. Avis

### POST `/api/avis`
Soumet un avis pour un restaurant ou un livreur.

**Accès :** Authentifié
**Body :**
```json
{
  "restaurantId": 10,
  "livreurId": null,
  "commandeId": 500,
  "note": 4,
  "commentaire": "Très bon service, livraison rapide !"
}
```
**Réponse 201 :** `AvisDTO`

---

### GET `/api/avis/{id}`
Détail d'un avis.

**Accès :** Public
**Réponse 200 :** `AvisDTO`

---

### GET `/api/avis/restaurant/{restaurantId}`
Avis d'un restaurant.

**Accès :** Public
**Réponse 200 :** `AvisDTO[]`

---

### GET `/api/avis/livreur/{livreurId}`
Avis d'un livreur.

**Accès :** Public
**Réponse 200 :** `AvisDTO[]`

---

### GET `/api/avis/mes-avis`
Avis laissés par l'utilisateur connecté.

**Accès :** Authentifié
**Réponse 200 :** `AvisDTO[]`

---

### PATCH `/api/avis/{id}/moderation`
Modère un avis (valider ou rejeter).

**Accès :** `ADMIN`
**Body :**
```json
{ "action": "VALIDER", "motif": "" }
```
`action` : `VALIDER` | `REJETER`
**Réponse 200 :** `AvisDTO`

---

### GET `/api/avis/admin/en-attente`
Avis en attente de modération.

**Accès :** `ADMIN`
**Réponse 200 :** `AvisDTO[]`

---

### DELETE `/api/avis/{id}`
Supprime un avis.

**Accès :** Auteur de l'avis ou `ADMIN`
**Réponse 204**

---

## 20. Favoris

### GET `/api/favoris`
Liste des restaurants favoris de l'utilisateur connecté.

**Accès :** Authentifié
**Réponse 200 :** `FavoriDTO[]`

---

### POST `/api/favoris/{restaurantId}`
Ajoute un restaurant aux favoris.

**Accès :** Authentifié
**Réponse 201 :** `FavoriDTO`

---

### DELETE `/api/favoris/{restaurantId}`
Retire un restaurant des favoris.

**Accès :** Authentifié
**Réponse 204**

---

### POST `/api/favoris/{restaurantId}/toggle`
Bascule l'état favori (ajoute si absent, retire si présent).

**Accès :** Authentifié
**Réponse 200 :**
```json
{ "isFavori": true, "action": "AJOUTE" }
```

---

### GET `/api/favoris/{restaurantId}/status`
Indique si un restaurant est dans les favoris de l'utilisateur connecté.

**Accès :** Authentifié
**Réponse 200 :**
```json
{ "isFavori": false }
```

---

### GET `/api/favoris/{restaurantId}/count`
Nombre total de fois qu'un restaurant a été mis en favori.

**Accès :** Public
**Réponse 200 :**
```json
{ "count": 342 }
```

---

## 21. Panier

### GET `/api/panier`
Contenu du panier de l'utilisateur connecté.

**Accès :** `CLIENT`
**Réponse 200 :** `PanierDTO`

---

### POST `/api/panier/items`
Ajoute un article au panier.

**Accès :** `CLIENT`
**Body :**
```json
{ "platId": 10, "quantite": 2, "remarque": "sans sauce" }
```
**Réponse 201 :** `PanierDTO`

---

### PATCH `/api/panier/items/{itemId}`
Modifie la quantité d'un article du panier.

**Accès :** `CLIENT`
**Query params :** `quantite=3`
**Réponse 200 :** `PanierDTO`

---

### DELETE `/api/panier`
Vide le panier.

**Accès :** `CLIENT`
**Réponse 204**

---

## 22. Menus

### POST `/api/menus`
Crée un menu (regroupement de plats).

**Accès :** `RESTAURANT_OWNER`, `ADMIN`
**Body :** `MenuCreateDTO`
**Réponse 201 :** `MenuDTO`

---

### GET `/api/menus/{id}`
Détail d'un menu.

**Accès :** Public
**Réponse 200 :** `MenuDTO`

---

### GET `/api/menus/restaurant/{restaurantId}`
Menus d'un restaurant.

**Accès :** Public
**Réponse 200 :** `MenuDTO[]`

---

### PATCH `/api/menus/{id}/activer`
Active ou désactive un menu.

**Accès :** `RESTAURANT_OWNER`, `ADMIN`
**Query params :** `actif=true|false`
**Réponse 200 :** `MenuDTO`

---

### DELETE `/api/menus/{id}`
Supprime un menu.

**Accès :** `RESTAURANT_OWNER`, `ADMIN`
**Réponse 204**

---

## 23. Dashboard Restaurant

### GET `/api/restaurant-dashboard/{restaurantId}`
Tableau de bord d'un restaurant (commandes du jour, stats, top plats).

**Accès :** `RESTAURANT_OWNER`, `ADMIN`
**Réponse 200 :** `RestaurantDashboardDTO`

---

### GET `/api/restaurant-dashboard/mon-restaurant`
Tableau de bord du restaurant du propriétaire connecté.

**Accès :** `RESTAURANT_OWNER`
**Réponse 200 :** `RestaurantDashboardDTO`

---

## 24. Employés Restaurant

### GET `/api/restaurants/{restaurantId}/employes`
Liste des employés d'un restaurant.

**Accès :** `RESTAURANT_OWNER`, `ADMIN`
**Réponse 200 :** `RestaurantEmployeDTO[]`

---

### POST `/api/restaurants/{restaurantId}/employes`
Ajoute un employé à un restaurant.

**Accès :** `RESTAURANT_OWNER`, `ADMIN`
**Body :**
```json
{ "userId": 55, "role": "CAISSIER" }
```
**Réponse 201 :** `RestaurantEmployeDTO`

---

### DELETE `/api/restaurants/{restaurantId}/employes/{employeId}`
Retire un employé du restaurant.

**Accès :** `RESTAURANT_OWNER`, `ADMIN`
**Réponse 204**

---

## 25. Fichiers (Minio)

### POST `/api/files/upload`
Upload d'un fichier vers Minio.

**Accès :** Authentifié
**Content-Type :** `multipart/form-data`
**Champs form :**
```
file*     fichier binaire
folder    string (défaut : "uploads") — ex: "restaurants", "plats"
```
**Réponse 201 :** `FileUploadResponse`
```json
{
  "objectName": "restaurants/logo-abc123.jpg",
  "bucket": "mysugu",
  "fileName": "logo-abc123.jpg",
  "size": 102400,
  "contentType": "image/jpeg",
  "url": "http://minio:9000/mysugu/restaurants/logo-abc123.jpg",
  "downloadUrl": "http://minio:9000/mysugu/restaurants/logo-abc123.jpg?download=true"
}
```

---

### GET `/api/files/{*objectName}`
Affiche ou télécharge un fichier par son chemin complet dans Minio.

**Accès :** Public
**Query params :**
| Param | Type | Défaut | Description |
|---|---|---|---|
| `download` | boolean | false | `true` pour forcer le téléchargement |

Exemples :
- `GET /api/files/restaurants/logo-abc123.jpg` → affiche l'image inline
- `GET /api/files/restaurants/logo-abc123.jpg?download=true` → force le téléchargement

---

### GET `/api/files`
Variante par query param.

**Query params :** `objectName=restaurants/logo-abc123.jpg&download=false`

---

### GET `/api/files/metadata/{*objectName}`
Métadonnées d'un fichier sans le télécharger.

**Accès :** Public
**Réponse 200 :** `FileMetadata`
```json
{
  "objectName": "restaurants/logo-abc123.jpg",
  "bucket": "mysugu",
  "fileName": "logo-abc123.jpg",
  "size": 102400,
  "contentType": "image/jpeg",
  "url": "...",
  "downloadUrl": "..."
}
```

---

### GET `/api/files/url/{*objectName}`
URL publique d'un fichier (sans le télécharger).

**Accès :** Public
**Réponse 200 :** `FileUrlResponse`

---

## 26. WebSocket Tracking GPS

Le tracking en temps réel utilise **STOMP over WebSocket**.

### Connexion
```
ws://localhost:8080/ws/tracking
```

### Envoyer une mise à jour GPS (livreur)
**Destination STOMP :** `/app/tracking.update`
**Payload :**
```json
{
  "commandeId": 500,
  "latitude": 31.6295,
  "longitude": -7.9811
}
```

### Recevoir les mises à jour (client)
**Topic à souscrire :** `/topic/tracking/{commandeId}`
**Payload reçu :**
```json
{
  "commandeId": 500,
  "latitude": 31.6295,
  "longitude": -7.9811,
  "timestamp": "2025-04-01T12:34:56"
}
```

---

## Codes d'erreur HTTP

| Code | Signification |
|---|---|
| `400` | Requête invalide (validation, zone hors couverture, etc.) |
| `401` | Non authentifié — token JWT absent ou expiré |
| `403` | Accès refusé — rôle insuffisant |
| `404` | Ressource introuvable |
| `409` | Conflit — ressource déjà existante (ex : code promo dupliqué) |
| `500` | Erreur interne du serveur |

**Format standard des erreurs :**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Notre service de livraison n'est pas encore disponible dans votre zone. Zones couvertes actuellement : Marrakech.",
  "timestamp": "2025-04-01T12:00:00"
}
```

---

## Rôles et accès

| Rôle | Description |
|---|---|
| `CLIENT` | Utilisateur final — passe des commandes |
| `LIVREUR` | Livreur — gère ses livraisons et sa caisse |
| `RESTAURANT_OWNER` | Propriétaire de restaurant |
| `RESTAURANT_STAFF` | Employé de restaurant |
| `ADMIN` | Administrateur de la plateforme — accès complet |

---

*Dernière mise à jour : 2026-03-29*
