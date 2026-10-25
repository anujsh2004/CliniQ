"""
The features the no-show model sees, and how they are built from the Kaggle data.

The model is trained on the Kaggle "Medical Appointment No Shows" dataset
(Brazil, 2016) but scored on CliniQ appointments. Only features that both can
supply are used. The dataset also has age, gender, neighbourhood and chronic
conditions, and they do predict no-shows, but CliniQ does not collect any of
them. Training on a column the backend cannot send means the model is scored on
a value it never saw in training - a default or a zero - and the score quietly
stops meaning anything. A weaker model that is honest at serving time is the
better trade.
"""

from __future__ import annotations

from typing import Final

import pandas as pd

# Order matters: the model is fitted on columns in this order and the scoring
# endpoint builds rows in the same order.
FEATURES: Final = [
    # Whole days between booking and the appointment. The strongest single
    # signal in the dataset: an appointment booked weeks ahead is forgotten far
    # more often than one booked today.
    "lead_days",
    # Monday is 0, Sunday 6 - pandas' day_of_week and Java's getValue() - 1.
    "appointment_weekday",
    # Hour of day the booking was made, in the clinic's own time.
    "booking_hour",
    # Earlier appointments for this patient that actually happened or were
    # missed. Cancellations are not visits, and the Kaggle data has none.
    "prior_visits",
    "prior_no_shows",
    "prior_no_show_rate",
    # Whether a reminder was sent or is scheduled. Kaggle's SMS_received.
    "reminder_sent",
]

LABEL: Final = "no_show"


def prior_rate(prior_no_shows: float, prior_visits: float) -> float:
    """Share of earlier visits that were missed; 0 for a first visit."""
    return prior_no_shows / prior_visits if prior_visits > 0 else 0.0


def from_kaggle(raw: pd.DataFrame) -> pd.DataFrame:
    """
    Turns the Kaggle CSV into one row per appointment with FEATURES and LABEL.

    Rows with a negative lead time are dropped: the appointment would be before
    the booking, which is a data entry error in the source, not a pattern.
    """
    df = pd.DataFrame(
        {
            "patient_id": raw["PatientId"].astype("int64"),
            "appointment_id": raw["AppointmentID"],
            "scheduled_at": pd.to_datetime(raw["ScheduledDay"], utc=True),
            "appointment_day": pd.to_datetime(raw["AppointmentDay"], utc=True).dt.normalize(),
            "reminder_sent": raw["SMS_received"].astype(int),
            LABEL: (raw["No-show"].str.strip().str.lower() == "yes").astype(int),
        }
    )

    df["lead_days"] = (df["appointment_day"] - df["scheduled_at"].dt.normalize()).dt.days
    df = df[df["lead_days"] >= 0].copy()

    df["appointment_weekday"] = df["appointment_day"].dt.day_of_week
    df["booking_hour"] = df["scheduled_at"].dt.hour

    df = _add_history(df)
    return df.reset_index(drop=True)


def _add_history(df: pd.DataFrame) -> pd.DataFrame:
    """
    Counts each patient's visits and no-shows strictly before the appointment day.

    Strictly before, not merely earlier in the file: a patient with two
    appointments on the same day must not have one's outcome leak into the
    other's features, because on the day itself neither outcome is known yet.
    """
    df = df.sort_values(["patient_id", "appointment_day", "scheduled_at"])
    grouped = df.groupby("patient_id", sort=False)

    # Counts over all earlier rows, then pinned to the first row of each
    # (patient, day) so same-day siblings share the value from before the day.
    visits_before = grouped.cumcount()
    no_shows_before = grouped[LABEL].cumsum() - df[LABEL]

    day_key = [df["patient_id"], df["appointment_day"]]
    df["prior_visits"] = visits_before.groupby(day_key).transform("first")
    df["prior_no_shows"] = no_shows_before.groupby(day_key).transform("first")
    df["prior_no_show_rate"] = [
        prior_rate(n, v) for n, v in zip(df["prior_no_shows"], df["prior_visits"])
    ]
    return df
