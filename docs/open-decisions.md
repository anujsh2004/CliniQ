# Open Decisions

Every point where implementation ran ahead of the API contract, or where the
contract left something undefined. Each entry records the default that shipped,
so nothing is silently a guess.

**Status key** — 🔴 needs a decision before production · 🟡 worth confirming ·
🟢 decided, recorded for the record.

The rule from API contract §22 applies to all of these: **if a requirement
changes, update the contract first, then implement it.**

---

## 🔴 Blocking before production

### D1 — Anyone can register as ADMIN or DOCTOR

- **Where:** `feature/auth` (PR #2), contract §8
- **Contract says:** the `POST /auth/register` payload includes `role`.
- **Shipped:** the contract as written — the client chooses its own role. The
  frontend registration form only ever sends `PATIENT`, but the API accepts any
  value, so `curl` can mint an admin.
- **Options:** (a) restrict `/auth/register` to `PATIENT` and create doctors and
  admins through an admin-only endpoint; (b) gate elevated roles behind an
  invite token.
- **Recommendation:** (a). It is the smaller change and matches how the clinic
  actually onboards staff.

### D16 — Razorpay has never run against real credentials

- **Where:** `feature/payments` (PR #12)
- **Shipped:** a stub gateway stands in whenever `RAZORPAY_KEY_ID` is blank. It
  signs and verifies webhooks with the same HMAC-SHA256 scheme as the real
  gateway, so the security-critical path is the one that ships; only the
  order-creation network call is faked.
- **What is missing:** sandbox keys. The `RazorpayGateway` REST call has never
  been exercised against Razorpay, so its request shape and error handling are
  unproven.

### D19 — No WhatsApp provider

- **Where:** `feature/notifications-worker` (PR #15)
- **Shipped:** a logging sender that records the message it would have sent, so
  a staging run shows exactly what a patient would receive. Nothing has ever
  been delivered to a real phone.
- **Needs:** a provider decision (Twilio, or a direct WhatsApp BSP —
  `tech-stack.md` §5 names Twilio as one option without committing) and sandbox
  credentials.

### D2 — Data retention, PII and backup policy

- **Where:** `product-description.md` §22 item 7
- **Shipped:** nothing. Appointments and patient contact details are stored
  indefinitely.
- **Why it matters:** this is health-adjacent personal data. The policy has to
  exist before real patient data is entered, not after.

---

## 🟡 Worth confirming

### D3 — No way for a doctor account to find its own doctor profile

- **Where:** `feature/doctor` (PR #3), `feature/frontend-doctor-dashboard` (PR #10), contract §9/§13
- **Contract says:** the create-doctor payload carries no `userId` or email, yet
  `GET /doctors/me/appointments` requires that a doctor account maps to a doctor
  profile.
- **Shipped:** a `DOCTOR` who creates their own profile is linked to it
  immediately; an `ADMIN` creating one leaves `user_id` null. The availability
  screen currently locates the doctor's own profile by **matching the account
  name against the doctor list** — the weakest code in the frontend.
- **Recommendation:** add `GET /doctors/me` to the contract. It removes the name
  matching and lets admins onboard doctors fully.

### D4 — Admins are not scoped to a clinic

- **Where:** PRs #3, #5, #6, #7; `product-description.md` §22 item 3
- **Shipped:** any `ADMIN` can manage any doctor, any availability and any
  appointment. There is no admin↔clinic relationship in the contract to scope
  against.
- **Consequence:** fine for the MVP assumption of one admin per clinic; unsafe
  the moment a second clinic exists.

### D5 — Admins may cancel and reschedule on a patient's behalf

- **Where:** `feature/appointments-booking` (PR #7); `product-description.md` §16
- **Contract says:** undefined — §16 lists it as an open question.
- **Shipped:** allowed, because a front desk realistically needs it.
- **Alternative:** restrict to `PATIENT` and add explicit admin endpoints to the
  contract rather than reusing the patient-facing ones.

### D6 — Slot generation horizon is global, not per clinic

- **Where:** `feature/availability` (PR #5); `product-description.md` §22 item 2
- **Shipped:** configuration `clinic.slots.generation-horizon-days`, default
  **30 days**, with a nightly top-up. Global rather than per-clinic, since a
  per-clinic value needs a clinic settings column.

### D7 — Refresh tokens cannot be revoked

- **Where:** `feature/auth` (PR #2)
- **Shipped:** stateless JWT refresh tokens with a 7-day life and no server-side
  store, so a leaked refresh token stays valid until it expires.
- **Fix when needed:** a `refresh_tokens` table with revocation on logout.

### D8 — Transport-level errors carry no `errorCode`

- **Where:** `chore/backend-scaffold` (PR #1), contract §7a
- **Shipped:** 404/405/415/500 use the standard envelope but omit `errorCode`,
  because §7a defines codes for domain failures only and inventing values would
  break the contract.
- **Alternative:** add canonical codes for them — a contract change.

### D9 — Duplicate phone number reports as `VALIDATION_ERROR`

- **Where:** `feature/auth` (PR #2), `feature/patient` (PR #4)
- **Shipped:** 422 with a field error on `phone`. §7a has `DUPLICATE_EMAIL` but
  no phone equivalent.

### D17 — Refunds are logged, not issued

- **Where:** `feature/payments` (PR #12)
- **Shipped:** a capture arriving for an already-cancelled appointment records
  the payment and writes a warning. No refund API call and no `REFUNDED`
  transition; contract §14 covers order creation and capture only.

### D18 — Payments auto-capture

- **Where:** `feature/payments` (PR #12)
- **Shipped:** orders are created with `payment_capture: 1`, so an authorised
  payment is captured immediately rather than needing a second step.

### D20 — `DELIVERED` is never reached

- **Where:** `feature/notifications-worker` (PR #15)
- **Contract says:** notifications move `QUEUED → SENT → DELIVERED` (§17).
- **Shipped:** delivery stops at `SENT`. `DELIVERED` requires a provider
  delivery-receipt callback, which needs an endpoint the contract does not
  define.

### D21 — One unexplained lost message

- **Where:** `feature/notifications-worker` (PR #15)
- **Observed:** a message published before the worker existed vanished from the
  queue with no consumer to take it, leaving its notification stuck at `QUEUED`.
- **Mitigated:** `QUEUED` and `FAILED` reminders are now republished when
  re-queued, so the state is recoverable. The disappearance itself is not root
  caused, and is recorded rather than assumed understood.

### D25 — The Redis cache makes the doctor list slower, not faster

- **Where:** `feature/redis` (PR #16), measured in `docs/load-test-results.md`
- **Observed:** at 100 concurrent clients the *cached* doctor list has a p95 of
  624ms, while the *uncached* slot fetch manages 334ms. The cache is overhead at
  this data size: the doctor list is a trivial query over three rows, and a
  Redis round trip plus deserialisation costs more than the query it replaces.
- **Options:** (a) drop caching for this endpoint until the roster is large
  enough to justify it; (b) keep it in anticipation of scale and accept the cost
  now; (c) keep it but shorten the path, for example by caching the serialised
  response rather than the object graph.
- **Recommendation:** (a) for now. The cache was added because `tech-stack.md`
  nominates doctor reads for caching, which is sound reasoning about a larger
  clinic, but it is not earning its place against today's data.

### D22 — Cached doctor data is only evicted on creation

- **Where:** `feature/redis` (PR #16)
- **Shipped:** there is no doctor *update* endpoint yet, so eviction is wired to
  creation only. When editing a doctor ships, it must evict too, or profiles go
  stale for up to the 10-minute TTL.

---

## 🟢 Decided, recorded

### D11 — Database name is `clinic_db`

The team's own files disagreed (`clinicdb` in docker-compose and
application.properties, `clinic_db` in the root application.yml). The
initialised PostgreSQL volume has `clinic_db`, so that spelling won.

### D12 — Base package is `com.clinic`

Matching contract §4 exactly, rather than the generated
`com.clinic.clinic_backend`.

### D13 — Non-feature branches use a `chore/` or `docs/` prefix

Contract §5 reserves the `feature/*` names for features. Project setup and
documentation use `chore/` and `docs/` rather than borrowing one.

### D14 — A released slot in the past becomes `EXPIRED`, not `AVAILABLE`

Cancelling an appointment an hour after it should have started must not put a
dead slot back on the market.

### D26 — Status and muted colours are darker than design.md's hex values

The hex codes in `design.md` §1.3 did not meet the 4.5:1 contrast that
`design.md` §3.7 itself requires. Measured against the 10% tint each status
badge sits on, the originals scored between 2.9 and 4.3. The shipped tokens keep
the same hues and clear 4.9:1. Where the document contradicted itself, the
accessibility commitment won over the illustrative colour values.

### D10 — Payment badges show only when they say something new

Resolved on `feature/frontend-payments`. A cancelled appointment carries no
payment obligation, so a "Pending" badge on one was noise at best and alarming
at worst, and `PENDING` beside `PENDING_PAYMENT` repeated the same fact. The
badge now appears only for `PAID`, `FAILED` and `REFUNDED`, and never on a
cancelled appointment.

### D23 — Redis listens on host port 6380

Another project on this machine already binds 6379. Same reasoning as Postgres
on 5433. Container ports are unchanged; only the host mapping differs.

### D24 — Slot availability and patient data are never cached

The database transaction is the only authority on whether a slot is free: a
cache answering that question could hand two patients the same slot. Patient
data is per-caller and does not belong in a shared cache. Only the doctor list
and doctor profile are cached.

### D15 — Registration creates patients only, in the UI

The frontend never offers a role choice. This does not close D1, which is a
server-side hole.

---

## Deferred by scope, not by oversight

These are named in the source documents as out of MVP scope. Listed so they are
not mistaken for gaps.

| Item | Source |
|---|---|
| Email verification and password reset | `product-description.md` §22 item 1 |
| Notification channels beyond WhatsApp | §22 item 5 |
| Doctor search and filter by specialization | §22 item 6 |
| Multi-clinic support for one admin | §17, §22 item 3 |
| Analytics dashboard | §17 |
| Cash / offline payment recording | §5.3 |
| Rescheduling UI (API exists, screen does not) | PR #9 |
| Editing or deleting an availability window | No `PUT`/`DELETE` in contract §11 |
| Admin dashboard screens | PR #10 |
