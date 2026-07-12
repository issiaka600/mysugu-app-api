# Vendor Slice 3f — Finances — Implementation Report

Date: 2026-07-12 · Branch: `feat/vendor-shim` · Base: `e064535` (3j green)

## Summary

All five 3f tasks (3f.0–3f.4 implementation, 3f.5 verification) are done and committed. `transactions`
surfaces real `FacturationRestaurant` payment history, owner-scoped and empty-safe. Everything else
(`withdraw-method-list`, `balance-withdraw`, `close-withdraw-request`, `refund/*`) is a benign STUB
per the spec's SCOPE DECISION — no native seller wallet/withdraw/refund domain exists in mysugu.

**Major finding (3f.0):** the real vendor-app Dart models expect **raw JSON arrays**, not the
`{total_size, limit, offset, <key>:[...]}` envelope the design doc assumed, for `transactions`,
`withdraw-method-list`, and `refund/list`. This is confirmed by the app code itself doing
`apiResponse.response!.data.forEach((row) => Model.fromJson(row))` — `List.forEach` takes a single
positional argument, which only type-checks in Dart if `data` is a `List`. All three endpoints were
implemented as raw arrays, deviating from the design doc's envelope, per the instruction to follow the
real Dart models when they conflict with the spec.

## Per-task summary

### 3f.0 — Confirm vendor-app finance models (read-only)
Read `Tiktak-vendor-app-moso/lib/features/{transaction,wallet,refund,profile}/**`. See "Dart-model
deviations" section below for the full list of confirmed contract details.

### 3f.1 — `GET /api/v3/seller/transactions` (real data)
- `SellerFinanceController.transactions` resolves the owner via `SellerContext.requireOwner`, then
  looks up the restaurant directly via `RestaurantRepository.findByOwnerId` (NOT
  `SellerContext.currentRestaurant`, which throws 404 — bypassed on purpose so a missing restaurant
  degrades to an empty array instead of an error).
- Calls `FacturationRestaurantServiceImpl.getHistoriquePaiements(restaurantId, Pageable.unpaged())` →
  `Page<PaiementRestaurantDTO>`, mapped via `SellerFinanceMapper.toTransactionRow`.
- Mapping: `id` = paiement id, `seller_id` = restaurant id (never null), `admin_id` = null,
  `amount` = `montantTotal` (BigDecimal, MAD pass-through, defaults to `0` if somehow null),
  `transaction_note` = `note` (fallback `reference`), `approved` = 1 iff
  `statut == EFFECTUE` else 0, `created_at`/`updated_at` = `datePaiement` (fallback `createdAt`,
  fallback `now()` — **never null**).
- Empty-safety: owner with no restaurant → `[]`; owner with restaurant but no `PaiementRestaurant`
  rows → `[]`. Neither path touches `ParametresPaiementRestaurant` at all (not required by
  `getHistoriquePaiements`), so "no `ParametresPaiementRestaurant`" is a non-issue for this endpoint.
- Test: `SellerTransactionsTest` — one real paiement mapped correctly, no-paiements owner → `[]`,
  no-restaurant owner → `[]`. All green.

### 3f.2 — `GET /api/v3/seller/withdraw-method-list` (benign enum echo)
- One row per `ModeVersementRestaurant` value (`CASH`, `VIREMENT_BANCAIRE`): `{id, method_name,
  method_fields: [], is_default, is_active, created_at, updated_at}`.
- `is_active` is always a JSON boolean (`true`) — `WithdrawModel.fromJson` does
  `json['is_active'] ? 1 : 0` with **no null guard**, so a null or int here would crash the app.
- `// STUB: no configurable seller withdraw methods; enum echo only.`
- Test: `SellerWithdrawMethodsTest` — green.

### 3f.3 — `balance-withdraw` + `close-withdraw-request` (benign no-op)
- Both return `200 {message}`, no persistence (no writable "seller withdraw request" entity exists
  natively to persist into).
- `close-withdraw-request`'s real path (confirmed via `app_constants.dart`'s `cancelBalanceRequest`)
  is `/api/v3/seller/close-withdraw-request` — **no `{id}` suffix**, unlike
  `/withdraw/close-request/{id}` assumed by the design doc. Note: this route is defined in the app's
  constants but is not currently called by any vendor-app screen (dead code in the client) — still
  implemented for forward-compatibility.
- `// STUB: no seller balance/withdrawal natively (spec 3f SCOPE DECISION). Needs product-owner
  sign-off for a real build.`
- Test: `SellerWithdrawStubTest` — green.

### 3f.4 — `refund/*` (benign empty/no-op)
- `GET refund/list` → `[]` (raw array, not enveloped).
- `GET refund/refund-details?order_details_id=` → single benign JSON object, **query param**
  `order_details_id`, not a path variable `{id}` (confirmed via
  `refund_repository.dart::getRefundReqDetails`). All numeric fields (`product_price`,
  `product_total_discount`, `product_total_tax`, `subtotal`, `coupon_discount`, `refund_amount`)
  default to `0`, never `null` — `RefundDetailsModel.fromJson` calls `.toDouble()` on each with no
  null guard.
- `POST refund/refund-status-update` → `200 {message}`.
- `// STUB: no native seller wallet/withdraw/refund (spec 3f SCOPE DECISION, umbrella §4).`
- Test: `SellerRefundStubTest` — green.

### 3f.5 — Full suite + smoke
- `./mvnw -q test` (foreground): **181 tests run, 4 failures + 3 errors, all in
  `PushNotificationTest`** — the known pre-existing baseline failure (unrelated commande/livreur
  auto-assign flow). No new failures introduced by 3f. Delta vs baseline: **zero**.
- Flutter vendor-app finance-screen smoke (plan step 2): **SKIPPED — no emulator available in this
  environment.** Recorded as a manual carry-forward (see Concerns).

## Dart-model deviations vs the 3f design doc (confirmed 3f.0)

| Endpoint | Design doc assumed | Real Dart contract | Source |
|---|---|---|---|
| `GET transactions` | Envelope `{total_size, limit, offset, transactions:[...]}` | **Raw JSON array** | `transaction_controller.dart::getTransactionList` does `apiResponse.response!.data.forEach(...)` |
| `GET transactions` row | `{id, amount, transaction_type, reference, status, created_at}` | `{id, seller_id, admin_id, amount, transaction_note, approved, created_at, updated_at}` | `transaction_model.dart::TransactionModel.fromJson` |
| `GET transactions` row nullability | not specified | `seller_id`/`amount` parsed via `int.parse(x.toString())`/`double.parse(x.toString())` **without a null guard** → must never be null. `created_at` used unguarded (`!`) by month filter → must never be null. | same file + `transaction_controller.dart::filterTransaction` |
| `GET withdraw-method-list` | array, keys TBD in 3f.0 | Confirmed: `{id, method_name, method_fields:[], is_default, is_active, created_at, updated_at}` | `withdraw_model.dart::WithdrawModel.fromJson` |
| `GET withdraw-method-list` nullability | not specified | `is_active` read as `json['is_active'] ? 1 : 0` **without a null guard** → must always be a JSON boolean | same file |
| `POST close-withdraw-request` | `/withdraw/close-request/{id}` | `/close-withdraw-request` (no id suffix); unused by current app UI | `app_constants.dart::cancelBalanceRequest` |
| `GET refund/list` | Envelope `{total_size, limit, offset, refunds:[...]}` | **Raw JSON array** | `refund_controller.dart::getRefundList` does `apiResponse.response!.data.forEach(...)` |
| `GET refund/refund-details/{id}` | path variable `{id}` | Query param `?order_details_id=` | `refund_repository.dart::getRefundReqDetails` |
| `GET refund/refund-details` nullability | "null-safe fields" | All numeric fields parsed via `.toDouble()` **without a null guard** → must never be null (default `0`) | `refund_details_model.dart::RefundDetailsModel.fromJson` |

## Files

- `src/main/java/ma/mysuguclientapp/legacy/seller/controller/SellerFinanceController.java` (new)
- `src/main/java/ma/mysuguclientapp/legacy/seller/mapper/SellerFinanceMapper.java` (new)
- `src/test/java/ma/mysuguclientapp/legacy/seller/SellerTransactionsTest.java` (new)
- `src/test/java/ma/mysuguclientapp/legacy/seller/SellerWithdrawMethodsTest.java` (new)
- `src/test/java/ma/mysuguclientapp/legacy/seller/SellerWithdrawStubTest.java` (new)
- `src/test/java/ma/mysuguclientapp/legacy/seller/SellerRefundStubTest.java` (new)

No `SecurityConfig` changes were needed — `/api/v3/seller/**` was already fully authenticated by
slice 3a; nothing weakened.

## STUB endpoints (per spec 3f SCOPE DECISION / umbrella §4 GAP)

- `GET /api/v3/seller/withdraw-method-list` — enum echo, no configurable methods.
- `POST /api/v3/seller/balance-withdraw` — benign success no-op, no persistence.
- `POST /api/v3/seller/close-withdraw-request` — benign success no-op, no persistence.
- `GET /api/v3/seller/refund/list` — always empty.
- `GET /api/v3/seller/refund/refund-details` — benign fixed object, never 404.
- `POST /api/v3/seller/refund/refund-status-update` — benign success no-op.

All GAP endpoints carry a `// STUB: ...` comment referencing the spec's SCOPE DECISION and note that
a real build needs product-owner sign-off.

## Concerns / carry-forward

1. **Flutter vendor-app finance-screen smoke (plan 3f.5 step 2) was not run** — no emulator/device
   available in this environment. Manual carry-forward: boot mysugu, point the vendor app at it,
   log in as a seeded `RESTAURANT_OWNER`, and open finance/wallet/withdraw/refund screens to confirm
   no crash (should render transaction history or an empty state, a 2-row withdraw-method list, and
   an empty refund list per the contracts implemented here).
2. **`close-withdraw-request` is currently dead code in the vendor app** (constant defined, never
   called by any screen) — implemented anyway for forward-compatibility and design-doc parity, but
   worth flagging that it has no live caller to validate against today.
3. The GAP items (seller wallet, seller-initiated withdrawal, refund workflow) remain explicitly
   out of scope per the SCOPE DECISION and need product-owner sign-off before any real build.
