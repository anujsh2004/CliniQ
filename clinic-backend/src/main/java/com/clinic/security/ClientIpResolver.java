package com.clinic.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Works out which client a request came from, for rate limiting.
 *
 * <p>In production the application sits behind Nginx (tech-stack.md 4), so the
 * socket address is the proxy's. The real client is the first entry of
 * X-Forwarded-For.
 *
 * <p><b>This header is trivially spoofable by a direct caller.</b> It is safe
 * here only because the deployment terminates TLS at a proxy that overwrites
 * the header. If the application is ever exposed directly, an attacker can
 * defeat rate limiting by varying this header, so the proxy is part of the
 * control, not an optimisation.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // "client, proxy1, proxy2" - the client is first.
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }
}
