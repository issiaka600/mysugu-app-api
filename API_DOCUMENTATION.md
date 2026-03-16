# Documentation API

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

## 2. Categories

### GET `/api/categories`

Liste toutes les categories.

### GET `/api/categories/{id}`

Retourne une categorie par identifiant.

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

### GET `/api/categories/{id}/restaurants`

Liste les restaurants actifs de la categorie.

## 3. Restaurants

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

## 4. Plats

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

`multipart/form-data`:

- `image` fichier requis

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

## 5. Commandes

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
  "modeReception": "LIVRAISON"
}
```

Regles metier notables:

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

## 6. Fichiers

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
- `presignedUrl` alignee sur l'URL stable applicative
- `expiresIn=0`

### GET `/api/files/legacy/{bucket}/**`

Endpoint de compatibilite pour anciens chemins.

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

### `CommandeDTO`

Champs utiles:

- `trackingStatut`
- `modeReception`
- `raisonAnnulation`
- `currency`
- `currencySymbol`

## Swagger

- UI: `GET /swagger-ui.html`
- JSON: `GET /v3/api-docs`

Le bouton `Authorize` attend un JWT au format `Bearer <token>`.
