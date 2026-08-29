package com.clinic.docs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The published OpenAPI document (tech-stack.md Phase 4).
 *
 * <p>Generated from the code, so it cannot drift from the implementation. This
 * test guards the other direction: that every endpoint the API contract defines
 * is actually present, so an endpoint cannot quietly disappear from the docs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ExtendWith(org.springframework.test.context.junit.jupiter.SpringExtension.class)
class OpenApiDocumentTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    /** The scheduled jobs are irrelevant here and must not run. */
    @MockitoBean
    private com.clinic.service.SlotGenerationService slotGenerationService;

    @MockitoBean
    private com.clinic.notification.ReminderPublisher reminderPublisher;

    @LocalServerPort
    private int port;

    private JsonNode fetchDocument() {
        String body = RestClient.create()
                .get()
                .uri("http://localhost:" + port + "/v3/api-docs")
                .retrieve()
                .body(String.class);
        return new ObjectMapper().readTree(body);
    }

    @Test
    void theDocumentIsPublicSoIntegratorsCanReadItWithoutAnAccount() {
        // It describes the contract and exposes no data; requiring a token to
        // read the docs would be friction with no security benefit.
        assertThat(fetchDocument().path("info").path("title").asString()).isEqualTo("Cliniva API");
    }

    @Test
    void everyEndpointInTheApiContractIsDocumented() {
        JsonNode paths = fetchDocument().path("paths");

        List<String> expected = List.of(
                "/api/v1/auth/register",
                "/api/v1/auth/login",
                "/api/v1/auth/refresh",
                "/api/v1/doctors",
                "/api/v1/doctors/{doctorId}",
                "/api/v1/doctors/{doctorId}/availability",
                "/api/v1/doctors/{doctorId}/slots",
                "/api/v1/doctors/me/appointments",
                "/api/v1/patients/me",
                "/api/v1/appointments",
                "/api/v1/appointments/my",
                "/api/v1/appointments/{appointmentId}",
                "/api/v1/appointments/{appointmentId}/cancel",
                "/api/v1/appointments/{appointmentId}/reschedule",
                "/api/v1/appointments/{appointmentId}/complete",
                "/api/v1/payments/create-order",
                "/api/v1/payments/webhook",
                "/api/v1/internal/notifications/reminders");

        assertThat(expected).allSatisfy(path ->
                assertThat(paths.has(path))
                        .withFailMessage("API contract endpoint %s is missing from the OpenAPI document", path)
                        .isTrue());
    }

    @Test
    void bearerAuthenticationIsDeclaredSoTheSwaggerUiCanAuthorise() {
        JsonNode schemes = fetchDocument().path("components").path("securitySchemes").path("bearerAuth");

        assertThat(schemes.path("scheme").asString()).isEqualTo("bearer");
        assertThat(schemes.path("bearerFormat").asString()).isEqualTo("JWT");
    }
}
