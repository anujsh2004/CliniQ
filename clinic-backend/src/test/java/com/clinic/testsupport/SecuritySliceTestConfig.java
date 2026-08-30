package com.clinic.testsupport;

import com.clinic.config.SecurityConfig;
import com.clinic.security.AuthRateLimitFilter;
import com.clinic.security.AuthRateLimiter;
import com.clinic.security.JwtAuthenticationFilter;
import com.clinic.security.RateLimitProperties;
import com.clinic.security.RestAccessDeniedHandler;
import com.clinic.security.RestAuthenticationEntryPoint;
import com.clinic.security.SecurityErrorResponder;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.Mockito.mock;

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
        AuthRateLimitFilter.class,
        SecurityErrorResponder.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
public class SecuritySliceTestConfig {

    /**
     * A disabled rate limiter. The filter is a @Component and so joins every
     * web slice, but slice tests are not about rate limiting and must not need
     * a Redis connection to run. Rate limiting has its own tests.
     */
    @Bean
    AuthRateLimiter authRateLimiter() {
        return new AuthRateLimiter(mock(StringRedisTemplate.class),
                new RateLimitProperties(false, 10, 60));
    }
}
