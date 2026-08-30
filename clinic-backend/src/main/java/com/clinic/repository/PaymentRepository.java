package com.clinic.repository;

import com.clinic.entity.Payment;
import com.clinic.entity.PaymentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @EntityGraph(attributePaths = {"appointment", "appointment.patient", "appointment.patient.user"})
    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findFirstByAppointmentIdAndStatusOrderByCreatedAtDesc(
            UUID appointmentId, PaymentStatus status);
}
