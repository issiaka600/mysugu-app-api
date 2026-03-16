# Structure du projet

## Arborescence fonctionnelle

```text
src/main/java/ma/mysuguclientapp
|-- config
|   |-- JacksonConfig.java
|   |-- MinioConfig.java
|   |-- MinioInitializer.java
|   |-- OpenApiConfig.java
|   |-- WebConfig.java
|   `-- security
|       |-- CustomUserDetailsService.java
|       |-- JwtAuthenticationFilter.java
|       |-- JwtTokenProvider.java
|       `-- SecurityConfig.java
|-- controllers
|   |-- CategorieRestaurantController.java
|   |-- CommandeController.java
|   |-- FileControllerSimple.java
|   |-- PlatController.java
|   |-- RestaurantController.java
|   `-- UserController.java
|-- dtos
|   |-- ... DTOs d'entree/sortie REST
|-- entities
|   |-- BaseEntity.java
|   |-- CategorieRestaurant.java
|   |-- Commande.java
|   |-- LigneCommande.java
|   |-- Localisation.java
|   |-- Plat.java
|   |-- Promotion.java
|   |-- Restaurant.java
|   `-- User.java
|-- enumerations
|   |-- CategoriePlat.java
|   |-- MethodePaiement.java
|   |-- ModeDisponibilitePlat.java
|   |-- ModeReceptionCommande.java
|   |-- StatutCommande.java
|   |-- StatutPaiement.java
|   `-- UserRole.java
|-- repositories
|   `-- repositories Spring Data JPA
|-- services
|   |-- interfaces
|   `-- implementations
|-- exceptions
`-- util
```

## Role des packages

## `config`

Contient la configuration transverse:

- Swagger/OpenAPI
- MinIO
- Jackson
- CORS
- securite JWT

## `controllers`

Expose les routes HTTP.

Controllers actuels:

- `UserController`
- `CategorieRestaurantController`
- `RestaurantController`
- `PlatController`
- `CommandeController`
- `FileControllerSimple`

## `dtos`

Contient les contrats REST. On y trouve notamment:

- DTOs d'authentification: `LoginDTO`, `RegisterDTO`, `LoginResponseDTO`, `GoogleAuthRequestDTO`
- DTOs metier: `RestaurantDTO`, `PlatDTO`, `CommandeDTO`, `CategorieRestaurantDTO`, `UserDTO`
- DTOs de creation/mise a jour: `RestaurantCreateDTO`, `PlatCreateDTO`, `CommandeCreateDTO`, `CommandeUpdateStatusDTO`
- DTOs de fichiers: `FileUploadResponse`, `FileMetadata`, `FileUrlResponse`

## `entities`

Modele JPA persistant.

Entites cles:

- `User`
- `Restaurant`
- `Plat`
- `Commande`
- `LigneCommande`
- `CategorieRestaurant`
- `Promotion`
- `Localisation`

## `repositories`

Acces base de donnees via Spring Data JPA.

Exemples:

- `UserRepository`
- `RestaurantRepository`
- `PlatRepository`
- `CommandeRepository`
- `LigneCommandeRepository`
- `CategoriesRestaurantRepository`

## `services.interfaces`

Contrats exposes aux controllers.

## `services.implementations`

Contient la logique metier.

Classes importantes:

- `UserServiceImpl`
- `GoogleAuthService`
- `RestaurantServiceImpl`
- `PlatServiceImpl`
- `CommandeServiceImpl`
- `CategorieRestaurantServiceImpl`
- `MinioService`

## `exceptions`

Exceptions metier et techniques renvoyees par l'API.

Exemples:

- `BadRequestException`
- `ResourceNotFoundException`
- `UnauthorizedException`

## `util`

Utilitaires transverses:

- constantes metier
- generation des numeros de commande

## Points d'entree documentaires

- [README](./README.md)
- [Architecture](./ARCHITECTURE.md)
- [Documentation API](./API_DOCUMENTATION.md)
- [Schema de base de donnees](./DATABASE_SCHEMA.md)
