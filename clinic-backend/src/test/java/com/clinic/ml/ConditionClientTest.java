package com.clinic.ml;

import com.clinic.config.MlProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The wire format between the backend and ml-service's /symptoms and
 * /conditions. A stub stands in for the Python service.
 */
class ConditionClientTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private ConditionClient start(String path, String responseBody, AtomicReference<String> received)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        return new ConditionClient(new MlProperties(true, url, Duration.ofSeconds(2)));
    }

    @Test
    void sendsCasesAndReadsRankedConditions() throws IOException {
        AtomicReference<String> received = new AtomicReference<>();
        ConditionClient client = start("/conditions", """
                {"modelVersion": "conditions-logistic-202610260000",
                 "results": [{"caseId": "a-1", "unknownSymptoms": [],
                              "conditions": [{"name": "Dengue", "probability": 0.62},
                                             {"name": "Malaria", "probability": 0.2}]}]}
                """, received);

        ConditionClient.ConditionsResponse response =
                client.conditions(List.of(new ConditionClient.Case("a-1", List.of("high_fever", "joint_pain"))));

        assertThat(received.get())
                .contains("\"cases\"")
                .contains("\"caseId\":\"a-1\"")
                .contains("\"symptoms\":[\"high_fever\",\"joint_pain\"]");
        assertThat(response.results().getFirst().conditions())
                .extracting(ConditionClient.Condition::name)
                .containsExactly("Dengue", "Malaria");
    }

    @Test
    void readsTheSymptomVocabulary() throws IOException {
        ConditionClient client = start("/symptoms", """
                {"modelVersion": "v1", "symptoms": [{"code": "skin_rash", "label": "Skin rash"}]}
                """, new AtomicReference<>());

        assertThat(client.symptoms().symptoms())
                .containsExactly(new ConditionClient.Symptom("skin_rash", "Skin rash"));
    }
}
