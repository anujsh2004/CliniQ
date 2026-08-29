package com.clinic.service;

import com.clinic.entity.Doctor;
import com.clinic.entity.DoctorAvailability;
import com.clinic.entity.Slot;
import com.clinic.entity.SlotStatus;
import com.clinic.repository.DoctorAvailabilityRepository;
import com.clinic.repository.SlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns recurring availability into concrete bookable slots
 * (tech-stack.md 2, Background jobs).
 *
 * <p>Generation is idempotent: a slot that already exists for a doctor at a
 * date and start time is left alone, so re-running the job never disturbs a
 * slot that has since been booked.
 */
@Service
public class SlotGenerationService {

    private static final Logger log = LoggerFactory.getLogger(SlotGenerationService.class);

    private final SlotRepository slotRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final SlotProperties slotProperties;

    public SlotGenerationService(SlotRepository slotRepository,
                                 DoctorAvailabilityRepository availabilityRepository,
                                 SlotProperties slotProperties) {
        this.slotRepository = slotRepository;
        this.availabilityRepository = availabilityRepository;
        this.slotProperties = slotProperties;
    }

    /**
     * Materialises slots for one availability block across the whole horizon.
     * Called when a doctor defines availability, so slots are bookable
     * immediately rather than after the next nightly run.
     */
    @Transactional
    public int generateFor(DoctorAvailability availability) {
        LocalDate from = LocalDate.now();
        LocalDate until = from.plusDays(slotProperties.generationHorizonDays());
        List<Slot> created = new ArrayList<>();

        for (LocalDate date = from; !date.isAfter(until); date = date.plusDays(1)) {
            if (date.getDayOfWeek() != availability.getDayOfWeek()) {
                continue;
            }
            created.addAll(slotsForDate(availability, date));
        }

        slotRepository.saveAll(created);
        return created.size();
    }

    /**
     * Nightly top-up so the bookable window keeps rolling forward, and cleanup
     * of slots whose time has passed.
     */
    @Scheduled(cron = "${clinic.slots.generation-cron:0 30 1 * * *}")
    @Transactional
    public void generateRollingWindow() {
        int created = 0;
        for (DoctorAvailability availability : availabilityRepository.findAll()) {
            created += generateFor(availability);
        }
        int expired = slotRepository.expirePastAvailableSlots(LocalDate.now(), LocalTime.now());
        log.info("Slot maintenance complete: {} slots created, {} expired", created, expired);
    }

    private List<Slot> slotsForDate(DoctorAvailability availability, LocalDate date) {
        Doctor doctor = availability.getDoctor();
        List<Slot> slots = new ArrayList<>();
        LocalTime cursor = availability.getStartTime();
        LocalTime dayEnd = availability.getEndTime();
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        while (true) {
            LocalTime slotEnd = cursor.plusMinutes(availability.getSlotDurationMinutes());
            // A trailing remainder shorter than one slot is dropped rather than
            // offered as a short appointment.
            if (slotEnd.isAfter(dayEnd) || slotEnd.equals(cursor)) {
                break;
            }
            boolean alreadyPast = date.isEqual(today) && !cursor.isAfter(now);
            if (!alreadyPast && !slotRepository.existsByDoctorIdAndDateAndStartTime(doctor.getId(), date, cursor)) {
                Slot slot = new Slot();
                slot.setDoctor(doctor);
                slot.setDate(date);
                slot.setStartTime(cursor);
                slot.setEndTime(slotEnd);
                slot.setStatus(SlotStatus.AVAILABLE);
                slots.add(slot);
            }
            cursor = slotEnd;
            if (!cursor.isBefore(dayEnd)) {
                break;
            }
        }
        return slots;
    }
}
