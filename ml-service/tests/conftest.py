from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
import pandas as pd
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))


def kaggle_like(rows: int = 4000, seed: int = 0) -> pd.DataFrame:
    """
    A synthetic frame in the Kaggle CSV's exact shape.

    No-shows grow with lead time, so a model trained on it has something real
    to find. The real dataset is not committed (it is CC BY-NC-SA), and tests
    must not depend on a file someone downloads by hand.
    """
    rng = np.random.default_rng(seed)
    start = pd.Timestamp("2016-04-29", tz="UTC")
    appointment_day = start + pd.to_timedelta(rng.integers(0, 40, rows), unit="D")
    lead = rng.integers(0, 30, rows)
    scheduled = (
        appointment_day
        - pd.to_timedelta(lead, unit="D")
        + pd.to_timedelta(rng.integers(7, 19, rows), unit="h")
    )
    no_show = rng.random(rows) < 0.08 + 0.012 * lead
    return pd.DataFrame(
        {
            "PatientId": rng.integers(1, rows // 3, rows).astype(float),
            "AppointmentID": np.arange(rows) + 5_000_000,
            "Gender": rng.choice(["F", "M"], rows),
            "ScheduledDay": scheduled.strftime("%Y-%m-%dT%H:%M:%SZ"),
            "AppointmentDay": appointment_day.strftime("%Y-%m-%dT00:00:00Z"),
            "Age": rng.integers(0, 90, rows),
            "Neighbourhood": "JARDIM DA PENHA",
            "Scholarship": 0,
            "Hipertension": 0,
            "Diabetes": 0,
            "Alcoholism": 0,
            "Handcap": 0,
            "SMS_received": (lead >= 3).astype(int),
            "No-show": np.where(no_show, "Yes", "No"),
        }
    )


@pytest.fixture(scope="session")
def kaggle_csv(tmp_path_factory: pytest.TempPathFactory) -> Path:
    path = tmp_path_factory.mktemp("data") / "kaggle.csv"
    kaggle_like().to_csv(path, index=False)
    return path


@pytest.fixture(scope="session")
def trained_model(kaggle_csv: Path, tmp_path_factory: pytest.TempPathFactory) -> tuple[Path, dict]:
    """Trained once for the whole run: training is the slow part of every test."""
    import train

    model_path = tmp_path_factory.mktemp("models") / "no_show.joblib"
    return model_path, train.train(kaggle_csv, model_path)
