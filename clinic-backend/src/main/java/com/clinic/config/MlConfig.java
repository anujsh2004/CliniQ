package com.clinic.config;

import com.clinic.ml.ConditionClient;
import com.clinic.ml.NoShowRiskClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the ML clients - no-show risk and condition hints - but only when the
 * ML service is switched on.
 *
 * <p>Without them the schedule simply carries no risk and no hints, which is
 * the same response v1.5 returned. Nothing calls a service that is not there.
 */
@Configuration
@ConditionalOnProperty(prefix = "clinic.ml", name = "enabled", havingValue = "true")
public class MlConfig {

    @Bean
    public NoShowRiskClient noShowRiskClient(MlProperties properties) {
        return new NoShowRiskClient(properties);
    }

    @Bean
    public ConditionClient conditionClient(MlProperties properties) {
        return new ConditionClient(properties);
    }
}
