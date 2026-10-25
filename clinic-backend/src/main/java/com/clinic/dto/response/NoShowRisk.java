package com.clinic.dto.response;

import com.clinic.ml.RiskBand;

/**
 * How likely a patient is to miss an appointment (API contract 26, v1.6).
 *
 * <p>Advice for the doctor, shown on their own schedule only. It is never shown
 * to the patient, and nothing in the product acts on it automatically.
 */
public record NoShowRisk(double probability, RiskBand band, String modelVersion) {
}
