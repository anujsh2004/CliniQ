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
  differs from `https://cliniq-backend.onrender.com`, update the rewrite.
