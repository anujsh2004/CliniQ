package com.clinic.controller;

import com.clinic.dto.response.ApiResponse;
import com.clinic.ml.ConditionClient;
import com.clinic.ml.MlUnavailableException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The symptoms a patient may report when booking (API contract 27, v1.6).
 *
 * <p>Present only when the ML service is enabled. Without it there is no
 * vocabulary to offer, and the booking page hides its symptom picker on the
 * 404, which is the honest outcome.
 */
@RestController
@RequestMapping("/api/v1/symptoms")
@ConditionalOnProperty(prefix = "clinic.ml", name = "enabled", havingValue = "true")
@PreAuthorize("isAuthenticated()")
public class SymptomController {

    private final ConditionClient conditionClient;

    public SymptomController(ConditionClient conditionClient) {
        this.conditionClient = conditionClient;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, List<ConditionClient.Symptom>>>> list() {
        ConditionClient.SymptomsResponse response;
        try {
            response = conditionClient.symptoms();
        } catch (RuntimeException exception) {
            throw new MlUnavailableException("The symptom list is unavailable right now.", exception);
        }
        return ResponseEntity.ok(ApiResponse.success("Symptoms fetched successfully",
                Map.of("symptoms", response.symptoms())));
    }
}
