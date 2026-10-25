from pathlib import Path

import pytest
from fastapi.testclient import TestClient

import app as service


def features(appointment_id: str, lead_days: int, **overrides) -> dict:
    body = {
        "appointmentId": appointment_id,
        "leadDays": lead_days,
        "appointmentWeekday": 2,
        "bookingHour": 10,
        "priorVisits": 0,
        "priorNoShows": 0,
        "reminderSent": False,
    }
    body.update(overrides)
    return body


@pytest.fixture
def client(trained_model, monkeypatch):
    monkeypatch.setattr(service, "MODEL_PATH", trained_model[0])
    with TestClient(service.app) as test_client:
        yield test_client


def test_training_writes_a_report_with_held_out_metrics(trained_model):
    model_path, report = trained_model

    assert report["chosen"] in report["models"]
    assert set(report["models"][report["chosen"]]["test"]) == {"rocAuc", "prAuc", "brier"}
    # The synthetic data has a real lead-time effect, so anything near 0.5
    # means the pipeline is broken rather than the data being hard.
    assert report["models"][report["chosen"]]["test"]["rocAuc"] > 0.6
    assert model_path.with_name("metrics.json").exists()


def test_health_reports_the_loaded_model(client: TestClient):
    body = client.get("/health").json()

    assert body["status"] == "UP"
    assert body["modelVersion"]


def test_long_lead_time_scores_riskier_than_same_day(client: TestClient):
    response = client.post(
        "/score", json={"appointments": [features("today", 0), features("later", 28)]}
    )

    assert response.status_code == 200
    scores = {s["appointmentId"]: s for s in response.json()["scores"]}
    assert scores["later"]["probability"] > scores["today"]["probability"]
    assert {s["band"] for s in scores.values()} <= {"LOW", "MEDIUM", "HIGH"}


def test_more_no_shows_than_visits_is_refused(client: TestClient):
    response = client.post(
        "/score", json={"appointments": [features("bad", 3, priorVisits=1, priorNoShows=2)]}
    )

    assert response.status_code == 422


def test_empty_batch_returns_no_scores(client: TestClient):
    response = client.post("/score", json={"appointments": []})

    assert response.json()["scores"] == []


def test_without_a_model_scoring_is_unavailable(tmp_path: Path, monkeypatch):
    monkeypatch.setattr(service, "MODEL_PATH", tmp_path / "missing.joblib")
    with TestClient(service.app) as test_client:
        assert test_client.get("/health").json()["status"] == "NO_MODEL"
        assert test_client.post("/score", json={"appointments": []}).status_code == 503
