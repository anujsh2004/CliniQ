package com.clinic.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where the ML service is found. Render supplies a service's internal address
 * as a bare {@code host:port}, which has to work as well as a full URL.
 */
class MlPropertiesTest {

    private static MlProperties withUrl(String url) {
        return new MlProperties(true, url, Duration.ofSeconds(2));
    }

    @Test
    void aBareHostAndPortIsTreatedAsPlainHttp() {
        assertThat(withUrl("cliniq-ml:10000").baseUrl()).isEqualTo("http://cliniq-ml:10000");
    }

    @Test
    void aFullUrlIsLeftAlone() {
        assertThat(withUrl("https://cliniq-ml.onrender.com").baseUrl()).isEqualTo("https://cliniq-ml.onrender.com");
        assertThat(withUrl("http://localhost:8002").baseUrl()).isEqualTo("http://localhost:8002");
    }

    @Test
    void aBlankUrlFallsBackToLocalhost() {
        assertThat(withUrl("").baseUrl()).isEqualTo("http://localhost:8002");
        assertThat(withUrl(null).baseUrl()).isEqualTo("http://localhost:8002");
    }
}
