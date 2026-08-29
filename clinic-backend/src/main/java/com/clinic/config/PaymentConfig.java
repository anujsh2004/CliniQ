package com.clinic.config;

import com.clinic.payment.PaymentGateway;
import com.clinic.payment.PaymentProperties;
import com.clinic.payment.RazorpayGateway;
import com.clinic.payment.StubPaymentGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the gateway from configuration: the real one when credentials are
 * present, the stub otherwise, so a developer with no Razorpay account can
 * still run the whole flow.
 */
@Configuration
public class PaymentConfig {

    @Bean
    public PaymentGateway paymentGateway(PaymentProperties properties) {
        return properties.isConfigured()
                ? new RazorpayGateway(properties)
                : new StubPaymentGateway(properties.webhookSecret());
    }
}
