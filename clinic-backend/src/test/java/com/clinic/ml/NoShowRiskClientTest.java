package com.clinic.ml;

import com.clinic.config.MlProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The wire format between the backend and ml-service/app.py. A stub server
 * stands in for the Python service, so a renamed field on either side fails
 * here rather than as silently missing badges.
 */
class NoShowRiskClientTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String start(String responseBody, AtomicReference<String> received) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/score", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void sendsFeaturesInTheServicesFieldNamesAndReadsTheBand() throws IOException {
        AtomicReference<String> received = new AtomicReference<>();
        String url = start("""
                {"modelVersion": "gradient_boosting-202610260000",
                 "scores": [{"appointmentId": "a-1", "probability": 0.37, "band": "HIGH"}]}
                """, received);
        NoShowRiskClient client = new NoShowRiskClient(new MlProperties(true, url, Duration.ofSeconds(2)));

        NoShowRiskClient.ScoreResponse response =
                client.score(List.of(new RiskFeatures("a-1", 9, 3, 14, 4, 1, true)));

        assertThat(received.get())
                .contains("\"appointments\"")
                .contains("\"appointmentId\":\"a-1\"")
                .contains("\"leadDays\":9")
                .contains("\"appointmentWeekday\":3")
                .contains("\"bookingHour\":14")
                .contains("\"priorVisits\":4")
                .contains("\"priorNoShows\":1")
                .contains("\"reminderSent\":true");
        assertThat(response.modelVersion()).isEqualTo("gradient_boosting-202610260000");
        assertThat(response.scores().getFirst().band()).isEqualTo(RiskBand.HIGH);
        assertThat(response.scores().getFirst().probability()).isEqualTo(0.37);
    }

    @Test
    void aServiceThatIsNotRunningIsAnError() {
        // Port 9 (discard) is closed on any normal machine.
        NoShowRiskClient client = new NoShowRiskClient(
                new MlProperties(true, "http://127.0.0.1:9", Duration.ofMillis(500)));

        assertThatThrownBy(() -> client.score(List.of(new RiskFeatures("a-1", 0, 0, 9, 0, 0, false))))
                .isInstanceOf(ResourceAccessException.class);
    }
}
