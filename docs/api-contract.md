# Clinic Management SaaS — Backend Team & API Contract

**Version:** 1.1
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
  "clinic": {
    "name": "Sharma Dental Clinic",
    "address": "MG Road, Chennai",
    "phone": "+919876543210"
  }
}
```

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

## 15. Future Notification Contract

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

### 23a. Version 1.1 Change Log

Version 1.1 incorporates three contract clarifications: (1) appointment ownership is split into
API/DTO/validation versus booking transaction/concurrency, (2) the slot response DTO is locked with
`date` only at the top level, and (3) canonical `ErrorCode` values and the
`SlotAlreadyBookedException` → `SLOT_ALREADY_BOOKED` → `@ControllerAdvice` integration are defined.

## 24. Versioning

Any breaking API change must increment the API contract version or be explicitly documented.
Non-breaking additions should still be recorded in the changelog.
