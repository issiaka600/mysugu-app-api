# 📚 Documentation API - Food Delivery Application

## Base URL
```
http://localhost:8080/api
```

---

## 🔐 AUTHENTICATION

### Register
```http
POST /api/users/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123",
  "nom": "Diop",
  "prenom": "Amadou",
  "telephone": "+221771234567",
  "role": "CLIENT"
}
```

### Login
```http
POST /api/users/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123"
}

Response:
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "user": { ... }
}
```

### Get Profile
```http
GET /api/users/profile
Authorization: Bearer {token}
```

### Update Profile
```http
PUT /api/users/profile
Authorization: Bearer {token}
Content-Type: multipart/form-data

nom=Diop&prenom=Amadou&telephone=+221771234567&avatar=[FILE]
```

### Update Location
```http
PATCH /api/users/location
Authorization: Bearer {token}
Content-Type: application/json

{
  "latitude": 14.6928,
  "longitude": -17.4467,
  "adresse": "Plateau, Dakar"
}
```

---

## 🏪 CATÉGORIES DE RESTAURANTS

### Get All Categories
```http
GET /api/categories
```

### Get Category by ID
```http
GET /api/categories/{id}
```

### Create Category
```http
POST /api/categories
Content-Type: multipart/form-data

nom=Subsahariens&description=Cuisine subsaharienne&image=[FILE]
```

### Update Category
```http
PUT /api/categories/{id}
Content-Type: multipart/form-data

nom=Subsahariens&description=Nouvelle description&image=[FILE]
```

### Delete Category
```http
DELETE /api/categories/{id}
```

### Get Restaurants by Category
```http
GET /api/categories/{id}/restaurants
```

---

## 🍽️ RESTAURANTS

### Get All Restaurants (with filters)
```http
GET /api/restaurants?categorieId=1&latitude=14.6928&longitude=-17.4467&maxDistance=5&page=0&size=10
```

### Get Restaurant by ID
```http
GET /api/restaurants/{id}
```

### Search Restaurants
```http
GET /api/restaurants/search?keyword=pizza
```

### Top Rated Restaurants
```http
GET /api/restaurants/top-rated?limit=10
```

### Nearby Restaurants
```http
GET /api/restaurants/nearby?latitude=14.6928&longitude=-17.4467&radiusKm=5
```

### Create Restaurant
```http
POST /api/restaurants
Content-Type: multipart/form-data

nom=Chez Fatou&description=Restaurant sénégalais&categorieId=1&ownerId=2&logo=[FILE]
&localisation.latitude=14.6928&localisation.longitude=-17.4467
&localisation.adresse=Plateau, Dakar&tempsLivraisonMoyen=30
```

### Update Restaurant
```http
PUT /api/restaurants/{id}
Content-Type: multipart/form-data

[Same as create]
```

### Delete Restaurant
```http
DELETE /api/restaurants/{id}
```

### Toggle Restaurant Status
```http
PATCH /api/restaurants/{id}/activate
```

### Get Restaurant's Plats
```http
GET /api/restaurants/{id}/plats
```

---

## 🍕 PLATS

### Get All Plats (with filters)
```http
GET /api/plats?restaurantId=1&categorie=PLAT_PRINCIPAL&available=true&page=0&size=10
```

### Get Plat by ID
```http
GET /api/plats/{id}
```

### Get Plats by Restaurant
```http
GET /api/plats/restaurant/{restaurantId}
```

### Search Plats
```http
GET /api/plats/search?keyword=thiéboudienne
```

### Create Plat
```http
POST /api/plats
Content-Type: multipart/form-data

nom=Thiéboudienne&description=Plat national&prix=5000&restaurantId=1
&categoriePlat=PLAT_PRINCIPAL&ingredients=riz&ingredients=poisson
&ingredients=légumes&tempsPreparation=45&image=[FILE]
```

### Update Plat
```http
PUT /api/plats/{id}
Content-Type: multipart/form-data

[Same as create]
```

### Delete Plat
```http
DELETE /api/plats/{id}
```

### Toggle Plat Availability
```http
PATCH /api/plats/{id}/availability
```

---

## 📦 COMMANDES

### Get All Commandes (with filters)
```http
GET /api/commandes?clientId=1&restaurantId=2&statut=EN_COURS&page=0&size=10
```

### Get Commande by ID
```http
GET /api/commandes/{id}
```

### Get Commande by Number
```http
GET /api/commandes/numero/{numeroCommande}
```

### Get Client's Commandes
```http
GET /api/commandes/client/{clientId}
```

### Get Restaurant's Commandes
```http
GET /api/commandes/restaurant/{restaurantId}
```

### Get Livreur's Commandes
```http
GET /api/commandes/livreur/{livreurId}
```

### Get Active Commandes
```http
GET /api/commandes/en-cours
```

### Create Commande
```http
POST /api/commandes
Content-Type: application/json

{
  "clientId": 1,
  "restaurantId": 2,
  "lignes": [
    {
      "platId": 5,
      "quantite": 2,
      "remarque": "Sans piment"
    },
    {
      "platId": 8,
      "quantite": 1
    }
  ],
  "adresseLivraison": {
    "latitude": 14.6928,
    "longitude": -17.4467,
    "adresse": "Plateau, Avenue Pompidou",
    "ville": "Dakar",
    "codePostal": "10000",
    "pays": "Sénégal"
  },
  "commentaire": "Livraison urgente",
  "methodePaiement": "MOBILE_MONEY"
}
```

### Update Commande Status
```http
PATCH /api/commandes/{id}/status
Content-Type: application/json

{
  "statut": "EN_PREPARATION"
}
```

### Assign Livreur
```http
PATCH /api/commandes/{id}/assign-livreur/{livreurId}
```

### Cancel Commande
```http
DELETE /api/commandes/{id}
```

### Track Commande
```http
GET /api/commandes/{id}/tracking
```

---

## 🚚 LIVREURS

### Get Available Livreurs
```http
GET /api/users/livreurs/disponibles?latitude=14.6928&longitude=-17.4467&radiusKm=10
```

---

## 📊 ENUMERATIONS

### UserRole
- `CLIENT`
- `LIVREUR`
- `RESTAURANT_OWNER`
- `ADMIN`

### StatutCommande
- `EN_ATTENTE` - Commande créée
- `CONFIRMEE` - Confirmée par le restaurant
- `EN_PREPARATION` - En préparation
- `EN_COURS` - En livraison
- `LIVREE` - Livrée
- `ANNULEE` - Annulée
- `NON_FINALISEE` - Panier non finalisé

### MethodePaiement
- `CARTE_BANCAIRE`
- `ESPECES`
- `MOBILE_MONEY`
- `PAYPAL`

### StatutPaiement
- `EN_ATTENTE`
- `PAYE`
- `REMBOURSE`
- `ECHOUE`

### CategoriePlat
- `ENTREE`
- `PLAT_PRINCIPAL`
- `DESSERT`
- `BOISSON`
- `ACCOMPAGNEMENT`

---

## 🔒 SÉCURITÉ

Toutes les routes (sauf `/register` et `/login`) nécessitent un token JWT:

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

---

## 📝 PAGINATION

La plupart des endpoints supportent la pagination:

```http
GET /api/restaurants?page=0&size=10&sort=nom,asc
```

Paramètres:
- `page`: Numéro de page (commence à 0)
- `size`: Nombre d'éléments par page
- `sort`: Tri (format: `champ,direction`)

---

## 🖼️ UPLOAD DE FICHIERS

Les fichiers sont stockés dans MinIO. Formats acceptés:
- Images: JPG, PNG, WEBP (max 10MB)

Buckets MinIO:
- `food-delivery-files`: Fichiers généraux
- `restaurant-images`: Images des restaurants et plats

---

## 🌍 LOCALISATION

Format des coordonnées:
```json
{
  "latitude": 14.6928,
  "longitude": -17.4467,
  "adresse": "Plateau, Avenue Pompidou",
  "ville": "Dakar",
  "codePostal": "10000",
  "pays": "Sénégal"
}
```

---

## 🚀 DÉMARRAGE RAPIDE

1. **Démarrer Docker**
```bash
docker-compose up -d
```

2. **Accéder à MinIO Console**
```
http://localhost:9001
Login: minioadmin / minioadmin123
```

3. **Tester l'API**
```bash
curl http://localhost:8080/api/categories
```

---

## 📦 DÉPENDANCES MAVEN

```xml
<dependencies>
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- JPA / Hibernate -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    
    <!-- PostgreSQL -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>
    
    <!-- MinIO -->
    <dependency>
        <groupId>io.minio</groupId>
        <artifactId>minio</artifactId>
        <version>8.5.7</version>
    </dependency>
    
    <!-- Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    
    <!-- Security (JWT) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.11.5</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.11.5</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.11.5</version>
    </dependency>
    
    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```
