# Architecture MySugu

## Vue systeme

```mermaid
flowchart LR
    Web[Frontend Web]
    Mobile[Application Mobile]
    Swagger[Swagger UI]

    Web --> API[Spring Boot API]
    Mobile --> API
    Swagger --> API

    API --> Security[Spring Security + JWT]
    API --> Services[Services metier]
    Services --> JPA[Spring Data JPA]
    JPA --> Postgres[(PostgreSQL)]

    Services --> Files[MinioService]
    Files --> MinIO[(MinIO bucket: mysugu)]

    Services --> Google[Google tokeninfo API]
```

## Organisation en couches

```mermaid
flowchart TD
    Controllers[Controllers REST]
    DTO[DTOs]
    Services[Services metier]
    Repositories[Repositories JPA]
    Entities[Entities JPA]
    Infra[Config / Security / MinIO / JWT]

    Controllers --> DTO
    Controllers --> Services
    Services --> DTO
    Services --> Repositories
    Repositories --> Entities
    Services --> Infra
```

## Principaux modules

- `controllers`: expose les endpoints REST.
- `services.interfaces`: contrats metier.
- `services.implementations`: logique applicative, validations et orchestration.
- `repositories`: acces aux donnees PostgreSQL.
- `entities`: modele persistant.
- `dtos`: contrats d'entree et de sortie de l'API.
- `config`: securite, Swagger, MinIO, Jackson, CORS.
- `util`: constantes et generation de numero de commande.

## Flux d'authentification JWT

```mermaid
sequenceDiagram
    participant Client
    participant API as API /auth/*
    participant UserService
    participant JWT as JwtTokenProvider

    Client->>API: POST /auth/login ou /auth/google
    API->>UserService: verifier identifiants / token Google
    UserService->>JWT: generer token
    JWT-->>UserService: JWT
    UserService-->>Client: LoginResponseDTO { token, user }
    Client->>API: Requetes protegees avec Authorization: Bearer <token>
```

## Flux de commande

```mermaid
sequenceDiagram
    participant Client
    participant CommandeController
    participant CommandeService
    participant Restaurant
    participant PlatRepo as PlatRepository
    participant DB as PostgreSQL

    Client->>CommandeController: POST /api/commandes
    CommandeController->>CommandeService: createCommande(dto)
    CommandeService->>Restaurant: verifier ouverture et mode reception
    CommandeService->>PlatRepo: charger les plats
    CommandeService->>CommandeService: calcul montant, frais, estimation
    CommandeService->>DB: sauvegarder commande + lignes
    DB-->>CommandeService: commande persistee
    CommandeService-->>Client: CommandeDTO
```

## Gestion des fichiers

```mermaid
flowchart LR
    Client[Web / Mobile]
    FileAPI[FileControllerSimple]
    MinioService[MinioService]
    Bucket[(MinIO bucket mysugu)]

    Client -->|upload multipart| FileAPI
    FileAPI --> MinioService
    MinioService --> Bucket
    Bucket --> MinioService
    MinioService -->|URL stable /api/files/...| Client
```

## Choix structurants

- Bucket MinIO unique: `mysugu`
- URLs de fichiers stables servies par l'application, pas de dependance metier aux presigned URLs temporaires
- Authentification stateless via JWT
- DTOs explicites pour isoler le modele de persistence
- Pagination Spring Data en mode `VIA_DTO`

## Packages cles

- `ma.mysuguclientapp.config`
- `ma.mysuguclientapp.config.security`
- `ma.mysuguclientapp.controllers`
- `ma.mysuguclientapp.services.implementations`
- `ma.mysuguclientapp.repositories`
- `ma.mysuguclientapp.entities`
- `ma.mysuguclientapp.dtos`

## Integrations externes

- PostgreSQL pour les donnees applicatives
- MinIO pour les fichiers
- Google `tokeninfo` pour la verification des `idToken`
- SMTP Gmail pour l'envoi d'emails
