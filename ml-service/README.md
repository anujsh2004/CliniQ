# No-show risk service

Predicts how likely each appointment on a doctor's day is to be missed, so the
front desk knows whom to call ahead. The backend sends the day's appointments
and shows the result as a badge on the doctor's schedule (API contract 26).

| Piece | What it is |
|---|---|
| `features.py` | The seven features the model sees, and how they are built from the Kaggle data |
| `train.py` | Trains, evaluates and saves the model to `models/` |
| `app.py` | FastAPI service that loads the model and scores appointments |

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

## Tests

```bash
uv run --no-project python -m pytest
```

The tests train on a synthetic frame in the Kaggle CSV's exact shape, so they
need neither the real dataset nor a network connection.

## Limits

- **Someone else's patients.** The model learns from Brazil in 2016. The order
  it puts patients in probably carries over; the exact probabilities are less
  likely to. Retrain on CliniQ's own outcomes once there are a few thousand
  (D29).
- **No abandoned holds.** Kaggle has no equivalent of CliniQ's payment hold, so
  that needs CliniQ's own data.
- **A hint, not a decision.** Only the doctor sees it, and nothing acts on it
  automatically.
