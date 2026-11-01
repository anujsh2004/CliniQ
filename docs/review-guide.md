# CliniQ — Complete Review Guide

Simple-words guide for the project review. Read it top to bottom once, then use the
sections as a cheat sheet.

> **Sources used:** the Project-I report (PDF) and the real code in this repository
> (`main`, after the pull that brought in the ML service). The PPT you mentioned did not
> come through, so nothing here is taken from slides.

---

## 0. Read this first (things to fix before the review)

These are places where the paper, the code and the git history do not agree. A reviewer
can spot them, so decide your answer in advance.

| # | What does not match | What to do |
|---|---|---|
| 1 | **The report never mentions the two ML models** (no-show risk and condition hints). The abstract and Section 1.3 talk only about voice, and "AI layer" is listed as future work. But the ML service is merged into `main` (PR #37). | Add a short "ML module" section to the report, or at least say it clearly in the review. Section 6 below is written so you can lift it. |
| 2 | **Who wrote what.** The git history shows **Anuj: 127 commits**, **Parth: 3 commits (all ML)**, **Amrita: 0 commits**. But `docs/api-contract.md` says Parth owns database, security, concurrency, payments, Redis, RabbitMQ and Docker. | Agree as a team how you describe this. Section 8 shows both the planned split and the git evidence. Do not claim more than the evidence shows. |
| 3 | **Amrita has no commits and is not in the API contract.** I cannot see her work anywhere in the repo. | Fill in Section 8.3 yourselves. If she worked on the report, testing, design or research, say so honestly. If she committed under another account, check `git log --all`. |
| 4 | `asr-service/README.md` still says text-to-speech is **IndicF5**. The code and the report use **MMS-TTS** (IndicF5 would not load, see decision D28). | Update that README line. |
| 5 | The no-show model's accuracy numbers are **not stored in the repo** (`models/` and `data/` are git-ignored). | Run `train.py` once before the review and keep the numbers from `models/metrics.json`. |
| 6 | The report says "360+ tests". The ML service adds Python tests and more Java tests since then. | Re-count before quoting a number. |

---

## 1. The project in one minute

**CliniQ** (called *Cliniva* in the code) is a web app for **small independent clinics in
India** (1–3 doctors, one receptionist).

Today these clinics use a paper register, phone calls and WhatsApp. That causes:

- two patients booked into the same time (double booking),
- patients cannot see when the doctor is free without calling,
- people forget and do not turn up (no-shows),
- payment (cash) is not linked to the booking,
- everything is in English only.

Hospital software fixes this but is too big and costly. A normal calendar app is cheap but
does not understand clinics.

**CliniQ gives a small clinic:**

1. Patients see live free slots, book, pay, cancel or reschedule online.
2. Doctors set weekly hours once; slots are made automatically.
3. A booked slot is **held for 5 minutes** while the patient pays, then released.
4. **Double booking is impossible** — the database itself refuses it.
5. Reminders (24 hours and 1 hour before the visit).
6. A **voice assistant** in English, Hindi and Tamil, running on the clinic's own machine.
7. **Machine learning for the doctor:** a no-show risk badge, and symptom-based condition
   hints. (This is the new part — Section 6.)

**Tech in one line:** Java 21 + Spring Boot + PostgreSQL (backend), React + TypeScript
(frontend), two small Python FastAPI services (voice and ML), Docker, GitHub Actions, Render + Vercel.

---

## 2. How the system is built (plain words)

```
 Patient / Doctor / Admin  (browser)
            │
   React website (frontend/)            hosted on Vercel
            │  all calls go to /api/...
            ▼
   Spring Boot backend (clinic-backend/) hosted on Render
     ├─ Security: login check (JWT), rate limit
     ├─ Controllers → Services → Repositories
     └─ talks to:
          ├─ PostgreSQL   ← the ONLY source of truth for bookings
          ├─ Redis        (cache — turned out not needed, see §7.3 of report)
          ├─ RabbitMQ     (reminder queue + worker)
          ├─ Payment gateway (Razorpay, or a fake "stub" for the demo)
          ├─ asr-service/ (Python) — speech to text / text to speech
          └─ ml-service/  (Python) — no-show risk + condition hints
```

**Key design idea:** the booking part is one **monolith** so that "book a slot" is one
database transaction. Everything optional (Redis, RabbitMQ, voice, ML) sits *outside* and
can fail without breaking a booking.

**Layers inside the backend:**

- **Controller** — receives the web request, checks the shape, returns the answer.
- **Service** — the business rules and the transactions.
- **Repository** — talks to the database.
- **DTO** — the clean shape of data sent over the API (database objects are never exposed).
- **Entity** — the Java version of a database table.

---

## 3. Walkthrough: one patient's journey (and which files do the work)

| Step | What happens | Main files |
|---|---|---|
| 1. Sign up | Patient registers. Password is hashed (BCrypt). Self-signup can only make a **patient**. | `AuthController`, `AuthService`, `RegisterPage.tsx` |
| 2. Log in | Gets two tokens: access (1 hour) and refresh (7 days). Max 10 login tries per minute per IP. | `JwtService`, `JwtAuthenticationFilter`, `AuthRateLimitFilter`, `LoginPage.tsx` |
| 3. Browse doctors | List of doctors with fee and speciality. | `DoctorController`, `DoctorsPage.tsx` |
| 4. See free slots | Pick a date, see the slots (free / taken). | `AvailabilityService`, `SlotGrid.tsx`, `DateStrip.tsx` |
| 5. *(New, ML)* Tick symptoms | Optional symptom checklist on the booking page. | `SymptomController`, `SymptomPicker.tsx` |
| 6. Book | Slot is **locked**, marked HELD, appointment becomes `PENDING_PAYMENT` with a 5-minute hold. | `AppointmentBookingService`, `V5__…sql` |
| 7. Pay | Order is created. **Only a signed webhook from the gateway** can mark it paid. | `PaymentService`, `SignatureVerifier`, `PaymentAction.tsx` |
| 8. Unpaid? | A job runs every 30 seconds and frees abandoned holds. | `HoldExpiryService`, `HoldReleaser`, `HoldCountdown.tsx` |
| 9. Reminders | Created at booking: 24h and 1h before. Patient can add their own. | `InAppReminderService`, `ReminderWorker`, `NotificationBell.tsx` |
| 10. Ask the assistant | Text or voice, in English / Hindi / Tamil. Answers only from real clinic data; refuses medical questions. | `assistant.ts` (+ `.hi`, `.ta`), `VoiceAssistantPage.tsx`, `speech/` |
| 11. Doctor's day | Doctor sees the day's appointments, **no-show risk badge** and **condition hints**, and marks visits complete. | `DoctorScheduleController`, `SchedulePage.tsx`, `RiskBadge.tsx`, `ConditionHintsPanel.tsx` |

**Appointment states:** `PENDING_PAYMENT → CONFIRMED → COMPLETED` (or `CANCELLED`, or `NO_SHOW`).
**Slot states:** `AVAILABLE → HELD → BOOKED` (or back to `AVAILABLE`, or `EXPIRED`).

### The core guarantee: no double booking (three safety nets)

1. **Row lock.** When booking, the code asks PostgreSQL `SELECT … FOR UPDATE` on that slot. Only one request at a time can hold it.
2. **Re-check under the lock.** After getting the lock, check the slot is still free. The loser gets `409 SLOT_ALREADY_BOOKED`.
3. **Database rule.** A *partial unique index* means only one live appointment (`PENDING_PAYMENT` or `CONFIRMED`) can exist per slot. Even buggy future code cannot break it.

**Proof:** a Testcontainers test on real PostgreSQL, a two-browser Playwright test, and a load test
(96 attempts on 32 slots → always exactly 32 booked, 64 rejected).

---

## 4. Why ML here? (the idea in simple words)

Two real clinic problems that rules alone cannot solve well:

1. **"Who will not turn up?"** Reminders help, but the front desk cannot call everyone. If the
   system tells the doctor *which few appointments are most likely to be missed*, the desk can call those first.
2. **"What is this patient probably coming for?"** If a patient ticked *high fever, joint pain, skin rash*,
   the doctor can walk in already thinking about the likely causes.

Both are **decision support for the doctor** — never shown to the patient and never acting on their own.

---

## 5. ML part — complete explanation

Everything is in `ml-service/` (Python, FastAPI, scikit-learn). It has **two models**.

### 5.1 Model 1 — No-show risk

**Question it answers:** "How likely is this appointment to be missed?"

**Data:** Kaggle *Medical Appointment No Shows* — about 110,000 real appointments from
Vitória, Brazil (2016). Licence **CC BY-NC-SA** → fine for coursework/demo, **not** for a product that is sold (decision D29).

**The honest-features idea (this is a strong point):**
The Kaggle file also has age, gender, neighbourhood and chronic illness. They help prediction,
but **CliniQ does not collect them**. A model trained on columns the live system can never send
would give scores that look fine but mean nothing. So the model uses **only 7 features that
exist in both places**:

| Feature | Plain meaning | In Kaggle | In CliniQ |
|---|---|---|---|
| `lead_days` | days between booking and visit | AppointmentDay − ScheduledDay | slot date − booking date |
| `appointment_weekday` | day of the week of the visit | AppointmentDay | slot date |
| `booking_hour` | hour of day the booking was made | ScheduledDay hour | booking time (Asia/Kolkata) |
| `prior_visits` | earlier visits of this patient | earlier rows of same patient | earlier `COMPLETED`/`NO_SHOW` |
| `prior_no_shows` | earlier missed visits | same | same |
| `prior_no_show_rate` | missed ÷ visits | calculated | calculated |
| `reminder_sent` | was a reminder sent? | SMS_received | reminder not `FAILED` |

**Avoiding "cheating" (data leakage):**
- Patient history counts only **earlier days**, so two visits on the same day never see each other's result.
- The split is **by date, not random**: earliest 60% of days to train, next 20% to choose the model, last 20% kept for final numbers. A random split lets the model peek at the future and makes results look better than they are.

**Training (`train.py`):**
- Two candidates: **Logistic Regression** (simple baseline) and **Gradient Boosting** (calibrated with isotonic calibration, so 0.3 really means ≈ 3 in 10).
- The winner is picked on **PR-AUC** (the no-show class is the minority; PR-AUC shows how clean the top of the list is).
- Reports ROC-AUC, PR-AUC, Brier score, and **lift** (how much likelier a HIGH-band appointment is to be missed than average).
- Saves `models/no_show.joblib` and `models/metrics.json`.

**Bands (`HIGH` / `MEDIUM` / `LOW`):**
HIGH = riskiest **20%** of appointments, MEDIUM = next **30%**. These are *shares*, not fixed
probability cut-offs, because a cut-off learned in Brazil would not mean the same thing in a
clinic with a different no-show rate. The ordering is likely to carry over; exact percentages may not.

### 5.2 Model 2 — Condition hints

**Question it answers:** "Given these symptoms, which 3 conditions do they most resemble?"

**Data:** Kaggle *Disease Prediction Using Machine Learning* — 132 yes/no symptom columns, 41 diseases.
It is **synthetic** and famous for "100% accuracy" because its 4,920 rows are only **~305 distinct
symptom sets repeated**, and almost all test rows also appear in training.

**What the code does about that (`conditions.py`, `train_conditions.py`) — the second strong point:**
1. **Removes duplicates before splitting**, so no case is tested on its own copy.
2. **Cleans** messy column names (`spotting_ urination`) and misspelt diseases (`Peptic ulcer diseae`).
3. **Hides symptoms on purpose.** The dataset lists *every* symptom of a disease, but a real patient ticks only some.
   Training and testing therefore use copies where symptoms are randomly hidden (training keeps 40–100%).
4. Tests **three ways**:

| Held-out cases | Right condition first | Right condition in top 3 |
|---|---|---|
| All symptoms | 98.9% | 100% |
| Half the symptoms | 94.2% | 99.0% |
| **Only two symptoms** (the minimum) | **71.6%** | **89.5%** |

   The last row is the honest one — and it is still an *upper bound*, because generated data is cleaner than real patients.
5. Candidates: Naive Bayes vs Logistic Regression (Logistic won). It returns the **top 3**, never one answer, because one answer reads like a diagnosis.
6. **Fewer than 2 known symptoms → no hint at all**, because with one symptom the model just guesses its prior.

### 5.3 How the ML service is served (`app.py`)

A small FastAPI app that **loads the trained models once at startup and never trains**.

| Endpoint | Job |
|---|---|
| `GET /health` | Says `UP` or `NO_MODEL` for each model. |
| `POST /score` | Gets the 7 features for each appointment → returns probability + band + model version. |
| `GET /symptoms` | The list of symptoms (code + readable label) the model understands. |
| `POST /conditions` | Gets symptom codes → returns top 3 conditions with probabilities, plus any unknown codes. |

Safety details: batch limit 500, input validation (e.g. `priorNoShows` cannot exceed `priorVisits`),
either model can be missing and the other keeps working.

### 5.4 How the backend uses it (`clinic-backend/.../ml/`)

| File | Job |
|---|---|
| `NoShowRiskService` | Builds the 7 features from CliniQ's own records, asks the ML service, turns answers into badges. |
| `NoShowRiskClient` | The HTTP call to `/score`. |
| `ConditionHintService` / `ConditionClient` | Same idea for `/conditions`. |
| `RiskBand`, `RiskFeatures`, `MlUnavailableException` | Small helper types. |
| `MlConfig`, `MlProperties` | Settings: on/off, URL, timeout. |
| `SymptomController` | Serves the symptom list to the booking page. |
| `V11__appointment_symptoms.sql` | New database column to store the symptoms a patient ticked. |

**Fail-safe design (strong point):** the ML is **off by default** (`CLINIC_ML_ENABLED=false`) and
has a **2-second timeout**. If the service is off, slow or broken, the doctor's schedule
**still loads — just without badges**. A hint is never worth a failed page. Booking never depends on ML.

### 5.5 How the frontend shows it

- `RiskBadge.tsx` — coloured LOW / MEDIUM / HIGH badge on the doctor's schedule.
- `SymptomPicker.tsx` — symptom checklist on the booking page.
- `ConditionHintsPanel.tsx` — the 3 likely conditions, **doctor-side only**.
- The patient only sees what they ticked plus a **fixed emergency notice** that does not depend on the model.

### 5.6 What is NOVEL (say this in the review)

1. **Features chosen for serving, not for the leaderboard.** Dropped age/gender/etc. on purpose so the live scores stay meaningful.
2. **Time-based split and same-day history rule** to stop leakage — most student projects use a random split.
3. **Calibrated probabilities + share-based bands** instead of raw cut-offs, designed for a population shift (Brazil → India).
4. **Honest evaluation of a famous "100%" dataset** — de-duplicated it, then tested with hidden symptoms, and reported the harsh "two symptoms" number.
5. **Privacy by design:** both models run on the clinic's own hardware; no patient data goes to a third-party AI API (matches NFR-9 and the DPDP Act, 2023).
6. **Safe-by-design product rules:** doctor-only, top-3 not top-1, minimum 2 symptoms, never automatic, off by default, fails open.
7. **Documented limits** as decisions: D29 (no-show model trained on someone else's patients, CC BY-NC-SA) and D30 (synthetic condition data; not a medical device).
8. **Fits the existing system** without breaking the contract (API contract sections 26 and 27, version 1.6) — the NFR-6 promise that AI can be added without breaking APIs.

### 5.7 Limits to admit (honesty scores points)

- No-show model learned from **Brazil 2016**, not Indian clinic patients. Order likely transfers, exact % may not.
- Condition data is **synthetic**. Real patients are vaguer. Not a medical device; Indian Medical Devices Rules may apply to diagnosis-suggesting software.
- Kaggle has nothing like CliniQ's **payment hold**, so abandoned holds are not modelled.
- **Future work:** retrain on CliniQ's own outcomes after a few thousand (the endpoint `PATCH /appointments/{id}/no-show` exists so outcomes get recorded), and record the doctor's final diagnosis per visit.

### 5.8 ML tests

`ml-service/tests/` — `test_features.py`, `test_conditions.py`, `test_app.py`, `conftest.py`.
They train on **tiny fake tables in the same shape as the Kaggle files**, so they need no dataset and no internet.
Backend side: `NoShowRiskServiceTest`, `NoShowRiskClientTest`, `NoShowHistoryIntegrationTest`, `ConditionHintServiceTest`, `ConditionClientTest`, `SymptomControllerTest`.

### 5.9 How to demo the ML (5 minutes)

```bash
# 1. Train (needs the two Kaggle datasets in ml-service/data/)
cd ml-service
uv venv --python 3.12 && uv pip install -r requirements.txt
uv run --no-project python train.py
uv run --no-project python train_conditions.py

# 2. Run the ML service
uv run --no-project uvicorn app:app --port 8002

# 3. Run the backend with ML turned on
cd ../clinic-backend
CLINIC_ML_ENABLED=true ./mvnw spring-boot:run
```

Then: log in as a patient → tick 3 symptoms while booking → log in as the doctor → open the
schedule → show the risk badge and the 3 condition hints. Finally **stop the ML service and
refresh** — the schedule still loads. That "it still works" moment is the best demo of the fail-safe design.

---

## 6. Other parts, briefly (so you can answer anything)

- **Payments:** order created through a gateway interface (Razorpay or a stub). Payment is confirmed **only** by a webhook whose **HMAC-SHA256 signature** is checked in **constant time**. A demo-confirm endpoint exists but is rejected once real keys are set.
- **Reminders:** made at booking, delivered through RabbitMQ to a worker. The sender currently only **logs** the message (no WhatsApp provider yet).
- **Voice assistant:** scripted (not a generative AI) so answers are always true; refuses symptoms/medicine questions; detects language by script. Speech runs in `asr-service/` using IndicConformer (Hindi/Tamil), Whisper (English) and MMS-TTS (speaking).
- **Security:** BCrypt, short-lived JWT, role checks, ownership checks (a patient cannot touch another patient's booking → 403), login rate limit, no privilege escalation at sign-up.
- **Performance:** slot fetch p95 = 171 ms at 100 clients (target 500 ms). Redis cache made doctor-list *slower* (624 ms → 97 ms after removing it), so it was removed — "measure before you cache".

---

## 7. Testing, CI and deployment

- **Backend tests** (JUnit, Mockito, Testcontainers) — services, controllers, security, concurrency, ML client/service.
- **Frontend tests** (Vitest + Testing Library) and **end-to-end tests** (Playwright, axe-core for accessibility).
- **ML tests** (pytest) in `ml-service/tests/`.
- **Load test:** `scripts/load-test.mjs`.
- **CI:** `.github/workflows/ci.yml` runs everything on every pull request; a red run blocks merging.
- **Deploy:** `render.yaml` (backend + Postgres + Redis on Render), `frontend/vercel.json` (frontend on Vercel, forwards `/api/*` to the backend).

---

## 8. Team contributions

### 8.1 What the git history shows (facts)

| Member | Commits | What the history contains |
|---|---|---|
| **Anuj Sharma** (`anujsh2004`, two emails) | **127** | Almost everything: backend scaffold, auth, doctors, patients, availability, slots, booking, payments, RabbitMQ, Redis, notifications, frontend (all pages), voice/ASR service, assistant, landing page, CI, security hardening, e2e + load + accessibility tests, deployment, docs, 25+ merged pull requests. |
| **Parth Khandelwal** (`parth-05122005`) | **3** | The **entire ML module** (PR #37): `ml-service/` plus the backend `ml/` package, symptom storage, doctor-schedule risk badge, symptom picker, hint panel, contract sections 26 and 27, decisions D29 and D30. About 3,500 lines across 60 files, with tests. |
| **Amrita Kumari** | **0** | No commits found under any author name. |

### 8.2 What the API contract planned (document, not git)

`docs/api-contract.md` §3 and §23 split the backend work between **Parth and Anuj** only:

- **Parth (planned):** architecture and database, Spring Security + JWT, **booking concurrency**, Redis, RabbitMQ, **payments**, Docker and deployment.
- **Anuj (planned):** doctor APIs, patient APIs, availability and slots, appointment APIs and validation, error handling, Swagger/OpenAPI and API testing.

> ⚠️ The plan and the git history differ (e.g. concurrency, payments, RabbitMQ were committed under Anuj's account). Before the review, agree on one honest story — for example, who designed vs who typed vs who reviewed. Reviewers often ask "who wrote this class?" and may check GitHub.

### 8.3 A breakdown you can use (edit to match reality)

Use this as the starting template. The **bold ML rows are backed by the repo**; the rest should match what each person truly did.

| Area | Anuj | Parth | Amrita |
|---|---|---|---|
| Requirements, literature review, report writing | *fill in* | *fill in* | *fill in* |
| Backend core (auth, doctor, patient, availability, booking, payments) | Implemented (git) | Design/architecture owner per contract — *confirm* | *fill in* |
| Concurrency-safe booking + load/concurrency tests | Committed (git) | Designated owner per contract — *confirm* | *fill in* |
| Redis, RabbitMQ, reminders | Committed (git) | Designated owner per contract — *confirm* | *fill in* |
| Frontend (all pages, design system) | Committed (git) | — | *fill in* |
| Voice assistant + speech service | Committed (git) | — | *fill in* |
| **ML: no-show risk model, features, training, service** | — | **Built (git, PR #37)** | — |
| **ML: condition-hint model, symptom picker, doctor hint panel** | — | **Built (git, PR #37)** | — |
| CI, Docker, Render/Vercel deployment | Committed (git) | Designated owner per contract — *confirm* | *fill in* |
| Testing (unit, e2e, accessibility) | Committed (git) | ML tests (git) | *fill in* |
| Documentation (contract, decisions) | Committed (git) | ML sections (git) | *fill in* |

If you present the ML section, say it clearly: **the ML module was built by Parth; Anuj reviewed/merged
it and the integration points.** Being accurate protects you if you are asked to explain a function.

---

## 9. File and folder map (what each thing is)

### Top level

| Path | What it is |
|---|---|
| `README.md` | Entry page: what the project is, folder layout, git rules, deployment notes. |
| `product-description.md` | The product requirements — what the app must do. |
| `design.md` | Look and feel: colours, fonts, accessibility rules. |
| `tech-stack.md` | Technology choices and the phase-by-phase roadmap. |
| `render.yaml` | Recipe to deploy the backend, database and Redis on Render. |
| `.github/workflows/ci.yml` | Robot that runs all tests on every pull request. |
| `scripts/load-test.mjs` | Script that sends many requests at once to measure speed and test double-booking. |
| `docs/` | Written decisions and results (below). |
| `clinic_db_backup_20261015_1812.sql` | A database backup file, **not tracked by git**. Do not commit it (may contain patient-like data). |

### `docs/`

| File | What it is |
|---|---|
| `api-contract.md` | The binding "agreement" of every API, error code and owner. Sections 26–27 are the ML features. |
| `open-decisions.md` | Honest list of every place we had to decide something (D1…D30), incl. D25 (no cache), D28 (voice licence), D29 and D30 (ML limits). |
| `load-test-results.md` | Speed test results and what they do *not* prove. |
| `review-guide.md` | This file. |

### `clinic-backend/` — the Java server

| Path | What it is |
|---|---|
| `pom.xml` | List of Java libraries and build settings (Maven). |
| `Dockerfile`, `docker-compose.yml` | Package the backend; start Postgres, Redis, RabbitMQ locally. |
| `mvnw`, `mvnw.cmd`, `.mvn/` | Maven wrapper — builds without installing Maven. |
| `.env.example` | Example of the secret settings (never real secrets). |
| `src/main/resources/application.yml` | All settings: database, JWT, hold time, ML on/off, speech, payments. |
| `src/main/resources/db/migration/V1…V11` | Database changes in order (Flyway): users, clinics/doctors, patients, availability/slots, appointments (with the no-double-booking index), payments, notifications, holds, in-app reminders, cascade fix, **V11 = symptoms for ML**. |
| `src/main/java/com/clinic/controller/` | Web entry points (Auth, Doctor, Patient, Appointment, Availability, Payment, Notification, Symptom, Voice, Health…). |
| `…/service/` | Business rules: booking, availability, slot generation, payments, reminders, hold expiry. |
| `…/repository/` | Database queries. |
| `…/entity/` | Database tables as Java classes (User, Doctor, Patient, Slot, Appointment, Payment, Notification…). |
| `…/dto/` | Request and response shapes for the API (46 files). |
| `…/mapper/` | Converts entities to DTOs. |
| `…/security/` | JWT, login filter, rate limiter, client IP finder, error responders. |
| `…/payment/` | Payment gateway interface, Razorpay and stub, webhook signature check. |
| `…/notification/` | Reminder publisher, RabbitMQ worker, sender (currently only logs). |
| `…/speech/` | Client that talks to the voice service. |
| `…/ml/` | **ML integration**: risk and condition clients and services. |
| `…/config/` | Settings classes (security, cache, RabbitMQ, ML, speech, OpenAPI, admin bootstrap, request ID). |
| `…/exception/` | Error types + one global handler that returns the standard error format. |
| `src/test/java/` | All backend tests (concurrency, security, controllers, services, ML). |

### `frontend/` — the website (React + TypeScript)

| Path | What it is |
|---|---|
| `package.json`, `vite.config.ts`, `tsconfig*.json` | Libraries and build settings. |
| `tailwind.config.js`, `postcss.config.js`, `src/styles/index.css` | Styling. |
| `vercel.json` | Forwards `/api/*` to the backend. |
| `playwright.config.ts`, `e2e/` | Browser end-to-end tests (booking, concurrency, payment, assistant, voice, accessibility, landing, navigation, states). |
| `src/main.tsx`, `App.tsx` | Start of the app and the page routes. |
| `src/api/` | `client.ts` (typed API caller), `endpoints.ts` (every endpoint), `session.ts` (stores login). |
| `src/context/AuthContext.tsx` | Keeps who is logged in. |
| `src/pages/` | Screens: Landing, Login, Register, Doctors, DoctorDetail (booking), Appointments, Notifications, Profile, VoiceAssistant, **Schedule (doctor, with ML badges)**, Availability (doctor). |
| `src/components/` | Reusable pieces: AppShell, SlotGrid, DateStrip, Stepper, HoldCountdown, PaymentAction, NotificationBell, ReminderPicker, AssistantWidget, StatusBadge, **RiskBadge**, **SymptomPicker**, **ConditionHintsPanel**, Button, Card, Field, Toast, ConfirmDialog, States, RequireAuth, LandingRedirect, PageHeader. |
| `src/lib/` | Helpers: `assistant.ts` (+ Hindi `.hi`, Tamil `.ta`) scripted assistant, `useVoiceInput.ts` (microphone), `razorpay.ts`, `format.ts`, `doctorNames.ts`. |
| `src/types/api.ts` | TypeScript shapes matching the API. |
| `src/test/setup.ts` and `*.test.ts(x)` | Frontend unit and component tests. |

### `asr-service/` — voice (Python)

| File | What it is |
|---|---|
| `app.py` | FastAPI service: speech to text (IndicConformer for Hindi/Tamil, Whisper for English) and text to speech (MMS-TTS). |
| `requirements.txt`, `.gitignore` | Python libraries; ignore downloaded models. |
| `README.md` | Setup guide (the TTS line about IndicF5 is out of date — see §0). |

### `ml-service/` — machine learning (Python)

| File | What it is |
|---|---|
| `features.py` | Defines the 7 no-show features and builds them from the Kaggle data without leaking the future. |
| `train.py` | Trains and compares the no-show models, evaluates, saves the best one + a metrics file. |
| `conditions.py` | Cleans the symptom dataset, removes duplicates, and hides symptoms to imitate real patients. |
| `train_conditions.py` | Trains and compares the condition models, reports the three accuracy levels, saves the best. |
| `app.py` | FastAPI service that loads both saved models and answers `/score`, `/conditions`, `/symptoms`, `/health`. |
| `requirements.txt` | Python libraries (FastAPI, pandas, scikit-learn, joblib; pytest for tests). |
| `README.md` | How to set up, train, run and call it; the honest limits. |
| `.gitignore` | Keeps the downloaded datasets (`data/`) and trained models (`models/`) out of git. |
| `tests/` | `conftest.py` (fake data in Kaggle's shape), `test_features.py`, `test_conditions.py`, `test_app.py`. |

---

## 10. Likely review questions and short answers

**Q. Why did you add ML to a booking system?**
To help the front desk call the people most likely to miss a visit, and to help the doctor prepare. Both are doctor-only hints.

**Q. Which model did you pick for no-show, and why?**
We compared Logistic Regression with calibrated Gradient Boosting and chose by PR-AUC on a later validation period. Calibration makes the probabilities honest.

**Q. Why not use all Kaggle columns?**
CliniQ does not collect age, gender, etc. A model trained on columns it will never receive gives meaningless scores at run time.

**Q. Why split by date and not randomly?**
A random split lets the model see the future. A date split mimics real use: learn from the past, predict the next bookings.

**Q. Your condition model got 100% on Kaggle. Is that real?**
No — the dataset has only ~305 distinct cases repeated. We removed duplicates, hid symptoms to imitate real patients, and report 71.6% top-1 / 89.5% top-3 with only two symptoms, which is still an upper bound.

**Q. Isn't this a medical diagnosis tool?**
No. Doctor-only, shows 3 possibilities not 1, needs at least 2 symptoms, never acts automatically, and the patient only sees an emergency notice. It would need real data and regulatory review before real clinical use (D30).

**Q. What if the ML service goes down?**
The schedule still loads without badges (2-second timeout, off by default). Booking never calls ML.

**Q. Where does patient data go?**
Nowhere outside the clinic's machines. Both ML models and the speech models are self-hosted.

**Q. Can two patients book the same slot?**
No: row lock + re-check + database partial unique index. Tested with a real database, two browsers, and a 96-requests-on-32-slots load test.

**Q. How do you know payment is real?**
Only a webhook with a valid HMAC-SHA256 signature (compared in constant time) can mark a payment as paid. The browser's claim is ignored.

**Q. What would you do next?**
Real Razorpay and WhatsApp credentials, retrain the no-show model on CliniQ's own outcomes, record doctors' final diagnoses, a commercial-licence voice, and token revocation.

---

## 11. 10-minute demo order

1. Landing page → register a patient → log in.
2. Doctors → pick a doctor → choose date and time.
3. Tick 3 symptoms → confirm booking → show the 5-minute hold countdown.
4. Pay (demo/stub) → appointment becomes Confirmed → bell shows reminders.
5. Open the voice assistant in Hindi, ask about a doctor's timing; ask a symptom question and show the refusal.
6. Switch to the doctor account → Schedule → **risk badge + condition hints** → mark a visit complete.
7. Stop the ML service → refresh → schedule still works.
8. Show the concurrency test or the load-test table (32 booked / 64 rejected).
9. Show GitHub Actions passing and the pull-request history.
