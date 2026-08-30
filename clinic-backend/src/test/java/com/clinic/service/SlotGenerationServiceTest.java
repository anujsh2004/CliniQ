package com.clinic.service;

import com.clinic.entity.Doctor;
import com.clinic.entity.DoctorAvailability;
import com.clinic.entity.Slot;
import com.clinic.entity.SlotStatus;
import com.clinic.repository.DoctorAvailabilityRepository;
import com.clinic.repository.SlotRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlotGenerationServiceTest {

    private final SlotRepository slotRepository = mock(SlotRepository.class);
    private final DoctorAvailabilityRepository availabilityRepository = mock(DoctorAvailabilityRepository.class);

    private SlotGenerationService service(int horizonDays) {
        return new SlotGenerationService(slotRepository, availabilityRepository, new SlotProperties(horizonDays));
    }

    private DoctorAvailability availability(DayOfWeek day, LocalTime start, LocalTime end, int minutes) {
        Doctor doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        DoctorAvailability availability = new DoctorAvailability();
        availability.setDoctor(doctor);
        availability.setDayOfWeek(day);
        availability.setStartTime(start);
        availability.setEndTime(end);
        availability.setSlotDurationMinutes(minutes);
        return availability;
    }

    @SuppressWarnings("unchecked")
    private List<Slot> captureSaved() {
        ArgumentCaptor<List<Slot>> captor = ArgumentCaptor.forClass(List.class);
        verify(slotRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void generatesOneSlotPerDurationAcrossTheWindow() {
        when(slotRepository.existsByDoctorIdAndDateAndStartTime(any(), any(), any())).thenReturn(false);
        // A horizon of 0 days keeps the assertion to a single date: tomorrow is
        // never reached, and only the matching weekday produces slots.
        LocalDate today = LocalDate.now();
        DoctorAvailability availability =
                availability(today.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(11, 0), 30);

        service(0).generateFor(availability);

        List<Slot> saved = captureSaved();
        // Slots at or before the current time are skipped, so assert on shape
        // rather than count: every generated slot is a 30-minute AVAILABLE
        // window inside 09:00-11:00 and starts in the future.
        assertThat(saved).allSatisfy(slot -> {
            assertThat(slot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
            assertThat(slot.getStartTime()).isAfterOrEqualTo(LocalTime.of(9, 0));
            assertThat(slot.getEndTime()).isBeforeOrEqualTo(LocalTime.of(11, 0));
            assertThat(java.time.Duration.between(slot.getStartTime(), slot.getEndTime()).toMinutes())
                    .isEqualTo(30);
        });
    }

    @Test
    void dropsATrailingRemainderShorterThanOneSlot() {
        when(slotRepository.existsByDoctorIdAndDateAndStartTime(any(), any(), any())).thenReturn(false);
        // Three days out, with a horizon that reaches exactly that far. A date
        // a week out shares today's weekday, so a horizon covering it also
        // covers today, and whether today produced slots as well would depend
        // on the time of day the test happened to run.
        LocalDate target = LocalDate.now().plusDays(3);
        // 09:00-10:10 at 30 minutes: two whole slots, and the last 10 minutes
        // must not be offered as a short appointment.
        DoctorAvailability availability =
                availability(target.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(10, 10), 30);

        service(3).generateFor(availability);

        List<Slot> saved = captureSaved();
        assertThat(saved).extracting(Slot::getStartTime)
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(9, 30));
        assertThat(saved).extracting(Slot::getEndTime)
                .containsExactly(LocalTime.of(9, 30), LocalTime.of(10, 0));
    }

    @Test
    void neverRegeneratesASlotThatAlreadyExists() {
        // Idempotence matters: the nightly job re-runs over availability whose
        // slots may already be booked, and must not disturb them.
        when(slotRepository.existsByDoctorIdAndDateAndStartTime(any(), any(), any())).thenReturn(true);
        LocalDate nextWeek = LocalDate.now().plusDays(7);

        service(8).generateFor(availability(nextWeek.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(17, 0), 30));

        assertThat(captureSaved()).isEmpty();
    }

    @Test
    void generatesNothingForAWeekdayOutsideTheHorizon() {
        when(slotRepository.existsByDoctorIdAndDateAndStartTime(any(), any(), any())).thenReturn(false);
        LocalDate farOff = LocalDate.now().plusDays(5);

        // Horizon of one day cannot reach a weekday five days out.
        service(1).generateFor(availability(farOff.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(17, 0), 30));

        assertThat(captureSaved()).isEmpty();
    }

    @Test
    void nightlyRunAlsoExpiresSlotsWhoseTimeHasPassed() {
        when(availabilityRepository.findAll()).thenReturn(List.of());

        service(30).generateRollingWindow();

        verify(slotRepository).expirePastAvailableSlots(any(), any());
    }
}
