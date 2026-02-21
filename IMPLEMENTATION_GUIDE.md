# 🎯 Guide d'Implémentation - Services, Security & Exception Handling

## 📦 Fichiers Créés

### ✅ SERVICES MÉTIER (10 fichiers)

#### Services Interfaces
1. **UserService.java** - Interface pour la gestion des utilisateurs
2. **RestaurantService.java** - Interface pour les restaurants
3. **PlatService.java** - Interface pour les plats
4. **CommandeService.java** - Interface pour les commandes
5. **CategorieRestaurantService.java** - Interface pour les catégories

#### Services Implémentations
6. **UserServiceImpl.java** - Logique métier utilisateurs (register, login, profile, etc.)
7. **RestaurantServiceImpl.java** - Logique métier restaurants (CRUD, recherche, proximité)
8. **PlatServiceImpl.java** - Logique métier plats (CRUD, disponibilité)
9. **CommandeServiceImpl.java** - Logique métier commandes (création, tracking, statuts)
10. **CategorieRestaurantServiceImpl.java** - Logique métier catégories

### ✅ SPRING SECURITY + JWT (4 fichiers)

11. **JwtTokenProvider.java** - Génération et validation des tokens JWT
12. **JwtAuthenticationFilter.java** - Filtre pour intercepter les requêtes et valider les tokens
13. **CustomUserDetailsService.java** - Service pour charger les utilisateurs
14. **SecurityConfig.java** - Configuration Spring Security complète avec règles d'autorisation

### ✅ EXCEPTION HANDLING (4 fichiers)

15. **ResourceNotFoundException.java** - Exception pour ressources non trouvées (404)
16. **BadRequestException.java** - Exception pour requêtes invalides (400)
17. **UnauthorizedException.java** - Exception pour problèmes d'authentification (401)
18. **GlobalExceptionHandler.java** - Gestionnaire global des exceptions avec réponses standardisées

### ✅ UTILITAIRES (3 fichiers)

19. **CommandeNumberGenerator.java** - Génération de numéros de commande uniques
20. **DistanceCalculator.java** - Calculs de distance géographique (Haversine)
21. **Constants.java** - Constantes de l'application

### ✅ CONFIGURATION (2 fichiers)

22. **WebConfig.java** - Configuration CORS et Web
23. **MinioInitializer.java** - Initialisation automatique des buckets MinIO

---

## 📁 Structure des Packages

```
src/main/java/com/fooddelivery/
│
├── config/
│   ├── MinioConfig.java (déjà créé)
│   ├── SecurityConfig.java ✅ NOUVEAU
│   ├── WebConfig.java ✅ NOUVEAU
│   └── MinioInitializer.java ✅ NOUVEAU
│
├── controller/
│   ├── UserController.java (déjà créé)
│   ├── RestaurantController.java (déjà créé)
│   ├── PlatController.java (déjà créé)
│   ├── CommandeController.java (déjà créé)
│   └── CategorieRestaurantController.java (déjà créé)
│
├── dto/
│   └── DTOs.java (déjà créé)
│
├── exception/
│   ├── ResourceNotFoundException.java ✅ NOUVEAU
│   ├── BadRequestException.java ✅ NOUVEAU
│   ├── UnauthorizedException.java ✅ NOUVEAU
│   └── GlobalExceptionHandler.java ✅ NOUVEAU
│
├── model/
│   └── (tous les modèles déjà créés)
│
├── repository/
│   └── (tous les repositories JPA - créés par vous)
│
├── security/
│   ├── JwtTokenProvider.java ✅ NOUVEAU
│   ├── JwtAuthenticationFilter.java ✅ NOUVEAU
│   └── CustomUserDetailsService.java ✅ NOUVEAU
│
├── service/
│   ├── MinioService.java (déjà créé)
│   ├── UserService.java ✅ NOUVEAU
│   ├── UserServiceImpl.java ✅ NOUVEAU
│   ├── RestaurantService.java ✅ NOUVEAU
│   ├── RestaurantServiceImpl.java ✅ NOUVEAU
│   ├── PlatService.java ✅ NOUVEAU
│   ├── PlatServiceImpl.java ✅ NOUVEAU
│   ├── CommandeService.java ✅ NOUVEAU
│   ├── CommandeServiceImpl.java ✅ NOUVEAU
│   ├── CategorieRestaurantService.java ✅ NOUVEAU
│   └── CategorieRestaurantServiceImpl.java ✅ NOUVEAU
│
└── util/
    ├── CommandeNumberGenerator.java ✅ NOUVEAU
    ├── DistanceCalculator.java ✅ NOUVEAU
    └── Constants.java ✅ NOUVEAU
```

---

## 🔧 Mise en Place

### Étape 1: Copier les Fichiers

Copiez tous les fichiers Java dans leurs packages respectifs selon la structure ci-dessus.

### Étape 2: Vérifier les Repositories

Assurez-vous que vos repositories JPA ont les méthodes suivantes :

**UserRepository.java**
```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    List<User> findByRoleAndIsActive(UserRole role, Boolean isActive);
    List<User> findByRole(UserRole role);
}
```

**RestaurantRepository.java**
```java
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
    Page<Restaurant> findByIsActive(Boolean isActive, Pageable pageable);
    Page<Restaurant> findByCategorieIdAndIsActive(Long categorieId, Boolean isActive, Pageable pageable);
    List<Restaurant> findByIsActiveOrderByAppreciationDesc(Boolean isActive);
    List<Restaurant> findByIsActive(Boolean isActive);
    
    @Query("SELECT r FROM Restaurant r WHERE r.isActive = true AND " +
           "(LOWER(r.nom) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(r.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Restaurant> searchByKeyword(@Param("keyword") String keyword);
}
```

**PlatRepository.java**
```java
public interface PlatRepository extends JpaRepository<Plat, Long> {
    Page<Plat> findByRestaurantId(Long restaurantId, Pageable pageable);
    List<Plat> findByRestaurantIdAndIsAvailable(Long restaurantId, Boolean isAvailable);
    Page<Plat> findByIsAvailable(Boolean isAvailable, Pageable pageable);
    Page<Plat> findByRestaurantIdAndCategoriePlatAndIsAvailable(
        Long restaurantId, CategoriePlat categoriePlat, Boolean isAvailable, Pageable pageable);
    
    @Query("SELECT p FROM Plat p WHERE " +
           "(LOWER(p.nom) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Plat> searchByKeyword(@Param("keyword") String keyword);
}
```

**CommandeRepository.java**
```java
public interface CommandeRepository extends JpaRepository<Commande, Long> {
    Optional<Commande> findByNumeroCommande(String numeroCommande);
    Page<Commande> findByClientId(Long clientId, Pageable pageable);
    Page<Commande> findByRestaurantId(Long restaurantId, Pageable pageable);
    Page<Commande> findByStatut(StatutCommande statut, Pageable pageable);
    Page<Commande> findByClientIdAndStatut(Long clientId, StatutCommande statut, Pageable pageable);
    Page<Commande> findByRestaurantIdAndStatut(Long restaurantId, StatutCommande statut, Pageable pageable);
    List<Commande> findByClientIdOrderByCreatedAtDesc(Long clientId);
    List<Commande> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
    List<Commande> findByLivreurIdOrderByCreatedAtDesc(Long livreurId);
    List<Commande> findByStatutInOrderByCreatedAtDesc(List<StatutCommande> statuts);
}
```

**CategorieRestaurantRepository.java**
```java
public interface CategorieRestaurantRepository extends JpaRepository<CategorieRestaurant, Long> {
    Optional<CategorieRestaurant> findByNom(String nom);
}
```

**LigneCommandeRepository.java**
```java
public interface LigneCommandeRepository extends JpaRepository<LigneCommande, Long> {
    // Méthodes de base suffisantes
}
```

### Étape 3: Vérifier application.yml

Assurez-vous que votre `application.yml` contient :

```yaml
jwt:
  secret: votre-super-secret-jwt-key-changez-ceci-en-production-avec-une-cle-longue-et-securisee
  expiration: 86400000 # 24 heures en millisecondes
```

⚠️ **IMPORTANT**: En production, utilisez une clé secrète longue et sécurisée (minimum 256 bits).

---

## 🔐 Authentification JWT

### Comment ça marche ?

1. **Inscription** : `POST /api/users/register`
   - L'utilisateur s'inscrit avec email/password
   - Le mot de passe est hashé avec BCrypt
   - Un compte est créé dans la base de données

2. **Connexion** : `POST /api/users/login`
   - L'utilisateur envoie email/password
   - Le système vérifie les credentials
   - Un token JWT est généré et retourné

3. **Requêtes authentifiées** : 
   - L'utilisateur envoie le token dans l'en-tête : `Authorization: Bearer {token}`
   - Le `JwtAuthenticationFilter` intercepte la requête
   - Le token est validé
   - L'utilisateur est authentifié dans le contexte Spring Security

### Exemple de flux complet

```bash
# 1. Inscription
curl -X POST http://localhost:8080/api/users/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "client@test.com",
    "password": "password123",
    "nom": "Diop",
    "prenom": "Amadou",
    "telephone": "+221771234567",
    "role": "CLIENT"
  }'

# 2. Connexion
curl -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "client@test.com",
    "password": "password123"
  }'

# Réponse:
# {
#   "token": "eyJhbGciOiJIUzUxMiJ9...",
#   "user": { ... }
# }

# 3. Utiliser le token pour une requête authentifiée
curl -X GET http://localhost:8080/api/users/profile \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9..."
```

---

## 🛡️ Règles d'Autorisation

### Routes Publiques (pas d'authentification)
- `POST /api/users/register` - Inscription
- `POST /api/users/login` - Connexion
- `GET /api/categories/**` - Toutes les catégories
- `GET /api/restaurants/**` - Tous les restaurants
- `GET /api/plats/**` - Tous les plats

### Routes CLIENT
- `POST /api/commandes` - Créer une commande
- `GET /api/commandes/client/{id}` - Voir ses commandes
- `GET /api/users/profile` - Voir son profil
- `PUT /api/users/profile` - Modifier son profil

### Routes RESTAURANT_OWNER
- `POST /api/restaurants` - Créer un restaurant
- `PUT /api/restaurants/**` - Modifier ses restaurants
- `POST /api/plats` - Créer des plats
- `PUT /api/plats/**` - Modifier ses plats
- `GET /api/commandes/restaurant/{id}` - Voir commandes du restaurant
- `PATCH /api/commandes/**/status` - Changer statut commande

### Routes LIVREUR
- `GET /api/commandes/livreur/{id}` - Voir ses livraisons
- `PATCH /api/commandes/**/status` - Mettre à jour statut livraison

### Routes ADMIN
- Accès à toutes les routes
- `POST /api/categories` - Créer catégories
- `PUT /api/categories/**` - Modifier catégories
- `DELETE /api/categories/**` - Supprimer catégories

---

## 🚨 Gestion des Erreurs

Le `GlobalExceptionHandler` gère automatiquement toutes les erreurs et retourne des réponses standardisées :

### Exemple de réponse d'erreur

```json
{
  "timestamp": "2026-02-16T14:30:52",
  "status": 404,
  "error": "Not Found",
  "message": "Restaurant non trouvé avec l'ID: 123",
  "path": "/api/restaurants/123"
}
```

### Types d'erreurs gérées

- **404 Not Found** : Ressource non trouvée
- **400 Bad Request** : Requête invalide, validation échouée
- **401 Unauthorized** : Authentification requise ou token invalide
- **403 Forbidden** : Permissions insuffisantes
- **413 Payload Too Large** : Fichier trop volumineux
- **500 Internal Server Error** : Erreur serveur

---

## 🧪 Tests Recommandés

### Test 1: Authentification
```bash
# Register → Login → Get Profile
```

### Test 2: Création de Restaurant
```bash
# Login as RESTAURANT_OWNER → Create Restaurant → Upload Logo
```

### Test 3: Commande Complète
```bash
# Login as CLIENT → Create Commande → Check Status → Track
```

### Test 4: Workflow Livraison
```bash
# Restaurant confirms → Assign Livreur → Update Status → Delivered
```

---

## 📊 Métriques et Fonctionnalités

### Services Implémentés
✅ Authentification JWT complète
✅ Upload de fichiers (MinIO)
✅ Calcul de distance géographique
✅ Génération de numéros de commande
✅ Gestion des statuts de commande
✅ Tracking de livraison
✅ Calcul automatique des frais de livraison
✅ Validation des transitions de statut
✅ Recherche et filtrage
✅ Pagination

### Sécurité
✅ Hashing des mots de passe (BCrypt)
✅ Tokens JWT avec expiration
✅ Validation des rôles
✅ Protection CSRF
✅ CORS configuré
✅ Routes protégées par rôle

---

## 🎯 Prochaines Étapes Recommandées

1. **Tests unitaires** : Créer des tests pour chaque service
2. **Tests d'intégration** : Tester les flows complets
3. **Documentation Swagger** : Ajouter OpenAPI/Swagger
4. **Logging avancé** : Améliorer les logs avec Logback
5. **Monitoring** : Ajouter Spring Actuator
6. **Rate Limiting** : Limiter les requêtes par IP
7. **Email Service** : Notifications par email
8. **WebSocket** : Tracking en temps réel
9. **Cache** : Implémenter Redis pour le cache
10. **CI/CD** : Pipeline de déploiement

---

## 🐛 Troubleshooting

### Problème : Token invalide
**Solution** : Vérifiez que le secret JWT dans `application.yml` est bien configuré et identique partout.

### Problème : 403 Forbidden
**Solution** : Vérifiez que l'utilisateur a le bon rôle pour accéder à la route.

### Problème : Files upload échoue
**Solution** : Vérifiez que MinIO est démarré et que les buckets sont créés.

### Problème : Distance calculation incorrecte
**Solution** : Vérifiez que les coordonnées GPS sont au bon format (latitude/longitude).

---

**✅ Votre backend est maintenant complet et prêt pour le développement !**
