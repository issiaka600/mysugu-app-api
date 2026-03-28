# Documentation API — MySugu

## Base URL

`http://localhost:8083`

## Authentification

- Type: `Bearer JWT`
- Header: `Authorization: Bearer <token>`
- Les routes publiques sont definies dans `SecurityConfig`.

Routes publiques principales:

- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/google`
- `POST /api/auth/forgot-password`
- `POST /api/auth/reset-password`
- `POST /api/auth/verify-email`
- `POST /api/auth/refresh`
- `GET /api/categories/**`
- `GET /api/restaurants/**`
- `GET /api/plats/**`
- `GET /api/files/**`
- `GET /swagger-ui.html`
- `GET /v3/api-docs`

## Formats et conventions

- JSON pour la plupart des endpoints
- `multipart/form-data` pour les creations et mises a jour avec fichiers
- Pagination Spring sur les listes paginees: `page`, `size`, `sort`
- Devise metier: `MAD` / `DH`
- Dates: `ISO 8601` — `2025-01-15T10:30:00`

---

## 1. Authentification et utilisateurs

### POST `/auth/register`

Cree un utilisateur.

Corps:

```json
{
  "email": "client@mysugu.ma",
  "password": "motdepasse",
  "nom": "Traore",
  "prenom": "Issiaka",
  "telephone": "0600000000",
  "role": "CLIENT"
}
```

Reponse: `201 Created`, `UserDTO`

### POST `/auth/login`

Authentifie un utilisateur par email/mot de passe.

Corps:

```json
{
  "email": "client@mysugu.ma",
  "password": "motdepasse"
}
```

Reponse: `200 OK`, `LoginResponseDTO`

### POST `/auth/google`

Authentifie via Google. Si le compte existe, il est reutilise. Sinon il est cree puis connecte.

Corps:

```json
{
  "idToken": "GOOGLE_ID_TOKEN",
  "role": "CLIENT",
  "telephone": "0600000000"
}
```

Reponse: `200 OK`, `LoginResponseDTO`

### POST `/api/auth/refresh`

Rafraichit un access token a partir du refresh token.

Corps:

```json
{
  "refreshToken": "..."
}
```

Reponse: `200 OK`, `{ "accessToken": "...", "refreshToken": "..." }`

### POST `/api/auth/logout`

Invalide les tokens. Corps optionnel:

```json
{
  "refreshToken": "..."
}
```

### POST `/api/auth/forgot-password`

Envoie un email de reinitialisation de mot de passe.

Corps: `{ "email": "..." }`

### POST `/api/auth/reset-password`

Reinitialise le mot de passe via le token recu par email.

Corps: `{ "token": "...", "newPassword": "..." }`

### POST `/api/auth/send-verification`

Envoie un email de verification. Necessite authentification.

### POST `/api/auth/verify-email`

Verifie l'email via le code recu.

Corps: `{ "token": "..." }`

### POST `/api/auth/change-password`

Change le mot de passe de l'utilisateur connecte.

Corps: `{ "ancienMotDePasse": "...", "nouveauMotDePasse": "..." }`

### GET `/users/profile`

Retourne le profil du token courant.

### PUT `/users/profile`

Met a jour le profil en `multipart/form-data`.

Champs:

- `nom`
- `prenom`
- `telephone`
- `localisation.latitude`
- `localisation.longitude`
- `localisation.adresse`
- `localisation.ville`
- `localisation.codePostal`
- `localisation.pays`
- `avatar` fichier optionnel

### PATCH `/users/location`

Met a jour uniquement la localisation.

### GET `/livreurs/disponibles`

Parametres:

- `latitude`
- `longitude`
- `radiusKm` optionnel, defaut `10.0`

### GET `/api/users/adresses`

Liste les adresses de livraison enregistrees de l'utilisateur.

### POST `/api/users/adresses`

Ajoute une adresse de livraison.

Corps: `AdresseLivraisonCreateDTO`

### PUT `/api/users/adresses/{adresseId}`

Modifie une adresse de livraison.

### PATCH `/api/users/adresses/{adresseId}/default`

Definit une adresse comme adresse par defaut.

### DELETE `/api/users/adresses/{adresseId}`

Supprime une adresse de livraison.

### DELETE `/api/users/compte`

Supprime le compte de l'utilisateur (RGPD). Irreversible.

---

## 2. Admin — Gestion des utilisateurs

Base: `/api/admin/users` — Role requis: `ADMIN`

### GET `/api/admin/users`

Liste paginee d'utilisateurs filtres par role.

Parametres:

- `role` — `CLIENT`, `LIVREUR`, `RESTAURANT_OWNER`, `ADMIN` (defaut: `CLIENT`)
- `page`, `size`
- `search` — recherche optionnelle dans nom/email

Reponse: `Page<UserDTO>`

### GET `/api/admin/users/{id}`

Retourne un utilisateur par son identifiant.

### PATCH `/api/admin/users/{id}/toggle`

Active ou desactive un utilisateur.

Reponse: `UserDTO`

---

## 3. Categories

### GET `/api/categories`

Liste toutes les categories.

### GET `/api/categories/{id}`

Retourne une categorie par identifiant.

### GET `/api/categories/{id}/restaurants`

Liste les restaurants actifs de la categorie.

### POST `/api/categories`

Role requis: `ADMIN`

`multipart/form-data`:

- `nom`
- `description`
- `image` fichier optionnel

### PUT `/api/categories/{id}`

Role requis: `ADMIN`

`multipart/form-data`:

- `nom`
- `description`
- `image` fichier optionnel

### DELETE `/api/categories/{id}`

Role requis: `ADMIN`

---

## 4. Restaurants

### GET `/api/restaurants`

Retourne une page de restaurants actifs.

Filtres:

- `categorieId`
- `latitude`
- `longitude`
- `maxDistance`
- pagination `page`, `size`, `sort`

### GET `/api/restaurants/{id}`

Retourne un restaurant.

### GET `/api/restaurants/search`

Parametre: `keyword`

### GET `/api/restaurants/top-rated`

Parametre: `limit`, defaut `10`

### GET `/api/restaurants/nearby`

Parametres:

- `latitude`
- `longitude`
- `radiusKm`, defaut `5.0`

### POST `/api/restaurants`

Roles requis: `RESTAURANT_OWNER`, `ADMIN`

`multipart/form-data`:

- `nom`
- `description`
- `categorieId`
- `ownerId`
- `localisation.latitude`
- `localisation.longitude`
- `localisation.adresse`
- `localisation.ville`
- `localisation.codePostal`
- `localisation.pays`
- `horairesOuverture`
- `tempsLivraisonMoyen`
- `autoCloseEnabled`
- `heureOuverture`
- `heureFermeture`
- `removeLogo`
- `logo` fichier optionnel

### PUT `/api/restaurants/{id}`

Meme format que la creation.

### PATCH `/api/restaurants/{id}/activate`

Active ou desactive un restaurant.

### DELETE `/api/restaurants/{id}`

Supprime le restaurant et son logo si present.

---

## 5. Plats

### GET `/api/plats`

Retourne une page de plats.

Filtres:

- `restaurantId`
- `categorie`
- `available`
- pagination `page`, `size`, `sort`

### GET `/api/plats/{id}`

Retourne un plat.

### GET `/api/plats/restaurant/{restaurantId}`

Retourne les plats disponibles d'un restaurant.

### GET `/api/plats/search`

Parametre: `keyword`

### POST `/api/plats`

Roles requis: `RESTAURANT_OWNER`, `ADMIN`

`multipart/form-data`:

- `nom`
- `description`
- `prix`
- `ingredients`
- `categoriePlat`
- `restaurantId`
- `tempsPreparation`
- `availabilityMode`
- `indisponibleJusqua`
- `removeImage`
- `image` fichier optionnel

### PUT `/api/plats/{id}`

Meme format que la creation.

### PATCH `/api/plats/{id}/image`

Met a jour uniquement l'image du plat.

`multipart/form-data`: `image` fichier requis

### PATCH `/api/plats/{id}/availability`

Corps optionnel:

```json
{
  "availabilityMode": "INDISPONIBLE_TEMPORAIRE"
}
```

Si le corps est absent, l'endpoint bascule entre disponible et indisponible definitive.

### DELETE `/api/plats/{id}`

Supprime le plat et son image.

---

## 6. Commandes

### GET `/api/commandes`

Filtres:

- `clientId`
- `restaurantId`
- `statut`
- pagination `page`, `size`, `sort`

### GET `/api/commandes/{id}`

Retourne une commande.

### GET `/api/commandes/numero/{numeroCommande}`

Recherche par numero de commande.

### GET `/api/commandes/client/{clientId}`

Accessible a `CLIENT`, `ADMIN`.

### GET `/api/commandes/restaurant/{restaurantId}`

Accessible a `RESTAURANT_OWNER`, `ADMIN`.

### GET `/api/commandes/livreur/{livreurId}`

Accessible a `LIVREUR`, `ADMIN`.

### GET `/api/commandes/en-cours`

Retourne les commandes dans les statuts actifs.

### POST `/api/commandes`

Accessible a `CLIENT`, `ADMIN`.

Exemple:

```json
{
  "clientId": 1,
  "restaurantId": 3,
  "lignes": [
    {
      "platId": 7,
      "quantite": 2,
      "remarque": "Sans piment"
    }
  ],
  "adresseLivraison": {
    "latitude": 33.5731,
    "longitude": -7.5898,
    "adresse": "Boulevard Zerktouni",
    "ville": "Casablanca",
    "codePostal": "20000",
    "pays": "Maroc"
  },
  "commentaire": "Appeler en arrivant",
  "methodePaiement": "ESPECES",
  "modeReception": "LIVRAISON",
  "codePromo": "SUMMER20"
}
```

Champ `codePromo` optionnel. Si fourni, un code promo valide est applique en plus de toute promotion restaurant active.

Logique de remise appliquee a la creation:

1. **Promotion restaurant** — si le restaurant a une promotion active, valide (dates, montant minimum, usageMax), le pourcentage est applique automatiquement sur le sous-total des plats.
2. **Code promo** — si `codePromo` est fourni, le code est valide (actif, dates, montant minimum, usageMax) et la reduction est calculee (`POURCENTAGE` ou `MONTANT_FIXE`, plafonnee par `montantMaxReduction` si defini).

Les deux remises sont cumulables. Le `montantFinal` retourne ne peut pas etre inferieur a zero.

Regles metier additionnelles:

- le restaurant doit etre ouvert
- tous les plats doivent appartenir au meme restaurant
- un plat indisponible ne peut pas etre commande
- `modeReception=LIVRAISON` exige une adresse
- les frais de livraison sont a `0` pour `RETRAIT_SUR_PLACE`

### PATCH `/api/commandes/{id}/status`

Corps:

```json
{
  "statut": "PRETE",
  "raisonAnnulation": null
}
```

Transitions principales:

- `EN_ATTENTE -> CONFIRMEE | ANNULEE`
- `CONFIRMEE -> EN_PREPARATION | PRETE | ANNULEE`
- `EN_PREPARATION -> PRETE | ANNULEE`
- `PRETE -> EN_COURS | LIVREE | ANNULEE` selon le mode
- `EN_COURS -> LIVREE`

### PATCH `/api/commandes/{id}/assign-livreur/{livreurId}`

Assigne un livreur.

### DELETE `/api/commandes/{id}`

Annule la commande si son etat le permet.

### GET `/api/commandes/{id}/tracking`

Retourne un objet de suivi contenant:

- `numeroCommande`
- `statut`
- `trackingStatut`
- `modeReception`
- `tempsEstime`
- `raisonAnnulation`
- `restaurant`
- `client`
- `livreur` si assigne
- `destination`

---

## 7. Promotions

Les promotions sont des remises en pourcentage applicables a un restaurant specifique ou a tous les restaurants de la plateforme.

### GET `/api/promotions`

Retourne la liste des promotions **actives** uniquement (vue publique / client).

### GET `/api/promotions/{id}`

Retourne une promotion par identifiant.

### GET `/api/promotions/flash`

Retourne les promotions flash actives.

### GET `/api/promotions/restaurant/{restaurantId}`

Retourne les promotions actives d'un restaurant specifique.

### GET `/api/admin/notifications/promotions`

Role requis: `ADMIN`

Retourne **toutes** les promotions (actives et inactives) — vue admin.

### POST `/api/promotions`

Role requis: `ADMIN`

Corps:

```json
{
  "pourcentage": 20,
  "dateDebut": "2025-06-01T00:00:00",
  "dateFin": "2025-06-30T23:59:59",
  "description": "Promo ete",
  "restaurantId": 3,
  "appliquerATousLesRestaurants": false,
  "code": "ETE2025",
  "montantMinCommande": 50.00,
  "usageMax": 100,
  "estFlash": false
}
```

- `restaurantId` — identifiant du restaurant cible (mutuellement exclusif avec `appliquerATousLesRestaurants`).
- `appliquerATousLesRestaurants` — si `true`, la promotion est liee a tous les restaurants actifs de la plateforme au moment de la creation. `restaurantId` est ignore.

### PATCH `/api/promotions/{id}/activer`

Role requis: `ADMIN`

Parametre: `actif=true|false`

Active ou desactive une promotion. Une promotion desactivee reste visible dans la vue admin.

### DELETE `/api/promotions/{id}`

Role requis: `ADMIN`

Supprime la promotion. Retire prealablement le lien FK sur tous les restaurants associes avant suppression.

---

## 8. Codes promo

Les codes promo sont des remises manuelles (pourcentage ou montant fixe) saisies par le client au moment de la commande.

Role requis: `ADMIN` pour toutes les operations de gestion. `CLIENT` ou `ADMIN` pour la validation.

### GET `/api/codes-promo`

Liste tous les codes promo.

### GET `/api/codes-promo/{id}`

Retourne un code promo par identifiant.

### POST `/api/codes-promo`

Corps:

```json
{
  "code": "SUMMER20",
  "description": "20% de reduction ete",
  "typeReduction": "POURCENTAGE",
  "valeur": 20,
  "montantMinCommande": 80.00,
  "montantMaxReduction": 50.00,
  "dateDebut": "2025-06-01T00:00:00",
  "dateFin": "2025-08-31T23:59:59",
  "usageMax": 500
}
```

`typeReduction`: `POURCENTAGE` ou `MONTANT_FIXE`

### PATCH `/api/codes-promo/{id}/activer`

Parametre: `actif=true|false`

### DELETE `/api/codes-promo/{id}`

### POST `/api/codes-promo/valider`

Accessible a `CLIENT`, `ADMIN`. Valide un code promo et calcule la remise estimee.

Corps:

```json
{
  "code": "SUMMER20",
  "montantCommande": 120.00
}
```

Reponse: `ResultatCodePromoDTO` — reduction calculee, montant final, validite.

---

## 9. Notifications

Les notifications sont in-app (base de donnees) et push FCM simultanement.

Chaque endpoint de cette section lit le token JWT dans le header `Authorization` pour identifier l'utilisateur.

### GET `/api/notifications`

Retourne la liste paginee de toutes les notifications de l'utilisateur connecte (lues + non lues).

Parametres: `page`, `size`

Reponse: `Page<NotificationDTO>`

### GET `/api/notifications/non-lues`

Retourne la liste complete des notifications non lues de l'utilisateur.

Reponse: `NotificationDTO[]`

### GET `/api/notifications/count`

Retourne le nombre de notifications non lues.

Reponse:

```json
{
  "nonLues": 5
}
```

### PATCH `/api/notifications/{id}/lire`

Marque une notification comme lue.

Reponse: `NotificationDTO` mis a jour.

### POST `/api/notifications/lire-toutes`

Marque toutes les notifications de l'utilisateur comme lues.

Reponse: `{ "message": "Toutes les notifications ont ete marquees comme lues." }`

---

## 10. Admin — Campagnes de notifications

Base: `/api/admin/notifications` — Role requis: `ADMIN`

Envoi de notifications in-app ET push FCM a un segment d'utilisateurs ou a un utilisateur specifique.

### POST `/api/admin/notifications/campagne`

Corps:

```json
{
  "titre": "Nouvelle promotion !",
  "message": "Profitez de -20% sur toutes vos commandes ce week-end.",
  "type": "PROMOTION",
  "cibleRole": "CLIENT",
  "entityId": 12,
  "entityType": "PROMOTION",
  "destinataireUserId": null
}
```

Champs:

| Champ | Requis | Description |
|---|---|---|
| `titre` | Oui | Titre de la notification (max 200 car.) |
| `message` | Oui | Corps du message (max 1000 car.) |
| `type` | Non | `PROMOTION` (defaut) ou `SYSTEME` |
| `cibleRole` | Non | `CLIENT` (defaut), `LIVREUR`, `RESTAURANT_OWNER`, `ADMIN`, `ALL` |
| `entityId` | Non | ID de l'entite liee (ex. id d'une promotion) |
| `entityType` | Non | Type de l'entite liee (ex. `"PROMOTION"`) |
| `destinataireUserId` | Non | ID d'un utilisateur specifique. Si renseigne, `cibleRole` est ignore et la notification est envoyee uniquement a cet utilisateur. |

Reponse: `CampagneNotificationResultDTO`

```json
{
  "destinatairesCount": 342,
  "notificationsCreees": 342,
  "pushEnvoyees": 298,
  "envoyeeAt": "2025-06-15T14:30:00"
}
```

### GET `/api/admin/notifications/promotion/{promotionId}`

Retourne la liste des utilisateurs notifies pour une promotion donnee, avec les champs `destinataireNom`, `destinatairePrenom`, `destinataireEmail` peuples.

Reponse: `NotificationDTO[]`

### GET `/api/admin/notifications/promotions`

Retourne toutes les promotions (actives et inactives) — utilise pour alimenter le selecteur de promotion dans l'interface admin.

Reponse: `PromotionDTO[]`

---

## 11. Push FCM — Tokens appareil

### POST `/api/device-tokens/register`

Enregistre ou met a jour un token FCM pour un utilisateur. A appeler au demarrage de l'application mobile ou lors du renouvellement du token.

Corps:

```json
{
  "userId": 1,
  "token": "FCM_TOKEN_STRING",
  "platform": "ANDROID"
}
```

`platform`: `ANDROID` ou `IOS`

### DELETE `/api/device-tokens/{token}`

Desactive un token FCM specifique (a appeler lors de la deconnexion).

### DELETE `/api/device-tokens/user/{userId}`

Desactive tous les tokens FCM d'un utilisateur.

---

## 12. Caisse livreur

Base: `/api/caisse`

### Endpoints livreur

#### GET `/api/caisse/ma-position`

Role: `LIVREUR`

Retourne la position de caisse du livreur connecte: solde actuel, plafond, avances, historique resume.

Reponse: `CaisseLivreurDTO`

#### GET `/api/caisse/mon-historique`

Role: `LIVREUR`

Retourne l'historique pagine des transactions de caisse du livreur.

Parametres: pagination standard

Reponse: `Page<TransactionCaisseDTO>`

#### GET `/api/caisse/info-commande/{commandeId}`

Role: `LIVREUR`

Retourne les informations de paiement d'une commande pour le livreur.

Reponse: `InfoPaiementCommandeDTO`

#### POST `/api/caisse/paiement-restaurant/{commandeId}`

Role: `LIVREUR`

Confirme que le livreur a paye le restaurant pour la commande.

Reponse: `CaisseLivreurDTO` mise a jour.

### Endpoints admin

#### GET `/api/caisse/bord-admin`

Role: `ADMIN`

Vue globale de la caisse: tous les livreurs, soldes, alertes.

Reponse: `BordCaisseAdminDTO`

#### GET `/api/caisse/{livreurId}/position`

Role: `ADMIN`

Position de caisse d'un livreur specifique.

#### GET `/api/caisse/{livreurId}/historique`

Role: `ADMIN`

Historique pagine des transactions d'un livreur.

#### POST `/api/caisse/avance/{livreurId}`

Role: `ADMIN`

Accorde une avance de liquidites au livreur.

Corps:

```json
{
  "montant": 100.00,
  "note": "Avance exceptionnelle"
}
```

Reponse: `CaisseLivreurDTO`

#### POST `/api/caisse/reconcilier`

Role: `ADMIN`

Reconciliation: le livreur remet les especes a l'admin.

Corps:

```json
{
  "livreurId": 3,
  "montantRemis": 250.00,
  "note": "Reconciliation fin de journee"
}
```

Reponse: `ReconciliationResultDTO`

#### GET `/api/caisse/parametres`

Role: `ADMIN`

Retourne les parametres globaux de la caisse (plafond par defaut, etc.).

#### PUT `/api/caisse/parametres`

Role: `ADMIN`

Met a jour les parametres globaux de la caisse.

#### PUT `/api/caisse/{livreurId}/plafond`

Role: `ADMIN`

Definit un plafond personnalise pour un livreur.

Parametre: `plafond` (BigDecimal)

---

## 13. Wallet

Base: `/api/wallet`

### GET `/api/wallet`

Roles: `CLIENT`, `ADMIN`

Retourne le wallet de l'utilisateur connecte.

Reponse: `WalletDTO`

### POST `/api/wallet/recharger`

Roles: `CLIENT`, `ADMIN`

Recharge le wallet.

Corps: `RechargeWalletDTO` — `{ "montant": 100.00, "reference": "..." }`

### POST `/api/wallet/payer`

Roles: `CLIENT`, `ADMIN`

Effectue un paiement depuis le wallet.

Corps: `PaiementWalletDTO`

### GET `/api/wallet/transactions`

Roles: `CLIENT`, `ADMIN`

Historique pagine des transactions du wallet de l'utilisateur connecte.

Reponse: `Page<TransactionWalletDTO>`

### GET `/api/wallet/admin/{userId}`

Role: `ADMIN`

Retourne le wallet d'un utilisateur specifique.

---

## 14. Fidelite

Base: `/api/fidelite`

### GET `/api/fidelite`

Roles: `CLIENT`, `ADMIN`

Retourne les points de fidelite de l'utilisateur connecte.

Reponse: `PointsFideliteDTO`

### GET `/api/fidelite/historique`

Roles: `CLIENT`, `ADMIN`

Historique pagine des transactions de points.

Reponse: `Page<TransactionPointsDTO>`

### GET `/api/fidelite/admin/{userId}`

Role: `ADMIN`

Retourne les points de fidelite d'un utilisateur specifique.

---

## 15. Facturation restaurant

Base: `/api/facturation-restaurant`

### GET `/api/facturation-restaurant/{restaurantId}/parametres`

Roles: `RESTAURANT_OWNER`, `ADMIN`

Retourne les parametres de paiement du restaurant (commission, periodicite, etc.).

### PUT `/api/facturation-restaurant/{restaurantId}/parametres`

Roles: `RESTAURANT_OWNER`, `ADMIN`

Met a jour les parametres de paiement.

Corps: `ParametresPaiementRestaurantDTO`

### GET `/api/facturation-restaurant/{restaurantId}/dettes`

Role: `ADMIN`

Retourne la liste des dettes en attente du restaurant.

Reponse: `DetteRestaurantDTO[]`

### GET `/api/facturation-restaurant/{restaurantId}/dettes/total`

Role: `ADMIN`

Retourne le montant total des dettes en attente.

Reponse: `BigDecimal`

### POST `/api/facturation-restaurant/{restaurantId}/payer`

Role: `ADMIN`

Declenche un paiement groupe au restaurant pour une periode donnee.

Parametres de requete:

- `periodeDebut` — `ISO 8601` datetime
- `periodeFin` — `ISO 8601` datetime
- `note` — optionnel

Reponse: `201 Created`, `PaiementRestaurantDTO`

### GET `/api/facturation-restaurant/{restaurantId}/paiements`

Roles: `RESTAURANT_OWNER`, `ADMIN`

Historique pagine des paiements d'un restaurant.

Reponse: `Page<PaiementRestaurantDTO>`

### GET `/api/facturation-restaurant/paiements/{paiementId}/dettes`

Roles: `RESTAURANT_OWNER`, `ADMIN`

Detail des dettes incluses dans un paiement specifique.

Reponse: `DetteRestaurantDTO[]`

---

## 16. Zones de livraison

Base: `/api/zones-livraison`

### GET `/api/zones-livraison/restaurant/{restaurantId}`

Public. Retourne les zones de livraison d'un restaurant.

Reponse: `ZoneLivraisonDTO[]`

### POST `/api/zones-livraison`

Roles: `ADMIN`, `RESTAURANT_OWNER`

Corps: `ZoneLivraisonDTO`

Reponse: `201 Created`, `ZoneLivraisonDTO`

### PUT `/api/zones-livraison/{id}`

Roles: `ADMIN`, `RESTAURANT_OWNER`

### DELETE `/api/zones-livraison/{id}`

Roles: `ADMIN`, `RESTAURANT_OWNER`

---

## 17. Statistiques admin

Base: `/api/admin/statistiques` — Role requis: `ADMIN`

Tous les endpoints acceptent des parametres de date `debut` et `fin` au format `ISO 8601` date (`yyyy-MM-dd`), sauf mention contraire.

### GET `/api/admin/statistiques/dashboard`

Vue d'ensemble generale du tableau de bord.

Reponse: `DashboardOverviewDTO`

### GET `/api/admin/statistiques/commandes/evolution`

Evolution du nombre de commandes.

Parametres:

- `debut`, `fin` — periode (defaut: mois courant)
- `periode` — `JOUR` (defaut) ou `MOIS`

Reponse: `EvolutionCommandesDTO[]`

### GET `/api/admin/statistiques/commandes/par-statut`

Repartition des commandes par statut.

Reponse: `CommandesParStatutDTO[]`

### GET `/api/admin/statistiques/commandes/par-mode`

Repartition par mode de reception (`LIVRAISON` / `RETRAIT_SUR_PLACE`).

Reponse: `CommandesParModeDTO[]`

### GET `/api/admin/statistiques/commandes/par-paiement`

Repartition par methode de paiement.

Reponse: `CommandesParPaiementDTO[]`

### GET `/api/admin/statistiques/commandes/heures-pointe`

Heures de pointe des commandes (defaut: 30 derniers jours).

Reponse: `HeurePointe[]`

### GET `/api/admin/statistiques/restaurants/top`

Parametres:

- `limit` — defaut `10`
- `debut`, `fin`
- `tri` — `COMMANDES` (defaut) ou `CA`

Reponse: `TopRestaurantDTO[]`

### GET `/api/admin/statistiques/restaurants/performance`

Performance detaillee de chaque restaurant.

Reponse: `RestaurantPerformanceDTO[]`

### GET `/api/admin/statistiques/clients/top`

Parametres: `limit`, `debut`, `fin` (defaut: 3 derniers mois)

Reponse: `ClientAnalyticsDTO[]`

### GET `/api/admin/statistiques/clients/retention`

Taux de retention clients.

Reponse: `RetentionDTO`

### GET `/api/admin/statistiques/clients/par-ville`

Repartition des commandes par ville.

Reponse: `ZoneCommandesDTO[]`

### GET `/api/admin/statistiques/livreurs`

Performance de tous les livreurs.

Reponse: `LivreurAnalyticsDTO[]`

### GET `/api/admin/statistiques/livreurs/top`

Parametres: `limit`, `debut`, `fin`

Reponse: `LivreurAnalyticsDTO[]`

### GET `/api/admin/statistiques/plats/top`

Parametres: `limit`, `debut`, `fin`

Reponse: `PlatAnalyticsDTO[]`

### GET `/api/admin/statistiques/plats/jamais-commandes`

Plats jamais commandes.

Reponse: `PlatAnalyticsDTO[]`

### GET `/api/admin/statistiques/plats/par-categorie`

Chiffre d'affaires par categorie de plat.

Reponse: `PlatAnalyticsDTO[]`

### GET `/api/admin/statistiques/financier`

Rapport financier consolide (CA total, commissions, remises, frais livraison).

Reponse: `FinancierDTO`

### GET `/api/admin/statistiques/monitoring`

Monitoring temps reel: commandes en cours, livreurs actifs, alertes.

Reponse: `MonitoringTempsReelDTO`

### GET `/api/admin/statistiques/alertes`

Liste des alertes actives (retards, livreurs inactifs, etc.).

Reponse: `AlerteDTO[]`

---

## 18. Fichiers

Bucket logique unique: `mysugu`

Dossiers utilises:

- `restaurants`
- `plats`
- `categories`
- `avatars`
- `uploads`

### POST `/api/files/upload`

Authentifie.

`multipart/form-data`:

- `file`
- `folder` optionnel, defaut `uploads`

Exemples de `folder`:

- `plats`
- `restaurants/logos`
- `categories`
- `avatars`

### GET `/api/files?objectName=plats/mon-image.jpg`

Retourne le fichier.

Parametre optionnel:

- `download=true` pour forcer le telechargement

### GET `/api/files/{objectName}`

Equivalent pour les appels directs navigateur/app.

### GET `/api/files/metadata?objectName=plats/mon-image.jpg`

Retourne les metadonnees et l'URL publique stable.

### GET `/api/files/url?objectName=plats/mon-image.jpg`

Retourne:

- `url`
- `downloadUrl`
- `presignedUrl`
- `expiresIn=0`

### GET `/api/files/legacy/{bucket}/**`

Endpoint de compatibilite pour anciens chemins.

---

## Objets retour frequents

### `LoginResponseDTO`

```json
{
  "token": "jwt",
  "user": {
    "id": 1,
    "email": "client@mysugu.ma",
    "nom": "Traore",
    "prenom": "Issiaka",
    "telephone": "0600000000",
    "role": "CLIENT",
    "avatar": "avatars/uuid.jpg",
    "isActive": true
  }
}
```

### `PromotionDTO`

```json
{
  "id": 1,
  "pourcentage": 20,
  "dateDebut": "2025-06-01T00:00:00",
  "dateFin": "2025-06-30T23:59:59",
  "description": "Promo ete",
  "isActive": true,
  "restaurantId": 3,
  "restaurantNom": "Pizza Palace",
  "code": "ETE2025",
  "montantMinCommande": 50.00,
  "usageMax": 100,
  "usageCount": 12,
  "estFlash": false
}
```

Note: `restaurantId` et `restaurantNom` ne sont peuples que si la promotion est liee a exactement un restaurant. Si `appliquerATousLesRestaurants=true` a ete utilise, ces champs sont `null`.

### `CommandeDTO`

Champs utiles:

- `montantTotal` — sous-total plats + frais de livraison avant remise
- `montantRemise` — remise totale appliquee (promotion + code promo)
- `montantFinal` — montant effectivement paye (`montantTotal - montantRemise`)
- `codePromoUtilise` — code promo applique, `null` si aucun
- `trackingStatut`
- `modeReception`
- `raisonAnnulation`
- `currency` — `"MAD"`
- `currencySymbol` — `"DH"`

### `NotificationDTO`

```json
{
  "id": 1,
  "destinataireId": 42,
  "destinataireNom": "Traore",
  "destinatairePrenom": "Issiaka",
  "destinataireEmail": "client@mysugu.ma",
  "titre": "Nouvelle promotion !",
  "message": "Profitez de -20% ce week-end.",
  "type": "PROMOTION",
  "lue": false,
  "lueAt": null,
  "entityId": 12,
  "entityType": "PROMOTION",
  "createdAt": "2025-06-15T14:30:00"
}
```

Note: `destinataireNom`, `destinatairePrenom`, `destinataireEmail` sont peuples uniquement dans les reponses des endpoints admin (`GET /api/admin/notifications/promotion/{promotionId}`).

### `CodePromoDTO`

```json
{
  "id": 5,
  "code": "SUMMER20",
  "description": "20% de reduction ete",
  "typeReduction": "POURCENTAGE",
  "valeur": 20,
  "montantMinCommande": 80.00,
  "montantMaxReduction": 50.00,
  "dateDebut": "2025-06-01T00:00:00",
  "dateFin": "2025-08-31T23:59:59",
  "usageMax": 500,
  "usageCount": 47,
  "isActive": true,
  "createdAt": "2025-05-20T09:00:00"
}
```

### `RestaurantDTO`

Champs utiles:

- `logoObjectName`
- `logoUrl`
- `openNow`
- `autoCloseEnabled`
- `heureOuverture`
- `heureFermeture`
- `distance`

### `PlatDTO`

Champs utiles:

- `prix`
- `currency`
- `currencySymbol`
- `imageObjectName`
- `imageUrl`
- `availabilityMode`
- `indisponibleJusqua`

---

## Swagger

- UI: `GET /swagger-ui.html`
- JSON: `GET /v3/api-docs`

Le bouton `Authorize` attend un JWT au format `Bearer <token>`.
