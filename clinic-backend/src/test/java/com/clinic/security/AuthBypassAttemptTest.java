package com.clinic.security;

import com.clinic.controller.AppointmentController;
import com.clinic.entity.Role;
import com.clinic.entity.User;
import com.clinic.service.AppointmentBookingService;
import com.clinic.service.AppointmentQueryService;
import com.clinic.testsupport.SecuritySliceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Attempts to reach a protected endpoint without valid credentials
 * (tech-stack.md Phase 5, basic security testing).
 *
 * <p>These are deliberately adversarial. The rest of the suite checks that the
 * right people can do the right things; this checks that the wrong people
 * cannot, which is the half that actually matters if it breaks.
 */
@WebMvcTest(AppointmentController.class)
@Import(SecuritySliceTestConfig.class)
class AuthBypassAttemptTest {

    private static final String PROTECTED_PATH = "/api/v1/appointments/" + UUID.randomUUID();
    private static final String SECRET = "test-secret-value-that-is-long-enough-for-hmac-sha256";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppointmentQueryService appointmentQueryService;

    @MockitoBean
    private AppointmentBookingService appointmentBookingService;

    /**
     * The real service, not a mock: these tests are about whether token
     * validation actually rejects things, so stubbing it would defeat them.
     */
    @org.springframework.test.context.bean.override.convention.TestBean
    private JwtService jwtService;

    static JwtService jwtService() {
        return new JwtService(new JwtProperties(SECRET, 3600, 604800));
    }

    private User user(Role role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Anjali Verma");
        user.setEmail("anjali@example.com");
        user.setPhone("+919876543210");
        user.setPasswordHash("irrelevant");
        user.setRole(role);
        return user;
    }

    private void expectRejected(String authorizationHeader) throws Exception {
        var request = get(PROTECTED_PATH);
        if (authorizationHeader != null) {
            request = request.header("Authorization", authorizationHeader);
        }
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));

        // The important half: the service was never reached.
        verify(appointmentQueryService, never()).get(any());
    }

    @Test
    void noTokenAtAll() throws Exception {
        expectRejected(null);
    }

    @Test
    void emptyBearerToken() throws Exception {
        expectRejected("Bearer ");
    }

    @Test
    void garbageInsteadOfAToken() throws Exception {
        expectRejected("Bearer not-a-jwt-at-all");
    }

    @Test
    void aTokenSignedWithADifferentSecret() throws Exception {
        // The attacker knows the payload format and forges a token with their
        // own key.
        JwtService attacker = new JwtService(
                new JwtProperties("an-attacker-controlled-secret-of-sufficient-length", 3600, 604800));

        expectRejected("Bearer " + attacker.issueAccessToken(user(Role.ADMIN)));
    }

    @Test
    void anExpiredToken() throws Exception {
        JwtService expiring = new JwtService(new JwtProperties(SECRET, -60, -60));

        expectRejected("Bearer " + expiring.issueAccessToken(user(Role.PATIENT)));
    }

    @Test
    void aRefreshTokenUsedAsAnAccessToken() throws Exception {
        // Refresh tokens live far longer, so accepting one as an access token
        // would quietly extend the blast radius of a leak.
        expectRejected("Bearer " + jwtService().issueRefreshToken(user(Role.PATIENT)));
    }

    @Test
    void aTokenWithTheSignatureStripped() throws Exception {
        // The "alg: none" family of attacks: keep the payload, drop the proof.
        String token = jwtService().issueAccessToken(user(Role.ADMIN));
        String withoutSignature = token.substring(0, token.lastIndexOf('.') + 1);

        expectRejected("Bearer " + withoutSignature);
    }

    @Test
    void aTokenWhosePayloadWasEditedToClaimAdmin() throws Exception {
        // Privilege escalation by editing the role claim, leaving the original
        // signature attached.
        String token = jwtService().issueAccessToken(user(Role.PATIENT));
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        String tampered = payload.replace("\"role\":\"PATIENT\"", "\"role\":\"ADMIN\"");
        String reencoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tampered.getBytes());

        expectRejected("Bearer " + parts[0] + "." + reencoded + "." + parts[2]);
    }

    @Test
    void theWrongAuthenticationScheme() throws Exception {
        expectRejected("Basic " + Base64.getEncoder().encodeToString("admin:admin".getBytes()));
    }

    @Test
    void aValidTokenInTheWrongHeader() throws Exception {
        // Some frameworks read a token from anywhere; this one should not.
        String token = jwtService().issueAccessToken(user(Role.ADMIN));
        mockMvc.perform(get(PROTECTED_PATH).header("X-Auth-Token", token))
                .andExpect(status().isUnauthorized());

        verify(appointmentQueryService, never()).get(any());
    }
}
