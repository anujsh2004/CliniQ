package com.clinic.testsupport;

import com.clinic.config.SecurityConfig;
import com.clinic.security.JwtAuthenticationFilter;
import com.clinic.security.RestAccessDeniedHandler;
import com.clinic.security.RestAuthenticationEntryPoint;
import com.clinic.security.SecurityErrorResponder;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * A @WebMvcTest slice does not pick up the application's own @Configuration
 * classes, so without this the authorization rules would not run and a test
 * asserting 403 would silently pass through an unprotected endpoint. Importing
 * the real SecurityConfig and its collaborators makes slice tests exercise the
 * rules that actually ship.
 */
@TestConfiguration
@ImportAutoConfiguration({
        SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityErrorResponder.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
public class SecuritySliceTestConfig {
}
