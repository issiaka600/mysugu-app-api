# Règles SecurityConfig — Module Statistiques Admin

## Routes ADMIN uniquement (hasRole('ADMIN')):
Toutes les routes sous `/api/admin/statistiques/**` nécessitent le rôle ADMIN.

Ajouter dans SecurityConfig.java:
```java
.requestMatchers("/api/admin/**").hasRole("ADMIN")
```

## Endpoints disponibles:
- GET /api/admin/statistiques/dashboard
- GET /api/admin/statistiques/commandes/evolution
- GET /api/admin/statistiques/commandes/par-statut
- GET /api/admin/statistiques/commandes/par-mode
- GET /api/admin/statistiques/commandes/par-paiement
- GET /api/admin/statistiques/commandes/heures-pointe
- GET /api/admin/statistiques/restaurants/top
- GET /api/admin/statistiques/restaurants/performance
- GET /api/admin/statistiques/clients/top
- GET /api/admin/statistiques/clients/retention
- GET /api/admin/statistiques/clients/par-ville
- GET /api/admin/statistiques/livreurs
- GET /api/admin/statistiques/livreurs/top
- GET /api/admin/statistiques/plats/top
- GET /api/admin/statistiques/plats/jamais-commandes
- GET /api/admin/statistiques/plats/par-categorie
- GET /api/admin/statistiques/financier
- GET /api/admin/statistiques/monitoring
- GET /api/admin/statistiques/alertes
