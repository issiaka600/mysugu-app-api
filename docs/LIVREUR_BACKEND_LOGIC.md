# Logique métier — Livreur (Tiktak) dans le backend MySugu

Documentation de référence de la fonctionnalité **livreur** après migration du contrat 6valley
`/api/v2/delivery-man/*` dans MySugu (Spring Boot 4 / Java 21 / PostgreSQL). Objectif : permettre de
se repérer rapidement dans le code et la logique métier.

Voir aussi : `TIKTAK_LIVREUR_MIGRATION_TECHSPEC.md` (décisions), `legacy-contracts/CONTRACT-REFERENCE.md`
(forme exacte des réponses), `TIKTAK_LIVREUR_MIGRATION_PROGRESS.md` (état d'avancement).

---

## 1. Vue d'ensemble

L'app mobile livreur **Tiktak** (Flutter, branche `moso`, ex-6valley) est **inchangée** (hors base URL).
Elle parle un contrat legacy `/api/v2/delivery-man/*` + `/api/v1/config`. Ce contrat est reproduit
**à l'identique** par une **couche d'adaptation** (`ma.mysuguclientapp.legacy.deliveryman`) qui traduit
vers les **entités natives MySugu** (`User` rôle `LIVREUR`, `Commande`, `GainsLivreur`, `CaisseLivreur`,
`Avis`, `Notification`). Un seul système livreur, pas de tables 6valley parallèles.

```
App Tiktak (moso)                 Couche legacy (shim)                Natif MySugu
─────────────────    HTTP     ────────────────────────    lit/écrit   ──────────────
/api/v2/delivery-man/*  ──────►  controller + mapper + DTO  ─────────►  User/Commande/
/api/v1/config                   (snake_case, token opaque)             GainsLivreur/Caisse…
```

Principe clé : l'app envoie de vrais **POST** (les "PUT" 6valley arrivent en POST avec un champ
`_method:put` ignoré) → on mappe tout en `@PostMapping`/`@GetMapping`. Pas de filtre de méthode.
Les réponses sont en **snake_case** avec les clés fragiles garanties (voir §6).

Package : `src/main/java/ma/mysuguclientapp/legacy/deliveryman/`
- `controller/` — les endpoints REST du contrat 6valley
- `service/`    — agrégations (`DeliveryManInfoService`, `DeliveryManOrderService`)
- `mapper/`     — `LegacyOrderMapper` (Commande → order 6valley)
- `dto/`        — DTOs de requête/réponse (LoginRequest, TokenResponse, ErrorsResponse, MessageResponse)
- `config/`     — `ConfigLegacyController` (/api/v1/config)

---

## 2. Authentification

- **Login** `POST /api/v2/delivery-man/auth/login` `{country_code, phone, password}` →
  `DeliveryManAuthController.login`. Résout un `User` LIVREUR par téléphone
  (`UserRepository.findByTelephoneAndRole`), vérifie le mot de passe BCrypt et `isActive`, puis émet
  un **JWT MySugu** (`JwtTokenProvider.generateToken`) renvoyé sous `{token}`. L'app traite ce token
  comme **opaque** (`Authorization: Bearer <jwt>`) — le `JwtAuthenticationFilter` natif le valide et
  pose le principal (email).
- **Reset mot de passe** : `forgot-password` → OTP 4 chiffres (entité `OtpResetLivreur`, 2 min) envoyé
  par **email** (`EmailService`) **+ push FCM** (`FcmService.sendToUser`, best-effort). `verify-otp`
  vérifie le code, `reset-password` (`@Transactional`) change le mot de passe. (Le SMS 6valley n'avait
  pas de credentials → email/FCM seulement.)
- **Sécurité** (`SecurityConfig`) : `/api/v2/delivery-man/auth/**` + `/api/v1/config` = publics ;
  `/api/v2/delivery-man/**` = `hasRole('LIVREUR')`.

---

## 3. Cycle de vie d'une commande (FCFS)

Modèle **FCFS** (premier arrivé) — l'auto-dispatch natif est **désactivé** par défaut
(`dispatch.auto.enabled=false`, `CommandeServiceImpl`).

1. À la confirmation d'une commande LIVRAISON, si l'auto-dispatch est off, la commande **reste non
   assignée** et est **diffusée** à tous les livreurs disponibles (`envoyerNotificationsConfirmation`).
2. `GET /current-orders` montre au livreur ses commandes actives **+ les commandes libres
   revendiquables**.
3. `POST /{orderId}/accept` (`DeliveryManOrderService.accept`) revendique la commande sous **verrou
   pessimiste** (`CommandeRepository.findByIdForUpdate`), avec la règle **une seule commande active par
   livreur**, puis diffuse une notification "trop tard" aux autres.
4. Le livreur fait avancer le statut via `POST /update-order-status` (`DeliveryManLifecycleController`):
   `out_for_delivery`→`EN_COURS`, `delivered`→`LIVREE`, `canceled`/`returned`→`ANNULEE`.
5. À `LIVREE` : `livreeAt`, `statutPaiement=PAYE`, livreur redevenu disponible, + effets **argent** (§4).

**Mapping des statuts** (`LegacyOrderMapper`) MySugu → 6valley :
`EN_ATTENTE/NON_FINALISEE→pending`, `CONFIRMEE/ASSIGNEE_LIVREUR→confirmed`,
`EN_PREPARATION/PRETE→processing`, `EN_COURS→out_for_delivery`, `LIVREE→delivered`, `ANNULEE→canceled`.

Autres transitions : `update-expected-delivery` (report + cause), `order-update-is-pause` (pause + cause),
`update-payment-status`, `verify-order-delivery-otp` / `resend-verification-code` (OTP 6 chiffres sur
`Commande.codeVerificationLivraison`), `order-delivery-verification` (photos preuve → `PreuveLivraison`
→ MinIO).

---

## 4. Modèle argent (LE point central)

Deux paradigmes coexistent (comme 6valley), **découplés** pour éviter tout double-comptage. Restaurants
réglés en **PERIODIQUE** (la plateforme paie en bulk) → le livreur **ne paie jamais le restaurant** et
doit **100% du COD** encaissé à la plateforme.

**À la livraison (`LIVREE`)** — déclenché par `DeliveryManLifecycleController.marquerLivree` :
- **Gains** : `GainsLivreurServiceImpl.enregistrerGains` (idempotent) crée un `GainsLivreur` avec
  `montantNet = fraisLivraison × 0.85` (commission plateforme 15%). Alimente `current_balance`.
- **Cash** (si `ESPECES`) : `CaisseServiceImpl.enregistrerCollecteClient` ajoute **`montantFinal`**
  (ce que le client paie réellement, remise incluse — bug historique de double-comptage corrigé) à
  `CaisseLivreur.soldeCourant`.

**Champs de l'écran `/info`** (`DeliveryManInfoService.buildInfo`) :

| Champ app            | Calcul                                                            |
|----------------------|------------------------------------------------------------------|
| `current_balance`    | Σ `GainsLivreur.montantNet` (tous) − Σ retraits **APPROUVE** (gère les retraits partiels) |
| `cash_in_hand`       | `CaisseLivreur.soldeCourant`                                     |
| `pending_withdraw`   | Σ `DemandeRetrait` EN_ATTENTE                                    |
| `total_withdraw`     | Σ `DemandeRetrait` APPROUVE                                      |
| `withdrawable_balance`| `max(0, current_balance − cash_in_hand − pending_withdraw)`     |
| `total_earn`         | `current_balance + total_withdraw`                              |
| `total_deposit`      | Σ transactions `REMISE_PLATEFORME`                              |

**Retrait bancaire** (`DeliveryManMoneyController` + `AdminRetraitLivreurController`) :
- `POST /withdraw-request` : valide `montant ≤ withdrawable`, crée `DemandeRetrait` EN_ATTENTE (aucune
  mutation de solde à la demande).
- Admin `POST /api/admin/livreurs/retraits/{id}/approuver` : passe le retrait en APPROUVE. Comme
  `current_balance = Σ gains − Σ retraits APPROUVE`, cela **réduit** automatiquement `current_balance`
  du montant retiré (retraits partiels gérés correctement). `refuser` → REFUSE (aucun impact solde).

**Réconciliation caisse** (`CaisseServiceImpl.reconcilier`, admin) = **pur encaissement du cash** :
le livreur remet 100% du cash, `soldeCourant → 0`, transaction `REMISE_PLATEFORME` (= le "dépôt" 6valley,
surfacé par `collected_cash_history` / `total_deposit`). **Découplée des gains** : elle ne marque plus
les gains payés (c'est le rôle du retrait). Tant que le livreur détient du cash, `withdrawable` reste
bloqué — mécanisme de contrôle voulu.

**Écran commission** (`/commission/{type}`) = **cut plateforme informatif** = 15% des frais de livraison
des commandes livrées sur la période ; marquer payé insère un `ReglementCommission` (bookkeeping, aucune
mutation de solde). **Invariant zéro double-comptage** : cash détenu (caisse) · gains nets (retrait) ·
commission (info) sont **disjoints**.

Tous les montants en **MAD** (mono-devise, `BigDecimal` HALF_UP 2 décimales).

---

## 5. Chat, notifications, avis, GPS

- **Notifications** (`DeliveryManNotificationController`) : lecture des `Notification` natives du livreur.
  ⚠ clé `description` (pas `body`), `delivery_man_id`/`order_id` numériques.
- **Avis** (`DeliveryManReviewController`) : `Avis` du livreur (`noteLivreur`), `is_saved` (booléen JSON)
  ↔ `Avis.sauvegarde`.
- **GPS** (`DeliveryManLocationController`) : trajet persisté (`HistoriqueGpsLivraison`) via
  record/last/history ; `distance-api` = structure Google Distance Matrix **synthétisée** (Haversine,
  sans clé/réseau) ; `seller-location` depuis `Commande.restaurant`.
- **Chat** (`DeliveryManChatController` + entité `MessageLivreur`) : conversations livreur↔client/vendeur.
  ⚠ `sent_by_*` en booléens. Le livreur peut envoyer/consulter ; **le counterpart (réponse côté app
  client) reste à câbler** (feature absente de MySugu à l'origine).

---

## 6. Clés fragiles à ne jamais casser

L'app plante (`fromJson`) si ces clés manquent/ont le mauvais type. Toutes sont garanties par le shim :
- `/info` : `is_online` (0/1), `identity_image` (chaîne JSON `"[]"`)
- notifications : `description` présent, `delivery_man_id`/`order_id` numériques
- avis : `is_saved` booléen ; chat : `sent_by_*` booléens
- retraits : `amount` numérique
- delivery-wise-earned : `shipping_address_data` + `billing_address_data` = objets présents
- order-details : `success:true` + `order` objet
- tout `seller.shop` : `seller_id` numérique
- `/api/v1/config` : `language` + `unit` = tableaux de chaînes

Détail exhaustif : `docs/legacy-contracts/CONTRACT-REFERENCE.md`.

---

## 7. Index des fichiers (code livreur)

**Couche legacy** (`ma.mysuguclientapp.legacy.deliveryman`)
- `controller/DeliveryManAuthController` — login, OTP reset
- `controller/DeliveryManProfileController` — info, dashboard-counts, is-online, bank-info, change-status,
  language-change, update-fcm-token, update-info, emergency-contact-list
- `controller/DeliveryManOrderController` — current/all/details/search/order-list-by-date, accept
- `controller/DeliveryManLifecycleController` — update-order-status, expected, pause, payment, OTP livraison, preuve
- `controller/DeliveryManMoneyController` — delivery-wise-earned, collected_cash_history, withdraw*, commission
- `controller/DeliveryManReviewController` — review-list, save-review
- `controller/DeliveryManNotificationController` — notifications
- `controller/DeliveryManLocationController` — record/last/history location, distance-api, seller-location
- `controller/DeliveryManChatController` — messages list/get/search/send
- `controller/AdminRetraitLivreurController` — approbation/refus des retraits (admin)
- `config/ConfigLegacyController` — /api/v1/config
- `service/DeliveryManInfoService` — agrégation /info + withdrawable
- `service/DeliveryManOrderService` — accept FCFS
- `mapper/LegacyOrderMapper` — Commande → order 6valley + mapping statuts

**Entités ajoutées** (`ma.mysuguclientapp.entities`)
- `DemandeRetrait`, `ReglementCommission`, `PreuveLivraison`, `HistoriqueGpsLivraison`,
  `ContactUrgence`, `OtpResetLivreur`, `MessageLivreur`
- champs ajoutés : `User` (countryCode, appLanguage, bank*), `Avis.sauvegarde`,
  `Commande` (codeVerificationLivraison, livraisonVerifiee, enPause/causePause, dateLivraisonPrevue/causeReport)
- enums : `StatutRetrait`, `StatutReglementCommission`

**Logique native modifiée**
- `CommandeServiceImpl` — flag auto-dispatch off (FCFS) + gains câblés sur LIVREE
- `CaisseServiceImpl` — collecte corrigée (montantFinal) + réconciliation découplée des gains
- `GainsLivreurServiceImpl` — `enregistrerGains` rendu idempotent + `getCurrentBalance`/`marquerGainsPayes`

---

## 8. Déploiement & config

- Build : `mvnw.cmd -DskipTests package` (toolchain Windows, JDK 21 ; voir `_shim_mvn.bat`) →
  `target/MySuguClientApp-0.0.1-SNAPSHOT.jar`.
- `ddl-auto=update` crée automatiquement les nouvelles tables/colonnes au démarrage.
- Propriétés : `dispatch.auto.enabled` (défaut false=FCFS), `app.public-base-url` (base des URLs fichiers),
  MinIO/DB/JWT via `.env`.
- Côté app : changer `baseUri` (app_constants.dart) vers l'hôte MySugu et rebuild.

---

## 8bis. Checklist de déploiement (prod 162.0.213.66)

État validé : le backend **compile, package (jar), boote** (contexte Spring OK contre Postgres+MinIO)
et **répond correctement** en runtime (tous les GET livreur testés = 200 avec la forme 6valley ;
login → JWT ; 403 sur route protégée sans token).

Étapes (procédure prod habituelle, cf. mémoire déploiement) :
1. **Build local du fat-jar** : `mvnw.cmd -DskipTests package` (via `_shim_mvn.bat`) →
   `target/MySuguClientApp-0.0.1-SNAPSHOT.jar`. Vérifier `BUILD SUCCESS`.
2. **Vérifier les variables d'env prod** (`.env` serveur) : `DATABASE_URL/USERNAME/PASSWORD`,
   `MINIO_*`, `JWT_SECRET` (>= 64 octets pour HS512), `APP_PUBLIC_BASE_URL` (host public réel pour les
   `base_urls` de /api/v1/config), `MAIL_*` (pour les OTP email). Nouveau : `dispatch.auto.enabled=false`
   (FCFS) — défaut déjà false, rien à ajouter sauf pour réactiver l'auto-dispatch.
3. **scp** le jar → `/root/mysugu/backend/mysugu-app-api.jar` (swap atomique) puis
   `cd /root/mysugu && docker compose up -d --build backend`.
4. **`ddl-auto=update`** crée automatiquement au démarrage les nouvelles tables (`demandes_retrait`,
   `reglements_commission`, `preuves_livraison`, `historique_gps_livraison`, `contacts_urgence`,
   `otp_reset_livreur`, `messages_livreur`, `messages_livreur_attachments`) et colonnes (`users`,
   `commandes`, `avis`). **Aucune migration destructive** — uniquement des ajouts.
5. **Créer les comptes livreurs** (rôle LIVREUR) avec `telephone` + `country_code` + mot de passe
   (via l'admin) — l'app livreur se connecte par indicatif+téléphone.
6. **App mobile** : pointer `baseUri` (app_constants.dart) sur l'hôte MySugu et republier le build.
7. **Smoke prod** : `GET /api/v1/config` (200), login livreur (JWT), `GET /info` (200).

Rollback : restaurer `mysugu-app-api.jar.bak.*`. Les nouvelles colonnes/tables ajoutées sont inertes
pour l'ancien code (pas de rollback schéma nécessaire).

## 9. Limites connues / TODO

- **Counterpart chat** : réception/réponse côté app client MySugu à câbler (le livreur, lui, est complet).
- **FCM des OTP** : ✅ fait (email + push FCM sur reset livreur et code de livraison client). Le push
  ne part réellement que si `FIREBASE_ENABLED=true` (sinon no-op propre).
- **Tests de contrat** (T12) : à écrire (fixtures reconstruites depuis les modèles Dart + source 6valley,
  voir CONTRACT-REFERENCE) — le serveur 6valley étant down, pas de capture live.
- **Vérification au boot** : nécessite Postgres + MinIO (stack docker locale) pour valider le chargement
  du contexte Spring avant déploiement.
- Champs profil non gérés (identity_number/type/image) renvoyés en valeurs sûres (`""`, `"[]"`).
