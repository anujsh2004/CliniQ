package com.clinic.service;

import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import com.clinic.repository.AppointmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Returns abandoned slots to the market (API contract 12, v1.4).
 *
 * <p>Booking holds a slot rather than selling it, so a patient who never
 * finishes paying would otherwise keep a slot off the market indefinitely.
 *
 * <p>The actual release lives in {@link HoldReleaser}, a separate bean, so that
 * each expiry runs in a real transaction. Calling a {@code @Transactional}
 * method on {@code this} from a scheduled method gets no transaction, and the
 * pessimistic lock the release depends on then fails outright.
 */
@Service
public class HoldExpiryService {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryService.class);

    private final AppointmentRepository appointmentRepository;
    private final HoldReleaser holdReleaser;

    public HoldExpiryService(AppointmentRepository appointmentRepository, HoldReleaser holdReleaser) {
        this.appointmentRepository = appointmentRepository;
        this.holdReleaser = holdReleaser;
    }

    @Scheduled(cron = "${clinic.holds.sweep-cron:*/30 * * * * *}")
    public void releaseExpiredHolds() {
        List<Appointment> expired = appointmentRepository
                .findByStatusAndHoldExpiresAtBefore(AppointmentStatus.PENDING_PAYMENT, OffsetDateTime.now());
        if (expired.isEmpty()) {
            return;
        }
        int released = 0;
        for (Appointment appointment : expired) {
            try {
                if (holdReleaser.release(appointment.getId())) {
                    released++;
                }
            } catch (RuntimeException ex) {
                // One stuck appointment must not stop the rest of the sweep.
                log.warn("Could not release the hold on appointment {}", appointment.getId(), ex);
            }
        }
        log.info("Hold sweep: {} expired, {} slots released", expired.size(), released);
    }
}
