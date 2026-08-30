package com.clinic.security;

import com.clinic.dto.response.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * Applies {@link AuthRateLimiter} to the authentication endpoints
 * (tech-stack.md 4, Security).
 *
 * <p>Only login, registration and refresh are limited. They are the endpoints
 * where guessing gets an attacker something, and they are cheap to call
 * repeatedly. Everything else already requires a valid token.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh");

    private final AuthRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public AuthRateLimitFilter(AuthRateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // Keyed by client and path: hammering login must not also lock the
        // client out of registering.
        String clientKey = ClientIpResolver.resolve(request) + ":" + request.getRequestURI();
        AuthRateLimiter.Decision decision = rateLimiter.recordAttempt(clientKey);

        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));

        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        // The standard envelope, with no errorCode: contract section 7a defines
        // codes for domain failures only, and inventing one would break the
        // contract. Same treatment as 404 and 405.
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(
                false,
                "Too many attempts. Please wait a moment and try again.",
                null,
                List.of(),
                OffsetDateTime.now(),
                com.clinic.config.RequestIdFilter.currentRequestId()));
    }
}
