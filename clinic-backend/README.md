# clinic-backend

Spring Boot backend for CliniQ (Cliniva). Layered monolith:
Controller → Service → Repository → PostgreSQL, per `tech-stack.md` §2.

## Requirements

- JDK 21 or newer (the project targets Java 21)
- Docker (for PostgreSQL)
- No local Maven install needed — use the bundled wrapper (`./mvnw`, `mvnw.cmd`)

## Running locally

```bash
docker compose up -d postgres     # PostgreSQL 16 on host port 5433
./mvnw spring-boot:run            # http://localhost:8080
curl http://localhost:8080/api/v1/health
```

The JVM timezone is fixed to `Asia/Kolkata` through the `spring-boot-maven-plugin`
block in `pom.xml`. Do not pass it as a command-line flag.

## Configuration

`src/main/resources/application.yml` reads every credential from an environment
variable with a local-dev default: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
`SERVER_PORT`. Copy `.env.example` to `.env` for local overrides; secrets are
never committed (API contract §19).

## Schema

Flyway owns the schema (`src/main/resources/db/migration`); Hibernate runs with
`ddl-auto: validate` and only verifies that entities match the migrated schema.
Each feature branch adds its own versioned migration.

## Package structure (API contract §4)

```
com.clinic
├── controller/   REST controllers
├── service/      business logic
├── repository/   Spring Data JPA repositories
├── entity/       JPA entities (BaseEntity supplies createdAt/updatedAt)
├── dto/
│   ├── request/  inbound, @Valid-annotated payloads
│   └── response/ outbound DTOs + the standard response envelope
├── security/     Spring Security and JWT
├── exception/    ErrorCode, domain exceptions, global handler
├── config/       cross-cutting configuration
└── mapper/       entity ↔ DTO mapping
```

## Response envelope

Every response uses the envelope from API contract §7:

- success — `ApiResponse.success(message, data)`
- error — `ErrorResponse.of(errorCode, message)` with a canonical `ErrorCode`
- validation error — 422 with a field-level `errors[]` array

`GlobalExceptionHandler` performs all HTTP formatting; service code only throws
`ApiException` subclasses carrying an `ErrorCode`. Every response also carries a
`requestId`, assigned by `RequestIdFilter` and mirrored into the logging MDC and
the `X-Request-Id` response header.

## Tests

```bash
./mvnw test
```
