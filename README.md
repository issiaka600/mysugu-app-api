# MySugu Client App Backend

API Spring Boot pour une plateforme de commande de repas multi-acteurs: clients, restaurants, livreurs et administrateurs.

## Vue d'ensemble

Le backend expose:

- l'authentification classique par email/mot de passe et l'authentification Google
- la gestion des utilisateurs et de leur localisation
- la gestion des categories, restaurants et plats
- la creation, le suivi et la mise a jour des commandes
- la gestion de fichiers via MinIO avec un bucket unique `mysugu`
- une documentation OpenAPI disponible dans Swagger

La devise fonctionnelle de l'application est le dirham marocain (`MAD`, symbole `DH`).

## Stack technique

- Java 21
- Spring Boot 4.0.2
- Spring Web MVC
- Spring Security + JWT
- Spring Data JPA / Hibernate
- PostgreSQL
- MinIO
- Springdoc OpenAPI / Swagger UI

## Lancement local

1. Renseigner les variables dans `.env`
2. Demarrer PostgreSQL et MinIO
3. Lancer l'application:

```bash
mvn spring-boot:run
```

Ports et URLs par defaut:

- API: `http://localhost:8083`
- Swagger UI: `http://localhost:8083/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8083/v3/api-docs`
- Healthcheck: `http://localhost:8083/actuator/health`

## Variables importantes

```properties
DATABASE_URL=
DATABASE_USERNAME=
DATABASE_PASSWORD=
JWT_SECRET=
JWT_EXPIRATION=
MINIO_ENDPOINT=
MINIO_ACCESS_KEY=
MINIO_SECRET_KEY=
GOOGLE_AUTH_CLIENT_ID=
APP_PUBLIC_BASE_URL=http://localhost:8083
```

## Documentation du projet

- [Architecture](./ARCHITECTURE.md)
- [Documentation API](./API_DOCUMENTATION.md)
- [Schema de base de donnees](./DATABASE_SCHEMA.md)
- [Structure du projet](./PROJECT_STRUCTURE.md)

## Points fonctionnels a connaitre

- Les restaurants peuvent etre fermes automatiquement selon `heureOuverture`, `heureFermeture` et `autoCloseEnabled`.
- Les plats supportent `DISPONIBLE`, `INDISPONIBLE_TEMPORAIRE` et `INDISPONIBLE_DEFINITIVE`.
- Les commandes supportent `LIVRAISON` et `RETRAIT_SUR_PLACE`.
- Les fichiers sont servis par l'application avec des URLs stables de type `/api/files/{objectName}`.
- Les uploads sont ranges dans un bucket MinIO unique `mysugu` avec des dossiers `restaurants`, `plats`, `categories`, `avatars` et `uploads`.
