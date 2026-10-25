package com.clinic.dto.response;

import java.util.List;

/**
 * Conditions the patient's reported symptoms most resemble (API contract 27,
 * v1.6), most likely first, at most three.
 *
 * <p>Decision support for the treating doctor before the visit. Never a
 * diagnosis, never shown to the patient.
 */
public record ConditionHints(String modelVersion, List<Condition> conditions) {

    public record Condition(String name, double probability) {
    }
}
