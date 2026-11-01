# CliniQ (Cliniva)

AI-powered clinic management SaaS for small, independent clinics: real-time slot booking,
an operational backbone for patients/appointments/payments, and (later) an AI layer for
FAQ answering and post-visit follow-ups.

## Source-of-truth documents

| Document | What it governs |
|---|---|
| [`docs/api-contract.md`](docs/api-contract.md) | **Binding.** Endpoint shapes, response envelope, canonical `ErrorCode` values, ownership split. |
| [`product-description.md`](product-description.md) | Product requirements, user journeys, MVP scope, acceptance criteria. |
| [`design.md`](design.md) | Design system and UX direction for the frontend. |
| [`tech-stack.md`](tech-stack.md) | Architecture, phased roadmap, recommended implementation order. |
| [`docs/open-decisions.md`](docs/open-decisions.md) | Every point where implementation ran ahead of the contract, and what still needs a decision. |
| [`docs/load-test-results.md`](docs/load-test-results.md) | Load test results against NFR-8, and what they do not cover. |

If a requirement changes, update the API contract first, then implement it.

## Repository layout

```
clinic-backend/    Java 21 + Spring Boot + PostgreSQL backend (see tech-stack.md §2)
asr-service/       Self-hosted speech in and out (FastAPI), behind clinic.speech.enabled
ml-service/        Self-hosted no-show risk and condition-hint models (FastAPI), behind clinic.ml.enabled
docs/              Binding API contract and supporting engineering docs
```

## Git workflow

- `main` is the stable branch; it is never committed to directly.
- One feature per branch, named per the contract: `feature/auth`, `feature/doctor`,
  `feature/patient`, `feature/availability`, `feature/appointments-api`,
  `feature/appointments-booking`, `feature/payments`, `feature/notifications-api`,
  `feature/notifications-worker`, `feature/redis`, `feature/rabbitmq`, `feature/ai-integration`.
  Frontend branches mirror the pattern: `feature/frontend-shell`, `feature/frontend-booking-flow`, …
- Every branch merges into `main` through a reviewed pull request.
- Conventional commits: `feat:`, `fix:`, `docs:`, `chore:`, `test:`.

## Engineering conventions

- Timezone is `Asia/Kolkata` (never `Asia/Calcutta`); JVM args live in `pom.xml`'s
  `spring-boot-maven-plugin` block, not on the command line.
- Java filenames match class names exactly.
- DTOs at the API boundary; JPA entities are never exposed directly.
- JWT secrets and database credentials are never committed.

## Deployment

- **Backend (Render):** `render.yaml` is a Blueprint for the API, Postgres and Redis.
  In Render choose *New → Blueprint* and pick this repository. RabbitMQ is not
  provisioned; reminders stay `QUEUED` until `SPRING_RABBITMQ_ADDRESSES` points at a
  hosted broker and `RABBITMQ_LISTENER_ENABLED=true`.
- **Frontend (Vercel):** import the repository with *Root Directory* `frontend`.
  `frontend/vercel.json` proxies `/api/*` to the Render service, so the SPA keeps
  calling its own origin and no CORS setup is needed. If the Render service URL
  differs from `https://cliniq-backend-sjj0.onrender.com`, update the rewrite.

### What runs where

| Part | Local | Deployed (Render + Vercel) |
|---|---|---|
| Website | Vite on :5173 | Vercel |
| Backend, PostgreSQL, Redis | Docker + Maven | Render |
| ML service (`ml-service/`) | `uvicorn` on :8002 | Render, as `cliniq-ml` (trained models baked into the image) |
| Voice (`asr-service/`) | `uvicorn` on :8001 | **Not deployed** |
| RabbitMQ | Docker | Not provisioned (in-app reminders still work) |

**Why voice is not deployed.** The speech models need about 2.3 GB of memory (IndicConformer,
Whisper and three MMS voices) and a 2.5 GB download, and Render's free instance has 512 MB. It
would need a paid instance with at least 4 GB. Hosting it on a third-party platform instead
would also send patients' audio outside the clinic's own infrastructure, which NFR-9 rules out.
The backend leaves the voice endpoints switched off (`CLINIC_SPEECH_ENABLED` is false), so the
site works without them.

**The ML service on a free instance.** It sleeps when idle. The backend waits only 2 seconds for
it, so the first schedule load after a quiet spell shows no risk badges or condition hints; they
return once the service has woken. This is intended: ML is a hint, never a reason for the
schedule to fail.

### Demo accounts

Set `CLINIC_DEMO_DATA_ENABLED=true` (the Render blueprint does) and an empty database starts
with these accounts. They are created only if missing, so an account that already exists is
never changed, and it is safe on every start.

| Role | Email | Password |
|---|---|---|
| Admin | `ramesh@example.com` | `StrongPassword123` |
| Doctor | `meera@example.com` (Dentist, ₹500) | `StrongPassword123` |
| Doctor | `arvind@example.com` (General Physician, ₹400) | `StrongPassword123` |
| Doctor | `kavya@example.com` (Dermatologist, ₹650) | `StrongPassword123` |
| Patient | `anjali@example.com` | `StrongPassword123` |

Each doctor works Monday to Friday, 09:00 to 17:00, in 30-minute slots, so there is something
to book straight away. Change the shared password with `CLINIC_DEMO_PASSWORD`.

> **These credentials are public.** Anyone who reads this file can sign in as the administrator.
> Use them for a demonstration only. Before real patients use the site, set
> `CLINIC_DEMO_DATA_ENABLED=false`, delete the demo accounts, and change the bootstrap
> administrator's password.
