# 📁 Structure du Projet Spring Boot

```
food-delivery-backend/
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── fooddelivery/
│   │   │           ├── FoodDeliveryApplication.java
│   │   │           │
│   │   │           ├── config/
│   │   │           │   ├── MinioConfig.java
│   │   │           │   ├── SecurityConfig.java
│   │   │           │   ├── JwtConfig.java
│   │   │           │   └── WebConfig.java
│   │   │           │
│   │   │           ├── controller/
│   │   │           │   ├── UserController.java
│   │   │           │   ├── RestaurantController.java
│   │   │           │   ├── PlatController.java
│   │   │           │   ├── CommandeController.java
│   │   │           │   └── CategorieRestaurantController.java
│   │   │           │
│   │   │           ├── dto/
│   │   │           │   ├── UserDTO.java
│   │   │           │   ├── RegisterDTO.java
│   │   │           │   ├── LoginDTO.java
│   │   │           │   ├── LoginResponseDTO.java
│   │   │           │   ├── RestaurantDTO.java
│   │   │           │   ├── RestaurantCreateDTO.java
│   │   │           │   ├── PlatDTO.java
│   │   │           │   ├── PlatCreateDTO.java
│   │   │           │   ├── CommandeDTO.java
│   │   │           │   ├── CommandeCreateDTO.java
│   │   │           │   ├── LigneCommandeDTO.java
│   │   │           │   ├── CategorieRestaurantDTO.java
│   │   │           │   ├── PromotionDTO.java
│   │   │           │   └── LocalisationDTO.java
│   │   │           │
│   │   │           ├── model/
│   │   │           │   ├── User.java
│   │   │           │   ├── UserRole.java (enum)
│   │   │           │   ├── Restaurant.java
│   │   │           │   ├── CategorieRestaurant.java
│   │   │           │   ├── Plat.java
│   │   │           │   ├── CategoriePlat.java (enum)
│   │   │           │   ├── Commande.java
│   │   │           │   ├── LigneCommande.java
│   │   │           │   ├── StatutCommande.java (enum)
│   │   │           │   ├── Promotion.java
│   │   │           │   ├── Localisation.java (Embeddable)
│   │   │           │   ├── MethodePaiement.java (enum)
│   │   │           │   └── StatutPaiement.java (enum)
│   │   │           │
│   │   │           ├── repository/
│   │   │           │   ├── UserRepository.java
│   │   │           │   ├── RestaurantRepository.java
│   │   │           │   ├── PlatRepository.java
│   │   │           │   ├── CommandeRepository.java
│   │   │           │   ├── LigneCommandeRepository.java
│   │   │           │   ├── CategorieRestaurantRepository.java
│   │   │           │   └── PromotionRepository.java
│   │   │           │
│   │   │           ├── service/
│   │   │           │   ├── UserService.java
│   │   │           │   ├── UserServiceImpl.java
│   │   │           │   ├── RestaurantService.java
│   │   │           │   ├── RestaurantServiceImpl.java
│   │   │           │   ├── PlatService.java
│   │   │           │   ├── PlatServiceImpl.java
│   │   │           │   ├── CommandeService.java
│   │   │           │   ├── CommandeServiceImpl.java
│   │   │           │   ├── CategorieRestaurantService.java
│   │   │           │   ├── CategorieRestaurantServiceImpl.java
│   │   │           │   ├── MinioService.java
│   │   │           │   ├── JwtService.java
│   │   │           │   └── LocationService.java
│   │   │           │
│   │   │           ├── security/
│   │   │           │   ├── JwtAuthenticationFilter.java
│   │   │           │   ├── JwtTokenProvider.java
│   │   │           │   └── CustomUserDetailsService.java
│   │   │           │
│   │   │           ├── exception/
│   │   │           │   ├── ResourceNotFoundException.java
│   │   │           │   ├── BadRequestException.java
│   │   │           │   ├── UnauthorizedException.java
│   │   │           │   └── GlobalExceptionHandler.java
│   │   │           │
│   │   │           └── util/
│   │   │               ├── DistanceCalculator.java
│   │   │               ├── CommandeNumberGenerator.java
│   │   │               └── Constants.java
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       └── data.sql (optional - données initiales)
│   │
│   └── test/
│       └── java/
│           └── com/
│               └── fooddelivery/
│                   ├── controller/
│                   ├── service/
│                   └── repository/
│
├── docker-compose.yml
├── pom.xml
├── .gitignore
└── README.md
```

## 📋 Fichiers Clés

### pom.xml
Gère les dépendances Maven (Spring Boot, PostgreSQL, MinIO, JWT, etc.)

### application.yml
Configuration principale (base de données, MinIO, JWT)

### docker-compose.yml
Configuration Docker pour PostgreSQL et MinIO

### FoodDeliveryApplication.java
Point d'entrée de l'application Spring Boot

## 🗂️ Packages Principaux

### config/
Configuration Spring (Security, MinIO, CORS, etc.)

### controller/
Endpoints REST API

### dto/
Data Transfer Objects pour les requêtes/réponses API

### model/
Entités JPA (mappées aux tables PostgreSQL)

### repository/
Interfaces JPA Repository

### service/
Logique métier

### security/
Gestion de l'authentification JWT

### exception/
Gestion centralisée des erreurs

### util/
Classes utilitaires (calcul de distance, génération de numéros, etc.)

## 🔧 Configuration

### Variables d'environnement recommandées:
```env
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=food_delivery
DB_USER=fooduser
DB_PASSWORD=foodpass123

# MinIO
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin123

# JWT
JWT_SECRET=your-secret-key-here
JWT_EXPIRATION=86400000

# Server
SERVER_PORT=8080
```
