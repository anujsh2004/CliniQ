package com.clinic.ml;

import com.clinic.config.MlProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Talks to the Python service in ml-service/ that hosts the no-show model.
 *
 * <p>Self-hosted for the same reason as speech: attendance history is health
 * information, and it should not leave the clinic's own hardware to be scored.
 *
 * <p>Failures are thrown as they come. Deciding that a failure costs the
 * doctor only their risk badges is the caller's job, not the client's.
 */
public class NoShowRiskClient {

    private final RestClient restClient;

    public NoShowRiskClient(MlProperties properties) {
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

    public ScoreResponse score(List<RiskFeatures> appointments) {
        ScoreResponse response = restClient.post()
                .uri("/score")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ScoreRequest(appointments))
                .retrieve()
                .body(ScoreResponse.class);
        if (response == null || response.scores() == null) {
            throw new IllegalStateException("The ML service returned no scores.");
        }
        return response;
    }

    record ScoreRequest(List<RiskFeatures> appointments) {
    }

    public record ScoreResponse(String modelVersion, List<Score> scores) {
    }

    public record Score(String appointmentId, double probability, RiskBand band) {
    }
}
