package com.clinic.config;

import com.clinic.ml.NoShowRiskClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the no-show risk client, but only when the ML service is switched on.
 *
 * <p>Without the client the schedule simply carries no risk, which is the same
 * response v1.5 returned. Nothing calls a service that is not there.
 */
@Configuration
@ConditionalOnProperty(prefix = "clinic.ml", name = "enabled", havingValue = "true")
public class MlConfig {

    @Bean
    public NoShowRiskClient noShowRiskClient(MlProperties properties) {
        return new NoShowRiskClient(properties);
    }
}
