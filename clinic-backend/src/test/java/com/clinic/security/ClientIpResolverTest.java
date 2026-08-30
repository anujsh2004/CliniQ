package com.clinic.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private MockHttpServletRequest request(String forwardedFor, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    @Test
    void usesTheSocketAddressWhenThereIsNoProxy() {
        assertThat(ClientIpResolver.resolve(request(null, "203.0.113.5"))).isEqualTo("203.0.113.5");
    }

    @Test
    void takesTheOriginalClientFromTheForwardedChain() {
        // Behind Nginx the socket address is the proxy's; the client is first
        // in the chain.
        assertThat(ClientIpResolver.resolve(
                request("203.0.113.5, 10.0.0.1, 10.0.0.2", "10.0.0.2")))
                .isEqualTo("203.0.113.5");
    }

    @Test
    void ignoresABlankForwardedHeader() {
        assertThat(ClientIpResolver.resolve(request("   ", "203.0.113.5"))).isEqualTo("203.0.113.5");
    }

    @Test
    void neverReturnsNullSoTheLimiterAlwaysHasAKey() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(null);

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("unknown");
    }
}
