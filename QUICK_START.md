# 🚀 Guide de Démarrage Rapide

## Prérequis

- ✅ Java 17 ou supérieur
- ✅ Maven 3.6+
- ✅ Docker & Docker Compose
- ✅ IDE (IntelliJ IDEA, Eclipse, VS Code)

## Étape 1: Cloner et Initialiser le Projet

```bash
# Si vous n'avez pas encore créé le projet Spring Boot
spring init --dependencies=web,data-jpa,postgresql,lombok,validation,security \
  --group-id=com.fooddelivery \
  --artifact-id=food-delivery-backend \
  --name=FoodDeliveryAPI \
  food-delivery-backend

cd food-delivery-backend
```

## Étape 2: Configurer les Dépendances Maven

Ajouter dans `pom.xml`:

```xml
<dependencies>
    <!-- Dépendances de base Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    
    <!-- PostgreSQL -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    
    <!-- MinIO Client -->
    <dependency>
        <groupId>io.minio</groupId>
        <artifactId>minio</artifactId>
        <version>8.5.7</version>
    </dependency>
    
    <!-- JWT -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.11.5</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.11.5</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.11.5</version>
        <scope>runtime</scope>
    </dependency>
    
    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    
    <!-- Test -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Étape 3: Démarrer Docker (PostgreSQL + MinIO)

```bash
# Créer le fichier docker-compose.yml (voir fichier fourni)
docker-compose up -d

# Vérifier que les conteneurs sont démarrés
docker ps

# Logs des conteneurs
docker-compose logs -f
```

## Étape 4: Configurer l'Application

Créer `src/main/resources/application.yml` (voir fichier fourni)

## Étape 5: Créer la Structure des Packages

```bash
mkdir -p src/main/java/com/fooddelivery/{config,controller,dto,model,repository,service,security,exception,util}
```

## Étape 6: Copier les Fichiers

Copier tous les fichiers Java fournis dans leurs packages respectifs:

- **model/** : User.java, Restaurant.java, Plat.java, Commande.java, etc.
- **controller/** : UserController.java, RestaurantController.java, etc.
- **config/** : MinioConfig.java
- **service/** : MinioService.java, etc.
- **dto/** : DTOs.java (séparer ensuite en fichiers individuels si nécessaire)

## Étape 7: Initialiser MinIO

Accéder à MinIO Console:
```
http://localhost:9001
Login: minioadmin
Password: minioadmin123
```

Créer les buckets:
1. `food-delivery-files`
2. `restaurant-images`

Ou laisser l'application les créer automatiquement au démarrage.

## Étape 8: Créer un Bean d'Initialisation MinIO

```java
@Component
@RequiredArgsConstructor
public class MinioInitializer {
    
    private final MinioService minioService;
    
    @PostConstruct
    public void init() {
        minioService.initBuckets();
    }
}
```

## Étape 9: Lancer l'Application

```bash
# Depuis Maven
mvn spring-boot:run

# Ou depuis votre IDE
# Run FoodDeliveryApplication.java
```

L'application démarre sur `http://localhost:8080`

## Étape 10: Tester les APIs

### Créer un utilisateur (Inscription)

```bash
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
```

### Se connecter

```bash
curl -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "client@test.com",
    "password": "password123"
  }'
```

Réponse:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "user": {
    "id": 1,
    "email": "client@test.com",
    ...
  }
}
```

### Créer une catégorie

```bash
curl -X POST http://localhost:8080/api/categories \
  -H "Authorization: Bearer {TOKEN}" \
  -F "nom=Subsahariens" \
  -F "description=Cuisine subsaharienne" \
  -F "image=@/path/to/image.jpg"
```

### Obtenir toutes les catégories

```bash
curl http://localhost:8080/api/categories
```

### Créer un restaurant

```bash
curl -X POST http://localhost:8080/api/restaurants \
  -H "Authorization: Bearer {TOKEN}" \
  -F "nom=Chez Fatou" \
  -F "description=Restaurant traditionnel sénégalais" \
  -F "categorieId=1" \
  -F "ownerId=1" \
  -F "localisation.latitude=14.6928" \
  -F "localisation.longitude=-17.4467" \
  -F "localisation.adresse=Plateau, Dakar" \
  -F "tempsLivraisonMoyen=30" \
  -F "logo=@/path/to/logo.jpg"
```

## 📝 Ordre de Développement Recommandé

1. ✅ **Modèles (Entities)** - Déjà créés
2. ✅ **DTOs** - Déjà créés
3. ⚠️ **Repositories** - À créer (interfaces JPA)
4. ⚠️ **Services** - À implémenter (logique métier)
5. ✅ **Controllers** - Structure créée
6. ⚠️ **Security** - JWT à implémenter
7. ⚠️ **Exception Handling** - À créer
8. ⚠️ **Tests** - À écrire

## 🔧 Commandes Utiles

```bash
# Rebuild l'application
mvn clean install

# Exécuter les tests
mvn test

# Voir les logs Docker
docker-compose logs -f postgres
docker-compose logs -f minio

# Redémarrer les conteneurs
docker-compose restart

# Arrêter tout
docker-compose down

# Supprimer les volumes (⚠️ perte de données)
docker-compose down -v
```

## 🐛 Résolution de Problèmes

### Problème: Port 5432 déjà utilisé
```bash
# Trouver le processus
sudo lsof -i :5432

# Arrêter PostgreSQL local
sudo systemctl stop postgresql
```

### Problème: MinIO ne démarre pas
```bash
# Vérifier les logs
docker logs food-delivery-minio

# Recréer le conteneur
docker-compose down
docker-compose up -d minio
```

### Problème: L'application ne se connecte pas à la DB
- Vérifier que PostgreSQL est démarré: `docker ps`
- Vérifier les credentials dans `application.yml`
- Vérifier les logs: `docker-compose logs postgres`

## 📚 Prochaines Étapes

1. Implémenter les repositories JPA
2. Implémenter les services métier
3. Configurer Spring Security et JWT
4. Créer le GlobalExceptionHandler
5. Ajouter des tests unitaires et d'intégration
6. Documenter avec Swagger/OpenAPI
7. Implémenter le tracking en temps réel (WebSocket)
8. Ajouter des notifications (email, SMS)

## 🎯 Fonctionnalités Avancées à Ajouter

- 🔔 Notifications push
- 🗺️ Suivi GPS en temps réel
- 💳 Intégration de paiement (Stripe, PayPal)
- 📊 Tableau de bord analytics
- ⭐ Système d'avis et notes
- 🎁 Programme de fidélité
- 📧 Service d'emailing
- 🔐 OAuth2 (Google, Facebook login, etc)
