# Cliniva — Product Requirements Document

**Version:** 1.0
**Status:** Draft — source of truth for implementation
**Companion documents:** `design.md`, `tech-stack.md`

---

## 1. Product Overview

Cliniva is an AI-powered clinic management SaaS built for **small, independent clinics** (single-doctor practices up to small multi-doctor clinics) that currently run on paper registers, WhatsApp, and phone calls instead of dedicated software.

Cliniva gives a clinic three things in one product:

1. A **booking system** — patients see real availability and book a slot themselves; doctors and clinic staff see a live schedule.
2. An **operational backbone** — patient records, appointment history, payments, and reminders live in one place instead of being scattered across notebooks and phones.
3. An **AI layer** (Phase 2) — automated FAQ answering and post-visit follow-up messages that reduce the manual front-desk workload.

The backend is a Java/Spring Boot layered monolith (Controller → Service → Repository → PostgreSQL) with Redis, RabbitMQ, Razorpay, and a FastAPI-based AI service added in later phases. See `tech-stack.md` for the full technical architecture.

---

## 2. Problem Statement

Small clinics are underserved by existing clinic-management software because most products are either:

- **Built for hospitals/large multi-specialty chains** — too expensive, too complex, and require dedicated IT staff to configure.
- **Generic booking tools** (e.g. calendar/scheduling apps) — not built around clinical workflows like doctor availability patterns, slot durations, patient history, or clinic-specific status flows (e.g. `PENDING_PAYMENT`, `NO_SHOW`).

As a result, a large number of small clinics run entirely on manual processes:

- A receptionist manually tracks bookings in a register or WhatsApp thread, which does not prevent double-booking.
- Patients call repeatedly to check doctor availability because there is no self-service visibility into open slots.
- There is no reliable digital record of a patient's appointment history at that clinic.
- Payment collection is entirely offline (cash at the counter), with no reconciliation against appointments.
- No-shows and late cancellations are common because there is no automated reminder system.
- Common patient questions ("What are your timings?", "Do you accept walk-ins?") consume front-desk staff time.

**Cliniva's problem statement:** small clinics need a system that prevents double-booking, gives patients self-service visibility into availability, and reduces repetitive front-desk work — without the cost or complexity of enterprise hospital software.

---

## 3. Product Goals

| Goal | Description |
|---|---|
| G1 — Reliable booking | A slot must never be double-booked, even under concurrent booking attempts. This is the single non-negotiable correctness guarantee of the product. |
| G2 — Self-service scheduling | Patients can view real-time doctor availability and book/cancel/reschedule without calling the clinic. |
| G3 — Single source of truth | Doctors, patients, appointments and payments live in one system instead of being scattered across paper and messaging apps. |
| G4 — Reduce front-desk load | Automated reminders and AI-answered FAQs reduce the volume of manual phone/WhatsApp interactions. |
| G5 — Low operational overhead for clinics | Clinics should be able to onboard with minimal setup: register the clinic, add doctors, define availability, start taking bookings. |
| G6 — Trustworthy payments | Online payment collection (Razorpay) that is verified server-side, never trusting client-reported payment status. |
| G7 — Extensible foundation | The backend interfaces are designed so Redis, RabbitMQ, payments, notifications, and AI can be added without breaking existing APIs (see `tech-stack.md` §Architecture). |

---

## 4. Target Users

Cliniva has three user types, matching the `Role` enum already defined in the API contract (`PATIENT | DOCTOR | ADMIN`):

1. **Patients** — people booking appointments at a clinic.
2. **Doctors** — clinicians who define their availability and manage their own appointments.
3. **Clinic Admin / Front-desk staff** — the `ADMIN` role, representing clinic owners or receptionists who manage doctors, clinic settings, and oversee bookings on the clinic's behalf.

**Primary buyer:** the clinic (via its admin/owner), not the individual patient. Patients are end-users of a product the clinic pays for.

---

## 5. User Personas

### 5.1 Dr. Meera Sharma — Solo Practitioner (Doctor)
- Runs a single dental clinic, no dedicated IT/reception software today.
- Wants to define her weekly working hours once and have the system generate bookable slots automatically.
- Needs to see her day's appointments at a glance and mark them complete after the visit.
- Not deeply technical — must be able to use the system from a phone browser between patients.

### 5.2 Ramesh — Clinic Front-Desk Admin (Admin)
- Manages 2–3 doctors at a multi-doctor clinic.
- Currently maintains a paper appointment register.
- Wants visibility into every doctor's schedule, and the ability to intervene (e.g. block a slot, add a doctor) without contacting each doctor individually.

### 5.3 Anjali — Patient
- Wants to book a dental check-up without calling the clinic during work hours.
- Wants to see which slots are actually free before deciding when to go.
- Wants a reminder before her appointment because she has missed appointments in the past due to forgetting.
- Wants to pay online rather than carry cash in some cases, but not always — see §12 open questions on Cash payment support.

---

## 6. Core User Journeys

### 6.1 Patient books an appointment
1. Patient registers/logs in (`POST /auth/register`, `POST /auth/login`).
2. Patient browses doctors (`GET /doctors`) and views a doctor's profile (`GET /doctors/{doctorId}`).
3. Patient fetches available slots for a date (`GET /doctors/{doctorId}/slots?date=...`).
4. Patient books a slot (`POST /appointments`) → appointment is created in `PENDING_PAYMENT` status.
5. Patient completes payment (`POST /payments/create-order` → Razorpay checkout → webhook confirms payment).
6. On verified payment, appointment status transitions to `CONFIRMED`.
7. Patient receives a reminder notification ahead of the appointment (Phase 4).
8. Patient can view (`GET /appointments/my`, `GET /appointments/{id}`), cancel (`PATCH /appointments/{id}/cancel`), or reschedule (`PATCH /appointments/{id}/reschedule`) the appointment.

### 6.2 Doctor manages their day
1. Doctor logs in and defines/edits recurring weekly availability (`POST /doctors/{doctorId}/availability`).
2. System (or a scheduled job) generates concrete bookable `Slot` rows from the recurring availability.
3. Doctor views today's/a chosen date's appointments (`GET /doctors/me/appointments?date=...`).
4. After seeing a patient, doctor marks the appointment complete (`PATCH /appointments/{id}/complete`), which may make the patient eligible for an AI-generated follow-up (Phase 2/4).

### 6.3 Admin onboards a clinic
1. Admin registers as a clinic admin.
2. Admin creates doctor profiles under the clinic (`POST /doctors`, including embedded `clinic` object).
3. Admin (or the doctor) sets up availability per doctor.
4. Clinic is now bookable by patients.

### 6.4 Double-booking prevention (system-level, not user-facing, but critical)
1. Two patients fetch the same slot list at the same time and both see slot X as `AVAILABLE`.
2. Both submit `POST /appointments` for slot X near-simultaneously.
3. The backend must guarantee, via transactional locking, that exactly one request succeeds and the other receives `409 SLOT_ALREADY_BOOKED`.

---

## 7. Main Features

### MVP (Phases 0–3, see `tech-stack.md` roadmap)
- User registration/login with JWT auth and role-based access (`PATIENT`, `DOCTOR`, `ADMIN`)
- Doctor profile & clinic management
- Patient profile management
- Doctor availability configuration and slot generation
- Slot browsing by date
- Appointment booking, cancellation, rescheduling
- Doctor-side daily appointment view and appointment completion
- Concurrency-safe slot booking (no double-booking)
- Standardized success/error API response envelope

### Post-MVP (Phase 4)
- Razorpay payment integration (order creation + webhook verification)
- Notification/reminder system (WhatsApp channel, 24-hour-before reminders) via RabbitMQ worker
- Redis caching (e.g. slot availability lookups)
- Swagger/OpenAPI documentation
- Full integration test suite

### Future (Phase 7 / Post-MVP)
- AI FAQ endpoint (`POST /ai/faq`) answering common clinic questions from a knowledge base
- AI post-visit follow-up messages (`followUpType: POST_VISIT`)
- Multi-clinic support for a single admin (chains)
- Analytics dashboard for clinic admins (no-show rate, revenue, utilization)

---

## 8. Detailed Feature Requirements

### 8.1 Authentication & Accounts
- Users register with `name`, `email`, `phone`, `password`, `role`.
- Login returns a short-lived `accessToken` and a `refreshToken`; refresh endpoint issues a new access token.
- Passwords are hashed (never stored or returned in plaintext or in any API response).
- **Assumption:** email verification and password-reset flows are not in the current API contract. Flagging as an **open question** — see §16.

### 8.2 Doctor Management
- A doctor has a name, specialization, license number, consultation fee, and belongs to a clinic (embedded `clinic` object: name, address, phone).
- Doctors are listed with pagination (`page`, `size`, `totalElements`, `totalPages`).
- Doctor detail view includes full clinic contact info; list view is a lighter summary (no clinic block) — this distinction must be preserved per the contract.

### 8.3 Patient Management
- A patient can view and update only their own profile (`/patients/me`), never another patient's.

### 8.4 Availability & Slots
- A doctor (or admin on their behalf) defines recurring weekly availability: `dayOfWeek`, `startTime`, `endTime`, `slotDurationMinutes`.
- The system generates individual bookable `Slot` records from this recurring pattern for actual calendar dates.
- Slot status values: `AVAILABLE | HELD | BOOKED | BLOCKED | EXPIRED`.
- The slot list response places `date` once at the top level, never duplicated per slot (locked contract rule — see API contract §11).
- **Assumption:** exact slot-generation horizon (e.g. "generate slots 30 days ahead") is not specified in the source contract. Flagging as an open question — see §16.

### 8.5 Appointments
- Booking requires `doctorId`, `slotId`, and an optional `reason`.
- New appointments start in `PENDING_PAYMENT` status until payment is confirmed, then move to `CONFIRMED`.
- Appointment status values: `PENDING_PAYMENT | CONFIRMED | COMPLETED | CANCELLED | NO_SHOW`.
- Cancellation requires a `reason` and transitions status to `CANCELLED`; the underlying slot must be released back to `AVAILABLE`.
- Rescheduling moves an appointment to a new slot (`newSlotId`); the original slot must be released and the new slot marked `BOOKED` atomically.
- A patient can only see/cancel/reschedule their own appointments (`403 UNAUTHORIZED_ACCESS` otherwise).
- A doctor can only manage appointments belonging to their own clinic.
- **Concurrency requirement (critical):** slot booking/holding/releasing must be wrapped in a transaction with appropriate locking (e.g. pessimistic locking or a DB-level unique constraint plus optimistic retry) so that a slot is never assigned to two appointments. This is Cliniva's single most important correctness guarantee.

### 8.6 Payments (Post-MVP)
- Payment order is created against an appointment via Razorpay (`gateway: RAZORPAY`).
- The payment webhook is the only trusted source of payment confirmation — the backend must verify the gateway signature before changing `Payment` or `Appointment` state. Client-reported payment success must never directly confirm a booking.
- Payment status values: `PENDING | CREATED | PAID | FAILED | REFUNDED`.

### 8.7 Notifications (Post-MVP)
- Reminder events are queued internally (`channel`, `reminderType`, `scheduledFor`) and delivered by a RabbitMQ-backed worker.
- Notification status values: `QUEUED | SENT | DELIVERED | FAILED`.
- **Assumption:** WhatsApp is the only channel named in the source contract. Email/SMS channels are not specified — open question, see §16.

### 8.8 AI Layer (Future)
- FAQ endpoint answers clinic-general questions (e.g. timings) from a clinic knowledge base, not from patient-specific data.
- Post-visit follow-up messages are generated per completed appointment and queued for delivery through the same notification channel infrastructure.
- AI job status values: `QUEUED | PROCESSING | COMPLETED | FAILED`.

---

## 9. Functional Requirements

| ID | Requirement |
|---|---|
| FR-1 | System must support registration and login with role assignment (`PATIENT`, `DOCTOR`, `ADMIN`). |
| FR-2 | System must issue JWT access + refresh tokens and support token refresh without re-login. |
| FR-3 | System must let a doctor define recurring weekly availability and derive concrete date-bound slots from it. |
| FR-4 | System must expose available slots for a doctor on a given date. |
| FR-5 | System must allow a patient to book an available slot, creating an appointment in `PENDING_PAYMENT` status. |
| FR-6 | System must guarantee a slot is never double-booked under concurrent requests. |
| FR-7 | System must allow a patient to cancel or reschedule their own appointment. |
| FR-8 | System must allow a doctor to view their appointments for a given date and mark an appointment complete. |
| FR-9 | System must create a payment order for an appointment and confirm payment only via a verified webhook. |
| FR-10 | System must queue and deliver appointment reminders ahead of the scheduled time. |
| FR-11 | System must answer general clinic FAQs via an AI endpoint using clinic-specific knowledge. |
| FR-12 | System must generate a post-visit follow-up message after an appointment is marked complete. |
| FR-13 | All list endpoints must support pagination (`page`, `size`, `totalElements`, `totalPages`). |

## 10. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-1 | **Consistency over availability for booking**: it is acceptable to reject a booking with `409 SLOT_ALREADY_BOOKED` under contention; it is never acceptable to accept two bookings for one slot. |
| NFR-2 | All API responses follow the standardized envelope (`success`, `message`, `data`/`errorCode`/`errors`, `timestamp`, `requestId`) — see API contract §7. |
| NFR-3 | All dates/times are ISO-8601 with timezone offset (e.g. `+05:30`), consistent with the clinic's local timezone (Asia/Kolkata) — see `tech-stack.md` for the timezone handling approach already established. |
| NFR-4 | Passwords, password hashes, JWT secrets, and internal database identifiers are never exposed in any API response. |
| NFR-5 | The system must remain usable on a low-end Android phone browser (target audience includes non-technical clinic staff and patients). |
| NFR-6 | Backend interfaces (Redis, RabbitMQ, payments, notifications, AI) must be addable without breaking already-shipped API contracts — additive versioning only, per API contract §24. |
| NFR-7 | IDs are UUID strings in all API responses. |
| NFR-8 | Response times for slot lookup and booking should be low enough to support real-time UI feedback (target: sub-500ms for slot fetch under normal load — see `tech-stack.md` for caching strategy). |

## 11. User Authentication & Authorization Requirements

- Authentication: email + password login issuing JWT access/refresh token pair.
- Authorization: role-based access control (`PATIENT`, `DOCTOR`, `ADMIN`) enforced at the API layer.
  - Patients may only access/modify their own profile and appointments.
  - Doctors may only manage appointments belonging to their own clinic.
  - Admins manage doctors and clinic-level configuration for clinics they own/operate.
- `403 UNAUTHORIZED_ACCESS` is returned when a user attempts to access another user's resource.
- `401 INVALID_CREDENTIALS` is returned for bad login attempts.
- **Assumption:** whether an `ADMIN` is scoped to a single clinic or can manage multiple clinics is not defined in the source contract. Treated as **single clinic per admin** for MVP — open question, see §16.

## 12. Data Requirements

Core entities (per API contract §18), all with UUID primary keys and standard audit fields (`createdAt`, `updatedAt`):

- **User** — identity, credentials (hashed), role.
- **Doctor** — professional profile, linked to a `Clinic`.
- **Patient** — patient profile, linked to a `User`.
- **Clinic** — clinic details/configuration.
- **DoctorAvailability** — recurring weekly working schedule.
- **Slot** — concrete bookable time interval, with status.
- **Appointment** — patient/doctor/slot relationship and status.
- **Payment** — gateway order, payment status, linked to an `Appointment`.
- **Notification** — reminder delivery records.
- **AiInteraction** — future chatbot/follow-up metadata.

Data retention, backup, and PII-handling policy are not specified in the source contract — see §16 open questions.

## 13. Notifications Requirements

- Reminder notifications are sent 24 hours before an appointment (`reminderType: "24_HOURS"`), via WhatsApp.
- Notifications are generated as internal events and delivered asynchronously by a RabbitMQ worker (Parth's ownership area per the team contract).
- Failed deliveries are tracked via `status: FAILED` for retry/observability.

## 14. Error Handling Requirements

- All errors use the standard error envelope with a canonical `errorCode` from the shared `ErrorCode` enum (API contract §7a):
  `VALIDATION_ERROR (422)`, `SLOT_ALREADY_BOOKED (409)`, `SLOT_NOT_FOUND (404)`, `APPOINTMENT_NOT_FOUND (404)`, `UNAUTHORIZED_ACCESS (403)`, `INVALID_CREDENTIALS (401)`, `DOCTOR_NOT_FOUND (404)`, `DUPLICATE_EMAIL (409)`.
- Validation errors return a field-level `errors[]` array (`field`, `message`).
- Domain exceptions (e.g. `SlotAlreadyBookedException`) are thrown from the service layer and mapped centrally by a global `@ControllerAdvice`, keeping exception-raising and HTTP-formatting concerns separate.

## 15. Search / Filter / Sort Requirements

- Doctor list supports pagination; **filtering by specialization and sorting are not yet defined in the source contract** — recommended as a Phase 3/4 addition (see Assumptions).
- Slot fetch is filtered by `date` (required query param) for a given doctor.
- Doctor's daily appointment view is filtered by `date`.
- Patient appointment history (`/appointments/my`) is paginated; filtering by status (e.g. show only `CONFIRMED`) is a reasonable MVP+1 addition but not in the current contract.

## 16. Admin Functionality

- Create/manage doctor profiles under a clinic.
- View clinic-wide doctor list and (implicitly) their schedules.
- **Not yet specified in the source contract, flagged as open questions:**
  - Can an admin cancel/reschedule an appointment on a patient's behalf?
  - Can an admin view aggregate clinic analytics (bookings, revenue, no-show rate)?
  - Is there an admin-only endpoint to deactivate a doctor or block a slot manually?

These are reasonable Phase 4+ additions and should be confirmed with the team before implementation, per the API-contract change rule ("update the contract first, then implement").

## 17. Future / Optional Features

- Multi-clinic support per admin account.
- Doctor specialization-based search/filter for patients.
- Clinic analytics dashboard.
- SMS/email notification channels in addition to WhatsApp.
- Patient-side appointment ratings/feedback.
- Waitlist for fully booked doctors.

## 18. MVP Scope

**In scope for MVP (Sprints 1–3 of the team's own roadmap):**
- Auth (register/login/refresh), JWT, role-based authorization
- Doctor profile & clinic APIs
- Patient profile APIs
- Doctor availability & slot generation/fetch
- Appointment create/cancel/reschedule, with transactional double-booking prevention
- Doctor daily appointment view + complete action
- Standard response envelope and canonical error codes

## 19. Explicitly Out of Scope for MVP

- Payments (Razorpay) — Sprint 4 / Phase 4
- Notifications/reminders (RabbitMQ worker) — Sprint 4 / Phase 4
- Redis caching — Sprint 4 / Phase 4
- Swagger/OpenAPI docs, full integration test suite — Sprint 4 / Phase 4
- AI FAQ and AI follow-up — Phase 7 (future)
- Multi-clinic admin support, analytics dashboards, doctor search/filter — future

## 20. Success Metrics

| Metric | Target / Direction |
|---|---|
| Double-booking incidents | Zero, always |
| Patient self-service booking rate | % of appointments booked without a phone call to the clinic — should trend upward post-launch |
| No-show rate | Should decrease after reminder notifications ship (Phase 4) |
| Front-desk call volume for FAQs | Should decrease after AI FAQ ships (future phase) |
| Time-to-onboard a new clinic | Time from admin signup to first patient-bookable slot |
| API error rate | `5xx` rate should stay near zero; `409 SLOT_ALREADY_BOOKED` rate under contention should reflect correct rejection, not silent double-booking |

## 21. Acceptance Criteria (MVP)

- [ ] A patient can register, log in, and receive a valid JWT pair.
- [ ] A doctor can define weekly availability and the system produces bookable slots for real dates.
- [ ] A patient can fetch a doctor's slots for a given date and see accurate `AVAILABLE`/`BOOKED` status.
- [ ] Two simultaneous booking requests for the same slot result in exactly one `201 Created` and one `409 SLOT_ALREADY_BOOKED`.
- [ ] A patient can cancel their own appointment; the underlying slot becomes `AVAILABLE` again.
- [ ] A patient can reschedule their own appointment to a new slot; the old slot is released and the new slot is booked atomically.
- [ ] A patient cannot view, cancel, or reschedule another patient's appointment (`403`).
- [ ] A doctor can view their appointments for a given date and mark one `COMPLETED`.
- [ ] Every API response — success or error — matches the standard envelope shape defined in the API contract.
- [ ] No API response ever includes a password, password hash, or JWT secret.

---

## 22. Assumptions & Open Questions

These items are not fully specified in the source API contract and should be confirmed with Parth/Anuj before implementation, per the contract's change-management rule ("if a requirement changes, first update the API contract, then implement it"):

1. **Email verification / password reset** — not present in the current auth contract. Assumed out of MVP scope.
2. **Slot generation horizon** — how far ahead (e.g. 30/60/90 days) slots are pre-generated from recurring availability is undefined. Recommend making this a configurable clinic setting.
3. **Admin scope** — assumed one admin manages exactly one clinic for MVP; multi-clinic admin support is deferred.
4. **Admin-initiated appointment actions** (cancel/reschedule on a patient's behalf) — not specified; recommend adding as an explicit contract addition if needed, rather than reusing patient-only endpoints.
5. **Notification channels beyond WhatsApp** (SMS/email) — not specified; treated as future scope.
6. **Doctor search/filter (by specialization, location)** — not specified beyond plain pagination; recommended as a near-term post-MVP addition given it's core to patient discovery.
7. **Data retention / PII / backup policy** — not specified; must be defined before handling real patient data, given the sensitivity of health-adjacent information.
8. **Timezone scope** — the existing engineering notes fix the stack to `Asia/Kolkata`; this document assumes Cliniva is India-market-only for MVP. Multi-timezone support is out of scope unless stated otherwise.
