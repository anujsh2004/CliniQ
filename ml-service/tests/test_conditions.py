from pathlib import Path

import numpy as np
import pandas as pd
import pytest
from fastapi.testclient import TestClient

import app as service
from conditions import MIN_SYMPTOMS, clean_symptom, hide_symptoms, load, symptom_label


def test_symptom_codes_are_cleaned():
    assert clean_symptom("spotting_ urination") == "spotting_urination"
    assert clean_symptom("dischromic _patches") == "dischromic_patches"
    assert clean_symptom(" cold_hands_and_feets") == "cold_hands_and_feets"
    assert clean_symptom("fluid_overload.1") == "fluid_overload"


def test_labels_read_like_words():
    assert symptom_label("skin_rash") == "Skin rash"


def test_loading_drops_duplicates_merges_columns_and_fixes_names(symptom_csv: Path):
    X, y = load(symptom_csv)

    # Every row in the fixture appears twice; each case must appear once.
    assert not pd.concat([X, y], axis=1).duplicated().any()
    assert "fluid_overload.1" not in X.columns
    assert "spotting_urination" in X.columns
    assert "Peptic ulcer disease" in set(y)
    assert "Peptic ulcer diseae" not in set(y)


def test_hiding_never_leaves_fewer_than_the_minimum(symptom_csv: Path):
    X, y = load(symptom_csv)

    partial, labels = hide_symptoms(X, y, copies=5, keep=(0.0, 0.0), rng=np.random.default_rng(1))

    assert (partial.sum(axis=1) == MIN_SYMPTOMS).all()
    assert len(labels) == len(X) * 5


def test_training_reports_every_test_setting(trained_conditions):
    model_path, report = trained_conditions

    assert set(report["models"][report["chosen"]]) >= {"testFull", "testPartial", "testTwoSymptoms"}
    assert model_path.with_name("conditions_metrics.json").exists()


@pytest.fixture
def client(trained_conditions, tmp_path: Path, monkeypatch):
    monkeypatch.setattr(service, "MODEL_PATH", tmp_path / "no-show-not-needed.joblib")
    monkeypatch.setattr(service, "CONDITIONS_PATH", trained_conditions[0])
    with TestClient(service.app) as test_client:
        yield test_client


def test_symptoms_are_the_models_vocabulary_with_labels(client: TestClient):
    body = client.get("/symptoms").json()

    codes = {s["code"]: s["label"] for s in body["symptoms"]}
    assert codes["skin_rash"] == "Skin rash"
    assert "spotting_urination" in codes


def test_ranks_the_matching_condition_first(client: TestClient):
    response = client.post(
        "/conditions",
        json={"cases": [{"caseId": "c-1", "symptoms": ["burning_micturition", "spotting_urination"]}]},
    )

    result = response.json()["results"][0]
    assert result["conditions"][0]["name"] == "Urinary tract infection"
    assert len(result["conditions"]) <= service.TOP_CONDITIONS
    probabilities = [c["probability"] for c in result["conditions"]]
    assert probabilities == sorted(probabilities, reverse=True)


def test_one_symptom_is_too_few_to_rank(client: TestClient):
    response = client.post("/conditions", json={"cases": [{"caseId": "c-1", "symptoms": ["itching"]}]})

    assert response.json()["results"][0]["conditions"] == []


def test_unknown_symptoms_are_reported_not_refused(client: TestClient):
    response = client.post(
        "/conditions",
        json={"cases": [{"caseId": "c-1", "symptoms": ["chest_pain", "breathlessness", "made_up"]}]},
    )

    result = response.json()["results"][0]
    assert response.status_code == 200
    assert result["unknownSymptoms"] == ["made_up"]
    assert result["conditions"]
