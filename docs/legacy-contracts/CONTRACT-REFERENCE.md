# Legacy Deliverer API — Endpoint-by-Endpoint Contract Reference

Single source of truth for the MySugu backend compatibility shim reproducing the old
6valley `/api/v2/delivery-man/*` + `/api/v1/config` contract for the Tiktak deliverer app
(Flutter, branch `moso`). Reconstructed from the Dart `fromJson` models (what the app parses)
cross-checked against the Laravel controller source (what the server returned). 6valley server
is down — no live capture.

## Legend
- **[load-bearing]** — the Dart model reads this key (null-guarded). Must be present with the right type, but a missing/null value degrades gracefully (default applied).
- **[fragile]** — the Dart model reads this key **without a null-guard** (e.g. `int.parse(x.toString())`, `jsonDecode(x)`, `x ? 1 : 0`, `X.fromJson(json[k])` with no null check). If absent/null/wrong-type → `fromJson` **throws** → screen crashes. MUST always be present and valid.
- **[ignored]** — present in 6valley output but the Dart model never reads it. Safe to omit or include.
- **PUT\*** — app sends **POST** with a body field `_method: put` (Laravel method spoofing). In this shim map as `@PostMapping` (the real HTTP method is POST) + `@JsonIgnoreProperties(ignoreUnknown=true)` so the `_method` field is ignored. No method-translation filter needed.
- All money values emitted as raw MAD numbers (no currency conversion). Dart parses via `parseToDouble`/`double.parse(String)` so numeric or numeric-string both work, unless marked fragile.
- Laravel-6 Eloquent serializes timestamps as `"Y-m-d H:i:s"` strings; the app treats all `created_at`/`updated_at`/`expected_delivery_date` as opaque `String`.
- 6valley `order_status` enum: `pending, confirmed, processing, out_for_delivery, delivered, canceled, returned`. `payment_status`: `paid|unpaid`. `payment_method`: e.g. `cash_on_delivery`. Emit the 6valley string forms.

Source roots:
- App: `Tiktak-deliverer-app/lib/…` — constants at `utill/app_constants.dart`.
- Backend: `atlantique-release-backend/app/Http/Controllers/RestAPI/v2/delivery_man/…` + `…/v1/ConfigController.php`.
- Routes: `routes/rest_api/v2/api.php:103-162`, `routes/rest_api/v1/api.php:54-56`.

Endpoints documented: **45** (44 paths; `/commission/{type}` counts GET + POST).

---

# 1. Auth & Config

## 1.1 POST `/api/v2/delivery-man/auth/login`
- **Request** (JSON, `auth_repository.dart:19`): `{ "country_code": String, "phone": String, "password": String }`.
- **Response (success 200)** — app reads only `token`. Consumed as opaque string (stored, echoed as `Authorization: Bearer <token>`).
  - `token` **[fragile]** String. (App: `LoginController@login` returns `{'token': Str::random(50)}`; shim returns a JWT.)
- **Response (error 401/403)**: `{ "errors": [ { "code": "auth-001", "message": String } ] }` — app shows `errors[0].message`.
- **6valley shape**: `LoginController.php:20-57`. Validates `phone` required + `password` min:8; checks `is_active==1` & `Hash::check`; country_code mismatch → 403 `errors[]`.
- **Notes**: shim must issue MySugu JWT (§4 techspec). App never decodes the token.

## 1.2 POST `/api/v2/delivery-man/auth/forgot-password`
- **Request** (`auth_repository.dart:181`): `{ "country_code": String?, "phone": String? }`.
- **Response 200**: `{ "message": String }`. Error 403: `{ "errors": [ { "code": "not-found", "message": String } ] }`. App reads `message` / `errors`.
- **6valley shape**: `LoginController.php:59-142`. Creates `PasswordReset` OTP `rand(1000,9999)`, sends by email/phone per `forgot_password_verification`.
- **Notes**: OTP is 4 digits, 2-min expiry. Shim: email + FCM channel.

## 1.3 POST `/api/v2/delivery-man/auth/verify-otp`
- **Request** (`auth_repository.dart:208`): `{ "otp": String, "phone": String? }`. NOTE: the app passes the OTP code in the field named `otp` (variable is confusingly named `countryCode`).
- **Response 200**: `{ "message": String, "phone": String }`. Error 403: `{ "message": String }`.
- **6valley shape**: `LoginController.php:144-170`. Looks up `PasswordReset` by token=otp; `diffInMinutes > 2` → 403 `OTP_expired`.
- **Notes**: app reads `message`; `phone` echoed for the reset step.

## 1.4 POST `/api/v2/delivery-man/auth/reset-password`
- **Request** (`profile_repository.dart:36`): `{ "phone": String?, "password": String, "confirm_password": String }`.
- **Response 200**: `{ "message": String }`. Error 403: `{ "errors": [...] }`.
- **6valley shape**: `LoginController.php:173-191`. Validates `password` `same:confirm_password|min:8`; updates DeliveryMan password; deletes PasswordReset row.

## 1.5 PUT\* `/api/v2/delivery-man/language-change`
- **Request** (POST, `auth_repository.dart:24`): `{ "current_language": String, "_method": "put" }`.
- **Response 200**: `{ "message": String }`. App ignores body (fire-and-forget).
- **6valley shape**: `DeliveryManController.php:910-917` sets `delivery_man.app_language`.

## 1.6 PUT\* `/api/v2/delivery-man/update-fcm-token`
- **Request** (POST, `auth_repository.dart:68` & `194`): `{ "_method": "put", "fcm_token": String }` with headers `Authorization: Bearer <token>`, `Content-Type: application/json`. On logout it also sends `fcm_token: "no"` (`auth_repository.dart:111`).
- **Response 200**: `{ "message": String }`. App ignores body.
- **6valley shape**: `DeliveryManController.php:512-528`. Validates `fcm_token` required.
- **Notes**: App bug — `searchConversationListUri` (`app_constants.dart:27`) also points here; that path (`chat_repository.dart:18` `searchConversationList`) is effectively **dead** in the moso build (chat search UI uses `chatSearch` / `messages/search`, see 5.16). Serve fcm-token harmlessly for GET-with-`?name=` too.

## 1.7 GET `/api/v1/config`
- **Request** (`splash_repository.dart:14`): none.
- **Response**: large object. `ConfigModel.fromJson` (`config_model.dart:120-186`). **Minimal config the app needs to boot without crashing** — every non-null-guarded read:
  - `language` **[fragile]** — `json['language'].cast<String>()` (`config_model.dart:146`). MUST be a JSON **array of strings** (e.g. `["fr","en"]`). ⚠ 6valley `ConfigController.php:38-45,131` returns `language` as an array of **objects** `{code,name}` — that would make `.cast<String>()` blow up. **Shim MUST emit `language` as `List<String>`** (list of language codes). FLAG: model↔6valley disagree.
  - `unit` **[fragile]** — `json['unit'].cast<String>()` (`config_model.dart:153`). MUST be a JSON array of strings (may be `[]`).
  - `company_phone` **[load-bearing]** — read via `.toString()` (`:164`); null tolerated (becomes `"null"`). Provide a string.
  - `decimal_point_settings` **[load-bearing]** — parsed with `int.parse` only when non-null/non-empty (`:166`). If present, must be int-parseable; safe to send `2`.
  - `system_default_currency` int; `digital_payment` bool; `about_us`/`privacy_policy`/`terms_&_conditions` (or `terms_conditions`) String; `currency_symbol_position` String; `maintenance_mode` bool; `shipping_method`/`currency_model` String; `email_verification`/`phone_verification` bool; `country_code` String; `forgot_password_verification` String; `company_email` String; `company_logo` String; `upload_picture_on_delivery` int/String; `order_verification` int/String — all **null-guarded** ([load-bearing], omittable).
  - `base_urls` object (`BaseUrls.fromJson`, `config_model.dart:242`) — null-guarded; if present the app reads `product_image_url, product_thumbnail_url, brand_image_url, customer_image_url, banner_image_url, category_image_url, review_image_url, seller_image_url, shop_image_url, notification_image_url, delivery_man_image_url` (all plain assignments, null-tolerant). The **`delivery_man_image_url`** and **`chatting_image_url`** paths matter for rendering driver/chat images — supply the MinIO base.
  - `static_urls`, `faq[]`, `currency_list[]`, `colors[]`, `social_login[]` — all null-guarded lists/objects.
  - Everything else in `ConfigController.php:82-181` is **[ignored]** by this app.
- **6valley shape**: `ConfigController.php:18-182`.
- **Minimal safe body**:
```json
{ "language": ["fr","en"], "unit": [], "system_default_currency": 1,
  "currency_symbol_position": "right", "maintenance_mode": false,
  "email_verification": false, "phone_verification": false,
  "forgot_password_verification": "email", "decimal_point_settings": 2,
  "company_phone": "", "company_email": "", "company_logo": "",
  "base_urls": { "delivery_man_image_url": "<minio>/delivery-man",
    "chatting_image_url": "<minio>/chatting", "customer_image_url": "<minio>/profile",
    "shop_image_url": "<minio>/shop", "notification_image_url": "<minio>/notification" },
  "upload_picture_on_delivery": 1, "order_verification": 1 }
```
- **Notes**: must never 500. `language` and `unit` presence-as-array is the only crash risk.

---

# 2. Profile

## 2.1 GET `/api/v2/delivery-man/info`
- **Request** (`profile_repository.dart:18`): none.
- **Response**: flat delivery_man object + money/counts. `UserInfoModel.fromJson` (`userinfo_model.dart:66-187`):
  - `id` [load-bearing] int; `f_name`,`l_name`,`phone`,`email`,`image` [load-bearing] String.
  - `is_online` **[fragile]** — `int.parse(json['is_online'].toString())` (`:73`). MUST be present, int-parseable (0/1). ⚠ mapped into the app's `isActive`.
  - `identity_number`,`identity_type` [load-bearing] String.
  - `identity_image` **[fragile]** — `jsonDecode(json['identity_image'])` (`:76`). MUST be a **JSON-encoded string** (e.g. `"[]"` or `"[{\"image_name\":\"x.png\"}]"`). Not a raw array, not null.
  - `created_at`,`updated_at`,`country_code`,`address` [load-bearing]; `address` null→`''`.
  - Money (all null-guarded, default 0; numeric or numeric-string): `withdrawable_balance`, `current_balance`, `cash_in_hand`, `pending_withdraw`, `total_withdraw`, `total_deposit` [load-bearing].
  - `total_earn` [load-bearing] — read from **first present of**: `delivery_charge_earned` | `total_delivery_charge` | `total_delivery_earning` | `delivered_shipping_cost` | `delivery_man_charge_earned` | `total_earn` (`:125-130`). Emit `total_earn`.
  - Counts (int, null→0): `completed_delivery`, `total_delivery`, `pause_delivery`, `pending_delivery` [load-bearing].
  - `bank_name`,`branch`,`account_no`,`holder_name` [load-bearing] String (only set if non-null).
  - `average_rating` — present in 6valley output but **[ignored]** by `UserInfoModel`. Safe to include.
- **6valley shape**: `DeliveryManController.php:48-75` — returns the full `$request['delivery_man']` array merged with computed `withdrawable_balance,current_balance,cash_in_hand,pending_withdraw,total_withdraw,total_earn,completed_delivery,pending_delivery,total_delivery,pause_delivery,total_deposit,average_rating`. `identity_image` stored as JSON string; `is_online` an int column.
- **Notes**: crash-critical keys `is_online` (as `0|1`) and `identity_image` (as JSON **string**) — always emit.

## 2.2 GET `/api/v2/delivery-man/profile-dashboard-counts`
- **Response**: `DeliverymanWallet` row + `total_delivery_count`, `delivered_orders`. No dedicated Dart model in moso reads it — structurally **[ignored]**; return the 6valley shape.
- **6valley shape**: `DeliveryManController.php:603-612`.

## 2.3 PUT\* (multipart) `/api/v2/delivery-man/update-info`
- **Request** (multipart POST, `profile_repository.dart:48-69`): fields `_method=put`, `f_name`, `l_name`, `address`, `password`, `confirm_password`; optional file part `image`.
- **Response 200**: `{ "message": String }`.
- **6valley shape**: `DeliveryManController.php:633-673`. Validates `f_name`,`l_name` required, `password` `nullable|same:confirm_password|min:8`; stores image to `delivery-man/`.

## 2.4 POST `/api/v2/delivery-man/bank-info` (route is PUT; app sends `_method:" put"`)
- **Request** (`profile_repository.dart:73`): `{ "bank_name", "branch", "account_no", "holder_name", "_method": " put" }` (leading space in `" put"` is a spoof quirk; it's a plain POST anyway).
- **Response 200**: `{ "message": String }`.
- **6valley shape**: `DeliveryManController.php:675-688`.

## 2.5 PUT\* `/api/v2/delivery-man/is-online`
- **Request** (POST, `profile_repository.dart:25` & `84`): `{ "is_online": int(0|1), "_method": "put" }`.
- **Response 200**: `{ "message": String }`. Error 403 (has active order): `{ "message": String }`.
- **6valley shape**: `DeliveryManController.php:768-785`. If going offline with an `out_for_delivery`+`is_pause=0` order → 403.

## 2.6 POST `/api/v2/delivery-man/change-status`
- **Request**: `{ "status": <0|1> }`. Not wired to a moso repository call; present for completeness.
- **Response 200**: string `"Status changed successfully"`.
- **6valley shape**: `DeliveryManController.php:614-631`. Sets `is_active`.

## 2.7 GET `/api/v2/delivery-man/emergency-contact-list`
- **Response**: `{ "contact_list": [ ContactList ] }` (`emergency_contact_model.dart:6`). Each `ContactList` (`:43`): `id` int, `user_id` int, `name` String, `phone` String, `created_at` String, `updated_at` String, `country_code` String — all **[load-bearing]**, null-guarded. Empty list ok.
- **6valley shape**: `DeliveryManController.php:737-745`.

---

# 3. Orders & FCFS

> `current-orders`/`all-orders` return a **bare JSON array** (no pagination wrapper). Parsed into `List<OrderModel>` (`order/domain/models/order_model.dart` `OrderModel.fromJson:72`). Heavily null-guarded; only fragile spot is nested `Shop.seller_id`.

## 3.1 GET `/api/v2/delivery-man/current-orders`
- **Response**: `[ OrderModel ]`. Keys (all null-guarded unless noted): `id`,`customer_id`,`customer_type`,`payment_status`,`order_status`,`payment_method`,`transaction_ref`; `order_amount` (double.tryParse); `created_at`,`updated_at`,`discount_amount`,`discount_type`,`coupon_code`,`shipping_method_id`,`shipping_cost`,`order_group_id`,`verification_code`,`seller_id`,`seller_is`,`delivery_man_id`,`order_note`,`expected_delivery_date`,`deliveryman_charge`,`is_pause`(bool),`is_guest`(bool); `customer`→Customer (guarded); `billing_address_data`/`shipping_address`→ShippingAddress (guarded); `seller`→SellerInfo with `shop`→Shop where **`seller_id` [fragile]** `int.parse` (`:444`); `is_shipping_free`/`shipping_free` bool; `total_commission`(+aliases), `seller_total`(+aliases) parseToDouble.
- **6valley shape**: `DeliveryManController.php:77-86` → `Order::with(['shippingAddress','customer','seller.shop'])` where `order_status IN (pending,processing,out_for_delivery,confirmed)` and `delivery_man_id=me`, ordered `expected_delivery_date asc`. Shim: for FCFS ALSO surface unassigned-claimable orders here (§8).
- **Notes**: bare array. Any emitted `seller.shop` MUST include numeric `seller_id`.

## 3.2 GET `/api/v2/delivery-man/all-orders`
- **Request** (query, `order_repository.dart:21`): `?status=<orderStatus|''>&start_date=&end_date=&search=&is_pause=<int>`.
- **Response**: `[ OrderModel ]` — identical to 3.1.
- **6valley shape**: `DeliveryManController.php:442-471` (search by id or customer.phone; status; is_pause; start/end on `created_at`; `latest()`). Bare array. Backs the order-history search box.

## 3.3 GET `/api/v2/delivery-man/order-details?order_id=<id>`
- **Response 200**: `{ "success": true, "order": {…}, "details": [ OrderDetail ], "customer": {…}, "shipping_address": {…} }`.
  - `success` **[fragile]** must be boolean `true`.
  - `order` **[fragile]** must be an object (app does `order['details']=…` then `OrderModel.fromJson(order)`).
  - `details[]`→`OrderDetailsModel` (`order_details_model.dart:79`): `id,order_id,product_id,seller_id`; `product_details`→Product (only if non-null); `qty` int; `price/tax/discount` parseToDouble; `delivery_status,payment_status,created_at,updated_at,shipping_method_id,variant,shipping_free`.
  - `Product.fromJson` fragile inner reads when present: `unit_price/purchase_price/tax/discount .toDouble()` (guarded by !=null), `Variation.price.toDouble()`, `ChoiceOptions.options.cast<String>()`. `images` null→`[]`.
  - top-level `customer`,`shipping_address` merged into order.
- **6valley shape**: `DeliveryManController.php:279-316`. Ownership check (403 if another driver). 404 `{success:false,message}` if not found.
- **Notes**: `product_details` must yield keys Product tolerates (numeric `unit_price/tax/discount` when present, `images` list of strings, `variation[].price` numeric, `choice_options[].options` list-of-strings).

## 3.4 GET `/api/v2/delivery-man/search?search=<q>`
- **Not invoked by the moso build** (search uses `all-orders`). Provide for safety: bare `[ OrderModel ]`, or string `"No Result Found"` HTTP 400 when empty.
- **6valley shape**: `DeliveryManController.php:575-601`.

## 3.5 GET `/api/v2/delivery-man/order-list-by-date`
- Constant `orderListFilterByDate` exists but is **never called**. 6valley route maps to a **missing** controller method (`order_list_date_filter`) → would 500. **Response shape INDETERMINATE.** Safe shim: mirror `all-orders` bare-array filtered by date, or leave unimplemented.

## 3.6 POST `/api/v2/delivery-man/{orderId}/accept` (FCFS claim)
- **Request** (`order_repository.dart:29`): path `orderId`; body `{ "order_id": orderId }`.
- **Response 200**: `{ "message": String, "order": { …Order fresh, with deliveryMan } }`. App treats 200 as success (message-based; does not fragile-deserialize `order` at the call site).
- **Error**: 404/409/403 `{ "message": String }` (not_found / already_taken / already_have_active_order).
- **6valley shape**: `DeliveryManController.php:322-386`. Row-locked claim; guards `order_status IN (pending,confirmed,processing)`, one-active-order rule; sets `delivery_man_id`, flips `pending→confirmed`; fires customer/seller/too-late notifications.

---

# 4. Delivery Lifecycle

All PUT\* endpoints return `{ "message": String }` (200) or `{ "errors": [...] }` (403); the app reads `message`/`errors` only.

## 4.1 PUT\* `/api/v2/delivery-man/update-order-status`
- **Request** (POST): `{ "order_id": int, "status": String, "_method": "put" }`; on cancel also `"cause": String`. `status` ∈ `{delivered, canceled, returned, out_for_delivery}`.
- **Response 200**: `{ "message": "Order status updated successfully!" }`; already-delivered → `{ "success": 0, "message": "order is already delivered." }`.
- **6valley shape**: `DeliveryManController.php:142-224`. Shim: map `out_for_delivery→EN_COURS`, `delivered→LIVREE`, `canceled→ANNULEE`; wire gains + caisse (§7).

## 4.2 PUT\* `/api/v2/delivery-man/update-expected-delivery`
- **Request**: `{ "order_id": int, "expected_delivery_date": String, "_method": "put", "cause": String? }`.
- **Response 200**: `{ "message": String }`; already-delivered → `{ "success":0, "message":… }`.
- **6valley shape**: `DeliveryManController.php:226-252`.

## 4.3 PUT\* `/api/v2/delivery-man/order-update-is-pause`
- **Request**: `{ "order_id": int, "is_pause": int(0|1), "_method": "put", "cause": String? }`.
- **Response 200**: `{ "message": String }`; already-delivered → `{ "success":0,… }`.
- **6valley shape**: `DeliveryManController.php:254-277`.

## 4.4 PUT\* `/api/v2/delivery-man/update-payment-status`
- **Request**: `{ "order_id": int, "payment_status": "paid", "_method": "put" }`.
- **Response 200**: `{ "message": String }`; not found → 404 `{ "errors":[{"code":"order","message":…}] }`.
- **6valley shape**: `DeliveryManController.php:486-510`.

## 4.5 POST `/api/v2/delivery-man/verify-order-delivery-otp`
- **Request**: `{ "order_id": int, "verification_code": String }`.
- **Response 200**: `{ "message": String }`; wrong code → 403 `{ "message": String }`.
- **6valley shape**: `DeliveryManController.php:852-863`. Compares `verification_code`; sets `verification_status=1`.

## 4.6 POST `/api/v2/delivery-man/resend-verification-code`
- **Request**: `{ "order_id": int }`.
- **Response 200**: `{ "message": String }`; failure → 403 `{ "message": String }`.
- **6valley shape**: `DeliveryManController.php:887-908`. Regenerates 6-digit code `rand(100000,999999)`, FCM to customer.

## 4.7 POST (multipart) `/api/v2/delivery-man/order-delivery-verification`
- **Request** (multipart): field `order_id` (String) + file parts `image[]` (key `image`).
- **Response 200**: `{ "message": "successfully_uploaded" }`; validation → 403 `{ "errors":[...] }`.
- **6valley shape**: `DeliveryManController.php:866-884`. Stores each to `delivery-man/verification-image/` (`PreuveLivraison` → MinIO in shim).

---

# 5. Location, Money, Reviews, Notifications, Chat

## 5.1 POST `/api/v2/delivery-man/record-location-data`
- **Request**: `{ order_id, longitude, latitude, location, speed? }`. NOT called by moso; provide for completeness.
- **Response 200**: `{ "message": "location recorded" }`.
- **6valley shape**: `DeliveryManController.php:88-127`. Inserts into `delivery_histories`.

## 5.2 GET `/api/v2/delivery-man/last-location?order_id=<id>`
- **Response 200**: latest `DeliveryHistory` row `{id, order_id, deliveryman_id, longitude, latitude, time, location, created_at, updated_at}` or `null`. Tracking controller reads lat/lng tolerantly.
- **6valley shape**: `DeliveryManController.php:473-484`.

## 5.3 GET `/api/v2/delivery-man/order-delivery-history?order_id=<id>`
- **Response 200**: bare array of `DeliveryHistory` rows. Validation → 403 `{ "errors":[...] }`.
- **6valley shape**: `DeliveryManController.php:129-140`.

## 5.4 POST `/api/v2/delivery-man/distance-api`
- **Request** (`rider_repository.dart:12`): `{ origin_lat, origin_lng, destination_lat, destination_lng }`.
- **Response 200**: **raw Google Distance Matrix JSON**. `DistanceModel.fromJson`: `destination_addresses`/`origin_addresses` **[fragile]** `.cast<String>()` (present arrays); `rows[].elements[].distance/duration.value` **[fragile]** `.toDouble()`.
- **6valley shape**: `DeliveryManController.php:803-821` — proxies Google distancematrix. Return Google's native structure verbatim.

## 5.5 GET `/api/v2/delivery-man/seller-location?seller_id=<id>&order_id=<id>`
- **Response**: very tolerant. Simplest: `{ "latitude": <num|str>, "longitude": <num|str> }` or `{ "shop": { "latitude":…, "longitude":… } }`.
- **6valley shape**: ⚠ **No route/controller exists**. App tolerates failure. **BUILD from `Commande.restaurant` pickup coords.**

## 5.6 GET `/api/v2/delivery-man/delivery-wise-earned`
- **Request** (query): `?start_date=&end_date=&limit=10&offset=<page>&type=<TodayEarn|ThisWeekEarn|ThisMonthEarn|...>`.
- **Response**: `{ total_size, limit, offset, orders:[ Orders ] }`. Wrapper key = **`orders`**. `total_size` int; `limit`,`offset` String.
  - Each `Orders`: mostly parseToDouble/guarded, BUT **`shipping_address_data` [fragile]** and **`billing_address_data` [fragile]** — `ShippingAddress.fromJson(...)` called **unconditionally** (`:142`,`:151`). MUST be present objects. `seller`/`customer` guarded. Other keys: `id,customer_id,customer_type,payment_status,order_status,payment_method,transaction_ref,order_amount,is_pause,cause,shipping_address(String),created_at,updated_at,discount_amount,discount_type,coupon_code,shipping_method_id,shipping_cost,order_group_id,verification_code,seller_id,seller_is,delivery_man_id,deliveryman_charge,expected_delivery_date,order_note,billing_address(int),order_type,extra_discount,extra_discount_type`.
- **6valley shape**: `DeliveryManController.php:530-573`. `payment_status=paid`, date-bucketed (`updated_at` for custom range; `created_at` for Today/Week/Month), paginated. ⚠ Always include both address objects.
- **Notes**: `type` ∈ `TodayEarn,ThisWeekEarn,ThisMonthEarn` (+ custom).

## 5.7 GET `/api/v2/delivery-man/collected_cash_history`
- **Request** (query): `?limit=10&offset=<page>&start_date=&end_date=&type=<TodayPaid|ThisWeekPaid|ThisMonthPaid|...>`.
- **Response**: `{ total_size, limit, offset, deposit:[ Deposit ] }`. Wrapper key = **`deposit`**. `Deposit`: `id`,`delivery_man_id`,`user_id`,`user_type`,`credit`,`transaction_type`,`created_at`,`updated_at` (all [load-bearing], guarded).
- **6valley shape**: `DeliveryManController.php:690-735`. Shim: source from `REMISE_PLATEFORME` transactions (§7).
- **Notes**: `type` ∈ `TodayPaid,ThisWeekPaid,ThisMonthPaid`.

## 5.8 POST `/api/v2/delivery-man/withdraw-request`
- **Request**: `{ "amount": String, "note": String }`.
- **Response 200**: `{ "message": String }`; over-balance/invalid → 403 `{ "message": String }`.
- **6valley shape**: `WithdrawController.php:17-43`. Guards `amount ≤ withdrawable_balance` and `amount > 1`. Shim: `DemandeRetrait` EN_ATTENTE (§7).

## 5.9 GET `/api/v2/delivery-man/withdraw-list-by-approved`
- **Request** (query): `?limit=10&offset=<page>&start_date=&end_date=&type=<withdrawn|pending>`.
- **Response**: `{ total_size, limit, offset, withdraws:[ Withdraws ] }`. Wrapper key = **`withdraws`**. `Withdraws`: `id` int; `amount` **[fragile]** `double.parse(amount.toString())` (present & numeric); `transaction_note`,`created_at`,`updated_at`.
- **6valley shape**: `WithdrawController.php:45-79`. `withdrawn→approved=1`, `pending→approved=0`.

## 5.10 GET / POST `/api/v2/delivery-man/commission/{type}`
- **GET Request**: path `type` ∈ `today|week|month|last_10_days|custom` (+ `?start_date=<d/m/Y>&end_date=<d/m/Y>` for custom).
- **GET Response 200**: `{ "success": true, "commissions": { CommissionModel } }`. App requires `success==true`. `CommissionModel`: `total`,`montant_to_pay` (?.toDouble), `start_date`,`end_date`,`period`,`status` String, `details:[ {order_id, order_amount, commission_amount, date} ]`.
- **POST** (mark-paid): path `type` (+ custom dates); body `{ "transaction_ref": String? }`. Response `{ "success": true, "commissions": {…} }` or 404 `{success:false,message}`.
- **6valley shape**: GET `DeliveryManController.php:927-969`; POST `:1060-1084`. Dates: `start_date`/`end_date`=`d-m-Y`; detail `date`=`Y-m-d H:i:s`; custom input=`d/m/Y`. Shim: base `total`/`montant_to_pay` on platform-cut (15% of fraisLivraison), NOT order_amount (§7).

## 5.11 GET `/api/v2/delivery-man/review-list`
- **Request** (query): `?is_saved=<0|1>&limit=20&offset=<page>`.
- **Response**: `{ total_size, limit, offset, review:[ Review ] }`. Wrapper key = **`review`**. `Review`: `id`,`product_id`,`customer_id`; `delivery_man_id`/`order_id` (int.parse only if non-null); `comment`,`rating` int,`status` int,`created_at`,`updated_at`; `customer`→Customer (guarded). `is_saved` **[fragile]** `json['is_saved'] ? 1 : 0` — MUST be a JSON **boolean** (not int 0/1). Shim MUST cast to boolean.
- **6valley shape**: `DeliveryManController.php:747-766`.

## 5.12 PUT\* `/api/v2/delivery-man/save-review`
- **Request** (POST): `{ "review_id": int, "_method": "put", "is_saved": int(0|1) }`.
- **Response 200**: `{ "message": String }`; not found → 404 `{ "errors":[{"code":"review","message":…}] }`.
- **6valley shape**: `DeliveryManController.php:822-850`.

## 5.13 GET `/api/v2/delivery-man/notifications`
- **Request** (query): `?limit=20&offset=<page>`.
- **Response**: `{ total_size, limit, offset, notifications:[ Notifications ] }`. Wrapper key = **`notifications`**. `Notifications`: `id` int; `delivery_man_id` **[fragile]** int.parse; `order_id` **[fragile]** int.parse (both present & numeric, even if no order); `description` String; `created_at`,`updated_at`.
- **6valley shape**: `DeliveryManController.php:787-801`. ⚠ 6valley column is `body`, Dart reads **`description`** → emit text under `description`. Always numeric `delivery_man_id`+`order_id`.

## 5.14 GET `/api/v2/delivery-man/messages/list/{type}`
- **Request**: path `type` ∈ `customer|seller|admin`; query `?limit=10&offset=<page>`.
- **Response**: `{ total_size, limit, offset, chat:[ Chat ] }`. Wrapper key = **`chat`**. `Chat`: `id`,`user_id`,`seller_id`,`message`; `sent_by_customer`/`sent_by_seller`/`sent_by_admin` **[fragile-ish]** MUST be JSON booleans (or null), NOT 0/1; `seen_by_delivery_man` bool; `created_at`; `customer`→Customer; `seller_info`→SellerInfo. `unseen_message_count` [ignored].
- **6valley shape**: `ChatController.php:17-73`. Shim: BUILD chat (T9). `sent_by_*` as booleans; customer under `customer`, seller under `seller_info`.

## 5.15 GET `/api/v2/delivery-man/messages/get-message/{type}/{id}`
- **Request**: path `type`,`id`; query `?offset=<page>&limit=10`.
- **Response**: `{ total_size, limit, offset, message:[ Message ] }`. Wrapper key = **`message`** (always return list form, empty `message:[]` if none). `Message`: `id`; `message`; `sent_by_*`/`seen_by_delivery_man` bool (default false); `created_at`; `customer`→Customer; `seller_info`→SellerInfo (reads `shops[]`); `attachment` **[fragile-ish]** `.cast<String>()` when non-null (array of strings) else `[]`.
- **6valley shape**: `ChatController.php:125-175`.

## 5.16 GET `/api/v2/delivery-man/messages/search/{type}`
- **Request**: path `type`; query `?search=<q>`.
- **Response**: bare `[ Chat ]` array (same shape as 5.14).
- **6valley shape**: `ChatController.php:75-123`. (`chatSearch` constant `app_constants.dart:49` points here correctly.)

## 5.17 POST (multipart) `/api/v2/delivery-man/messages/send-message/{type}`
- **Request** (multipart): path `type`; fields `message` (String), `id` (String, recipient id); file parts `image[]`.
- **Response 200**: `{ "message": String, "time": <timestamp>, "image": [String] }`; failure → 403 `{ "message": String }`.
- **6valley shape**: `ChatController.php:177-230`. Stores images to `chatting/`.

---

# Cross-source disagreements to enforce (crash risks)
1. **config `language`/`unit`** — emit as `List<String>`, always present. (6valley emits `language` as `[{code,name}]`.)
2. **notifications `description`** — Dart reads `description`; 6valley column is `body`. Emit text under `description`. `delivery_man_id`+`order_id` numeric, never null.
3. **review `is_saved`** — JSON `true/false`, not `0/1`.
4. **chat `sent_by_*`** — JSON booleans, not `0/1`.
5. **withdraw `amount`** — always present & numeric (`double.parse` unguarded).
6. **delivery-wise-earned `shipping_address_data` + `billing_address_data`** — always present objects (parsed unconditionally).
7. **/info `is_online` (int-parseable) + `identity_image` (JSON string)** — always present/valid.
8. **order-details `success` must be `true`** and `order` must be an object.
9. Any emitted `seller.shop`/`Shop` object MUST carry numeric `seller_id`.

# Endpoints where response shape could NOT be fully determined
- **`GET /order-list-by-date`** — never called by app; 6valley controller method missing (would 500). No required shape. (Mirror `all-orders` bare-array if implemented.)
- **`GET /seller-location`** — called by app but no 6valley route to cross-check; parser is fully tolerant. Minimal `{latitude, longitude}` suffices.
