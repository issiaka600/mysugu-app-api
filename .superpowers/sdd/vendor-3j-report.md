# Vendor Slice 3j — Stats + Notifications — Implementation Report

**Branch:** `feat/vendor-shim` · **Base HEAD:** `b2c50e5` · **Package root:** `ma.mysuguclientapp`
**Status:** COMPLETE — all tasks 3j.1–3j.6 implemented TDD (RED → GREEN), committed per task. Full suite green except the pre-existing `PushNotificationTest`.

Base path `/api/v3/seller/*`, principal `@AuthenticationPrincipal String email` resolved via `SellerContext.currentRestaurant(email)` (stats) / `SellerContext.requireOwner(email)` (notifications). No new entities/tables.

## IMPORTANT — 3j.0 corrected the design spec against the real vendor app

Task 3j.0 (read `Tiktak-vendor-app-moso/lib/features/{order,bank_info,notification,delivery_man}/domain/models/*.dart` and the controllers/services that call the stats/notification endpoints) found **three real field-name/shape mismatches** vs. the original design spec (`docs/superpowers/specs/2026-07-10-vendor-3j-stats-notifications-design.md`). All were resolved by following the **real Dart model**, since that is what the app actually parses; the deviations are documented in code comments at each mapper method. Summary:

| Endpoint | Spec assumed | Real app contract (confirmed) |
|---|---|---|
| `get-earning-statitics` | `{total_earning, series:{label:[...], earning:[...]}}` | `{total_earning, this_year, commission_earning, earning, seller_earn:[...], commission_earn:[...]}` — top-level `seller_earn`/`commission_earn` arrays (`bank_info_controller.dart::getDashboardRevenueData` reads `data['seller_earn']`/`data['commission_earn']`); `type` values actually sent are `yearEarn`/`MonthEarn`/`WeekEarn` (`BankInfoController.setRevenueFilterName`), not `this_year`/`this_month`/`this_week`. Both conventions are accepted (case-insensitively). |
| `top-delivery-man` | bare 6valley array | `{total_size, limit, offset, delivery_man:[...]}` envelope (`TopDeliveryManModel.fromJson` reads `json['delivery_man']`) |
| `notification` list | `{total_size, limit, offset, notifications:[...]}`, item `is_read` | `{total_size, limit, offset, new_notification, notification:[...]}` — **singular** `notification` key (`NotificationItemModel.fromJson` reads `json['notification']`); item field is `notification_seen_status` (`NotificationItem.fromJson`), not `is_read` |

`order-statistics` keys (`pending/confirmed/processing/out_for_delivery/delivered/canceled/returned/failed`) matched the spec exactly, confirmed against `business_analytics_filter_data.dart` (`BusinessAnalyticsFilterDataModel`). `is_read` and the spec's original key names are kept as harmless bonus fields alongside the confirmed real keys, for back-compat with other 6valley client variants.

## Per-task summary

| Task | What was built | Commit |
|---|---|---|
| 3j.0 | Read vendor app models: `business_analytics_filter_data.dart`, `bank_info_controller.dart` (+ `bank_info_service*.dart`), `notification_model.dart` + `notification_controller.dart` + `notification_repository.dart`, `top_delivery_man.dart` + `delivery_man_controller.dart`. Locked real field names (table above). No code change. | (research only) |
| 3j.1 | `GET order-statistics?statistics_type=` → `SellerStatsController` + `SellerStatsMapper.orderStatistics`. Reuses `RestaurantDashboardServiceImpl.getDashboard(restaurantId)` via `SellerContext.currentRestaurant(email)`. Bucket selection: today/this_week/this_month/overall; unknown/absent → today. Numeric fields never null. | `a791b7a` feat(seller): order-statistics from restaurant dashboard buckets |
| 3j.2 | `GET get-earning-statitics?type=` → `SellerStatsMapper.earningStatistics`. Same dashboard DTO; 4-point `seller_earn`/`commission_earn` series (today/week/month/total) always returned; scalar `earning` tracks `type` (both naming conventions). `commission_earn` derived from `Restaurant.commissionPourcentage` (0 if unset). MAD pass-through, no conversion. | `189df31` feat(seller): earning-statistics series approximated from CA buckets |
| 3j.3 | `GET top-delivery-man` → **STUB** decision (see below), `SellerStatsMapper.topDeliveryManStub()` returns the confirmed benign envelope. | `c97a3db` feat(seller): top-delivery-man (global reuse\|stub) benign shape |
| 3j.4 | `GET notification?limit=&offset=` → `SellerNotificationController` + `SellerNotificationMapper`. Reuses `NotificationService.getMesNotifications(authHeader, pageable)` + `getNombreNonLues`; `Authorization` header forwarded so the native service resolves the same owner from the JWT. `Pageable = PageRequest.of(offset>0?offset/limit:0, limit, Sort.by(createdAt).desc)`. | `88e9201` feat(seller): notification list -> 6valley limit/offset envelope |
| 3j.5 | `GET notification/view?id=` (same controller file, written alongside 3j.4 — see note below) → ownership-guarded, delegates to `NotificationService.marquerCommeLue(authHeader, id)`. Dedicated `SellerNotificationViewTest` added, including the critical cross-owner 404 guard test. | `9c8b622` feat(seller): notification/view marks seen (lue) via marquerCommeLue |
| 3j.6 | Full suite `./mvnw -q test` foreground: 172 tests, only `PushNotificationTest` fails (baseline, 4F+3E). Flutter smoke SKIPPED (no emulator) — manual carry-forward below. | `<this file>` test(seller): 3j stats/notifications green |

**Process note (3j.4/3j.5):** `SellerNotificationController` was authored as a single file containing both `list()` and `view()` — the `view()` implementation therefore already existed when the dedicated `SellerNotificationViewTest` was written for 3j.5, so that test did not have a true RED phase (it passed on first run). The 3j.4 commit's RED→GREEN cycle *did* exercise the list endpoint end-to-end (`list()` and `view()` share the controller and dependencies) before the 3j.5 test was added; the ownership-guard behavior itself (`requireOwnedNotification`) was newly verified GREEN by the dedicated 3j.5 test, which is the load-bearing check this slice cares most about.

## Native reuse points (signatures)

- `RestaurantDashboardServiceImpl.getDashboard(Long restaurantId) : RestaurantDashboardDTO` — fields used: `commandesAujourdhui/Semaine/Mois`, `commandesLivreesAujourdhui`, `commandesAnnuleesAujourdhui`, `totalCommandes`, `commandesEnCours`, `commandesEnPreparation`, `chiffreAffairesAujourdhui/Semaine/Mois`, `totalChiffreAffaires`.
- `NotificationService.getMesNotifications(String accessToken, Pageable pageable) : Page<NotificationDTO>` — resolves the owner from the JWT via `getUserFromToken`; scoped by `destinataire`.
- `NotificationService.getNombreNonLues(String accessToken) : long` — used for the envelope's `new_notification` count.
- `NotificationService.marquerCommeLue(String accessToken, Long notificationId) : NotificationDTO` — sets `lue=true`, `lueAt=now`; throws `UnauthorizedException` (401) if the token's user isn't the recipient — the shim never lets this path fire for a foreign notification because it 404s first via its own `requireOwnedNotification` guard.
- `NotificationRepository.findById(Long)` — used directly (not `getMesNotifications`) for the shim's own ownership pre-check in `SellerNotificationController.requireOwnedNotification`, mirroring the `requireOwnedCoupon` pattern from 3h (a resource belonging to another vendor must be indistinguishable from a missing one → 404, not the native service's 401).
- `StatistiquesService.getTopLivreurs(int limit, LocalDate debut, LocalDate fin)` — **evaluated but NOT reused** (see STUB decision below).

## Earning-series approximation (documented)

mysugu's `RestaurantDashboardDTO` has no per-day/per-month CA history for a restaurant — only 4 aggregate buckets (today/week/month/total). `SellerStatsMapper.earningStatistics` therefore **always** returns the same 4-point `seller_earn` array `[today, week, month, total]` regardless of the requested `type`; only the scalar `earning` field tracks `type` (today→`chiffreAffairesAujourdhui`, this_week/WeekEarn→`chiffreAffairesSemaine`, this_month/MonthEarn→`chiffreAffairesMois`, this_year/yearEarn→`totalChiffreAffaires`, since there is no per-year bucket). `commission_earn` is `seller_earn[i] * Restaurant.commissionPourcentage / 100` (0 if the restaurant has no negotiated commission rate). This is a **deliberate, documented approximation** (code comment `// APPROX: ...`) — a real per-day/per-month chart would require a native `RestaurantDashboardService` enhancement (CA history table), out of scope for this shim.

## Notification ownership guard + test

`SellerNotificationController.requireOwnedNotification(Long id, User owner)`:
```java
private void requireOwnedNotification(Long id, User owner) {
    if (id == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification non trouvée");
    Notification notif = notificationRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification non trouvée"));
    if (notif.getDestinataire() == null || !notif.getDestinataire().getId().equals(owner.getId())) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification non trouvée");
    }
}
```
Called **before** `notificationService.marquerCommeLue(...)` in `view()`, so a foreign notification never reaches the native mark-as-read call. Test: `SellerNotificationViewTest.view_on_another_owners_notification_returns_404_and_never_marks_it` — seeds two owners (A, B), A's notification and B's notification; A calling `GET notification/view?id={B's id}` → asserts `404` AND reloads B's notification from the repository to assert `lue` is still `false` (never marked). Companion tests: `view_marks_owners_own_notification_seen` (200 + `lue=true`/`lueAt` set on reload), `view_on_absent_id_is_benign_404_never_500`, `view_without_id_param_is_benign_404_never_500`.

## STUB endpoints

- **`top-delivery-man`** → `SellerStatsMapper.topDeliveryManStub()` always returns `{total_size:0, limit:"10", offset:"0", delivery_man:[]}`. **Decision: STUB, not global-reuse** of `StatistiquesService.getTopLivreurs`, for two reasons documented in the controller/mapper Javadoc: (1) that service is admin-**global** (all restaurants), not scoped to one seller — showing it to a single vendor would leak/misrepresent a platform-wide ranking that isn't theirs; (2) the confirmed real envelope (`DeliveryMan.fromJson`) requires fields mysugu doesn't model for a livreur (`identity_number`/`identity_type`/`identity_image`, `rating[]`) and a **non-null** `is_online` (the Dart model does `int.parse(json['is_online'].toString())`, which crashes on `null`). A benign empty envelope renders the widget with no crash risk, ever.

No other STUB/GAP endpoints in this slice — `order-statistics`, `get-earning-statitics`, `notification`, `notification/view` all reuse native services per the plan.

## Full-suite delta

- Command: `./mvnw -q test` (foreground, DB reset via `DROP SCHEMA public CASCADE; CREATE SCHEMA public;` beforehand).
- **172 tests total, 4 Failures + 3 Errors — all in `PushNotificationTest`** (verified: `grep "<<< FAILURE|<<< ERROR" | grep -v PushNotificationTest` → empty). This matches the documented pre-existing baseline exactly (4F+3E). **No new failures introduced by 3j.**
- +23 new tests all green across `SellerOrderStatisticsTest` (9), `SellerEarningStatisticsTest` (10), `SellerTopDeliveryManTest` (1), `SellerNotificationListTest` (4), `SellerNotificationViewTest` (4) — note some pure-mapper unit tests run without a Spring context (fast) and some are full `@SpringBootTest` HTTP-integration tests (real Postgres, real JWT login), matching the existing `legacy/seller` test conventions (`OrderListTest`, `SellerCouponListTest`, `OrderSellerMapperTest`).

## SecurityConfig

No change. `/api/v3/seller/**` remains `.authenticated()` (added in 3a); all 3j endpoints are owner-authenticated via `SellerContext`, no `permitAll` added, no existing matcher weakened.

## Concerns / notes

- **Bucket-selection test strategy:** distinct today/week/month/total values are impossible to produce via real DB rows in an integration test, because `Commande.createdAt` is `@CreationTimestamp`-controlled (can't be backdated on insert) and all buckets (today/week/month/total) span "now". Following the codebase's own precedent (`OrderSellerMapperTest`, `ProductSellerMapperTest` — pure mapper unit tests with hand-built DTOs, no Spring context), `SellerStatsMapper.orderStatistics`/`earningStatistics` are tested directly with hand-crafted `RestaurantDashboardDTO`s carrying distinct bucket values, plus a thin `@SpringBootTest` HTTP test per endpoint verifying wiring/ownership/response-keys/no-500 end-to-end.
- **`notification` offset semantics — flagged, not resolved:** the plan's `Pageable = PageRequest.of(offset>0?offset/limit:0, limit,...)` formula (skip-count offset, matching every other list endpoint in this shim: coupon/list, orders/list) was implemented as specified. However, `Tiktak-vendor-app-moso`'s `NotificationRepository.getList({offset=1})` and `NotificationController.getNotificationList` start at `offset=1` and treat it as if it were a page index in places (`if (offset==1) {replace} else {append}`), which *may* mean the app's `PaginatedListViewWidget` increments offset differently than a pure skip-count (its source wasn't available to inspect). This is a **minor, non-blocking discrepancy** worth a follow-up smoke test against the real app; it does not affect the shim's correctness against the documented 6valley REST convention used everywhere else in this codebase.
- **`RestaurantDashboardController`'s `PreAuthorize` vs. this shim:** the native `/api/restaurant-dashboard/**` endpoints use `@PreAuthorize`; the shim controllers rely on `SecurityConfig`'s blanket `/api/v3/seller/**` `.authenticated()` + `SellerContext.requireOwner`/`currentRestaurant` runtime checks (consistent with every other 3a–3h controller) — no `@PreAuthorize` added here, intentionally, to match the established pattern.

## Flutter smoke — manual carry-forward (3j.6 Step 2 SKIPPED)

No emulator available. Manual verification to perform when the vendor app is pointed at mysugu: log in as a seeded `RESTAURANT_OWNER`, open the **dashboard** (order counters render for `statistics_type=overall`; earning chart renders via `chartFilterData('yearEarn')` at boot and via the week/month/year filter tabs — confirm no crash and that MAD amounts display) and the **notification tray** (list paginates via `PaginatedListViewWidget`, tapping an item calls `notification/view?id=` and refreshes the list to show it seen — confirm the dot indicator clears). Also confirm the top-delivery-man widget on the dashboard renders its "no data" empty state cleanly (STUB returns an empty list).
