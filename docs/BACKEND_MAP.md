# MySugu Backend — Carte complète (navigation code & logique métier)

Vue d'ensemble à jour du backend `MySuguClientApp` (Spring Boot 4 / Java 21 / PostgreSQL,
package `ma.mysuguclientapp`). Remplace les docs top-niveau devenues obsolètes. But : se repérer
vite dans le code et la logique métier.

**Chiffres** : ~39 controllers natifs + 12 controllers legacy livreur · 52 entités · 37 services ·
44 repositories · 27 enums.

**Architecture** : `controllers` (REST) → `services.interfaces` + `services.implementations` →
`repositories` (Spring Data JPA) → `entities`. DTOs isolent l'API de la persistance. Auth JWT
stateless (+ Google/Apple sign-in). MinIO (fichiers), Firebase (FCM), Stripe (paiement), WebSocket
STOMP (tracking). Devise MAD. Base path REST `/api/...`. `ddl-auto=update` (pas de Flyway).

---

## Domaines

### 1. Auth & Utilisateurs
- **Controllers** : `AuthEnhancedController` (login/refresh/verify-email/forgot-reset, Google/Apple),
  `UserController` (profil, `/users/livreur/disponibilite`, `/users/location`), `AdminUsersController`.
- **Entités** : `User` (rôles CLIENT/LIVREUR/RESTAURANT_OWNER/RESTAURANT_STAFF/ADMIN), `RefreshToken`,
  `TokenBlacklist`, `TokenVerification`, `DeviceToken` (FCM).
- **Sécurité** : `config/security/` — `SecurityConfig`, `JwtTokenProvider`, `JwtAuthenticationFilter`,
  `CustomUserDetailsService`. Principal = email.

### 2. Restaurants & Catalogue
- **Controllers** : `RestaurantController`, `RestaurateurController` (onboarding), `RestaurantDashboardController`,
  `RestaurantEmployeController`, `AdminRestaurantController`, `MenuController`, `PlatController`,
  `PlatOptionController`, `CategoriePlatController`, `CategorieProduitController`, `CategorieRestaurantController`,
  `ServiceCategorieController`, `FiltreController`/`AdminFiltreController`.
- **Entités** : `Restaurant`, `RestaurantEmploye`, `Menu`, `MenuPlat`, `Plat`, `OptionGroup`, `OptionItem`,
  `CategorieRestaurant`, `Category`, `ServiceCategorie`, `Filtre`, `Promotion`.

### 3. Commandes, Panier, Avis
- **Controllers** : `CommandeController` (création, statut, assign-livreur, tracking), `PanierController`,
  `FavoriController`, `AvisController` (avis client + note livreur, modération).
- **Entités** : `Commande`, `LigneCommande`, `LigneCommandeOption`, `Panier`, `PanierItem`,
  `PanierItemOption`, `Favori`, `Avis`, `AdresseLivraison`, `Localisation` (embeddable).
- **Enums** : `StatutCommande` (EN_ATTENTE→CONFIRMEE→EN_PREPARATION→PRETE→ASSIGNEE_LIVREUR→EN_COURS→LIVREE,
  ANNULEE, NON_FINALISEE), `ModeReceptionCommande`, `MethodePaiement` (CARTE_BANCAIRE/ESPECES),
  `StatutPaiement`, `StatutAvis`.
- **Logique clé** : `CommandeServiceImpl` — assignation livreur (auto-dispatch `trouverMeilleurLivreur`
  **désactivable** via `dispatch.auto.enabled`, défaut false=FCFS), effets à LIVREE (gains + caisse).

### 4. Paiement & Finance
- **Controllers** : `WalletController` (client), `FideliteController`, `CodePromoController`,
  `PromotionController`, `StripeWebhookController`, `FacturationRestaurantController`.
- **Entités** : `Wallet`, `TransactionWallet`, `PointsFidelite`, `TransactionPoints`, `CodePromo`,
  `UtilisationCodePromo`, `DetteRestaurant`, `PaiementRestaurant`, `ParametresPaiementRestaurant`.
- **Enums** : `TypeTransaction`, `TypeReduction`, `NiveauFidelite`, `StatutDetteRestaurant`,
  `ModePaiementRestaurant`, `ModeVersementRestaurant`, `StatutPaiementRestaurant`.

### 5. Livreur — infrastructure NATIVE
- **Controllers** : `CaisseController` (caisse livreur : position, historique, paiement-resto,
  réconciliation admin), `GainsLivreurController` (gains), `TrackingWebSocketController` (GPS live STOMP).
- **Entités** : `CaisseLivreur` (soldeCourant), `TransactionCaisse`, `ParametresCaisse` (singleton id=1,
  créé par `ParametresCaisseInitializer`), `GainsLivreur`.
- **Enums** : `TypeTransactionCaisse` (COLLECTE_CLIENT, PAIEMENT_RESTAURANT, REMISE_PLATEFORME, …).
- **Services** : `CaisseServiceImpl` (collecte, réconciliation), `GainsLivreurServiceImpl`.

### 6. Livreur — SHIM LEGACY (app Tiktak, contrat 6valley)  → voir `LIVREUR_BACKEND_LOGIC.md`
- **Package** : `legacy.deliveryman` — 12 controllers (`DeliveryManAuth/Profile/Order/Lifecycle/Money/
  Review/Notification/Location/Chat`, `AdminRetraitLivreur`, `LegacyDeliveryManPing`) + `ConfigLegacyController`.
  Reproduit `/api/v2/delivery-man/*` + `/api/v1/config` sur les entités natives.
- **Nouvelles entités** : `DemandeRetrait`, `ReglementCommission`, `PreuveLivraison`,
  `HistoriqueGpsLivraison`, `ContactUrgence`, `OtpResetLivreur`, `MessageLivreur`.
- **Enums** : `StatutRetrait`, `StatutReglementCommission`.
- **Contrat** : `docs/legacy-contracts/CONTRACT-REFERENCE.md`. **Logique métier + modèle argent** :
  `docs/LIVREUR_BACKEND_LOGIC.md`.

### 7. Notifications
- **Controllers** : `NotificationController` (liste, non-lues, lire), `DeviceTokenController` (FCM),
  `CampagneNotificationController`, `ZoneDeploiementController`/`AdminZonesController` (+ hors-zone).
- **Entités** : `Notification`, `DeviceToken`, `ZoneDeploiement`, `ZoneAttenteNotification`.
- **Enum** : `TypeNotification`. **Service** : `FcmService`/`FcmServiceImpl` (gated `FIREBASE_ENABLED`).

### 8. Ops, Stats, Intégrations
- **Controllers** : `StatistiquesController` (admin), `MessageContactController` (contact public),
  `TikTakIntegrationController` (intégration externe TikTak — `/api/integrations/tiktak/**`),
  `FileControllerSimple` (fichiers MinIO).
- **Entités** : `MessageContact`, `BaseEntity`.
- **Config** : `config/` — `MinioConfig`/`MinioInitializer`, `FirebaseConfig`, `StripeConfig`,
  `WebSocketConfig`, `JacksonConfig`, `WebConfig`, `OpenApiConfig`, `FiltreInitializer`,
  `ParametresCaisseInitializer`, `TestDataInitializer` (seed si `TEST_DATA_ENABLED`).
- **Scheduler** : `AlerteCaisseScheduler` (alertes plafond/réconciliation caisse).

---

## Où regarder pour…
- **Un endpoint livreur (app Tiktak)** → `legacy/deliveryman/controller/*` + `LIVREUR_BACKEND_LOGIC.md`.
- **Le modèle argent livreur** → `LIVREUR_BACKEND_LOGIC.md` §4 + `CaisseServiceImpl`/`GainsLivreurServiceImpl`
  + `DeliveryManInfoService`.
- **Le flux de commande** → `CommandeServiceImpl` (création, statuts, assignation, effets LIVREE).
- **La sécurité / rôles** → `config/security/SecurityConfig`.
- **La forme exacte d'une réponse legacy** → `docs/legacy-contracts/CONTRACT-REFERENCE.md`.
- **Les décisions de migration** → `docs/TIKTAK_LIVREUR_MIGRATION_TECHSPEC.md` + `..._PROGRESS.md`.
