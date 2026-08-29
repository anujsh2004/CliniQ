package com.clinic.controller;

import com.clinic.dto.response.PaymentOrderResponse;
import com.clinic.entity.PaymentStatus;
import com.clinic.security.JwtService;
import com.clinic.service.PaymentService;
import com.clinic.testsupport.SecuritySliceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Payment endpoints (API contract 14).
 */
@WebMvcTest(PaymentController.class)
@Import(SecuritySliceTestConfig.class)
class PaymentControllerTest {

    private static final String CAPTURED_EVENT = """
            {"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_1","order_id":"order_1"}}}}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    @WithMockUser(roles = "PATIENT")
    void createsAnOrderInTheContractsShape() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        when(paymentService.createOrder(appointmentId)).thenReturn(new PaymentOrderResponse(
                UUID.randomUUID().toString(), appointmentId.toString(), "RAZORPAY", "order_xxx",
                new BigDecimal("500.00"), "INR", PaymentStatus.CREATED));

        mockMvc.perform(post("/api/v1/payments/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appointmentId\":\"" + appointmentId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Payment order created successfully"))
                .andExpect(jsonPath("$.data.gateway").value("RAZORPAY"))
                .andExpect(jsonPath("$.data.orderId").value("order_xxx"))
                .andExpect(jsonPath("$.data.currency").value("INR"))
                .andExpect(jsonPath("$.data.status").value("CREATED"));
    }

    @Test
    void theWebhookIsReachableWithoutAuthentication() throws Exception {
        // The gateway has no bearer token; its signature is what authenticates it.
        when(paymentService.isSignatureValid(anyString(), any())).thenReturn(false);

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CAPTURED_EVENT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anUnsignedWebhookChangesNothing() throws Exception {
        // The whole point of API contract 14: a forged "payment captured" must
        // not confirm an appointment.
        when(paymentService.isSignatureValid(anyString(), any())).thenReturn(false);

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", "forged")
                        .content(CAPTURED_EVENT))
                .andExpect(status().isUnauthorized());

        verify(paymentService, never()).applyVerifiedWebhook(anyString(), anyString(), anyBoolean());
    }

    @Test
    void aVerifiedCaptureIsApplied() throws Exception {
        when(paymentService.isSignatureValid(anyString(), any())).thenReturn(true);
        when(paymentService.applyVerifiedWebhook("order_1", "pay_1", true)).thenReturn(
                new com.clinic.dto.response.PaymentWebhookResult(
                        UUID.randomUUID().toString(), UUID.randomUUID().toString(), PaymentStatus.PAID));

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", "valid")
                        .content(CAPTURED_EVENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));

        verify(paymentService).applyVerifiedWebhook("order_1", "pay_1", true);
    }

    @Test
    void anEventWeDoNotActOnIsAcknowledgedRatherThanRetriedForever() throws Exception {
        when(paymentService.isSignatureValid(anyString(), any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", "valid")
                        .content("""
                                {"event":"order.paid","payload":{"payment":{"entity":{"order_id":"order_1"}}}}"""))
                .andExpect(status().isOk());

        verify(paymentService, never()).applyVerifiedWebhook(anyString(), anyString(), anyBoolean());
    }

    @Test
    void aVerifiedButUnreadableBodyIsAcknowledgedWithoutChangingState() throws Exception {
        when(paymentService.isSignatureValid(anyString(), any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", "valid")
                        .content("not json at all"))
                .andExpect(status().isOk());

        verify(paymentService, never()).applyVerifiedWebhook(anyString(), anyString(), anyBoolean());
    }
}
