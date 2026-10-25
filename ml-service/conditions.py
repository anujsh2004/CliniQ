"""
Symptoms in, likely conditions out: the data side of the condition hints.

Trained on the Kaggle "Disease Prediction Using Machine Learning" dataset
(kaushil268), which is 132 yes/no symptom columns and one of 41 diseases per
row. Two things about it shape everything here:

* It is synthetic and heavily duplicated. Its 4,920 training rows are only 304
  distinct symptom sets, and 41 of its 42 test rows also appear in training,
  which is why it is famous for 100% accuracy. Duplicates are dropped before
  anything is split, so no case is ever tested on its own copy.
* Every row lists a disease's complete symptom set. A patient ticking boxes on
  a booking form reports some of their symptoms, not all of them, so the model
  is trained and tested on cases with symptoms randomly hidden.

The result is decision support for the doctor, never shown to the patient
(API contract 27).
"""

from __future__ import annotations

import re
from pathlib import Path
from typing import Final

import numpy as np
import pandas as pd

LABEL: Final = "prognosis"

# Fewer symptoms than this and any ranking is mostly the prior: the model would
# answer "fungal infection" to "itching" and sound sure about it.
MIN_SYMPTOMS: Final = 2

# The source spells several diseases wrongly or inconsistently. Fixed once,
# here, so a doctor never reads "Peptic ulcer diseae" on their schedule.
DISEASE_NAMES: Final = {
    "(vertigo) Paroymsal  Positional Vertigo": "Benign paroxysmal positional vertigo",
    "Dimorphic hemmorhoids(piles)": "Haemorrhoids (piles)",
    "Osteoarthristis": "Osteoarthritis",
    "Peptic ulcer diseae": "Peptic ulcer disease",
    "Paralysis (brain hemorrhage)": "Paralysis (brain haemorrhage)",
    "hepatitis A": "Hepatitis A",
    "Diabetes ": "Diabetes",
    "Hypertension ": "Hypertension",
}


def clean_symptom(column: str) -> str:
    """'spotting_ urination' and 'dischromic _patches' become snake_case codes."""
    code = re.sub(r"\.\d+$", "", column.strip().lower())  # pandas' ".1" duplicate suffix
    code = re.sub(r"[\s_]+", "_", code)
    return code.strip("_")


def symptom_label(code: str) -> str:
    """The code as a person reads it: 'skin_rash' is 'Skin rash'."""
    words = code.replace("_", " ")
    return words[0].upper() + words[1:]


def disease_name(raw: str) -> str:
    return DISEASE_NAMES.get(raw, raw.strip())


def load(*paths: Path) -> tuple[pd.DataFrame, pd.Series]:
    """
    Reads the Kaggle CSVs into a 0/1 symptom matrix and disease labels, with
    exact duplicates removed.
    """
    raw = pd.concat([pd.read_csv(path) for path in paths], ignore_index=True)
    raw = raw.loc[:, ~raw.columns.str.startswith("Unnamed")]

    symptoms = raw.drop(columns=[LABEL])
    # Two source columns can clean to the same code; a symptom is present if
    # either says so.
    symptoms = symptoms.T.groupby(clean_symptom).max().T.astype(int)
    labels = raw[LABEL].map(disease_name)

    combined = symptoms.assign(**{LABEL: labels}).drop_duplicates().reset_index(drop=True)
    return combined.drop(columns=[LABEL]), combined[LABEL]


def hide_symptoms(
    X: pd.DataFrame,
    y: pd.Series,
    copies: int,
    keep: tuple[float, float],
    rng: np.random.Generator,
) -> tuple[pd.DataFrame, pd.Series]:
    """
    Makes `copies` partial versions of each case, keeping each present symptom
    with a probability drawn from `keep`, and never fewer than MIN_SYMPTOMS.

    This is what a booking form actually receives: a patient with dengue
    mentions the fever and the joint pain and forgets the rest.
    """
    values = X.to_numpy()
    rows, labels = [], []
    for row, label in zip(values, y):
        present = np.flatnonzero(row)
        for _ in range(copies):
            kept = present[rng.random(len(present)) < rng.uniform(*keep)]
            if len(kept) < MIN_SYMPTOMS:
                kept = rng.choice(present, size=min(MIN_SYMPTOMS, len(present)), replace=False)
            partial = np.zeros_like(row)
            partial[kept] = 1
            rows.append(partial)
            labels.append(label)
    return pd.DataFrame(rows, columns=X.columns), pd.Series(labels, name=y.name)
