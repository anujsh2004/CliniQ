"""
No-show risk and condition hints for CliniQ.

The backend sends the appointments on a doctor's day and gets back how likely
each one is to be missed, and, for patients who reported symptoms when
booking, which conditions those symptoms most resemble. Both models are
trained offline (train.py, train_conditions.py) and loaded once at startup;
this service never trains. Either can be missing without affecting the other.

Run it with:

    uv run uvicorn app:app --port 8002
"""

from __future__ import annotations

import logging
import os
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Final, Literal

import joblib
import pandas as pd
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from conditions import MIN_SYMPTOMS, symptom_label
from features import FEATURES, prior_rate

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)-5s %(message)s")
log = logging.getLogger("ml")

MODEL_PATH: Final = Path(os.environ.get("ML_MODEL_PATH", "models/no_show.joblib"))
CONDITIONS_PATH: Final = Path(os.environ.get("ML_CONDITIONS_PATH", "models/conditions.joblib"))

# Three, not one: a single answer reads as a diagnosis, and the point is to
# give the doctor a short list to rule in or out.
TOP_CONDITIONS: Final = 3

# A doctor's day is tens of appointments. A request far beyond that is a bug
# upstream, not a busy clinic.
MAX_BATCH: Final = 500

Band = Literal["LOW", "MEDIUM", "HIGH"]

state: dict = {}
conditions_state: dict = {}


@asynccontextmanager
async def lifespan(_: FastAPI):
    # A missing model is reported, not fatal. The service still starts so its
    # health endpoint can say what is wrong, which beats a crash loop.
    if MODEL_PATH.exists():
        state.update(joblib.load(MODEL_PATH))
        log.info("loaded model %s from %s", state["version"], MODEL_PATH)
    else:
        log.warning("no model at %s - run train.py first", MODEL_PATH)
    if CONDITIONS_PATH.exists():
        conditions_state.update(joblib.load(CONDITIONS_PATH))
        log.info("loaded model %s from %s", conditions_state["version"], CONDITIONS_PATH)
    else:
        log.warning("no model at %s - run train_conditions.py first", CONDITIONS_PATH)
    yield
    state.clear()
    conditions_state.clear()


app = FastAPI(title="CliniQ ML", lifespan=lifespan)


class AppointmentFeatures(BaseModel):
    appointmentId: str
    leadDays: int = Field(ge=0)
    appointmentWeekday: int = Field(ge=0, le=6)
    bookingHour: int = Field(ge=0, le=23)
    priorVisits: int = Field(ge=0)
    priorNoShows: int = Field(ge=0)
    reminderSent: bool


class ScoreRequest(BaseModel):
    appointments: list[AppointmentFeatures] = Field(max_length=MAX_BATCH)


class Score(BaseModel):
    appointmentId: str
    probability: float
    band: Band


class ScoreResponse(BaseModel):
    modelVersion: str
    scores: list[Score]


def _model_health(loaded: dict, path: Path) -> dict:
    if not loaded:
        return {"status": "NO_MODEL", "modelPath": str(path)}
    return {"status": "UP", "modelVersion": loaded["version"]}


@app.get("/health")
def health() -> dict:
    # The top level is the no-show model, as before; conditions report beside it.
    return {**_model_health(state, MODEL_PATH), "conditions": _model_health(conditions_state, CONDITIONS_PATH)}


def band(probability: float, thresholds: dict[str, float]) -> Band:
    if probability >= thresholds["high"]:
        return "HIGH"
    if probability >= thresholds["medium"]:
        return "MEDIUM"
    return "LOW"


@app.post("/score")
def score(request: ScoreRequest) -> ScoreResponse:
    if not state:
        raise HTTPException(503, f"No model is loaded. Run train.py to create {MODEL_PATH}.")
    if not request.appointments:
        return ScoreResponse(modelVersion=state["version"], scores=[])

    for item in request.appointments:
        if item.priorNoShows > item.priorVisits:
            raise HTTPException(422, f"{item.appointmentId}: priorNoShows exceeds priorVisits")

    rows = pd.DataFrame(
        [
            {
                "lead_days": a.leadDays,
                "appointment_weekday": a.appointmentWeekday,
                "booking_hour": a.bookingHour,
                "prior_visits": a.priorVisits,
                "prior_no_shows": a.priorNoShows,
                "prior_no_show_rate": prior_rate(a.priorNoShows, a.priorVisits),
                "reminder_sent": int(a.reminderSent),
            }
            for a in request.appointments
        ],
        columns=FEATURES,
    )
    probabilities = state["model"].predict_proba(rows)[:, 1]

    return ScoreResponse(
        modelVersion=state["version"],
        scores=[
            Score(
                appointmentId=a.appointmentId,
                probability=round(float(p), 4),
                band=band(float(p), state["thresholds"]),
            )
            for a, p in zip(request.appointments, probabilities)
        ],
    )


class Symptom(BaseModel):
    code: str
    label: str


class SymptomsResponse(BaseModel):
    modelVersion: str
    symptoms: list[Symptom]


class Case(BaseModel):
    caseId: str
    symptoms: list[str] = Field(max_length=40)


class ConditionsRequest(BaseModel):
    cases: list[Case] = Field(max_length=MAX_BATCH)


class Condition(BaseModel):
    name: str
    probability: float


class CaseResult(BaseModel):
    caseId: str
    # Codes the model was not trained on. Reported rather than refused, so a
    # renamed symptom costs one hint, not the whole schedule.
    unknownSymptoms: list[str]
    conditions: list[Condition]


class ConditionsResponse(BaseModel):
    modelVersion: str
    results: list[CaseResult]


def _require_conditions() -> None:
    if not conditions_state:
        raise HTTPException(503, f"No condition model is loaded. Run train_conditions.py to create {CONDITIONS_PATH}.")


@app.get("/symptoms")
def symptoms() -> SymptomsResponse:
    """The symptoms a patient may choose from: exactly the model's vocabulary."""
    _require_conditions()
    return SymptomsResponse(
        modelVersion=conditions_state["version"],
        symptoms=[Symptom(code=code, label=symptom_label(code)) for code in conditions_state["symptoms"]],
    )


@app.post("/conditions")
def conditions(request: ConditionsRequest) -> ConditionsResponse:
    _require_conditions()
    vocabulary: list[str] = conditions_state["symptoms"]
    index = {code: i for i, code in enumerate(vocabulary)}
    model = conditions_state["model"]

    results = []
    for case in request.cases:
        known = sorted({code for code in case.symptoms if code in index})
        unknown = sorted({code for code in case.symptoms if code not in index})
        if len(known) < MIN_SYMPTOMS:
            # Too little to rank on. An empty list is the honest answer.
            results.append(CaseResult(caseId=case.caseId, unknownSymptoms=unknown, conditions=[]))
            continue

        row = pd.DataFrame([[0] * len(vocabulary)], columns=vocabulary)
        row.loc[0, known] = 1
        probabilities = model.predict_proba(row)[0]
        top = probabilities.argsort()[::-1][:TOP_CONDITIONS]
        results.append(
            CaseResult(
                caseId=case.caseId,
                unknownSymptoms=unknown,
                conditions=[
                    Condition(name=str(model.classes_[i]), probability=round(float(probabilities[i]), 4))
                    for i in top
                ],
            )
        )
    return ConditionsResponse(modelVersion=conditions_state["version"], results=results)
