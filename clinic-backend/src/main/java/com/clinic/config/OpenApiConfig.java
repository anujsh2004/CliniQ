package com.clinic.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Published API documentation (tech-stack.md Phase 4).
 *
 * <p>The generated document is derived from the code, so it cannot drift from
 * what the API actually does. It does not replace {@code docs/api-contract.md},
 * which remains the agreement the code is written against; this is the
 * always-accurate reflection of the current implementation.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI clinicOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cliniva API")
                        .version("v1")
                        .description("""
                                Clinic management API.

                                Every response uses the standard envelope: `success`, `message`, \
                                `data` or `errorCode` plus `errors`, `timestamp` and `requestId`. \
                                Errors carry a canonical `ErrorCode`; see docs/api-contract.md \
                                section 7a for the full list.

                                All endpoints except `/auth/**`, `/health` and the payment webhook \
                                require a bearer access token from `POST /auth/login`.""")
                        .contact(new Contact().name("Cliniva backend team")))
                // Declared once, applied to every operation, so the Swagger UI
                // Authorize button works across the whole document.
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Paste the accessToken returned by POST /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
