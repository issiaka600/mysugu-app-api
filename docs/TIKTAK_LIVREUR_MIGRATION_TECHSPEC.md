# Tech-Spec — Tiktak Deliverer → MySugu Legacy Shim Migration

**Status:** Ready for execution. All requirements resolved via `/grill-me` (aggregate ambiguity 0.18, gate passed). Decision ledger persisted in memory `project_tiktak_livreur_migration.md`.

**Author context:** Written for an agent with zero prior context. Everything needed to execute is here or cited to a source file.

---

## 1. Goal

Re-implement the old 6valley Laravel backend's deliverer API contract — **`/api/v2/delivery-man/*`** (~29 endpoints) plus **`/api/v1/config`** — **byte-compatibly** inside the new **MySuguClientApp** (Spring Boot 4, Java 21, PostgreSQL, package `ma.mysuguclientapp`), backed by MySugu's **native** entities. The existing **Tiktak-deliverer-app** (Flutter, branch `moso`, 6valley "sixvalley_delivery_boy") is rebuilt with a new base URL and otherwise **unchanged**; it must run against MySugu without code changes to its logic or models.

### Success definition
The moso app, pointed at MySugu, completes the full deliverer lifecycle (login → OTP/reset → see available orders → accept → out-for-delivery → delivery OTP + proof photo → cash/earnings → withdraw → commission → chat/notifications/reviews) with **no deserialization errors and no broken/empty screens**, verified by automated contract tests (§10).

---

## 2. Constraints & Non-Goals

**Constraints**
- App logic and Dart models are **frozen**. The only permitted app change is the `baseUri` constant (`lib/utill/app_constants.dart:9`) + rebuild.
- Responses must match what the moso Dart `fromJson` models parse (field names, nesting, types), including Laravel `_method` spoofing (PUT-as-POST with `_method: put` body field) and multipart uploads.
- One **unified** livreur system — no parallel 6valley tables. Backing entities: `User`(role `LIVREUR`), `Commande`, `GainsLivreur`, `CaisseLivreur`, `Avis`, `Notification`.
- New endpoints live under the legacy namespaces (`/api/v2/delivery-man/**`, `/api/v1/config`); no collision with native `/api/**`.

**Non-Goals**
- **No ETL / no data migration.** Drivers, wallets, orders are created fresh in MySugu.
- **Do NOT touch** `TikTakOrderIntegrationService` / `Commande.tiktakOrderId` (existing external integration, out of scope).
- Not modifying the MySugu customer/restaurant apps except where a counterpart is required to make a driver feature real (chat).
- Not introducing Flyway/Liquibase in this workstream (MySugu uses `ddl-auto=update`; new entities/columns auto-add).

---

## 3. Architecture

### 3.1 Shim layer placement
Add a self-contained legacy package `ma.mysuguclientapp.legacy.deliveryman` containing:
- `controller/` — REST controllers mapped under `/api/v2/delivery-man/**` and one `/api/v1/config`.
- `dto/` — **response DTOs shaped exactly like the 6valley JSON** (snake_case field names via `@JsonProperty`). These are the contract; do NOT reuse native camelCase DTOs on the wire.
- `mapper/` — translate native entities (`Commande`, `User`, `GainsLivreur`, `CaisseLivreur`, `Avis`) → legacy DTOs.
- `service/` — legacy-facing orchestration that calls existing native services where possible.
- `auth/` — phone+password login + token handling (§4).

Native services/entities are reused; the legacy layer is an **adapter**, not a fork. Where native business logic must change (auto-dispatch, caisse bugs, gains wiring), change it in the native code (§7, §8) since the system is unified.

### 3.2 JSON conventions
- Global: the legacy controllers must emit **snake_case**. Use a dedicated `ObjectMapper`/`@JsonNaming` on legacy DTOs (do not flip the global mapper — native APIs stay camelCase).
- Money fields: emit as **numbers** matching what the Dart models parse with `parseToDouble`/`double.parse(String)` fallback. 6valley stores/returns "USD" units in single-currency pass-through mode; MySugu currency is MAD. Confirm single-currency assumption at fixture-capture time (§11).

### 3.3 `_method` spoofing + multipart
- 6valley "PUT" calls arrive as **POST** with a body field `_method=put`. Add a servlet filter (or `HiddenHttpMethodFilter`, Spring's built-in) so these route to the intended handler. Verify it works for both `application/json` and `multipart/form-data` bodies (the app sends `_method` inside multipart for profile/update-info).
- Multipart endpoints (profile image, delivery proof photos, chat images) accept `multipart/form-data`; store binaries in MinIO (bucket `mysugu`, existing `FileStorageService`).

### 3.4 Base path / SecurityConfig
- Add `/api/v2/delivery-man/auth/**` and `/api/v1/config` to the **permitAll** whitelist.
- All other `/api/v2/delivery-man/**` require an authenticated LIVREUR (`hasRole('LIVREUR')`), resolved from the shim token (§4).

---

## 4. Authentication

**6valley contract:** `POST /auth/login {country_code, phone, password}` → `{token}`; token is an opaque `Str::random(50)` stored in `delivery_men.auth_token`; sent as `Authorization: Bearer <token>`; the app treats the token as an **opaque string** (never decodes it).

**MySugu today:** email+password only; `User` has `telephone` (nullable, plain, **no** `country_code`), email-only `findByEmail`; JWT keyed on email subject (`JwtTokenProvider.generateToken`).

**Decision & plan (token = JWT, app-opaque):**
1. Add `User` fields: normalized `telephone` (E.164 or `countryCode+phone` concatenation) + `countryCode`. Since drivers are fresh, enforce a storage convention at creation.
2. Add `findByTelephoneAndCountryCode` (or by normalized phone) to `UserRepository`.
3. Legacy `LoginController`: validate `country_code+phone+password` against a LIVREUR `User` (BCrypt), check `isActive`, then **issue a MySugu JWT** via existing `JwtTokenProvider.generateToken(user)` and return `{"token": <jwt>}`. The app stores and echoes it; the existing `JwtAuthenticationFilter` validates it and populates the LIVREUR principal — no opaque-token column needed.
4. Password reset OTP flow: reuse MySugu's `TokenVerification` (email-verify/reset codes) to back `forgot-password` / `verify-otp` / `reset-password`. 6valley OTP is 2-min expiry (a 4-digit `rand(1000,9999)`) — match expiry and digit count.
   - **RESOLVED (dependency #2): email + FCM only.** SMS OTP in 6valley is coded (`app/Utils/sms_module.php`, 6 generic providers) but ships with **no usable credentials** in the repo (no `.env`, blank `.env.example` Nexmo keys, no seeded `addon_settings` `sms_config` row); the declared `Modules/Gateways` doesn't exist on disk (dead path); there is **no Morocco-specific gateway**; and the 6valley server is down so the production `addon_settings`/`business_settings.forgot_password_verification` values can't be confirmed. Stock 6valley defaults to email. → Deliver the OTP via **email (MySugu SMTP) + FCM push**; do not build SMS now.
   - Keep an internal `OtpChannel` abstraction so an SMS provider (Twilio/Nexmo/Releans/…) can be plugged later **if** real credentials + a decision to enable phone verification arrive. The app doesn't care which channel delivers — it only submits the code.
5. `update-fcm-token` (PUT-spoofed) → store on `User` FCM token (reuse native FCM device-token store from the FCM feature).
6. `language-change`, `is-online`, `bank-info` → map to `User` fields (`is_online`→`livreurDisponible`; add `appLanguage`, bank fields `bankName/branch/accountNo/holderName` to `User`).

---

## 5. Data Model Changes (native entities)

New/changed under `ma.mysuguclientapp.entities` (auto-DDL adds columns/tables):

| Change | Entity / Table | Fields |
|---|---|---|
| Add | `User` | `countryCode`, normalized phone lookup, `appLanguage`, `bankName`, `branch`, `accountNo`, `holderName` |
| Add | `Commande` | delivery OTP `codeVerificationLivraison` + `verifie` flag; proof photos (see below); `enPause` (Boolean) + `causePause`; `dateLivraisonPrevue` (reschedule) + `causeReport`; keep existing `raisonAnnulation`, `scheduledAt` |
| New | `PreuveLivraison` (proof-of-delivery) | `id, commande, imageUrl, createdAt` (0..n per order; MinIO URLs) |
| New | `DemandeRetrait` (withdraw) | `id, livreur(User), montant, note, statut{EN_ATTENTE,APPROUVE,REFUSE}, transactionRef, adminValidateur, createdAt, updatedAt` |
| New | `HistoriqueGpsLivraison` (persisted GPS trail) | `id, commande, livreur, latitude, longitude, vitesse, localisation, timestamp` |
| New | `ReglementCommission` (commission_history analog) | `id, livreur, startDate, endDate, montant, statut{PENDING,PAID,VALIDATED}, transactionRef, payeAt, validateur` |
| New | `Conversation` + `MessageChat` (chat) | driver↔client/seller threads + messages (+ optional image URLs); requires a counterpart on the customer/seller side (§9, ticket T9) |
| New | `ContactUrgence` (emergency contacts) | scoped to seller/restaurant; `GET` returns list per order/seller |
| Add flag | `GainsLivreur` | reuse as-is; **wire creation** (§7) |
| Add | `Avis` | `sauvegarde` (Boolean) to back `save-review` / `is_saved` |

`GainsLivreur.estPaye` semantics change (§7). Add repository queries: `sumMontantNetByLivreurAndEstPayeFalse`, withdraw sums, GPS-by-commande, etc.

---

## 6. Endpoint Mapping (the contract)

Legend for **Backing**: EXISTS = native maps directly · ADAPT = wrap native · BUILD = new logic/table. All paths prefixed `/api/v2/delivery-man` unless noted. "PUT*" = arrives POST + `_method=put`.

### 6.1 Auth & config
| Method | Path | Backing | Notes |
|---|---|---|---|
| POST | `/auth/login` | BUILD | phone+cc+password → JWT (§4) |
| POST | `/auth/forgot-password` | ADAPT | OTP via TokenVerification |
| POST | `/auth/verify-otp` | ADAPT | 2-min expiry |
| POST | `/auth/reset-password` | ADAPT | |
| PUT* | `/language-change` | ADAPT | `User.appLanguage` |
| PUT* | `/update-fcm-token` | ADAPT | native FCM store |
| GET | `/api/v1/config` | BUILD | app-boot config; return a valid minimal 6valley-shaped config (currency, business name, flags the app reads at splash). **Must not 500** or app won't boot. |

### 6.2 Profile
| Method | Path | Backing | Notes |
|---|---|---|---|
| GET | `/info` | ADAPT | full profile + money block (§7) + counts + avg rating from `Avis.getAverageNoteLivreur` |
| GET | `/profile-dashboard-counts` | ADAPT | wallet row + counts |
| PUT* (multipart) | `/update-info` | ADAPT | name/address/password/image → MinIO |
| POST | `/bank-info` | ADAPT | `User` bank fields |
| PUT* | `/is-online` | ADAPT | `User.livreurDisponible`; block going offline with active order |
| POST | `/change-status` | ADAPT | `User.isActive` |
| GET | `/emergency-contact-list` | BUILD | `ContactUrgence` |

### 6.3 Orders & FCFS
| Method | Path | Backing | Notes |
|---|---|---|---|
| GET | `/current-orders` | ADAPT | active statuses → Commande in {ASSIGNEE_LIVREUR, EN_COURS, …}; also **unassigned broadcastable** orders visible for claim (§8) |
| GET | `/all-orders` | ADAPT | history + status/date/search filters |
| GET | `/order-details` | ADAPT | ownership-checked |
| GET | `/search` | ADAPT | by id / customer name |
| GET | `/order-list-by-date` | ADAPT | ⚠ 6valley route exists but **controller method missing** — verify app actually calls it; if unused, return the same shape as all-orders by date |
| POST | `/{orderId}/accept` | BUILD | **row-locked FCFS claim** (§8) |

### 6.4 Delivery lifecycle
| Method | Path | Backing | Notes |
|---|---|---|---|
| PUT* | `/update-order-status` | ADAPT | status map 6valley↔MySugu (§6.6); triggers gains + caisse (§7) |
| PUT* | `/update-expected-delivery` | ADAPT | `Commande.dateLivraisonPrevue` + cause |
| PUT* | `/order-update-is-pause` | BUILD | `Commande.enPause` + cause |
| PUT* | `/update-payment-status` | ADAPT | `Commande.statutPaiement` |
| POST | `/verify-order-delivery-otp` | BUILD | check `codeVerificationLivraison` |
| POST | `/resend-verification-code` | BUILD | regen 6-digit + FCM to client |
| POST (multipart) | `/order-delivery-verification` | BUILD | `PreuveLivraison` images → MinIO |

### 6.5 Location, money, reviews, notifications, chat
| Method | Path | Backing | Notes |
|---|---|---|---|
| POST | `/record-location-data` | BUILD | insert `HistoriqueGpsLivraison` |
| GET | `/last-location` | BUILD | latest GPS for order |
| GET | `/order-delivery-history` | BUILD | GPS trail for order |
| POST | `/distance-api` | ADAPT | proxy Google Distance Matrix (reuse map key) |
| GET | `/seller-location` | ADAPT | restaurant pickup location from `Commande.restaurant` |
| GET | `/delivery-wise-earned` | ADAPT | order list, payment_status=paid, date buckets (§7) |
| GET | `/collected_cash_history` | ADAPT | `REMISE_PLATEFORME` transactions (§7) |
| POST | `/withdraw-request` | BUILD | `DemandeRetrait` (§7) |
| GET | `/withdraw-list-by-approved` | BUILD | filter by statut |
| GET/POST | `/commission/{type}` | BUILD | informational platform cut (§7) |
| GET | `/review-list` | ADAPT | `Avis` by livreur; `is_saved` filter |
| PUT* | `/save-review` | BUILD | `Avis.sauvegarde` |
| GET | `/notifications` | EXISTS | native `Notification` for LIVREUR |
| GET | `/messages/list/{type}` | BUILD | chat (T9) |
| GET | `/messages/get-message/{type}/{id}` | BUILD | chat thread |
| GET | `/messages/search/{type}` | BUILD | ⚠ app bug: `searchConversationList` mistakenly points at `update-fcm-token` (`app_constants.dart:29`). Serve the fcm-token path harmlessly AND provide the real search; confirm which the app actually hits. |
| POST (multipart) | `/messages/send-message/{type}` | BUILD | chat send + images |

### 6.6 Status mapping (6valley ↔ MySugu `StatutCommande`)
6valley uses `pending, confirmed, processing, out_for_delivery, delivered, canceled, returned`. MySugu: `EN_ATTENTE, CONFIRMEE, EN_PREPARATION, PRETE, ASSIGNEE_LIVREUR, EN_COURS, LIVREE, ANNULEE, NON_FINALISEE`.
Define a **bidirectional translation** in the mapper. Minimum required by the app's driver flow:
- `out_for_delivery` → `EN_COURS`; `delivered` → `LIVREE`; `canceled` → `ANNULEE`.
- `current-orders` "active" = {CONFIRMEE, EN_PREPARATION, PRETE, ASSIGNEE_LIVREUR, EN_COURS} + unassigned-claimable.
- `returned` has no native equivalent — decide: add a `RETOURNEE` status or map to `ANNULEE` with a cause. (Low frequency; confirm the app exposes it before adding.)
Emit the 6valley string form in responses.

---

## 7. Money Model (resolved contract)

**Model:** wallet/withdraw **and** commission paradigms coexist (like 6valley), decoupled to avoid double-counting. Restaurants are settled **PERIODIQUE** (platform pays in bulk via `FacturationRestaurant`, food commission deducted) → the driver **never pays a restaurant** and owes **100% of collected COD** to the platform.

**On `LIVREE` (`CommandeServiceImpl.updateCommandeStatus`):**
1. **Always** wire `GainsLivreurServiceImpl.enregistrerGains(commande)` (currently dead code — never called) → `GainsLivreur{ montant=fraisLivraison, commissionPlateforme=0.15×fraisLivraison, montantNet=0.85×fraisLivraison, estPaye=false }`.
2. **If `ESPECES` (COD):** `enregistrerCollecteClient` → `COLLECTE_CLIENT` adds **`montantFinal`** to `soldeCourant`. **FIX the existing bug** (`CaisseServiceImpl.java:187-188`) that adds `montantTotal + fraisLivraison` (double-counts the fee) and ignores the discount — this correction is in-scope for the shared caisse and improves native drivers too.

**`GET /info` money block (derived):**
| Field | Formula |
|---|---|
| `current_balance` | Σ `GainsLivreur.montantNet` where `estPaye=false` |
| `cash_in_hand` | `CaisseLivreur.soldeCourant` |
| `pending_withdraw` | Σ `DemandeRetrait` EN_ATTENTE |
| `total_withdraw` | Σ `DemandeRetrait` APPROUVE |
| `withdrawable_balance` | `max(0, current_balance − cash_in_hand − pending_withdraw)` |
| `total_earn` | `current_balance + total_withdraw` |
| `total_deposit` | Σ `REMISE_PLATEFORME` transaction amounts |
| `average_rating` | `Avis.getAverageNoteLivreur(livreurId)` |
| counts | from `Commande` by status |

**Withdraw workflow (`DemandeRetrait`):**
- Request: validate `montant ≤ withdrawable_balance` and `montant > 1`; create EN_ATTENTE; `pending_withdraw` derives from it (no balance mutation at request time).
- Admin approve: `total_withdraw += montant`, and mark the corresponding `GainsLivreur` rows `estPaye=true` (this is what reduces `current_balance`); pending clears. Deny: pending clears only, gains stay unpaid. (6valley `approved` codes: 0 pending / 1 approve / 2 deny.)

**Reconciliation DECOUPLED from gains (shared caisse change):**
- Reconciliation = pure cash remittance: `soldeCourant → 0`, `totalCollecteSession → 0`, record `REMISE_PLATEFORME` (this **is** the 6valley "deposit" → surfaced by `collected_cash_history` / `total_deposit`).
- **Remove** `marquerGainsPaies` / `VERSEMENT_GAINS` from `reconcilier` — gains are settled via **withdraw approval**, not reconciliation. (Otherwise `current_balance` is wiped without a payout.)
- Reconciliation reducing `soldeCourant` (→ `cash_in_hand`) is what **unlocks** `withdrawable` — matches the 6valley control that a driver can't withdraw earnings while holding company cash.

**Commission screen (`/commission/{type}`):** informational platform cut = 15% of `fraisLivraison` per delivered order (= `GainsLivreur.commissionPlateforme`), pure bookkeeping via `ReglementCommission` (pending → paid on driver POST → validated by admin). **No wallet mutation.** Keep the 6valley response shape (`{success, commissions:{total, montant_to_pay, start_date, end_date, period, status, details[]}}`) — define `total`/`montant_to_pay` from the platform-cut basis, NOT from `order_amount` (which would double-count COD cash).

**No double-counting invariant:** cash-held (`soldeCourant`/reconciliation) · net gains (`GainsLivreur`/withdraw) · commission (informational) are **disjoint** money concepts. Any change that makes one read from another's source is a bug.

**Currency:** compute in `BigDecimal` (HALF_UP, 2dp). **RESOLVED (dependency #3): single-currency, Moroccan Dirham (MAD / DH).** All 6valley `Convert::usd`/`currency_to_usd`/`currencyConverter` calls are **pass-through no-ops** — no exchange conversion anywhere. Wallet/caisse/gains numbers are raw MAD; emit as-is. No "USD unit" translation needed.

---

## 8. FCFS Assignment (replace auto-dispatch)

**Today:** on transition to `CONFIRMEE`, `CommandeServiceImpl.envoyerNotificationsConfirmation` runs `trouverMeilleurLivreur` and auto-assigns the best available driver. Hard-coded, no flag.

**Change:**
1. Add config flag `dispatch.auto.enabled` (default keep native behavior for native flow; **off** for the deliverer/shim flow). Simplest: gate the auto-assign call behind the flag.
2. With auto-dispatch off: on `CONFIRMEE`/`PRETE`, leave `commande.livreur = null` and **broadcast** "Nouvelle commande disponible" to all available LIVREURs (the fallback path already exists in `trouverMeilleurLivreur` when no driver is found — reuse it as the primary path).
3. `POST /{orderId}/accept` (BUILD): in a DB transaction with `lockForUpdate` (JPA pessimistic write) on the order row — guard the order is unassigned and in a claimable status; guard the driver has **no other non-terminal order** (one-active-order rule); set `commande.livreur = currentDriver`, transition status, save. On conflict return 409/403 matching 6valley semantics.
4. Fire the 6valley notification side effects: confirm to customer, notify seller, and a **"too late"** broadcast to other available drivers.

---

## 9. Features MISSING in MySugu — build notes

- **Chat (T9):** no in-app messaging exists. Build `Conversation`/`MessageChat` + the driver endpoints AND a minimal counterpart so a customer/seller can receive/reply (otherwise the feature is dead). Scope the counterpart with the customer-app owner. Multipart image messages → MinIO.
- **Emergency contacts:** build `ContactUrgence` scoped to restaurant/seller; `GET` returns the list for the order's seller.
- **Config endpoint:** build a 6valley-shaped `/api/v1/config` returning the fields the splash reads. Must always 200.
- **Persisted GPS:** today tracking is in-memory (`TrackingLocationStore`, WebSocket only). Build `HistoriqueGpsLivraison` + the three REST endpoints; optionally also push to the existing WebSocket topic for the customer live-map.
- **Delivery OTP + proof photo:** add fields/entity (§5) + the three endpoints (§6.4).
- **Withdraw:** §7.

---

## 10. Testing & Acceptance

**Acceptance = automated contract tests per endpoint** comparing the MySugu response to a **reference fixture**.

> **RESOLVED (dependency #1):** the 6valley server is **down** — **no live instance available for capture**. Source of truth is therefore the **moso Dart `fromJson` models** (what the app actually parses), cross-checked against the 6valley **controller source** (`app/Http/Controllers/RestAPI/v2/delivery_man/*` — response arrays are readable in code) to recover optional/edge fields the models ignore. Reconstruct fixtures by hand from these two sources. Risk: a field present in a rare 6valley branch but absent from both the model and the visible controller path could be missed — accept and mitigate by covering every code branch of each controller method.

Procedure:
1. **Reconstruct** the expected JSON for every endpoint from the Dart models + 6valley controller source (all branches: each status, each date-bucket `type`, COD vs card, empty vs populated). Store as fixtures under `src/test/resources/legacy-fixtures/`.
2. **Cross-check** each fixture's keys against the corresponding Dart model to mark load-bearing vs ignored fields (see the money exploration's Part B for the profile/earnings/withdraw/commission key lists; note fragile un-null-guarded keys `is_online`, `identity_image` that must always be present/valid).
3. For each shim endpoint, a test asserts structural + type equivalence to the fixture (field presence, nesting, types; tolerant of values that legitimately differ like ids/timestamps).
4. **Smoke** at least once end-to-end: moso app on staging → MySugu → full lifecycle, no crash. (Manual, pre-cutover; not the gating criterion but a required sign-off.)

Definition of done per endpoint: contract test green + covered by the lifecycle smoke.

---

## 11. Open Dependencies / Risks (must confirm before/at execution)

1. ✅ **RESOLVED — 6valley is down, no live capture.** Fixtures reconstructed from moso Dart models + 6valley controller source (§10). Cover every controller branch to mitigate missed optional fields.
2. ✅ **RESOLVED — email/FCM-only OTP.** 6valley SMS OTP has no usable credentials in the repo (blank keys, missing `Modules/Gateways`, no seeded `sms_config`), no Morocco gateway, and the prod DB can't be checked (server down). Deliver OTP via email + FCM; keep a pluggable `OtpChannel` for a future SMS provider. (§4)
3. ✅ **RESOLVED — single-currency MAD (DH).** All 6valley currency conversions are pass-through no-ops; numbers are raw MAD. (§7)
4. **`order-list-by-date`** — 6valley controller method missing; confirm the app actually calls it (`order_repository`/constants) before building.
5. **Chat counterpart** — needs customer/seller-side messaging; coordinate scope.
6. **`messages/search` app bug** (points at `update-fcm-token`, `app_constants.dart:29`) — confirm real call target.
7. **`returned` status** — confirm the app surfaces it before adding a native status.
8. **Shared caisse changes** (collecte fix, reconciliation decoupling) affect native MySugu livreurs — validate no regression to any existing native driver flow.

---

## 12. Suggested Ticket Breakdown (execution order)

Ordered so each ticket is independently testable; earlier tickets unblock later ones.

- **T0 — Shim skeleton:** package, snake_case DTO mapper, `_method`/`HiddenHttpMethodFilter`, SecurityConfig whitelist, multipart plumbing. Deliver: a trivial endpoint round-trips.
- **T1 — Auth:** phone+cc+password login → JWT, OTP reset flow, fcm-token, language. (§4)
- **T2 — Profile & is-online & bank-info & emergency-contact.** (§6.2)
- **T3 — Orders read** (`current/all/details/search`) + status mapping. (§6.3, §6.6)
- **T4 — FCFS accept + disable auto-dispatch.** (§8)
- **T5 — Delivery lifecycle** (status update, expected-delivery, pause, payment-status). (§6.4)
- **T6 — Delivery verification** (OTP + resend + proof photo). (§6.4, §9)
- **T7 — Money core:** wire `enregistrerGains`, fix collecte bug, decouple reconciliation, `/info` money block, `delivery-wise-earned`, `collected_cash_history`. (§7)
- **T8 — Withdraw + commission** (`DemandeRetrait`, admin approval, `ReglementCommission`, `/commission`). (§7)
- **T9 — Chat** (+ counterpart) and **reviews** (`save-review`, `review-list`) and **notifications** wiring. (§9, §6.5)
- **T10 — GPS** (persist + record/last/history), **distance-api**, **seller-location**. (§6.5, §9)
- **T11 — `/api/v1/config`** boot endpoint. (§9)
- **T12 — Contract-test harness + fixtures** (runs alongside every ticket; finalized here). (§10)
- **T13 — App rebuild** with new base URL + staging smoke + cutover. (§3.3, §11)

---

*Derived from three deep code explorations this session (moso app inventory, 6valley deliverer backend, MySugu native feasibility) + two money-flow deep-dives. File:line citations reflect current code as of the exploration.*
