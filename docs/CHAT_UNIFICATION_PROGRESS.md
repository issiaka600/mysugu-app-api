# Chat Unification — Progress & E2E Verification

Branch: `feat/chat-unification`. Spec: `docs/superpowers/specs/2026-07-10-chat-unification-design.md`.
Plan: `docs/superpowers/plans/2026-07-10-chat-unification.md`.

Goal: unify the three legacy chat systems (livreur `MessageLivreur`, client↔restaurant System-B
`Conversation`/`MessageChat`) into ONE `ConversationUnifiee`/`MessageUnifie` store behind
`ConversationService`, and expose every mobile-app chat surface as a legacy-compatible façade so the
MySuKu client changes ONLY its base URL. This closes MySuKu's last dependency on
`atlantique-release-backend`.

## Task status (SDD)

| Task | What | Commit | Tests |
|------|------|--------|-------|
| 1 | `ParticipantType` + `ParticipantRef` | 32eb028..2a6695b | ParticipantRefTest |
| 2 | unified entities + repositories | ..a561eb7 | ConversationRepositoryTest |
| 3 | `ConversationService` + `ParticipantResolver` (FCM, seen) | ..77a61c6 | ConversationServiceTest 3/3 |
| 4 | one-shot migration System A + System B → unified | ..62ae0dc | ChatMigrationTest 3/3 |
| 5 | reback LIVREUR façade `/api/v2/delivery-man/messages` | ..0f870a6 | DeliveryManChatShimTest 4/4; contract 7/7 |
| 6 | reback SELLER channel `/api/messages` | ..d67d05c | MessagerieServiceUnifiedTest 5/5 |
| 7 | CUSTOMER façade `/api/v1/customer/chat/*` (integer flags) | ..624e187 | CustomerChatShimTest 5/5 |
| 8 | `/api/v1/auth` login+register shim → `{token}` | ..175bac1 | CustomerAuthShimTest 3/3 |
| 9 | cross-façade integration + `@Deprecated` legacy entities | ..c6d6a51 | CrossFacadeChatTest 2/2 |
| 10 | E2E verification (this doc) | — | see below |

Full suite at c6d6a51: **63 tests, 0 chat failures.** The only failing class is `PushNotificationTest`
(4 failures + 3 errors) — a **PRE-EXISTING, unrelated** real-FCM livreur auto-assignment E2E that fails
**identically at the pre-feature baseline `77a61c6`** (verified in a throwaway worktree). Not caused by
this feature. Flagged separately for the team.

## Task 10 — End-to-end verification

### Step 1: App boots + migration runs — ✅ AUTOMATED-VERIFIED
The full application context boots cleanly under `@SpringBootTest(webEnvironment = RANDOM_PORT)`
(embedded Tomcat, real PostgreSQL, full Spring Security filter chain, `ChatMigrationRunner` executing
as an `ApplicationRunner` at startup). Across the test suite this boot occurred **11×**, each logging
`Chat migration done: N conversations, M messages` (idempotent skip on non-empty store). This is the
same startup path as `spring-boot:run`; the migration runner and all beans initialize without error.

### Step 2: Endpoint smoke (backend) — ✅ AUTOMATED-VERIFIED over real HTTP
Every MySuKu-facing endpoint is exercised over **real HTTP** (JDK `HttpClient` → embedded Tomcat →
real Postgres), not mocks:

| Step-2 check | Automated evidence |
|--------------|--------------------|
| Auth: `/api/v1/auth/login` → `{token}`, no `temporary_token`; register → `{token}` (native path) | CustomerAuthShimTest 3/3 |
| Customer→delivery-man chat send/receive; **integer** `sent_by_customer` (1/0) | CustomerChatShimTest; CrossFacadeChatTest Scenario A |
| Message written via CUSTOMER façade is read via LIVREUR façade for the same pair (and back) | CrossFacadeChatTest Scenario A |
| Seller (restaurant) chat: customer message appears on the mysugu restaurant side `/api/messages` | CrossFacadeChatTest Scenario B |
| Exactly **one** `ConversationUnifiee` row per participant pair (canonical dedup across façades) | CrossFacadeChatTest (both scenarios assert count==1 / single conversation) |
| Unread counts + seen-on-read | shim "seen" tests (DeliveryManChatShimTest, CustomerChatShimTest); ConversationServiceTest |
| `type=seller` `id=0` (admin) opens without error (empty thread OK) | CustomerChatShimTest `seller/0` case |
| Per-façade flag shape correct: BOOLEAN (livreur) vs INTEGER 1/0 (customer) | CrossFacadeChatTest asserts both shapes on the SAME messages |

### Step 2b: MySuKu Flutter on-device smoke — ⚠️ REQUIRED MANUAL CARRY-FORWARD (cannot be automated here)
The one check the automated suite cannot cover is the **rendered UI** of the real MySuKu Flutter app
(message bubble alignment / "sent by me" styling), which depends on the client interpreting the flags.
This MUST be done on a device/emulator before prod cutover:

1. Build MySuKu pointing at the mysugu backend:
   `flutter run --dart-define=TIKTAK_CHAT_BASE_URL=http://<mysugu-host>:8083`
   (leave `TIKTAK_CHAT_TOKEN` unset so it authenticates via `/api/v1/auth/login`).
2. From a customer account, verify on-device:
   - delivery-man chat: send/receive, **bubble alignment correct** (own messages right, counterpart left) — this exercises the integer `sent_by_customer` interpretation;
   - seller (restaurant) chat: send, then confirm it appears on the mysugu restaurant side (`/api/messages`);
   - unread counts + "seen" update correctly;
   - `type=seller` `id=0` (admin) opens without error.
3. **Behavioral change to watch (from Task 5 review):** counterpart-authored messages now report the
   correct `sent_by_*` flag (the legacy livreur store always returned false because counterpart replies
   were never wired). Confirm the Flutter app renders a counterpart bubble correctly for a now-reachable
   `sent_by_customer = true`/`1`.
4. **Status-code change to watch (from Task 6 review):** accessing a `conversationId` you are not a party
   to now returns **404** (was 401) on `/api/messages/*`. Confirm MySuKu has no 401-keyed special handling
   (e.g. force-logout) on that route.

If any JSON-shape or rendering mismatch appears, open a fix task; the backend façade mappers are the
single place to adjust.

## Deferred / follow-up
- **Retire legacy chat tables/entities** (`MessageLivreur`, System-B `Conversation`/`MessageChat` + repos,
  and `ChatMigrationRunner`) AFTER prod cutover is confirmed and the old tables are drained. They are
  currently `@Deprecated` and kept only so first-boot migration works on environments that still hold
  old data.
- Minor test-coverage and polish items are rolled up in `.superpowers/sdd/progress.md` for the
  whole-branch review.
