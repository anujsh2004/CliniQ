# Clinic Management SaaS — Backend Team & API Contract

**Version:** 1.5
**Authors:** Parth + Anuj | Java Spring Boot Backend
**Status:** Binding. Endpoint shapes, the response envelope, and the canonical `ErrorCode` values
defined here take precedence over anything improvised during implementation.

> Markdown transcription of `Clinic_Backend_Team_API_Contract_Parth_Anuj_v1_1_UPDATED.docx`,
> committed into the repository so the contract lives alongside the code it governs. Content is
> unchanged from v1.1; only formatting has been adapted.

---

## 1. Purpose of This Document

This document is the shared contract between Parth and Anuj while building the backend. It defines
module ownership, repository conventions, API paths, request/response JSON formats, error formats,
status values and integration rules. Both members must follow these contracts unless they agree and
update the versioned document before changing them.

## 2. Current Scope

- Current development is backend-only.
- Primary stack: Java + Spring Boot + PostgreSQL.
- Frontend will be developed later and must consume the APIs defined here.
- Redis, RabbitMQ, payments, notifications and AI are planned later; backend interfaces should be
  designed so they can be added without breaking existing APIs.
- Both members work in the same backend repository.

## 3. Team Responsibilities

### Parth — Backend Architecture Lead

- Own overall Spring Boot architecture and package conventions.
- Own database design, entity relationships and migration strategy.
- Own Spring Security, JWT authentication and role-based authorization.
- Own appointment/slot concurrency, transactions and double-booking prevention.
- Own Redis integration when introduced.
- Own RabbitMQ/event-driven architecture when introduced.
- Own Docker/Docker Compose and backend deployment later.
- Review pull requests affecting architecture, security, database or concurrency.

### Anuj — Backend Feature Lead

- Own REST API implementation and controller/service contracts.
- Own doctor management APIs.
- Own patient/user profile APIs.
- Own doctor availability and slot APIs.
- Own appointment APIs and associated validation.
- Own global validation/error-response implementation with Parth.
- Own Swagger/OpenAPI documentation and API testing.
- Prepare integration-test cases for feature modules.

### Shared Responsibilities

- Both must understand the complete backend workflow.
- Both review each other's pull requests.
- Both follow the API contracts in this document.
- Neither member should directly modify another member's module without discussion.
- Database schema changes must be communicated before implementation.
- Every completed feature must include API testing and a meaningful Git commit.

## 4. Repository Structure

```
clinic-backend/
├── src/main/java/com/clinic/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   ├── dto/
│   │   ├── request/
│   │   └── response/
│   ├── security/
│   ├── exception/
│   ├── config/
│   └── mapper/
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
├── src/test/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
└── README.md
```

## 5. Git Workflow

`main` = stable branch only. Feature branches:

`feature/auth`, `feature/doctor`, `feature/patient`, `feature/availability`, `feature/appointments`,
`feature/payments`, `feature/notifications`, `feature/redis`, `feature/rabbitmq`,
`feature/ai-integration`

Use Pull Requests before merging to `main`.

Commit examples: `feat: add doctor registration API`, `fix: prevent duplicate appointment booking`,
`docs: update appointment API contract`.

## 6. API Design Rules

- Base path: `/api/v1`
- JSON is the default request/response format.
- Use plural nouns for resources: `/doctors`, `/patients`, `/appointments`.
- Use HTTP status codes correctly: 200, 201, 204, 400, 401, 403, 404, 409, 422, 500.
- Never expose passwords, password hashes, JWT secrets or internal database details.
- IDs are UUID strings in API responses; database implementation may use UUID.
- Dates/times use ISO-8601. Example: `2026-08-20T10:00:00+05:30`.
- Pagination uses `page`, `size` and `totalElements` where applicable.
- The response envelope is standardized so the future frontend does not need feature-specific
  parsing.

## 7. Standard API Response Contract

**Success**

```json
{
  "success": true,
  "message": "Doctor fetched successfully",
  "data": {},
  "timestamp": "2026-08-20T10:00:00+05:30",
  "requestId": "req_01J..."
}
```

**Error**

```json
{
  "success": false,
  "message": "Appointment slot is already booked",
  "errorCode": "SLOT_ALREADY_BOOKED",
  "errors": [],
  "timestamp": "2026-08-20T10:00:00+05:30",
  "requestId": "req_01J..."
}
```

**Validation Error**

```json
{
  "success": false,
  "message": "Validation failed",
  "errorCode": "VALIDATION_ERROR",
  "errors": [
    { "field": "phone", "message": "Phone number is invalid" }
  ],
  "timestamp": "2026-08-20T10:00:00+05:30",
  "requestId": "req_01J..."
}
```

### 7a. Canonical ErrorCode Enum

The backend uses one shared Java enum named `ErrorCode` in `exception/`. Both members must reference
these values instead of hardcoding strings. Global `@ControllerAdvice` maps domain exceptions to the
standard error response.

| ErrorCode | Meaning | HTTP |
|---|---|---|
| `VALIDATION_ERROR` | Request body failed validation | 422 |
| `SLOT_ALREADY_BOOKED` | Slot taken between fetch and booking | 409 |
| `SLOT_NOT_FOUND` | Slot id invalid or expired | 404 |
| `APPOINTMENT_NOT_FOUND` | Appointment does not exist | 404 |
| `UNAUTHORIZED_ACCESS` | Accessing another user's resource | 403 |
| `INVALID_CREDENTIALS` | Bad login | 401 |
| `DOCTOR_NOT_FOUND` | Doctor does not exist | 404 |
| `DUPLICATE_EMAIL` | Registration conflict | 409 |

**Integration boundary:** Parth's transactional appointment code may throw
`SlotAlreadyBookedException`. Anuj's global `@ControllerAdvice` maps it to `SLOT_ALREADY_BOOKED`.
This keeps exception generation and standard HTTP error formatting separate.

## 8. Authentication APIs — Parth Primary

### POST /api/v1/auth/register

Public self-registration. **Creates a `PATIENT` and nothing else.**

`role` remains in the payload for backward compatibility, but `PATIENT` is the
only accepted value; anything else is rejected with `VALIDATION_ERROR` (422).
Staff accounts are created by an administrator through
`POST /api/v1/admin/accounts` below.

> **Changed in v1.2.** Version 1.1 allowed the client to choose its own role,
> which meant anyone able to reach the endpoint could create an administrator
> for themselves and then manage every doctor, schedule and appointment in the
> clinic. The endpoint is public by necessity, so the role had to stop being
> the caller's choice.

Request:

```json
{
  "name": "Anuj Kumar",
  "email": "anuj@example.com",
  "phone": "+919876543210",
  "password": "StrongPassword123",
  "role": "PATIENT"
}
```

Response 201:

```json
{
  "success": true,
  "message": "User registered successfully",
  "data": {
    "userId": "uuid",
    "name": "Anuj Kumar",
    "email": "anuj@example.com",
    "phone": "+919876543210",
    "role": "PATIENT"
  }
}
```

### POST /api/v1/admin/accounts

Creates a staff account. **`ADMIN` only.** This is how doctors and further
administrators are onboarded, now that self-registration cannot grant a role.

Request:

```json
{
  "name": "Dr. Meera Sharma",
  "email": "meera@example.com",
  "phone": "+919876543211",
  "password": "StrongPassword123",
  "role": "DOCTOR"
}
```

`role` accepts `DOCTOR` or `ADMIN`. A `PATIENT` created here would be
indistinguishable from one who registered normally, so it is rejected with
`VALIDATION_ERROR` and pointed at `/auth/register`.

Response 201: the same body as `/auth/register`.

Errors: `UNAUTHORIZED_ACCESS` (403) for a non-admin caller,
`DUPLICATE_EMAIL` (409), `VALIDATION_ERROR` (422).

### POST /api/v1/auth/login

Request:

```json
{ "email": "anuj@example.com", "password": "StrongPassword123" }
```

Response 200:

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "jwt-access-token",
    "refreshToken": "jwt-refresh-token",
    "expiresIn": 3600,
    "user": { "userId": "uuid", "name": "Anuj Kumar", "role": "PATIENT" }
  }
}
```

### POST /api/v1/auth/refresh

Request:

```json
{ "refreshToken": "jwt-refresh-token" }
```

Response:

```json
{
  "success": true,
  "message": "Token refreshed successfully",
  "data": { "accessToken": "new-access-token", "expiresIn": 3600 }
}
```

## 9. Doctor APIs — Anuj Primary

### POST /api/v1/doctors

Request:

```json
{
  "name": "Dr. Sharma",
  "specialization": "Dentist",
  "licenseNumber": "LIC-12345",
  "consultationFee": 500,
  "accountEmail": "sharma@example.com",
  "clinic": {
    "name": "Sharma Dental Clinic",
    "address": "MG Road, Chennai",
    "phone": "+919876543210"
  }
}
```

`accountEmail` is optional and **added in v1.2**. It links the profile to an
existing `DOCTOR` account, so that account can then use `GET /doctors/me` and
manage its own availability. A `DOCTOR` creating their own profile is linked
automatically and does not need it. Without it, an administrator creates a
profile no doctor can administer.

Response:

```json
{
  "success": true,
  "message": "Doctor created successfully",
  "data": {
    "doctorId": "uuid",
    "name": "Dr. Sharma",
    "specialization": "Dentist",
    "consultationFee": 500,
    "clinic": {
      "clinicId": "uuid",
      "name": "Sharma Dental Clinic",
      "address": "MG Road, Chennai"
    }
  }
}
```

### GET /api/v1/doctors

Response:

```json
{
  "success": true,
  "message": "Doctors fetched successfully",
  "data": {
    "content": [
      {
        "doctorId": "uuid",
        "name": "Dr. Sharma",
        "specialization": "Dentist",
        "consultationFee": 500
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### GET /api/v1/doctors/me

The doctor profile belonging to the calling account. **`DOCTOR` only.**

> **Added in v1.2.** A doctor account and a doctor profile are separate records,
> and nothing in v1.1 let an account find its own profile. The frontend was
> matching on the account's *name* against the doctor list, which picks the
> wrong profile when two doctors share a name and finds nothing at all for a
> profile an administrator created.

Response: identical to `GET /doctors/{doctorId}`.

Returns `DOCTOR_NOT_FOUND` (404) when the account has no profile linked yet -
which is the state an administrator leaves behind by creating a profile without
naming an account.

### GET /api/v1/doctors/{doctorId}

Response:

```json
{
  "success": true,
  "message": "Doctor fetched successfully",
  "data": {
    "doctorId": "uuid",
    "name": "Dr. Sharma",
    "specialization": "Dentist",
    "consultationFee": 500,
    "clinic": {
      "clinicId": "uuid",
      "name": "Sharma Dental Clinic",
      "address": "MG Road, Chennai",
      "phone": "+919876543210"
    }
  }
}
```

## 10. Patient APIs — Anuj Primary

### GET /api/v1/patients/me

Response:

```json
{
  "success": true,
  "message": "Patient profile fetched successfully",
  "data": {
    "patientId": "uuid",
    "name": "Anuj Kumar",
    "email": "anuj@example.com",
    "phone": "+919876543210"
  }
}
```

### PUT /api/v1/patients/me

Request:

```json
{ "name": "Anuj Kumar", "phone": "+919876543210" }
```

Response:

```json
{
  "success": true,
  "message": "Patient profile updated successfully",
  "data": { "patientId": "uuid", "name": "Anuj Kumar", "phone": "+919876543210" }
}
```

## 11. Availability & Slot APIs — Anuj Primary / Parth Concurrency Review

### POST /api/v1/doctors/{doctorId}/availability

Request:

```json
{ "dayOfWeek": "MONDAY", "startTime": "09:00:00", "endTime": "17:00:00", "slotDurationMinutes": 30 }
```

Response:

```json
{
  "success": true,
  "message": "Availability created successfully",
  "data": {
    "availabilityId": "uuid",
    "doctorId": "uuid",
    "dayOfWeek": "MONDAY",
    "startTime": "09:00:00",
    "endTime": "17:00:00",
    "slotDurationMinutes": 30
  }
}
```

### GET /api/v1/doctors/{doctorId}/availability

Every weekly window the doctor has defined. **Added in v1.3.** Without it a
doctor is told a new window "overlaps availability already defined" with no way
to see, change or remove what is in the way.

```json
{
  "success": true,
  "message": "Availability fetched successfully",
  "data": [
    {
      "availabilityId": "uuid",
      "doctorId": "uuid",
      "dayOfWeek": "MONDAY",
      "startTime": "09:00:00",
      "endTime": "17:00:00",
      "slotDurationMinutes": 30
    }
  ]
}
```

### PUT /api/v1/doctors/{doctorId}/availability/{availabilityId}

Replaces one window. Same request body as `POST`. **Added in v1.3.**

Overlap is checked against the doctor's *other* windows, so a window can be
edited without colliding with itself. Slots already generated from the old
window are regenerated: future slots that no longer fall inside any window are
removed unless they are booked, and booked slots are always kept.

### DELETE /api/v1/doctors/{doctorId}/availability/{availabilityId}

Removes a window and its future unbooked slots. **Added in v1.3.**
Returns `409 SLOT_ALREADY_BOOKED` if the window has booked slots in the future,
since silently cancelling patients is never the right default. Cancel those
appointments first.

### POST /api/v1/doctors/{doctorId}/time-off

Blocks a single date, for a holiday or a conference, without touching the weekly
pattern. **Added in v1.3.**

```json
{ "date": "2026-09-15", "reason": "Conference" }
```

Unbooked slots on that date become `BLOCKED`. Booked slots are left alone and
reported back, so the doctor knows who still needs rescheduling:

```json
{
  "success": true,
  "message": "Time off recorded",
  "data": { "date": "2026-09-15", "slotsBlocked": 12, "appointmentsToReschedule": 2 }
}
```

### LOCKED SLOT RESPONSE DTO

```json
{
  "doctorId": "uuid",
  "date": "2026-08-20",
  "slots": [
    { "slotId": "uuid", "startTime": "10:00:00", "endTime": "10:30:00", "status": "AVAILABLE" }
  ]
}
```

**Rule:** `date` lives once at the top level of the slots response; never duplicate it inside
individual slot objects.

### GET /api/v1/doctors/{doctorId}/slots?date=2026-08-20

Response:

```json
{
  "success": true,
  "message": "Available slots fetched successfully",
  "data": {
    "doctorId": "uuid",
    "date": "2026-08-20",
    "slots": [
      { "slotId": "uuid", "startTime": "10:00:00", "endTime": "10:30:00", "status": "AVAILABLE" },
      { "slotId": "uuid", "startTime": "10:30:00", "endTime": "11:00:00", "status": "BOOKED" }
    ]
  }
}
```

## 12. Appointment APIs — Shared, Parth Owns Concurrency

### POST /api/v1/appointments

Request:

```json
{ "doctorId": "uuid", "slotId": "uuid", "reason": "Dental check-up" }
```

Response 201:

```json
{
  "success": true,
  "message": "Appointment created successfully",
  "data": {
    "appointmentId": "uuid",
    "doctorId": "uuid",
    "patientId": "uuid",
    "slotId": "uuid",
    "appointmentDate": "2026-08-20",
    "startTime": "10:00:00",
    "endTime": "10:30:00",
    "status": "PENDING_PAYMENT",
    "paymentStatus": "PENDING"
  }
}
```

> **Changed in v1.4.** Booking now puts the slot in `HELD`, not `BOOKED`, and
> the appointment carries a `holdExpiresAt`. The slot is off the market while
> the patient pays and returns to `AVAILABLE` if they do not. Paying moves the
> slot to `BOOKED` and clears the hold. A slot whose time has already passed
> becomes `EXPIRED` rather than going back on sale.

### POST /api/v1/payments/demo-confirm

Confirms an appointment without a gateway. **Added in v1.4** while Razorpay is
deferred. Takes the same path a captured webhook does: payment `PAID`,
appointment `CONFIRMED`, slot `BOOKED`.

Request: `{ "appointmentId": "uuid" }`

**Refused once gateway credentials are configured**, so a demo shortcut can
never confirm an unpaid appointment in an environment that takes real money.

### GET /api/v1/appointments/{appointmentId}

Response:

```json
{
  "success": true,
  "message": "Appointment fetched successfully",
  "data": {
    "appointmentId": "uuid",
    "doctor": { "doctorId": "uuid", "name": "Dr. Sharma" },
    "patient": { "patientId": "uuid", "name": "Anuj Kumar" },
    "date": "2026-08-20",
    "startTime": "10:00:00",
    "endTime": "10:30:00",
    "status": "CONFIRMED",
    "paymentStatus": "PAID"
  }
}
```

### GET /api/v1/appointments/my

Response:

```json
{
  "success": true,
  "message": "Appointments fetched successfully",
  "data": {
    "content": [
      {
        "appointmentId": "uuid",
        "doctorName": "Dr. Sharma",
        "date": "2026-08-20",
        "startTime": "10:00:00",
        "status": "CONFIRMED",
        "paymentStatus": "PAID"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### PATCH /api/v1/appointments/{appointmentId}/cancel

Request:

```json
{ "reason": "Personal reason" }
```

Response:

```json
{
  "success": true,
  "message": "Appointment cancelled successfully",
  "data": { "appointmentId": "uuid", "status": "CANCELLED" }
}
```

### PATCH /api/v1/appointments/{appointmentId}/reschedule

Request:

```json
{ "newSlotId": "uuid" }
```

Response:

```json
{
  "success": true,
  "message": "Appointment rescheduled successfully",
  "data": {
    "appointmentId": "uuid",
    "date": "2026-08-21",
    "startTime": "11:00:00",
    "endTime": "11:30:00",
    "status": "CONFIRMED"
  }
}
```

## 13. Doctor Appointment Management APIs

### GET /api/v1/doctors/me/appointments?date=2026-08-20

Response:

```json
{
  "success": true,
  "message": "Doctor appointments fetched successfully",
  "data": {
    "date": "2026-08-20",
    "appointments": [
      {
        "appointmentId": "uuid",
        "patient": { "patientId": "uuid", "name": "Anuj Kumar", "phone": "+919876543210" },
        "startTime": "10:00:00",
        "endTime": "10:30:00",
        "status": "CONFIRMED"
      }
    ]
  }
}
```

### PATCH /api/v1/appointments/{appointmentId}/complete

Response:

```json
{
  "success": true,
  "message": "Appointment marked as completed",
  "data": { "appointmentId": "uuid", "status": "COMPLETED", "followUpEligible": true }
}
```

## 14. Payment API Contract — Later

### POST /api/v1/payments/create-order

Request:

```json
{ "appointmentId": "uuid" }
```

Response:

```json
{
  "success": true,
  "message": "Payment order created successfully",
  "data": {
    "paymentId": "uuid",
    "appointmentId": "uuid",
    "gateway": "RAZORPAY",
    "orderId": "order_xxx",
    "amount": 500,
    "currency": "INR",
    "status": "CREATED"
  }
}
```

### POST /api/v1/payments/webhook

This endpoint is called by the payment gateway. The backend must verify the gateway signature before
changing payment or appointment state.

Successful internal result:

```json
{
  "success": true,
  "message": "Payment processed successfully",
  "data": { "paymentId": "uuid", "appointmentId": "uuid", "status": "PAID" }
}
```

## 15. Notification Contract

> **Changed in v1.4.** Reminders are shown in the patient's own list rather than
> sent over WhatsApp, for which no provider is configured (decision D19). The
> clinic's reminder rules - when each fires and what it says - are configuration
> under `clinic.notifications.reminders`, so changing them needs no code change.
> A patient may add their own reminders on top of the clinic's.

### GET /api/v1/notifications

The caller's own reminders that are due, newest first. `PATIENT` only.

### GET /api/v1/notifications/unread-count

`{ "unread": 3 }` — drives the unread badge.

### POST /api/v1/notifications/reminders

`{ "appointmentId": "uuid", "minutesBefore": 180 }` — a reminder the patient
chooses, in addition to the clinic's. Refused if it would fall closer to the
appointment than `clinic.notifications.minimum-patient-offset`, or in the past.

### DELETE /api/v1/notifications/reminders/{notificationId}

Removes a reminder the patient added. The clinic's own reminders are not theirs
to delete and are refused with `VALIDATION_ERROR`.

### PATCH /api/v1/notifications/{notificationId}/read · PATCH /api/v1/notifications/read-all

Marks reminders read.

### Legacy queue contract

### POST /api/v1/internal/notifications/reminders

Internal event payload:

```json
{
  "appointmentId": "uuid",
  "patientId": "uuid",
  "channel": "WHATSAPP",
  "reminderType": "24_HOURS",
  "scheduledFor": "2026-08-19T10:00:00+05:30"
}
```

Worker result:

```json
{
  "success": true,
  "message": "Reminder queued successfully",
  "data": { "notificationId": "uuid", "status": "QUEUED" }
}
```

## 16. Future AI Contract

### FAQ — POST /api/v1/ai/faq

Request:

```json
{ "message": "What are your clinic timings?" }
```

Response:

```json
{
  "success": true,
  "message": "FAQ response generated successfully",
  "data": {
    "answer": "The clinic is open Monday to Saturday from 9 AM to 7 PM.",
    "source": "CLINIC_KNOWLEDGE_BASE"
  }
}
```

### AI Follow-up

Internal request:

```json
{ "appointmentId": "uuid", "patientId": "uuid", "followUpType": "POST_VISIT" }
```

Response:

```json
{
  "success": true,
  "message": "Follow-up message generated successfully",
  "data": {
    "messageId": "uuid",
    "channel": "WHATSAPP",
    "message": "Hello Anuj, we hope you are recovering well after your visit. Are you experiencing any discomfort?",
    "status": "QUEUED"
  }
}
```

## 17. Standard Status Values

| Domain | Values |
|---|---|
| User role | `PATIENT` \| `DOCTOR` \| `ADMIN` |
| Appointment | `PENDING_PAYMENT` \| `CONFIRMED` \| `COMPLETED` \| `CANCELLED` \| `NO_SHOW` |
| Payment | `PENDING` \| `CREATED` \| `PAID` \| `FAILED` \| `REFUNDED` |
| Slot | `AVAILABLE` \| `HELD` \| `BOOKED` \| `BLOCKED` \| `EXPIRED` |
| Notification | `QUEUED` \| `SENT` \| `DELIVERED` \| `FAILED` |
| AI job | `QUEUED` \| `PROCESSING` \| `COMPLETED` \| `FAILED` |

## 18. Core Database Entities

- **User** — identity, credentials, role.
- **Doctor** — professional profile and clinic ownership.
- **Patient** — patient profile.
- **Clinic** — clinic details and configuration.
- **DoctorAvailability** — recurring working schedule.
- **Slot** — concrete bookable time interval.
- **Appointment** — patient/doctor/slot relationship and status.
- **Payment** — gateway order, payment status and appointment relationship.
- **Notification** — reminder delivery records.
- **AiInteraction** — future chatbot/follow-up metadata.

## 19. Critical Backend Rules

1. A slot must never be booked twice.
2. Appointment creation must be transactional.
3. Payment success must be verified server-side/webhook-side.
4. Patients can only access their own appointments.
5. Doctors can only manage appointments belonging to their clinic.
6. Passwords are never returned in JSON.
7. JWT secrets and database credentials are never committed to Git.
8. Use DTOs for API contracts; do not expose JPA entities directly.
9. Use global exception handling with the standard error response.
10. API contract changes require both members' agreement and a document/version update.

## 20. Immediate Sprint Plan

**Sprint 1** — Create Spring Boot project; configure PostgreSQL; create package structure; create
User/Doctor/Patient entities; implement health endpoint; set up Git branches and pull-request
workflow; test first APIs with Postman.

**Sprint 2** — Implement registration/login; JWT security; role-based authorization; doctor profile
APIs; patient profile APIs.

**Sprint 3** — Doctor availability; generate/fetch slots; appointment creation; cancellation and
rescheduling; transaction and double-booking protection.

**Sprint 4** — Razorpay integration; notification model; Redis; RabbitMQ; reminder workflow;
Swagger/OpenAPI; integration testing.

## 21. Definition of Done

- Feature implemented according to this API contract.
- Request validation added.
- Success and error responses follow the standard format.
- Authorization rules tested.
- Database changes documented/migrated.
- Postman/Swagger test completed.
- Relevant unit/integration tests added.
- Code reviewed by the other member.
- Pull request merged only after review.

## 22. Conflict Prevention Rules

- Before coding a new endpoint, check this document.
- Never independently invent a JSON field name if the contract already defines one.
- Use camelCase in JSON.
- Use UUID IDs consistently.
- Use ISO-8601 for date/time values.
- If a requirement changes, first update the API contract, then implement it.
- If an endpoint is shared by both members, one person owns implementation and the other owns
  review/testing.
- The frontend team, when started, must consume these contracts rather than asking the backend team
  to redesign APIs ad hoc.

**Appointment ownership rule:** If a method changes a Slot's status or checks/locks slot
availability, it is Parth's responsibility. If it only reads, formats, or validates request shape, it
is Anuj's responsibility.

Anuj owns `AppointmentController`, request/response DTOs, `@Valid` input validation, service
invocation, standard response-envelope mapping, and `GET /appointments/{id}` and
`GET /appointments/my` end-to-end. Parth owns `createAppointment`, `cancelAppointment` and
`rescheduleAppointment` logic that touches slot state, including `@Transactional` boundaries, locking
and slot status transitions.

**Practical collaboration rule:** Anuj can build the controller, DTOs and stub service methods first.
Parth then fills in the transactional booking logic so both members do not edit the same class
simultaneously.

## 23. Final Ownership Summary

| Module / Responsibility | Primary Owner | Reviewer / Collaborator |
|---|---|---|
| Architecture & DB | Parth | Anuj |
| Authentication & Security | Parth | Anuj |
| Doctor APIs | Anuj | Parth |
| Patient APIs | Anuj | Parth |
| Availability & Slots | Anuj | Parth |
| Appointments (API/DTO/validation) | Anuj | Parth |
| Appointments (booking transaction/concurrency) | Parth | Anuj |
| Payments | Parth | Anuj |
| Validation & Exceptions | Anuj | Parth |
| Redis | Parth | Anuj |
| RabbitMQ | Parth | Anuj |
| API Documentation & Testing | Anuj | Parth |
| Docker & Deployment | Parth | Anuj |
| Future AI Integration | Shared | Shared |

### 23d. Version 1.4 Change Log

Version 1.4 holds a slot rather than selling it: booking sets `HELD` with a
`holdExpiresAt`, an unpaid hold is swept back to `AVAILABLE`, and a demo
confirmation stands in for the gateway while Razorpay is deferred.

### 23c. Version 1.3 Change Log

Version 1.3 makes availability manageable rather than write-only: listing,
editing and deleting weekly windows, and blocking a single date for time off.
A doctor could previously only add windows, and was told about overlaps with no
way to see or change what caused them.

### 23e. Version 1.5 Change Log

Version 1.5 adds the voice endpoints in section 25: `POST /voice/transcribe`,
`POST /voice/speak` and `GET /voice/languages`, in Hindi, Tamil and English.
They are feature-flagged off by default and absent from the API when disabled.
`/voice/speak` is the one endpoint that does not return the standard envelope,
because it returns a waveform; its errors still do.

Voice is read-only by design: it answers questions and refuses to book, cancel
or advise. The speech models are CC-BY-NC and must be replaced before sale
(D28).

### 23b. Version 1.2 Change Log

Version 1.2 closes a privilege escalation in registration, and lets a doctor
account find its own profile: `GET /doctors/me`, plus an optional
`accountEmail` on doctor creation so an administrator can link the two.
`POST /auth/register` now creates a `PATIENT` only; the endpoint is public, so
allowing the caller to name their own role let anyone create an administrator.
Staff accounts move to a new admin-only `POST /api/v1/admin/accounts`.

### 23a. Version 1.1 Change Log

Version 1.1 incorporates three contract clarifications: (1) appointment ownership is split into
API/DTO/validation versus booking transaction/concurrency, (2) the slot response DTO is locked with
`date` only at the top level, and (3) canonical `ErrorCode` values and the
`SlotAlreadyBookedException` → `SLOT_ALREADY_BOOKED` → `@ControllerAdvice` integration are defined.

## 25. Voice (v1.5)

Speech in and speech out, for patients who would rather speak than type. Every
model runs on the clinic's own hardware, so patient audio never leaves it.

These endpoints exist **only when `clinic.speech.enabled` is true**. With the
flag off they are absent from the API entirely rather than present and failing,
because an endpoint that always errors is worse than an honest 404.

All three require an authenticated caller: transcription costs real GPU time.

### Transcribe — POST /api/v1/voice/transcribe

`multipart/form-data`:

| Field | Required | Notes |
|---|---|---|
| `audio` | yes | The recording, as the browser captured it. WebM/Opus and wav both work. Max 10 MB, 30 seconds. |
| `language` | no | `hi`, `ta` or `en`. Defaults to `hi`. |

```json
{
  "success": true,
  "message": "Audio transcribed successfully",
  "data": {
    "text": "डॉक्टर शर्मा का समय क्या है",
    "language": "hi",
    "decoder": "rnnt",
    "durationSeconds": 2.21,
    "elapsedMs": 617
  }
}
```

Failures use the standard envelope: `422 VALIDATION_ERROR` for an empty or
silent recording, an oversized upload, or an unsupported language; `503` when
the speech service is not running.

### Speak — POST /api/v1/voice/speak

`multipart/form-data` with `text` and `language`. Returns **`audio/wav`**
rather than the standard envelope — the envelope is JSON by contract, and a
waveform cannot travel inside it. Errors still use the envelope.

Replies are capped at **600 characters**. A clinic answer is a few sentences;
anything longer indicates a bug upstream.

### Languages — GET /api/v1/voice/languages

```json
{ "success": true, "data": { "languages": ["hi", "ta", "en"] } }
```

Read from configuration so the browser never offers a language the service
will then refuse.

### Models

| Stage | Model | Licence |
|---|---|---|
| Hindi/Tamil recognition | `ai4bharat/indic-conformer-600m-multilingual` | MIT |
| English recognition | `openai/whisper-small` | MIT |
| Hindi/Tamil/English speech | `facebook/mms-tts-{hin,tam,eng}` | **CC-BY-NC** |

The speech models are **non-commercial** and must be replaced before CliniQ is
sold. Tracked as decision D28.

### What voice deliberately does not do

It answers questions. It does **not** book, cancel or reschedule, and it does
not give medical advice. Recognition mishears — "अपॉइंटमेंट" is routinely
garbled — and a misheard cancellation would destroy a real appointment. Acting
on speech requires a spoken confirmation step, which is a later phase.

## 24. Versioning

Any breaking API change must increment the API contract version or be explicitly documented.
Non-breaking additions should still be recorded in the changelog.
