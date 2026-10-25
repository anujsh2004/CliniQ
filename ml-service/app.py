"""
No-show risk scoring for CliniQ.

The backend sends the appointments on a doctor's day and gets back how likely
each one is to be missed. The model is trained by train.py and loaded once at
startup; this service never trains.

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

from features import FEATURES, prior_rate

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)-5s %(message)s")
log = logging.getLogger("ml")

MODEL_PATH: Final = Path(os.environ.get("ML_MODEL_PATH", "models/no_show.joblib"))

# A doctor's day is tens of appointments. A request far beyond that is a bug
# upstream, not a busy clinic.
MAX_BATCH: Final = 500

Band = Literal["LOW", "MEDIUM", "HIGH"]

state: dict = {}


@asynccontextmanager
async def lifespan(_: FastAPI):
    # A missing model is reported, not fatal. The service still starts so its
    # health endpoint can say what is wrong, which beats a crash loop.
    if MODEL_PATH.exists():
        state.update(joblib.load(MODEL_PATH))
        log.info("loaded model %s from %s", state["version"], MODEL_PATH)
    else:
        log.warning("no model at %s - run train.py first", MODEL_PATH)
    yield
    state.clear()


app = FastAPI(title="CliniQ no-show risk", lifespan=lifespan)


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


@app.get("/health")
def health() -> dict:
    if not state:
        return {"status": "NO_MODEL", "modelPath": str(MODEL_PATH)}
    return {"status": "UP", "modelVersion": state["version"]}


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
