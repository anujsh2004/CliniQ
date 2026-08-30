package com.clinic.service;

import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import com.clinic.entity.Doctor;
import com.clinic.entity.Patient;
import com.clinic.entity.Payment;
import com.clinic.entity.PaymentStatus;
import com.clinic.entity.Role;
import com.clinic.entity.User;
import com.clinic.exception.ApiException;
import com.clinic.exception.AppointmentNotFoundException;
import com.clinic.exception.ErrorCode;
import com.clinic.exception.FieldValidationException;
import com.clinic.payment.StubPaymentGateway;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.PaymentRepository;
import com.clinic.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Payment confirmation (API contract 14). The rule under test throughout: only
 * a verified webhook moves money-related state, and a webhook that arrives
 * twice must not apply twice.
 */
class PaymentServiceTest {

    private static final String WEBHOOK_SECRET = "webhook-secret-value";

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);

    private final PaymentService service = new PaymentService(
            paymentRepository, appointmentRepository, new StubPaymentGateway(WEBHOOK_SECRET));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UUID userId, Role role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, "caller@example.com", role), null, List.of()));
    }

    private Appointment appointment(UUID patientUserId, AppointmentStatus status, PaymentStatus paymentStatus) {
        User user = new User();
        user.setId(patientUserId);
        user.setName("Anjali Verma");

        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setUser(user);

        Doctor doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        doctor.setName("Dr. Sharma");
        doctor.setConsultationFee(new BigDecimal("500.00"));

        Appointment appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setStatus(status);
        appointment.setPaymentStatus(paymentStatus);
        return appointment;
    }

    private Payment payment(Appointment appointment, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setAppointment(appointment);
        payment.setGateway("RAZORPAY");
        payment.setOrderId("order_1");
        payment.setAmount(new BigDecimal("500.00"));
        payment.setCurrency("INR");
        payment.setStatus(status);
        return payment;
    }

    private void stubOrderCreation() {
        when(paymentRepository.findFirstByAppointmentIdAndStatusOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> {
            Payment saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
    }

    @Test
    void createsAnOrderForTheAppointmentFee() {
        UUID patientUserId = UUID.randomUUID();
        Appointment appointment = appointment(patientUserId, AppointmentStatus.PENDING_PAYMENT,
                PaymentStatus.PENDING);
        when(appointmentRepository.findWithDetailsById(appointment.getId()))
                .thenReturn(Optional.of(appointment));
        stubOrderCreation();
        authenticateAs(patientUserId, Role.PATIENT);

        var response = service.createOrder(appointment.getId());

        assertThat(response.amount()).isEqualByComparingTo("500.00");
        assertThat(response.currency()).isEqualTo("INR");
        assertThat(response.gateway()).isEqualTo("RAZORPAY");
        assertThat(response.status()).isEqualTo(PaymentStatus.CREATED);
        // Creating an order does not confirm anything.
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.PENDING_PAYMENT);
    }

    @Test
    void reusesAnOpenOrderRatherThanCreatingASecond() {
        // A patient reloading checkout must not end up with two live orders.
        UUID patientUserId = UUID.randomUUID();
        Appointment appointment = appointment(patientUserId, AppointmentStatus.PENDING_PAYMENT,
                PaymentStatus.CREATED);
        Payment existing = payment(appointment, PaymentStatus.CREATED);
        when(appointmentRepository.findWithDetailsById(appointment.getId()))
                .thenReturn(Optional.of(appointment));
        when(paymentRepository.findFirstByAppointmentIdAndStatusOrderByCreatedAtDesc(
                appointment.getId(), PaymentStatus.CREATED)).thenReturn(Optional.of(existing));
        authenticateAs(patientUserId, Role.PATIENT);

        assertThat(service.createOrder(appointment.getId()).orderId()).isEqualTo("order_1");
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void aPatientCannotPayForAnotherPatientsAppointment() {
        Appointment appointment = appointment(UUID.randomUUID(), AppointmentStatus.PENDING_PAYMENT,
                PaymentStatus.PENDING);
        when(appointmentRepository.findWithDetailsById(appointment.getId()))
                .thenReturn(Optional.of(appointment));
        authenticateAs(UUID.randomUUID(), Role.PATIENT);

        assertThatThrownBy(() -> service.createOrder(appointment.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED_ACCESS);
    }

    @Test
    void cannotPayForACancelledAppointment() {
        UUID patientUserId = UUID.randomUUID();
        Appointment appointment = appointment(patientUserId, AppointmentStatus.CANCELLED, PaymentStatus.PENDING);
        when(appointmentRepository.findWithDetailsById(appointment.getId()))
                .thenReturn(Optional.of(appointment));
        authenticateAs(patientUserId, Role.PATIENT);

        assertThatThrownBy(() -> service.createOrder(appointment.getId()))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void cannotPayTwiceForOneAppointment() {
        UUID patientUserId = UUID.randomUUID();
        Appointment appointment = appointment(patientUserId, AppointmentStatus.CONFIRMED, PaymentStatus.PAID);
        when(appointmentRepository.findWithDetailsById(appointment.getId()))
                .thenReturn(Optional.of(appointment));
        authenticateAs(patientUserId, Role.PATIENT);

        assertThatThrownBy(() -> service.createOrder(appointment.getId()))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void orderForAnUnknownAppointmentIs404() {
        when(appointmentRepository.findWithDetailsById(any())).thenReturn(Optional.empty());
        authenticateAs(UUID.randomUUID(), Role.PATIENT);

        assertThatThrownBy(() -> service.createOrder(UUID.randomUUID()))
                .isInstanceOf(AppointmentNotFoundException.class);
    }

    @Test
    void aCapturedPaymentConfirmsTheAppointment() {
        Appointment appointment = appointment(UUID.randomUUID(), AppointmentStatus.PENDING_PAYMENT,
                PaymentStatus.CREATED);
        Payment stored = payment(appointment, PaymentStatus.CREATED);
        when(paymentRepository.findByOrderId("order_1")).thenReturn(Optional.of(stored));

        var result = service.applyVerifiedWebhook("order_1", "pay_1", true);

        assertThat(result.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(stored.getGatewayPaymentId()).isEqualTo("pay_1");
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(appointment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void aFailedPaymentLeavesTheAppointmentUnconfirmed() {
        Appointment appointment = appointment(UUID.randomUUID(), AppointmentStatus.PENDING_PAYMENT,
                PaymentStatus.CREATED);
        Payment stored = payment(appointment, PaymentStatus.CREATED);
        when(paymentRepository.findByOrderId("order_1")).thenReturn(Optional.of(stored));

        var result = service.applyVerifiedWebhook("order_1", "pay_1", false);

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.PENDING_PAYMENT);
    }

    @Test
    void aRepeatedWebhookIsIgnoredRatherThanAppliedTwice() {
        // Gateways retry. The second delivery must be a no-op.
        Appointment appointment = appointment(UUID.randomUUID(), AppointmentStatus.CONFIRMED, PaymentStatus.PAID);
        Payment alreadyPaid = payment(appointment, PaymentStatus.PAID);
        alreadyPaid.setGatewayPaymentId("pay_1");
        when(paymentRepository.findByOrderId("order_1")).thenReturn(Optional.of(alreadyPaid));

        var result = service.applyVerifiedWebhook("order_1", "pay_other", true);

        assertThat(result.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(alreadyPaid.getGatewayPaymentId()).isEqualTo("pay_1");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void payingForAnAlreadyCancelledAppointmentDoesNotResurrectIt() {
        // Its slot went back on the market when it was cancelled; reviving the
        // appointment would double-book that slot. The payment is recorded so
        // the money can be refunded.
        Appointment appointment = appointment(UUID.randomUUID(), AppointmentStatus.CANCELLED,
                PaymentStatus.CREATED);
        Payment stored = payment(appointment, PaymentStatus.CREATED);
        when(paymentRepository.findByOrderId("order_1")).thenReturn(Optional.of(stored));

        service.applyVerifiedWebhook("order_1", "pay_1", true);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(stored.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void aWebhookForAnUnknownOrderChangesNothing() {
        when(paymentRepository.findByOrderId(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyVerifiedWebhook("order_unknown", "pay_1", true))
                .isInstanceOf(FieldValidationException.class);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void signatureCheckingIsDelegatedToTheGateway() {
        String body = "{\"event\":\"payment.captured\"}";

        assertThat(service.isSignatureValid(body,
                com.clinic.payment.SignatureVerifier.sign(body, WEBHOOK_SECRET))).isTrue();
        assertThat(service.isSignatureValid(body, "forged")).isFalse();
    }
}
