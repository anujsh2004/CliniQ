"""
Trains the condition-hint model and writes it to models/.

    uv run --no-project python train_conditions.py

Duplicates are dropped first (see conditions.py), then the distinct cases are
split by disease so every disease is in both halves: 70% to train, 30% held back.
The candidates are compared on a slice of the training cases, the winner is
refitted on all of them, and the held-back cases are scored once, two ways:
with every symptom present, as the dataset has them; with half hidden, as a
patient reports them; and with only two, the least the service accepts. The
first number says little about use; the last says the most.
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
from sklearn.base import clone
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split
from sklearn.naive_bayes import BernoulliNB

from conditions import hide_symptoms, load

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)-5s %(message)s")
log = logging.getLogger("train_conditions")

SEED = 42
TOP_K = 3

# Training sees each case with between 40% and all of its symptoms; testing
# keeps half, the harder and more realistic setting.
TRAIN_COPIES = 30
TRAIN_KEEP = (0.4, 1.0)
TEST_COPIES = 20
TEST_KEEP = (0.5, 0.5)
# The floor: exactly MIN_SYMPTOMS symptoms, as from a patient who ticks two
# boxes and moves on.
FLOOR_KEEP = (0.0, 0.0)


def candidates() -> dict[str, object]:
    return {
        # The textbook model for yes/no symptoms, and the baseline.
        "naive_bayes": BernoulliNB(alpha=0.5),
        "logistic": LogisticRegression(C=2.0, max_iter=3000),
    }


def top_k_accuracy(model, X: pd.DataFrame, y: pd.Series, k: int) -> float:
    probabilities = model.predict_proba(X)
    top = np.argsort(probabilities, axis=1)[:, ::-1][:, :k]
    hits = [label in model.classes_[row] for label, row in zip(y, top)]
    return round(float(np.mean(hits)), 4)


def evaluate(model, X: pd.DataFrame, y: pd.Series) -> dict[str, float]:
    return {"top1": top_k_accuracy(model, X, y, 1), "top3": top_k_accuracy(model, X, y, TOP_K)}


def train(paths: list[Path], out: Path) -> dict:
    rng = np.random.default_rng(SEED)
    X, y = load(*paths)
    log.info("%d distinct cases, %d symptoms, %d conditions", len(X), X.shape[1], y.nunique())

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.3, stratify=y, random_state=SEED
    )
    X_fit, X_valid, y_fit, y_valid = train_test_split(
        X_train, y_train, test_size=0.25, stratify=y_train, random_state=SEED
    )

    def augmented(Xs, ys):
        Xp, yp = hide_symptoms(Xs, ys, TRAIN_COPIES, TRAIN_KEEP, rng)
        return pd.concat([Xs, Xp], ignore_index=True), pd.concat([ys, yp], ignore_index=True)

    X_valid_partial, y_valid_partial = hide_symptoms(X_valid, y_valid, TEST_COPIES, TEST_KEEP, rng)
    X_fit_aug, y_fit_aug = augmented(X_fit, y_fit)

    results: dict[str, dict] = {}
    for name, model in candidates().items():
        model.fit(X_fit_aug, y_fit_aug)
        results[name] = {"validPartial": evaluate(model, X_valid_partial, y_valid_partial)}
        log.info("%s on validation, half the symptoms: %s", name, results[name]["validPartial"])

    chosen = max(results, key=lambda n: (results[n]["validPartial"]["top3"], results[n]["validPartial"]["top1"]))

    X_train_aug, y_train_aug = augmented(X_train, y_train)
    X_test_partial, y_test_partial = hide_symptoms(X_test, y_test, TEST_COPIES, TEST_KEEP, rng)
    X_test_floor, y_test_floor = hide_symptoms(X_test, y_test, TEST_COPIES, FLOOR_KEEP, rng)
    for name, template in candidates().items():
        model = clone(template).fit(X_train_aug, y_train_aug)
        results[name]["testFull"] = evaluate(model, X_test, y_test)
        results[name]["testPartial"] = evaluate(model, X_test_partial, y_test_partial)
        results[name]["testTwoSymptoms"] = evaluate(model, X_test_floor, y_test_floor)
        if name == chosen:
            final = model

    version = f"conditions-{chosen}-{datetime.now(timezone.utc):%Y%m%d%H%M}"
    report = {
        "version": version,
        "chosen": chosen,
        "trainedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "datasets": [path.name for path in paths],
        "distinctCases": len(X),
        "rows": {"train": len(X_train), "test": len(X_test)},
        "symptoms": X.shape[1],
        "conditions": int(y.nunique()),
        "models": results,
    }

    out.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump({"model": final, "symptoms": list(X.columns), "version": version}, out)
    out.with_name("conditions_metrics.json").write_text(json.dumps(report, indent=2))
    log.info("chose %s; test %s", chosen, {k: v for k, v in results[chosen].items() if k.startswith("test")})
    log.info("wrote %s", out)
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument(
        "--data",
        type=Path,
        nargs="+",
        default=[Path("data/symptoms/Training.csv"), Path("data/symptoms/Testing.csv")],
    )
    parser.add_argument("--out", type=Path, default=Path("models/conditions.joblib"))
    args = parser.parse_args()
    train(args.data, args.out)


if __name__ == "__main__":
    main()
