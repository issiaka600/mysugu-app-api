# Règles SecurityConfig — Module Social (Avis, Favoris, Notifications)

## Avis
- GET /api/avis/restaurant/** — public
- GET /api/avis/livreur/** — public
- GET /api/avis/{id} — public
- POST /api/avis — CLIENT authentifié
- GET /api/avis/mes-avis — authentifié
- PATCH /api/avis/{id}/moderation — ADMIN
- GET /api/avis/admin/en-attente — ADMIN
- DELETE /api/avis/{id} — ADMIN ou auteur (authentifié)

## Favoris (tous authentifiés)
- GET /api/favoris — authentifié
- POST /api/favoris/{restaurantId} — authentifié
- DELETE /api/favoris/{restaurantId} — authentifié
- POST /api/favoris/{restaurantId}/toggle — authentifié
- GET /api/favoris/{restaurantId}/status — authentifié
- GET /api/favoris/{restaurantId}/count — public

## Notifications (tous authentifiés)
- GET /api/notifications — authentifié
- GET /api/notifications/non-lues — authentifié
- GET /api/notifications/count — authentifié
- PATCH /api/notifications/{id}/lire — authentifié
- POST /api/notifications/lire-toutes — authentifié

## À ajouter dans SecurityConfig:
```java
// Avis publics
.requestMatchers(HttpMethod.GET, "/api/avis/restaurant/**").permitAll()
.requestMatchers(HttpMethod.GET, "/api/avis/livreur/**").permitAll()
.requestMatchers(HttpMethod.GET, "/api/avis/{id}").permitAll()
// Count favoris public
.requestMatchers(HttpMethod.GET, "/api/favoris/*/count").permitAll()
// Modération admin
.requestMatchers("/api/avis/admin/**").hasRole("ADMIN")
.requestMatchers(HttpMethod.PATCH, "/api/avis/*/moderation").hasRole("ADMIN")
```
