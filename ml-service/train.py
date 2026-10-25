"""
Trains the no-show model on the Kaggle dataset and writes it to models/.

    uv run python train.py --data data/KaggleV2-May-2016.csv

The split is by appointment date, not random. A random split lets the model see
next week's appointments while learning, which flatters every metric and says
nothing about how it will do on bookings it has not seen. Training runs on the
earliest 60% of days, the model is chosen on the next 20%, and the last 20% is
touched once, for the numbers reported.
"""

from __future__ import annotations

import argparse
import json
import logging
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.calibration import CalibratedClassifierCV
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import average_precision_score, brier_score_loss, roc_auc_score
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

from features import FEATURES, LABEL, from_kaggle

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)-5s %(message)s")
log = logging.getLogger("train")

# The riskiest fifth of appointments are HIGH and the next 30% MEDIUM. Fixed
# shares rather than fixed probabilities, because a doctor acts on "these few
# are the ones to call", and a probability cut-off learned in Brazil would not
# keep that meaning in a clinic with a different base rate.
HIGH_SHARE = 0.20
MEDIUM_SHARE = 0.30

SEED = 42


def split_by_day(df: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    days = np.sort(df["appointment_day"].unique())
    train_end = days[int(len(days) * 0.6)]
    valid_end = days[int(len(days) * 0.8)]
    train = df[df["appointment_day"] < train_end]
    valid = df[(df["appointment_day"] >= train_end) & (df["appointment_day"] < valid_end)]
    test = df[df["appointment_day"] >= valid_end]
    return train, valid, test


def candidates() -> dict[str, object]:
    return {
        # The baseline. If boosting cannot beat this it is not worth its
        # opacity.
        "logistic": make_pipeline(StandardScaler(), LogisticRegression(max_iter=1000)),
        # Boosted trees, calibrated so that 0.3 means roughly three in ten
        # rather than just "more than 0.2". Isotonic calibration needs plenty of
        # data, which ~60k training rows provide.
        "gradient_boosting": CalibratedClassifierCV(
            HistGradientBoostingClassifier(
                max_iter=300, learning_rate=0.05, max_leaf_nodes=31, random_state=SEED
            ),
            method="isotonic",
            cv=3,
        ),
    }


def evaluate(y: pd.Series, p: np.ndarray) -> dict[str, float]:
    return {
        "rocAuc": round(float(roc_auc_score(y, p)), 4),
        "prAuc": round(float(average_precision_score(y, p)), 4),
        "brier": round(float(brier_score_loss(y, p)), 4),
    }


def thresholds(p: np.ndarray) -> dict[str, float]:
    return {
        "high": float(np.quantile(p, 1 - HIGH_SHARE)),
        "medium": float(np.quantile(p, 1 - HIGH_SHARE - MEDIUM_SHARE)),
    }


def high_band_lift(y: pd.Series, p: np.ndarray, cut: float) -> dict[str, float]:
    """How much likelier a HIGH appointment is to be missed than an average one."""
    flagged = p >= cut
    rate = float(y[flagged].mean()) if flagged.any() else 0.0
    base = float(y.mean())
    return {
        "highShare": round(float(flagged.mean()), 4),
        "highNoShowRate": round(rate, 4),
        "baseNoShowRate": round(base, 4),
        "lift": round(rate / base, 2) if base > 0 else 0.0,
    }


def train(data: Path, out: Path) -> dict:
    raw = pd.read_csv(data)
    df = from_kaggle(raw)
    train_df, valid_df, test_df = split_by_day(df)
    log.info("rows: train %d, valid %d, test %d", len(train_df), len(valid_df), len(test_df))

    results: dict[str, dict] = {}
    fitted: dict[str, object] = {}
    for name, model in candidates().items():
        model.fit(train_df[FEATURES], train_df[LABEL])
        p_valid = model.predict_proba(valid_df[FEATURES])[:, 1]
        results[name] = {"valid": evaluate(valid_df[LABEL], p_valid)}
        fitted[name] = model
        log.info("%s on validation: %s", name, results[name]["valid"])

    # Chosen on validation PR-AUC: no-shows are the minority class, and what
    # matters is how clean the top of the list is, which ROC-AUC blurs.
    chosen = max(results, key=lambda n: results[n]["valid"]["prAuc"])
    model = fitted[chosen]

    cuts = thresholds(model.predict_proba(valid_df[FEATURES])[:, 1])
    p_test = model.predict_proba(test_df[FEATURES])[:, 1]
    for name, candidate in fitted.items():
        results[name]["test"] = evaluate(test_df[LABEL], candidate.predict_proba(test_df[FEATURES])[:, 1])

    version = f"{chosen}-{datetime.now(timezone.utc):%Y%m%d%H%M}"
    report = {
        "version": version,
        "chosen": chosen,
        "trainedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "dataset": data.name,
        "rows": {"train": len(train_df), "valid": len(valid_df), "test": len(test_df)},
        "features": FEATURES,
        "thresholds": cuts,
        "models": results,
        "testHighBand": high_band_lift(test_df[LABEL], p_test, cuts["high"]),
    }

    out.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(
        {"model": model, "features": FEATURES, "thresholds": cuts, "version": version},
        out,
    )
    out.with_name("metrics.json").write_text(json.dumps(report, indent=2))
    log.info("chose %s; test %s", chosen, results[chosen]["test"])
    log.info("HIGH band on test: %s", report["testHighBand"])
    log.info("wrote %s", out)
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--data", type=Path, default=Path("data/KaggleV2-May-2016.csv"))
    parser.add_argument("--out", type=Path, default=Path("models/no_show.joblib"))
    args = parser.parse_args()
    train(args.data, args.out)


if __name__ == "__main__":
    main()
