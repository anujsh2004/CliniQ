import pandas as pd

from features import FEATURES, LABEL, from_kaggle


def frame(rows: list[tuple[int, str, str, str]]) -> pd.DataFrame:
    """(patient, scheduled, appointment day, no-show) per row."""
    return pd.DataFrame(
        {
            "PatientId": [float(p) for p, *_ in rows],
            "AppointmentID": range(len(rows)),
            "ScheduledDay": [s for _, s, _, _ in rows],
            "AppointmentDay": [d for _, _, d, _ in rows],
            "SMS_received": 0,
            "No-show": [n for *_, n in rows],
        }
    )


def test_history_counts_only_earlier_days():
    df = from_kaggle(
        frame(
            [
                (1, "2016-05-01T09:00:00Z", "2016-05-02T00:00:00Z", "Yes"),
                (1, "2016-05-01T10:00:00Z", "2016-05-05T00:00:00Z", "No"),
                (1, "2016-05-01T11:00:00Z", "2016-05-09T00:00:00Z", "No"),
            ]
        )
    ).sort_values("appointment_day")

    assert df["prior_visits"].tolist() == [0, 1, 2]
    assert df["prior_no_shows"].tolist() == [0, 1, 1]
    assert df["prior_no_show_rate"].tolist() == [0.0, 1.0, 0.5]


def test_same_day_appointments_do_not_see_each_others_outcome():
    # Two visits on one day: neither outcome is known when the day starts.
    df = from_kaggle(
        frame(
            [
                (7, "2016-05-01T09:00:00Z", "2016-05-03T00:00:00Z", "Yes"),
                (7, "2016-05-01T09:05:00Z", "2016-05-03T00:00:00Z", "No"),
            ]
        )
    )

    assert df["prior_visits"].tolist() == [0, 0]
    assert df["prior_no_shows"].tolist() == [0, 0]


def test_lead_time_weekday_and_hour():
    df = from_kaggle(frame([(1, "2016-05-02T18:38:08Z", "2016-05-06T00:00:00Z", "No")]))

    row = df.iloc[0]
    assert row["lead_days"] == 4
    assert row["appointment_weekday"] == 4  # Friday
    assert row["booking_hour"] == 18
    assert row[LABEL] == 0


def test_appointment_before_booking_is_dropped():
    df = from_kaggle(frame([(1, "2016-05-06T09:00:00Z", "2016-05-02T00:00:00Z", "No")]))

    assert df.empty


def test_every_model_feature_is_produced():
    df = from_kaggle(frame([(1, "2016-05-02T09:00:00Z", "2016-05-02T00:00:00Z", "No")]))

    assert set(FEATURES) <= set(df.columns)
