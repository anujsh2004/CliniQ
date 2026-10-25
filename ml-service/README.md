# ML service

Two self-hosted models for CliniQ, both shown to the doctor on their schedule
and never to the patient:

1. **No-show risk** - how likely each appointment is to be missed, so the
   front desk knows whom to call ahead (API contract 26).
2. **Condition hints** - for patients who tick symptoms when booking, the
   three conditions those symptoms most resemble (API contract 27). See
   [Condition hints](#condition-hints) below.

| Piece | What it is |
|---|---|
| `features.py` | The seven no-show features, and how they are built from the Kaggle data |
| `train.py` | Trains, evaluates and saves the no-show model to `models/` |
| `conditions.py` | Loads and cleans the symptom dataset; hides symptoms to mimic real patients |
| `train_conditions.py` | Trains, evaluates and saves the condition model to `models/` |
| `app.py` | FastAPI service that loads both models and serves them |

Either model can be missing; the other keeps working.

## Why only these features

The Kaggle data also has age, gender, neighbourhood and chronic conditions.
They help, but CliniQ collects none of them, so the backend could never send
them. A model trained on a column it never receives when scoring gives scores
that look fine and mean nothing. The model uses only what both sides have:

| Feature | In Kaggle | In CliniQ |
|---|---|---|
| `lead_days` | `AppointmentDay − ScheduledDay` | slot date − booking date |
| `appointment_weekday` | `AppointmentDay` | slot date |
| `booking_hour` | `ScheduledDay` hour | booking time, Asia/Kolkata |
| `prior_visits`, `prior_no_shows`, `prior_no_show_rate` | earlier rows for the same `PatientId` | earlier `COMPLETED` / `NO_SHOW` appointments |
| `reminder_sent` | `SMS_received` | a reminder that is not `FAILED` |

History counts only earlier *days*, so two visits on the same day never see
each other's outcome.

## Setup, once

Python 3.11 or 3.12.

```bash
cd ml-service
uv venv --python 3.12
uv pip install -r requirements.txt
```

## The dataset

Download `KaggleV2-May-2016.csv` from
<https://www.kaggle.com/datasets/joniarroba/noshowappointments> into
`ml-service/data/`. It is **CC BY-NC-SA**: fine for a demo or coursework, not
for a product that is sold (decision D29). `data/` is git-ignored and the CSV
must never be committed.

## Training

```bash
uv run --no-project python train.py
```

The split goes by date, not at random: the earliest 60% of days to train, the
next 20% to choose between a logistic regression baseline and calibrated
gradient boosting, and the last 20% held back for the reported numbers. A random
split lets the model learn from appointments that come after the ones it is
tested on, which makes every number look better than it is.

It writes `models/no_show.joblib` and `models/metrics.json`, with ROC-AUC,
PR-AUC and Brier score for both models, plus how much likelier a `HIGH`
appointment is to be missed than an average one.

## Running it

```bash
uv run --no-project uvicorn app:app --port 8002
```

```bash
curl http://localhost:8002/health
```

`status` is `UP` with a model loaded, or `NO_MODEL` if `train.py` has not been
run. Then turn it on in the backend:

```bash
CLINIC_ML_ENABLED=true ./mvnw spring-boot:run
```

Without the flag the backend never calls this service, and the schedule is the
same as before. With the flag on but the service down or slow (over 2 s), the
schedule still loads, just without risk badges.

## Scoring by hand

```bash
curl -X POST http://localhost:8002/score -H "Content-Type: application/json" -d '{
  "appointments": [{
    "appointmentId": "a-1", "leadDays": 21, "appointmentWeekday": 0,
    "bookingHour": 14, "priorVisits": 3, "priorNoShows": 2, "reminderSent": true
  }]
}'
```

```json
{
  "modelVersion": "gradient_boosting-202610260000",
  "scores": [{ "appointmentId": "a-1", "probability": 0.47, "band": "HIGH" }]
}
```

`HIGH` is the riskiest 20% of appointments and `MEDIUM` the next 30%. These
are shares, not fixed probability cut-offs, because a cut-off learned on
Brazilian data would not mean the same thing in a clinic with a different
no-show rate.

## Condition hints

### The dataset

Kaggle's *Disease Prediction Using Machine Learning* (kaushil268, DbCL 1.0):
132 yes/no symptom columns and one of 41 diseases per row. With the Kaggle
token in place:

```bash
uv pip install kaggle
.venv/Scripts/kaggle datasets download -d kaushil268/disease-prediction-using-machine-learning -p data/symptoms --unzip
```

It is **synthetic**. Its 4,920 training rows are only 304 distinct symptom sets
repeated, and 41 of its 42 test rows also appear in training, which is why it
is famous for 100% accuracy. `conditions.py` removes the duplicates before
anything is split, cleans the column names (`spotting_ urination`) and fixes
misspelt diseases (`Peptic ulcer diseae`).

### Training

```bash
uv run --no-project python train_conditions.py
```

Every row lists a condition's complete symptom set, but a patient ticking boxes
reports some of their symptoms, not all. So training uses cases with symptoms
randomly hidden (keeping 40-100% of each), and the held-out cases are scored
three ways. Most recent run, logistic regression chosen over Naive Bayes:

| Held-out cases | Right condition first | Right condition in top 3 |
|---|---|---|
| All symptoms (as the dataset has them) | 98.9% | 100% |
| Half the symptoms | 94.2% | 99.0% |
| **Only two symptoms** | **71.6%** | **89.5%** |

The last row is the most honest one, and still an upper bound: real patients
are messier than generated data.

### Scoring by hand

```bash
curl -X POST http://localhost:8002/conditions -H "Content-Type: application/json" -d '{
  "cases": [{ "caseId": "a-1", "symptoms": ["high_fever", "joint_pain", "skin_rash"] }]
}'
```

Returns the top three conditions with probabilities. Fewer than two known
symptoms returns an empty list, and unknown codes are listed in
`unknownSymptoms` rather than refused. `GET /symptoms` lists the vocabulary
with readable labels.

### Why the patient never sees this

A ranked list of diseases on a patient's screen reads as a diagnosis, and
CliniQ refuses to give medical advice everywhere else. The booking page shows
the patient only what they ticked and a fixed emergency notice that does not
depend on the model. This is decision support for a doctor, not a medical
device (D30).

## Tests

```bash
uv run --no-project python -m pytest
```

The tests train on small synthetic frames in each Kaggle CSV's exact shape,
quirks included, so they need neither real dataset nor a network connection.

## Limits

- **Someone else's patients.** The model learns from Brazil in 2016. The order
  it puts patients in probably carries over; the exact probabilities are less
  likely to. Retrain on CliniQ's own outcomes once there are a few thousand
  (D29).
- **No abandoned holds.** Kaggle has no equivalent of CliniQ's payment hold, so
  that needs CliniQ's own data.
- **A hint, not a decision.** Only the doctor sees it, and nothing acts on it
  automatically.
