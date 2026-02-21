# 🍔 API Food Delivery - Architecture Complète

## 📦 Contenu du Package

Ce package contient toute l'architecture backend nécessaire pour votre application de livraison de plats avec Spring Boot, PostgreSQL et MinIO.

## 📂 Fichiers Inclus

### 🔧 Configuration
- `docker-compose.yml` - Configuration Docker (PostgreSQL + MinIO)
- `application.yml` - Configuration Spring Boot
- `MinioConfig.java` - Configuration MinIO pour le stockage de fichiers

### 🗃️ Modèles de Données (Entities)
- `User.java` - Utilisateurs (clients, livreurs, propriétaires)
- `UserRole.java` - Rôles utilisateurs (enum)
- `Restaurant.java` - Restaurants
- `CategorieRestaurant.java` - Catégories de restaurants
- `Plat.java` - Plats/Menu
- `CategoriePlat.java` - Catégories de plats (enum)
- `Commande.java` - Commandes
- `LigneCommande.java` - Détails des commandes
- `StatutCommande.java` - Statuts de commande (enum)
- `Promotion.java` - Promotions
- `Localisation.java` - Données de géolocalisation
- `MethodePaiement.java` - Méthodes de paiement (enum)
- `StatutPaiement.java` - Statuts de paiement (enum)

### 🎮 Controllers (API REST)
- `UserController.java` - Authentification et gestion utilisateurs
- `RestaurantController.java` - Gestion des restaurants
- `PlatController.java` - Gestion des plats
- `CommandeController.java` - Gestion des commandes
- `CategorieRestaurantController.java` - Gestion des catégories

### 📊 DTOs (Data Transfer Objects)
- `DTOs.java` - Tous les DTOs pour les requêtes/réponses API

### 🛠️ Services
- `MinioService.java` - Service de gestion des fichiers (upload/download)

### 📚 Documentation
- `API_DOCUMENTATION.md` - Documentation complète des endpoints API
- `DATABASE_SCHEMA.md` - Schéma de base de données avec scripts SQL
- `PROJECT_STRUCTURE.md` - Structure du projet Spring Boot
- `QUICK_START.md` - Guide de démarrage rapide
- `README.md` - Ce fichier

---

## 🎯 APIs Disponibles

### 🔐 Authentification
```
POST   /api/users/register          - Inscription
POST   /api/users/login             - Connexion
GET    /api/users/profile           - Profil utilisateur
PUT    /api/users/profile           - Mise à jour profil
PATCH  /api/users/location          - Mise à jour localisation
```

### 🏪 Catégories
```
GET    /api/categories              - Liste des catégories
GET    /api/categories/{id}         - Détails catégorie
POST   /api/categories              - Créer catégorie
PUT    /api/categories/{id}         - Modifier catégorie
DELETE /api/categories/{id}         - Supprimer catégorie
GET    /api/categories/{id}/restaurants - Restaurants par catégorie
```

### 🍽️ Restaurants
```
GET    /api/restaurants             - Liste restaurants (filtres disponibles)
GET    /api/restaurants/{id}        - Détails restaurant
GET    /api/restaurants/search      - Rechercher restaurants
GET    /api/restaurants/top-rated   - Meilleurs restaurants
GET    /api/restaurants/nearby      - Restaurants à proximité
POST   /api/restaurants             - Créer restaurant
PUT    /api/restaurants/{id}        - Modifier restaurant
DELETE /api/restaurants/{id}        - Supprimer restaurant
PATCH  /api/restaurants/{id}/activate - Activer/désactiver
```

### 🍕 Plats
```
GET    /api/plats                   - Liste plats (filtres disponibles)
GET    /api/plats/{id}              - Détails plat
GET    /api/plats/restaurant/{id}   - Plats d'un restaurant
GET    /api/plats/search            - Rechercher plats
POST   /api/plats                   - Créer plat
PUT    /api/plats/{id}              - Modifier plat
DELETE /api/plats/{id}              - Supprimer plat
PATCH  /api/plats/{id}/availability - Changer disponibilité
```

### 📦 Commandes
```
GET    /api/commandes               - Liste commandes (filtres)
GET    /api/commandes/{id}          - Détails commande
GET    /api/commandes/numero/{num}  - Par numéro
GET    /api/commandes/client/{id}   - Commandes d'un client
GET    /api/commandes/restaurant/{id} - Commandes d'un restaurant
GET    /api/commandes/livreur/{id}  - Commandes d'un livreur
GET    /api/commandes/en-cours      - Commandes actives
POST   /api/commandes               - Créer commande
PATCH  /api/commandes/{id}/status   - Changer statut
PATCH  /api/commandes/{id}/assign-livreur/{livreurId} - Assigner livreur
DELETE /api/commandes/{id}          - Annuler commande
GET    /api/commandes/{id}/tracking - Suivre commande
```

---

## 🚀 Démarrage Rapide

### 1. Démarrer Docker
```bash
docker-compose up -d
```

### 2. Vérifier MinIO
Accédez à http://localhost:9001 (minioadmin / minioadmin123)

### 3. Configurer l'application
- Copiez `application.yml` dans `src/main/resources/`
- Ajustez les paramètres si nécessaire

### 4. Créer la structure du projet
```bash
mkdir -p src/main/java/com/fooddelivery/{config,controller,dto,model,repository,service,security,exception,util}
```

### 5. Copier les fichiers
- Copiez tous les fichiers `.java` dans leurs packages respectifs
- Voir `PROJECT_STRUCTURE.md` pour la structure exacte

### 6. Lancer l'application
```bash
mvn spring-boot:run
```

L'API sera disponible sur `http://localhost:8080`

---

## 📊 Modèle de Données

### Entités Principales
- **Users** : Clients, livreurs, propriétaires, admins
- **Restaurants** : Infos restaurants avec localisation
- **Plats** : Menu des restaurants
- **Commandes** : Commandes avec lignes de détail
- **Catégories** : Organisation des restaurants

### Relations
- Un restaurant appartient à une catégorie
- Un restaurant a plusieurs plats
- Une commande référence un restaurant, un client, et (optionnel) un livreur
- Une commande contient plusieurs lignes de commande
- Chaque ligne référence un plat

---

## 🔒 Sécurité

### JWT Authentication
Toutes les routes (sauf `/register` et `/login`) nécessitent un token JWT:

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

### Rôles
- **CLIENT** : Commander des plats
- **LIVREUR** : Livrer les commandes
- **RESTAURANT_OWNER** : Gérer son restaurant
- **ADMIN** : Administration complète

---

## 📍 Fonctionnalités de Géolocalisation

### Calcul de Distance
- Distance entre utilisateur et restaurant
- Restaurants à proximité (rayon configurable)
- Livreurs disponibles dans une zone

### Données Stockées
- Latitude / Longitude
- Adresse complète
- Ville, code postal, pays

---

## 🖼️ Gestion des Fichiers (MinIO)

### Types de Fichiers Supportés
- Logos de restaurants
- Images de plats
- Photos de profil utilisateurs
- Images de catégories

### Buckets
- `restaurant-images` : Images restaurants et plats
- `food-delivery-files` : Fichiers généraux

### Opérations
- Upload avec multipart/form-data
- Génération d'URLs pré-signées (7 jours)
- Suppression de fichiers
- Téléchargement de fichiers

---

## 📝 Statuts de Commande

1. **NON_FINALISEE** : Panier non validé
2. **EN_ATTENTE** : Commande créée, en attente de confirmation
3. **CONFIRMEE** : Restaurant a confirmé
4. **EN_PREPARATION** : En cours de préparation
5. **EN_COURS** : En livraison
6. **LIVREE** : Commande livrée
7. **ANNULEE** : Commande annulée

---

## 🎨 Attributs des Entités (Selon vos Maquettes)

### ✅ Catégories de Restaurants
- ✅ image
- ✅ nom (subsahariens, marocains, maghrébins, orientaux)
- ✅ description (ajouté en plus)

### ✅ Restaurants
- ✅ nom
- ✅ description
- ✅ appréciation (note moyenne)
- ✅ nombreAvis (ajouté pour calculer la moyenne)
- ✅ tempsLivraisonMoyen (en minutes)
- ✅ distance (calculée dynamiquement)
- ✅ createdAt (date de création)
- ✅ logoUrl
- ✅ localisation complète
- ✅ promotion avec pourcentage et période

### ✅ Plats
- ✅ image
- ✅ nom
- ✅ description
- ✅ ingredients (liste)
- ✅ prix
- ✅ tempsPreparation (ajouté en plus)
- ✅ categoriePlat (ajouté en plus)

### ✅ Commandes
- ✅ statut (EN_COURS, LIVREE, NON_FINALISEE, etc.)
- ✅ restaurant_id
- ✅ client_id
- ✅ date (createdAt)
- ✅ lieuLivraison (adresse complète)
- ✅ livreur
- ✅ numeroCommande (ajouté pour tracking)
- ✅ montantTotal
- ✅ methodePaiement
- ✅ statutPaiement

### ✅ Localisation
- ✅ client (via User.localisation)
- ✅ livreur (via User.localisation)
- ✅ restaurant (via Restaurant.localisation)
- ✅ latitude/longitude pour chaque entité

---

## 🔨 À Implémenter Ensuite

### 1. Repositories (Priorité 1)
```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    List<User> findByRole(UserRole role);
}
```

### 2. Services Métier (Priorité 1)
- UserService / UserServiceImpl
- RestaurantService / RestaurantServiceImpl
- PlatService / PlatServiceImpl
- CommandeService / CommandeServiceImpl
- etc.

### 3. Spring Security + JWT (Priorité 1)
- JwtTokenProvider
- JwtAuthenticationFilter
- SecurityConfig
- CustomUserDetailsService

### 4. Exception Handling (Priorité 2)
- ResourceNotFoundException
- BadRequestException
- UnauthorizedException
- GlobalExceptionHandler

### 5. Utilitaires (Priorité 2)
- DistanceCalculator (formule Haversine)
- CommandeNumberGenerator
- Constants

### 6. Tests (Priorité 3)
- Tests unitaires des services
- Tests d'intégration des controllers
- Tests des repositories

---

## 📦 Dépendances Maven Requises

Voir le fichier `QUICK_START.md` pour la liste complète des dépendances à ajouter dans `pom.xml`.

Principales :
- spring-boot-starter-web
- spring-boot-starter-data-jpa
- spring-boot-starter-security
- spring-boot-starter-validation
- postgresql
- minio (8.5.7)
- jjwt (0.11.5)
- lombok

---

## 🌟 Fonctionnalités Avancées Possibles

### Court Terme
- ✅ Upload de fichiers (MinIO)
- ✅ Géolocalisation
- ⚠️ Authentification JWT
- ⚠️ Recherche et filtrage
- ⚠️ Pagination

### Moyen Terme
- 📊 Dashboard analytics
- ⭐ Système d'avis et de notes
- 🔔 Notifications (Firebase, email)
- 💳 Intégration paiement (Stripe, Wave)
- 🗺️ Tracking en temps réel (WebSocket)

### Long Terme
- 🎁 Programme de fidélité
- 🤖 Recommandations IA
- 📱 Application mobile (React Native / Flutter)
- 📧 Service emailing
- 🔐 OAuth2 (Google, Facebook)

---

## 📞 Support et Contact

Pour toute question ou problème :
1. Consultez `API_DOCUMENTATION.md`
2. Vérifiez `QUICK_START.md`
3. Consultez les logs Docker : `docker-compose logs -f`

---

## 📄 Licence

Ce projet est un template d'architecture pour votre application de livraison de plats.

---

**Bon développement ! 🚀**
