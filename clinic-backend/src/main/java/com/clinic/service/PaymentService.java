package com.clinic.service;

import com.clinic.dto.response.PaymentOrderResponse;
import com.clinic.dto.response.PaymentWebhookResult;
import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import com.clinic.entity.Payment;
import com.clinic.entity.PaymentStatus;
import com.clinic.entity.Role;
import com.clinic.exception.ApiException;
import com.clinic.exception.AppointmentNotFoundException;
import com.clinic.exception.ErrorCode;
import com.clinic.exception.FieldValidationException;
import com.clinic.payment.PaymentGateway;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.PaymentRepository;
import com.clinic.security.AuthenticatedUser;
import com.clinic.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Payment orders and webhook confirmation (API contract 14).
 *
 * <p>The rule this class exists to enforce: <b>only a signature-verified
 * webhook may change payment or appointment state</b>. The client tells us
 * nothing we act on - it can lie about a successful checkout, and a manipulated
 * client must not be able to confirm an unpaid appointment
 * (product-description.md 8.6).
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String CURRENCY = "INR";

    private final PaymentRepository paymentRepository;
    private final AppointmentRepository appointmentRepository;
    private final PaymentGateway gateway;

    public PaymentService(PaymentRepository paymentRepository,
                          AppointmentRepository appointmentRepository,
                          PaymentGateway gateway) {
        this.paymentRepository = paymentRepository;
        this.appointmentRepository = appointmentRepository;
        this.gateway = gateway;
    }

    @Transactional
    public PaymentOrderResponse createOrder(UUID appointmentId) {
        Appointment appointment = appointmentRepository.findWithDetailsById(appointmentId)
                .orElseThrow(AppointmentNotFoundException::new);
        requireOwnAppointment(appointment);

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new FieldValidationException("appointmentId",
                    "This appointment has been cancelled");
        }
        if (appointment.getPaymentStatus() == PaymentStatus.PAID) {
            throw new FieldValidationException("appointmentId",
                    "This appointment has already been paid for");
        }

        // Reuse an order that is still open rather than creating a second one
        // for the same appointment, so a patient who reloads checkout does not
        // end up with two live orders.
        Optional<Payment> existing = paymentRepository
                .findFirstByAppointmentIdAndStatusOrderByCreatedAtDesc(appointmentId, PaymentStatus.CREATED);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        var order = gateway.createOrder(appointment.getDoctor().getConsultationFee(), CURRENCY,
                "appointment_" + appointment.getId());

        Payment payment = new Payment();
        payment.setAppointment(appointment);
        payment.setGateway(gateway.name());
        payment.setOrderId(order.orderId());
        payment.setAmount(order.amount());
        payment.setCurrency(order.currency());
        payment.setStatus(PaymentStatus.CREATED);

        appointment.setPaymentStatus(PaymentStatus.CREATED);
        appointmentRepository.save(appointment);

        return toResponse(paymentRepository.saveAndFlush(payment));
    }

    /**
     * Applies a webhook the caller has already proven is genuine.
     *
     * <p>Idempotent: gateways retry, so the same event arriving twice must not
     * double-apply. A payment already marked PAID is left alone.
     */
    @Transactional
    public PaymentWebhookResult applyVerifiedWebhook(String orderId, String gatewayPaymentId, boolean captured) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new FieldValidationException("orderId", "Unknown order"));

        if (payment.getStatus() == PaymentStatus.PAID) {
            log.info("Webhook for order {} ignored: already paid", orderId);
            return result(payment);
        }

        Appointment appointment = payment.getAppointment();

        if (!captured) {
            payment.setStatus(PaymentStatus.FAILED);
            appointment.setPaymentStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            appointmentRepository.save(appointment);
            log.info("Payment failed for order {}", orderId);
            return result(payment);
        }

        payment.setStatus(PaymentStatus.PAID);
        payment.setGatewayPaymentId(gatewayPaymentId);
        appointment.setPaymentStatus(PaymentStatus.PAID);

        // A cancelled appointment that somehow gets paid is refunded rather than
        // silently resurrected - its slot has already gone back on the market.
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            log.warn("Payment captured for cancelled appointment {}; needs a refund", appointment.getId());
        } else if (appointment.getStatus() == AppointmentStatus.PENDING_PAYMENT) {
            appointment.setStatus(AppointmentStatus.CONFIRMED);
        }

        paymentRepository.save(payment);
        appointmentRepository.save(appointment);
        log.info("Payment {} captured for appointment {}", gatewayPaymentId, appointment.getId());
        return result(payment);
    }

    public boolean isSignatureValid(String rawBody, String signature) {
        return gateway.verifyWebhookSignature(rawBody, signature);
    }

    private void requireOwnAppointment(Appointment appointment) {
        AuthenticatedUser caller = CurrentUser.require();
        if (caller.role() == Role.ADMIN) {
            return;
        }
        if (!appointment.getPatient().getUser().getId().equals(caller.userId())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
    }

    private PaymentOrderResponse toResponse(Payment payment) {
        return new PaymentOrderResponse(
                payment.getId().toString(),
                payment.getAppointment().getId().toString(),
                payment.getGateway(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus());
    }

    private PaymentWebhookResult result(Payment payment) {
        return new PaymentWebhookResult(
                payment.getId().toString(),
                payment.getAppointment().getId().toString(),
                payment.getStatus());
    }
}
