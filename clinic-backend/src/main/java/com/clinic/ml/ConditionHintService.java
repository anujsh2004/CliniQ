package com.clinic.ml;

import com.clinic.dto.response.ConditionHints;
import com.clinic.entity.Appointment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Condition hints on a doctor's schedule (API contract 27, v1.6).
 *
 * <p>As with no-show risk, every failure ends with no hints rather than a
 * failed schedule: the hints are a prompt for the doctor, and the schedule is
 * what they came for.
 */
@Service
public class ConditionHintService {

    private static final Logger log = LoggerFactory.getLogger(ConditionHintService.class);

    /** The service's own floor; asking with fewer is a wasted call. */
    static final int MIN_SYMPTOMS = 2;

    private final ObjectProvider<ConditionClient> client;

    public ConditionHintService(ObjectProvider<ConditionClient> client) {
        this.client = client;
    }

    /**
     * @return hints for each live appointment whose patient reported at least
     *         two symptoms; empty when the ML service is off or failing
     */
    public Map<UUID, ConditionHints> hints(List<Appointment> appointments) {
        ConditionClient conditionClient = client.getIfAvailable();
        if (conditionClient == null) {
            return Map.of();
        }
        // A visit that already happened, or never will, needs no preparation.
        List<ConditionClient.Case> cases = appointments.stream()
                .filter(appointment -> appointment.getStatus().isActive())
                .filter(appointment -> appointment.getSymptoms().size() >= MIN_SYMPTOMS)
                .map(appointment -> new ConditionClient.Case(
                        appointment.getId().toString(), List.copyOf(appointment.getSymptoms())))
                .toList();
        if (cases.isEmpty()) {
            return Map.of();
        }

        try {
            ConditionClient.ConditionsResponse response = conditionClient.conditions(cases);
            Map<UUID, ConditionHints> hints = new HashMap<>();
            for (ConditionClient.CaseResult result : response.results()) {
                if (result.conditions() == null || result.conditions().isEmpty()) {
                    continue;
                }
                hints.put(UUID.fromString(result.caseId()), new ConditionHints(response.modelVersion(),
                        result.conditions().stream()
                                .map(c -> new ConditionHints.Condition(c.name(), c.probability()))
                                .toList()));
            }
            return hints;
        } catch (RuntimeException exception) {
            log.warn("Condition hints unavailable, serving the schedule without them: {}",
                    exception.getMessage());
            return Map.of();
        }
    }
}
