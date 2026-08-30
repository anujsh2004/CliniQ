package com.clinic;

import org.springframework.boot.SpringApplication;
import com.clinic.payment.PaymentProperties;
import com.clinic.security.JwtProperties;
import com.clinic.service.SlotProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class, SlotProperties.class, PaymentProperties.class})
public class ClinicBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClinicBackendApplication.class, args);
    }
}
