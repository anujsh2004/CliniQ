package com.clinic.ml;

import com.clinic.config.MlProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Talks to the condition-hint model in ml-service/ (API contract 27).
 *
 * <p>Self-hosted, as with no-show risk: symptoms are health information and
 * are not sent anywhere the clinic does not control.
 */
public class ConditionClient {

    private final RestClient restClient;

    public ConditionClient(MlProperties properties) {
        // RestClient.Builder is not auto-configured in Spring Boot 4, so the
        // request factory is built by hand to apply the timeout.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.timeout().toMillis());
        factory.setReadTimeout((int) properties.timeout().toMillis());

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }

    /** The symptoms a patient may choose from: the model's vocabulary. */
    public SymptomsResponse symptoms() {
        SymptomsResponse response = restClient.get()
                .uri("/symptoms")
                .retrieve()
                .body(SymptomsResponse.class);
        if (response == null || response.symptoms() == null) {
            throw new IllegalStateException("The ML service returned no symptoms.");
        }
        return response;
    }

    public ConditionsResponse conditions(List<Case> cases) {
        ConditionsResponse response = restClient.post()
                .uri("/conditions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ConditionsRequest(cases))
                .retrieve()
                .body(ConditionsResponse.class);
        if (response == null || response.results() == null) {
            throw new IllegalStateException("The ML service returned no conditions.");
        }
        return response;
    }

    public record Symptom(String code, String label) {
    }

    public record SymptomsResponse(String modelVersion, List<Symptom> symptoms) {
    }

    public record Case(String caseId, List<String> symptoms) {
    }

    record ConditionsRequest(List<Case> cases) {
    }

    public record Condition(String name, double probability) {
    }

    public record CaseResult(String caseId, List<String> unknownSymptoms, List<Condition> conditions) {
    }

    public record ConditionsResponse(String modelVersion, List<CaseResult> results) {
    }
}
