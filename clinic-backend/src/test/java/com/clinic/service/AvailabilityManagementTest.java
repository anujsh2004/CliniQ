package com.clinic.service;

import com.clinic.dto.request.CreateAvailabilityRequest;
import com.clinic.entity.Doctor;
import com.clinic.entity.DoctorAvailability;
import com.clinic.entity.Role;
import com.clinic.entity.Slot;
import com.clinic.entity.SlotStatus;
import com.clinic.entity.User;
import com.clinic.exception.ApiException;
import com.clinic.exception.ErrorCode;
import com.clinic.exception.FieldValidationException;
import com.clinic.repository.DoctorAvailabilityRepository;
import com.clinic.repository.DoctorRepository;
import com.clinic.repository.SlotRepository;
import com.clinic.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Managing availability after it has been created (API contract 11, v1.3).
 *
 * <p>Until v1.3 a doctor could only add windows. Adding one that overlapped was
 * refused, with no way to see, edit or remove whatever was in the way - an
 * error the doctor could read but not act on.
 */
class AvailabilityManagementTest {

    private final DoctorRepository doctorRepository = mock(DoctorRepository.class);
    private final DoctorAvailabilityRepository availabilityRepository =
            mock(DoctorAvailabilityRepository.class);
    private final SlotRepository slotRepository = mock(SlotRepository.class);
    private final SlotGenerationService slotGenerationService = mock(SlotGenerationService.class);

    private final AvailabilityService service = new AvailabilityService(
            doctorRepository, availabilityRepository, slotRepository, slotGenerationService);

    private final UUID doctorUserId = UUID.randomUUID();
    private Doctor doctor;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(doctorUserId);
        user.setName("Dr. Sharma");

        doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        doctor.setName("Dr. Sharma");
        doctor.setUser(user);

        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(doctorRepository.existsById(doctor.getId())).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(doctorUserId, "sharma@example.com", Role.DOCTOR), null, List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private DoctorAvailability window(DayOfWeek day, LocalTime start, LocalTime end) {
        DoctorAvailability availability = new DoctorAvailability();
        availability.setId(UUID.randomUUID());
        availability.setDoctor(doctor);
        availability.setDayOfWeek(day);
        availability.setStartTime(start);
        availability.setEndTime(end);
        availability.setSlotDurationMinutes(30);
        return availability;
    }

    private CreateAvailabilityRequest request(DayOfWeek day, LocalTime start, LocalTime end) {
        return new CreateAvailabilityRequest(day, start, end, 30);
    }

    private Slot slot(LocalDate date, LocalTime start, SlotStatus status) {
        Slot s = new Slot();
        s.setId(UUID.randomUUID());
        s.setDoctor(doctor);
        s.setDate(date);
        s.setStartTime(start);
        s.setEndTime(start.plusMinutes(30));
        s.setStatus(status);
        return s;
    }

    /** The next occurrence of a weekday, so tests never depend on today. */
    private LocalDate nextOccurrenceOf(DayOfWeek day) {
        LocalDate date = LocalDate.now().plusDays(1);
        while (date.getDayOfWeek() != day) {
            date = date.plusDays(1);
        }
        return date;
    }

    @Test
    void listsEveryWindowSoTheDoctorCanSeeWhatIsInTheWay() {
        when(availabilityRepository.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctor.getId()))
                .thenReturn(List.of(window(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)),
                        window(DayOfWeek.THURSDAY, LocalTime.of(14, 0), LocalTime.of(17, 0))));

        assertThat(service.list(doctor.getId())).hasSize(2);
    }

    @Test
    void aWindowCanBeEditedWithoutCollidingWithItself() {
        // The trap: editing Thursday 09:00-12:00 to 09:00-13:00 overlaps the
        // window being edited. Checking against itself would make every edit
        // impossible.
        DoctorAvailability existing = window(DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(12, 0));
        when(availabilityRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(availabilityRepository.findByDoctorIdAndDayOfWeek(doctor.getId(), DayOfWeek.THURSDAY))
                .thenReturn(List.of(existing));
        when(slotRepository.findByDoctorIdAndDateGreaterThanEqual(any(), any())).thenReturn(List.of());
        when(availabilityRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        var updated = service.update(doctor.getId(), existing.getId(),
                request(DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(13, 0)));

        assertThat(updated.endTime()).isEqualTo(LocalTime.of(13, 0));
    }

    @Test
    void anEditStillCollidesWithADifferentWindow() {
        DoctorAvailability edited = window(DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(12, 0));
        DoctorAvailability other = window(DayOfWeek.THURSDAY, LocalTime.of(14, 0), LocalTime.of(17, 0));
        when(availabilityRepository.findById(edited.getId())).thenReturn(Optional.of(edited));
        when(availabilityRepository.findByDoctorIdAndDayOfWeek(doctor.getId(), DayOfWeek.THURSDAY))
                .thenReturn(List.of(edited, other));

        assertThatThrownBy(() -> service.update(doctor.getId(), edited.getId(),
                request(DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(15, 0))))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void editingRegeneratesTheSlotsAndDropsTheStaleOnes() {
        DoctorAvailability existing = window(DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(12, 0));
        LocalDate thursday = nextOccurrenceOf(DayOfWeek.THURSDAY);
        when(availabilityRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(availabilityRepository.findByDoctorIdAndDayOfWeek(any(), any())).thenReturn(List.of(existing));
        when(slotRepository.findByDoctorIdAndDateGreaterThanEqual(any(), any()))
                .thenReturn(List.of(slot(thursday, LocalTime.of(9, 0), SlotStatus.AVAILABLE)));
        when(availabilityRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        service.update(doctor.getId(), existing.getId(),
                request(DayOfWeek.THURSDAY, LocalTime.of(10, 0), LocalTime.of(12, 0)));

        verify(slotRepository).deleteAll(any());
        verify(slotGenerationService).generateFor(existing);
    }

    @Test
    void aBookedSlotIsNeverDeletedByAnEdit() {
        // A patient's appointment is not the doctor's to remove from here.
        DoctorAvailability existing = window(DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(12, 0));
        LocalDate thursday = nextOccurrenceOf(DayOfWeek.THURSDAY);
        Slot booked = slot(thursday, LocalTime.of(9, 0), SlotStatus.BOOKED);
        when(availabilityRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(availabilityRepository.findByDoctorIdAndDayOfWeek(any(), any())).thenReturn(List.of(existing));
        when(slotRepository.findByDoctorIdAndDateGreaterThanEqual(any(), any())).thenReturn(List.of(booked));
        when(availabilityRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        service.update(doctor.getId(), existing.getId(),
                request(DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(11, 0)));

        ArgumentCaptor<Iterable<Slot>> deleted = ArgumentCaptor.captor();
        verify(slotRepository).deleteAll(deleted.capture());
        assertThat(deleted.getValue()).isEmpty();
    }

    @Test
    void deletingAWindowWithFutureBookingsIsRefused() {
        // Silently cancelling patients is never the right default.
        DoctorAvailability existing = window(DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(12, 0));
        LocalDate thursday = nextOccurrenceOf(DayOfWeek.THURSDAY);
        when(availabilityRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(slotRepository.findByDoctorIdAndDateGreaterThanEqual(any(), any()))
                .thenReturn(List.of(slot(thursday, LocalTime.of(9, 0), SlotStatus.BOOKED)));

        assertThatThrownBy(() -> service.delete(doctor.getId(), existing.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.SLOT_ALREADY_BOOKED);

        verify(availabilityRepository, never()).delete(any());
    }

    @Test
    void deletingAnEmptyWindowRemovesItAndItsFreeSlots() {
        DoctorAvailability existing = window(DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(12, 0));
        LocalDate thursday = nextOccurrenceOf(DayOfWeek.THURSDAY);
        when(availabilityRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(slotRepository.findByDoctorIdAndDateGreaterThanEqual(any(), any()))
                .thenReturn(List.of(slot(thursday, LocalTime.of(9, 0), SlotStatus.AVAILABLE)));

        assertThatCode(() -> service.delete(doctor.getId(), existing.getId())).doesNotThrowAnyException();

        verify(slotRepository).deleteAll(any());
        verify(availabilityRepository).delete(existing);
    }

    @Test
    void aDoctorCannotTouchAnotherDoctorsWindow() {
        Doctor other = new Doctor();
        other.setId(UUID.randomUUID());
        DoctorAvailability foreign = new DoctorAvailability();
        foreign.setId(UUID.randomUUID());
        foreign.setDoctor(other);
        when(availabilityRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.delete(doctor.getId(), foreign.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED_ACCESS);
    }

    @Test
    void blockingADateBlocksFreeSlotsAndReportsBookedOnes() {
        // The doctor needs to know who still has to be contacted.
        LocalDate date = LocalDate.now().plusDays(3);
        when(slotRepository.findByDoctorIdAndDate(doctor.getId(), date)).thenReturn(List.of(
                slot(date, LocalTime.of(9, 0), SlotStatus.AVAILABLE),
                slot(date, LocalTime.of(9, 30), SlotStatus.AVAILABLE),
                slot(date, LocalTime.of(10, 0), SlotStatus.BOOKED)));

        var response = service.blockDate(doctor.getId(), date);

        assertThat(response.slotsBlocked()).isEqualTo(2);
        assertThat(response.appointmentsToReschedule()).isEqualTo(1);
    }

    @Test
    void aBookedSlotSurvivesTimeOff() {
        LocalDate date = LocalDate.now().plusDays(3);
        Slot booked = slot(date, LocalTime.of(10, 0), SlotStatus.BOOKED);
        when(slotRepository.findByDoctorIdAndDate(doctor.getId(), date)).thenReturn(List.of(booked));

        service.blockDate(doctor.getId(), date);

        assertThat(booked.getStatus()).isEqualTo(SlotStatus.BOOKED);
    }

    @Test
    void timeOffCannotBeTakenInThePast() {
        assertThatThrownBy(() -> service.blockDate(doctor.getId(), LocalDate.now().minusDays(1)))
                .isInstanceOf(FieldValidationException.class);
    }
}
