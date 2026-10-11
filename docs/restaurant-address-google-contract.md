# Adresse restaurant, devis et Google Routes

## Portée

Seul le flux de localisation **restaurant vendeur** est modifié. Les routes et validations des adresses client (`/api/users/adresses`) restent inchangées. Le devis et la commande relisent toujours le point client enregistré appartenant au client connecté.

## Configuration serveur

Configurer `GOOGLE_MAPS_API_KEY` (ou deux clés `GOOGLE_GEOCODING_API_KEY` et `GOOGLE_ROUTES_API_KEY`) dans l'environnement du backend. Activer Geocoding API et Routes API, avec facturation et restrictions adaptées à une clé serveur. Aucun secret ne doit être intégré aux applications ou au dépôt. Le délai maximal d'un appel est `GOOGLE_MAPS_TIMEOUT_MS`, 5000 ms par défaut.

L'absence de clé, un refus fournisseur, un quota épuisé, une erreur réseau, un délai dépassé ou une réponse inexploitable renvoient 503. Aucune distance à vol d'oiseau ne remplace un échec Google Routes.

Références d'implémentation : [Geocoding](https://developers.google.com/maps/documentation/geocoding/guides-v3/requests-geocoding), [Routes computeRoutes](https://developers.google.com/maps/documentation/routes/compute_route_directions).

## Enregistrement vendeur

`POST /api/v3/seller/shop-update`, authentification vendeur, multipart/form-data.

### Adresse seule

```text
address=12 Rue précise, Marrakech, Maroc
```

Le backend géocode chaque adresse fournie sans coordonnées, même si la fiche dispose déjà d'un GPS. Il accepte automatiquement un unique résultat, sans `partial_match`, de type bâtiment (`street_address`, `premise`, `subpremise` ou `establishment`) avec précision `ROOFTOP` et coordonnées terrestres finies. Le libellé envoyé est conservé, après retrait des espaces extérieurs. Une réponse interpolée, approximative ou centrée sur une zone exige la carte.

### Recours à la carte

```text
address=Entrée choisie sur la carte
latitude=31.600000
longitude=-8.020000
location_confirmed=true
```

Ces coordonnées illustrent uniquement un payload ; elles ne localisent aucun restaurant réel. Les quatre champs sont requis ensemble pour une sélection carte. Les bornes sont latitude [-90, 90], longitude [-180, 180], valeurs finies.

Validation et géocodage précèdent toute écriture de la fiche ou des médias. Adresse, coordonnées et date de validation sont sauvegardées dans une transaction. Un échec conserve la fiche précédente, y compris nom, description, temps de livraison, horaires, zone et date de validation. Une modification sans champs de localisation conserve le point et son état. Une nouvelle adresse ne réutilise jamais implicitement les anciennes coordonnées.

Succès : HTTP 200, même objet shop qu'auparavant, avec notamment :

```json
{
  "id": 16,
  "name": "Restaurant exemple",
  "address": "Entrée choisie sur la carte",
  "latitude": 31.6,
  "longitude": -8.02,
  "location_confirmed": true,
  "location_confirmed_at": "2026-10-11T13:00:00"
}
```

L'objet conserve ses autres champs existants. `GET /api/v3/seller/shop-info` expose les mêmes champs de localisation.

Adresse ambiguë, introuvable, vide, coordonnées incomplètes/invalides ou choix carte non confirmé : HTTP 422.

```json
{
  "code": "RESTAURANT_LOCATION_REQUIRED",
  "message": "Précisez l’adresse ou choisissez le point sur la carte."
}
```

Service indisponible : HTTP 503 et en-tête `Retry-After: 5`.

```json
{
  "code": "GEOCODING_UNAVAILABLE",
  "message": "La recherche d’adresse est indisponible. Réessayez.",
  "retryable": true
}
```

Un paramètre multipart impossible à convertir (par exemple latitude="abc") reste une erreur de saisie HTTP 400 de Spring ; la fiche n'est pas écrite.

## Point livreur

`GET /api/v2/delivery-man/seller-location?order_id={id}` exige une commande assignée au livreur connecté. `order_id` absent : 400 ; autre livreur : 403 ; coordonnées absentes/invalides : 409. Un point non validé est refusé pour le guidage, avec 409 et code `RESTAURANT_LOCATION_REQUIRED`.

Succès HTTP 200 :

```json
{
  "latitude": "31.6",
  "longitude": "-8.02",
  "address": "Entrée choisie sur la carte",
  "location_confirmed": true,
  "location_confirmed_at": "2026-10-11T13:00:00"
}
```

Les commandes natives exposent `restaurant.localisation`, `restaurant.locationConfirmed` et `restaurant.locationConfirmedAt`. Les commandes livreur conservent `seller.shop` et y ajoutent `location_confirmed` et `location_confirmed_at`. Le chat conserve `seller_info.shop` et son alias `shops`. Tous relisent le point courant enregistré du restaurant, même pour une commande préexistante. Les anciens points restent non validés jusqu'à une nouvelle validation ; les applications doivent tenir compte de cet état.

## Devis panier

`POST /api/commandes/devis`, JSON, session client. `clientId` est déduit du JWT s'il est absent ; s'il est fourni, il doit correspondre au client connecté (sinon 403).

```json
{
  "restaurantId": 16,
  "modeReception": "LIVRAISON",
  "adresseLivraison": {"id": 42},
  "lignes": [{"platId": 8, "quantite": 2, "optionItemIds": [3]}],
  "codePromo": "EXEMPLE"
}
```

Formats équivalents pour la destination : `adresseLivraisonId: 42` (contrat existant), `adresseLivraison: {"id": 42}`, ou l'objet client existant avec `adresse`, `latitude`, `longitude` exactement égaux à une adresse enregistrée du client. Les coordonnées brutes ne créent/modifient aucune adresse. Avec un identifiant, les champs bruts supplémentaires sont ignorés au profit des valeurs enregistrées. Une adresse d'un autre client est refusée (404).

Le devis vérifie le panier, reconstruit les prix des plats et options et lit les promotions côté serveur. Il appelle Google Routes **une fois**, du point restaurant validé au point client existant validé, en mode DRIVE. `distanceKm` est la distance en mètres divisée par 1000, sans arrondi préalable à la tarification.

Exemple pour sous-total 90 DH, distance routière 5,251 km, tarif global 10 DH + 4 DH/km, promotion restaurant de 10 % et coupon fixe 5 DH : HTTP 200.

```json
{
  "sousTotal": 90,
  "fraisLivraison": 32,
  "montantTotal": 122,
  "montantRemise": 14,
  "montantFinal": 108,
  "remisePromotion": 9,
  "distanceKm": 5.251,
  "distanceMethod": "GOOGLE_ROUTES",
  "detailFrais": {
    "distanceKm": 5.251,
    "tarifBase": 10,
    "fraisDistance": 22,
    "fraisLivraison": 32
  }
}
```

Montants en DH. `montantTotal = sousTotal + fraisLivraison`. `montantFinal = montantTotal - montantRemise`. `remisePromotion` est la remise automatique restaurant ; `montantRemise` inclut aussi le coupon, plafonné au sous-total.

Grille inchangée, avec la distance routière :

- Si `prixParKm` et minimum sont configurés : `max(distanceKm × prixParKm, minimum)`, arrondi au DH supérieur.
- Sinon, grille avec seuil : minimum jusqu'à `distanceMinKm`, puis minimum + kilomètres supplémentaires × `prixExtraParKm`, arrondi au DH supérieur au-delà du seuil.
- Sinon : constantes globales 10 DH + 4 DH/km, arrondi au DH supérieur, avec plancher de zone si renseigné.

`tarifBase` représente le minimum/base applicable ; `fraisDistance` représente le supplément effectif après application du minimum et de l'arrondi. Leur somme égale `fraisLivraison`.

Aucune commande, réservation de stock ni utilisation de promotion n'est créée par le devis. `POST /api/commandes` accepte les mêmes références de panier et recalcule avec les mêmes fonctions, une nouvelle distance Google Routes et les prix courants. Des prix ou itinéraires modifiés entre les deux appels peuvent modifier le total.

Indisponibilité Google Routes : HTTP 503, `Retry-After: 5`.

```json
{
  "code": "GOOGLE_ROUTES_UNAVAILABLE",
  "message": "Le calcul de l’itinéraire est indisponible. Réessayez.",
  "retryable": true
}
```

Un échec à la création annule la transaction, notamment la réservation du stock. Le retrait sur place ne nécessite aucun appel Routes et conserve des frais de livraison nuls.

`GET /api/commandes/devis?restaurantId={id}&adresseLivraisonId={id}&sousTotal={montant}` reste disponible pour compatibilité ; le POST est le contrat officiel qui recalcule le panier depuis les identifiants.

## Vérification

Les tests fournisseur utilisent un serveur HTTP local pour vérifier les requêtes réelles et les réponses Google simulées. Les tests d'intégration utilisent un PostgreSQL isolé et des réponses fournisseur simulées ; ils ne touchent pas la base de production. La validation réelle des API Google nécessite la clé serveur configurée.
