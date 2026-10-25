package com.clinic.ml;

/**
 * How a no-show probability is presented to a doctor.
 *
 * <p>Bands are shares of appointments, not fixed probabilities: HIGH is the
 * riskiest fifth and MEDIUM the next 30%, as set when the model is trained.
 */
public enum RiskBand {
    LOW,
    MEDIUM,
    HIGH
}
