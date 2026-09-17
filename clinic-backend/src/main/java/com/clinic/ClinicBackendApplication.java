package com.clinic;

import org.springframework.boot.SpringApplication;
import com.clinic.config.BootstrapProperties;
import com.clinic.config.HoldProperties;
import com.clinic.config.ReminderProperties;
import com.clinic.config.SpeechProperties;
import com.clinic.payment.PaymentProperties;
import com.clinic.security.JwtProperties;
import com.clinic.security.RateLimitProperties;
import com.clinic.service.SlotProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class, SlotProperties.class, PaymentProperties.class,
        RateLimitProperties.class, BootstrapProperties.class,
        HoldProperties.class, ReminderProperties.class, SpeechProperties.class})
public class ClinicBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClinicBackendApplication.class, args);
    }
}
