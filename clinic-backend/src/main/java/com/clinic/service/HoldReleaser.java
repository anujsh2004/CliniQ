package com.clinic.service;

import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import com.clinic.entity.Slot;
import com.clinic.entity.SlotStatus;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.SlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Releases one expired hold, in its own transaction.
 *
 * <p>Deliberately a separate bean from the sweep that calls it. Spring applies
 * {@code @Transactional} through a proxy, so a scheduled method calling a
 * transactional method on {@code this} gets no transaction at all - and the
 * pessimistic lock below then fails with "No active transaction". Crossing a
 * bean boundary is what makes the annotation take effect.
 */
@Service
public class HoldReleaser {

    private static final Logger log = LoggerFactory.getLogger(HoldReleaser.class);
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Kolkata");

    private final AppointmentRepository appointmentRepository;
    private final SlotRepository slotRepository;

    public HoldReleaser(AppointmentRepository appointmentRepository, SlotRepository slotRepository) {
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
    }

    /**
     * @return whether anything changed; false when the appointment was paid for
     *         or cancelled between the sweep's query and this lock
     */
    @Transactional
    public boolean release(UUID appointmentId) {
        Appointment appointment = appointmentRepository.findWithDetailsById(appointmentId).orElse(null);
        if (appointment == null || appointment.getStatus() != AppointmentStatus.PENDING_PAYMENT) {
            return false;
        }
        if (appointment.getHoldExpiresAt() == null
                || appointment.getHoldExpiresAt().isAfter(OffsetDateTime.now())) {
            // Paid or extended since the sweep started reading.
            return false;
        }

        Slot slot = slotRepository.findByIdForUpdate(appointment.getSlot().getId()).orElse(null);
        if (slot != null && slot.getStatus() == SlotStatus.HELD) {
            // A slot whose time has already passed goes to EXPIRED rather than
            // back on sale: nobody can book 9am at 10am.
            slot.setStatus(hasPassed(slot) ? SlotStatus.EXPIRED : SlotStatus.AVAILABLE);
            slotRepository.save(slot);
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancellationReason("Payment was not completed in time");
        appointment.setHoldExpiresAt(null);
        appointmentRepository.save(appointment);

        log.info("Released the expired hold on appointment {}", appointment.getId());
        return true;
    }

    private boolean hasPassed(Slot slot) {
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        return slot.getDate().isBefore(today)
                || (slot.getDate().equals(today) && slot.getStartTime().isBefore(LocalTime.now(CLINIC_ZONE)));
    }
}
