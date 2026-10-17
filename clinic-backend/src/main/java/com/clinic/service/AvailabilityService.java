package com.clinic.service;

import com.clinic.dto.request.CreateAvailabilityRequest;
import com.clinic.dto.response.AvailabilityResponse;
import com.clinic.dto.response.DoctorSlotsResponse;
import com.clinic.dto.response.SlotSummary;
import com.clinic.dto.response.TimeOffResponse;
import com.clinic.entity.Doctor;
import com.clinic.entity.DoctorAvailability;
import com.clinic.entity.Role;
import com.clinic.entity.Slot;
import com.clinic.entity.SlotStatus;
import com.clinic.exception.ApiException;
import com.clinic.exception.DoctorNotFoundException;
import com.clinic.exception.ErrorCode;
import com.clinic.exception.FieldValidationException;
import com.clinic.repository.DoctorAvailabilityRepository;
import com.clinic.repository.DoctorRepository;
import com.clinic.repository.SlotRepository;
import com.clinic.security.AuthenticatedUser;
import com.clinic.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Doctor availability and slot fetch (API contract 11).
 */
@Service
public class AvailabilityService {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Kolkata");

    private final DoctorRepository doctorRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final SlotRepository slotRepository;
    private final SlotGenerationService slotGenerationService;

    public AvailabilityService(DoctorRepository doctorRepository,
                               DoctorAvailabilityRepository availabilityRepository,
                               SlotRepository slotRepository,
                               SlotGenerationService slotGenerationService) {
        this.doctorRepository = doctorRepository;
        this.availabilityRepository = availabilityRepository;
        this.slotRepository = slotRepository;
        this.slotGenerationService = slotGenerationService;
    }

    @Transactional
    public AvailabilityResponse create(UUID doctorId, CreateAvailabilityRequest request) {
        Doctor doctor = doctorRepository.findById(doctorId).orElseThrow(DoctorNotFoundException::new);
        requireCanManage(doctor);
        validateWindow(request);
        rejectOverlap(doctorId, request);

        DoctorAvailability availability = new DoctorAvailability();
        availability.setDoctor(doctor);
        availability.setDayOfWeek(request.dayOfWeek());
        availability.setStartTime(request.startTime());
        availability.setEndTime(request.endTime());
        availability.setSlotDurationMinutes(request.slotDurationMinutes());
        DoctorAvailability saved = availabilityRepository.saveAndFlush(availability);

        // Materialise slots straight away so the doctor is bookable now rather
        // than after the next nightly run.
        slotGenerationService.generateFor(saved);

        return new AvailabilityResponse(saved.getId().toString(), doctor.getId().toString(),
                saved.getDayOfWeek(), saved.getStartTime(), saved.getEndTime(), saved.getSlotDurationMinutes());
    }

    /** Every window the doctor has defined (API contract 11, v1.3). */
    @Transactional(readOnly = true)
    public List<AvailabilityResponse> list(UUID doctorId) {
        if (!doctorRepository.existsById(doctorId)) {
            throw new DoctorNotFoundException();
        }
        return availabilityRepository.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Replaces a window (API contract 11, v1.3).
     *
     * <p>Overlap is checked against the doctor's <em>other</em> windows, so a
     * window can be edited without colliding with itself - the trap that would
     * make an edit feature feel broken the first time it is used.
     */
    @Transactional
    public AvailabilityResponse update(UUID doctorId, UUID availabilityId,
                                       CreateAvailabilityRequest request) {
        Doctor doctor = doctorRepository.findById(doctorId).orElseThrow(DoctorNotFoundException::new);
        requireCanManage(doctor);
        validateWindow(request);

        DoctorAvailability availability = requireOwnWindow(doctorId, availabilityId);
        rejectOverlap(doctorId, request, availabilityId);

        releaseFutureSlots(availability);

        availability.setDayOfWeek(request.dayOfWeek());
        availability.setStartTime(request.startTime());
        availability.setEndTime(request.endTime());
        availability.setSlotDurationMinutes(request.slotDurationMinutes());
        DoctorAvailability saved = availabilityRepository.saveAndFlush(availability);

        slotGenerationService.generateFor(saved);
        return toResponse(saved);
    }

    /**
     * Removes a window (API contract 11, v1.3).
     *
     * <p>Refuses while it still has appointments on it in the future. Silently
     * cancelling patients is never the right default, so the doctor is told to
     * deal with those appointments first.
     */
    @Transactional
    public void delete(UUID doctorId, UUID availabilityId) {
        Doctor doctor = doctorRepository.findById(doctorId).orElseThrow(DoctorNotFoundException::new);
        requireCanManage(doctor);
        DoctorAvailability availability = requireOwnWindow(doctorId, availabilityId);

        if (hasFutureBookings(availability)) {
            throw new ApiException(ErrorCode.SLOT_ALREADY_BOOKED,
                    "This window has booked appointments. Cancel or reschedule them first.");
        }
        releaseFutureSlots(availability);
        availabilityRepository.delete(availability);
    }

    /**
     * Blocks a single date (API contract 11, v1.3).
     *
     * <p>Booked slots are deliberately left alone and counted back to the
     * caller rather than cancelled on the patient's behalf.
     */
    @Transactional
    public TimeOffResponse blockDate(UUID doctorId, LocalDate date) {
        Doctor doctor = doctorRepository.findById(doctorId).orElseThrow(DoctorNotFoundException::new);
        requireCanManage(doctor);
        if (date.isBefore(LocalDate.now(CLINIC_ZONE))) {
            throw new FieldValidationException("date", "That date has already passed");
        }

        int blocked = 0;
        int stillBooked = 0;
        for (Slot slot : slotRepository.findByDoctorIdAndDate(doctorId, date)) {
            if (slot.getStatus() == SlotStatus.BOOKED || slot.getStatus() == SlotStatus.HELD) {
                stillBooked++;
            } else if (slot.getStatus() == SlotStatus.AVAILABLE) {
                slot.setStatus(SlotStatus.BLOCKED);
                slotRepository.save(slot);
                blocked++;
            }
        }
        return new TimeOffResponse(date, blocked, stillBooked);
    }

    @Transactional(readOnly = true)
    public DoctorSlotsResponse slotsFor(UUID doctorId, LocalDate date) {
        if (!doctorRepository.existsById(doctorId)) {
            throw new DoctorNotFoundException();
        }
        List<SlotSummary> slots = slotRepository.findByDoctorIdAndDateOrderByStartTime(doctorId, date).stream()
                .map(slot -> new SlotSummary(slot.getId().toString(), slot.getStartTime(), slot.getEndTime(),
                        slot.getStatus()))
                .toList();
        // date lives here, once, and never inside a slot object (contract 11).
        return new DoctorSlotsResponse(doctorId.toString(), date, slots);
    }

    /**
     * A doctor may only manage their own availability. An admin manages the
     * clinic's roster on the doctors' behalf.
     */
    private void requireCanManage(Doctor doctor) {
        AuthenticatedUser caller = CurrentUser.require();
        if (caller.role() == Role.ADMIN) {
            return;
        }
        boolean ownsProfile = doctor.getUser() != null && doctor.getUser().getId().equals(caller.userId());
        if (!ownsProfile) {
            throw new ApiException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
    }

    private AvailabilityResponse toResponse(DoctorAvailability availability) {
        return new AvailabilityResponse(availability.getId().toString(),
                availability.getDoctor().getId().toString(), availability.getDayOfWeek(),
                availability.getStartTime(), availability.getEndTime(),
                availability.getSlotDurationMinutes());
    }

    private DoctorAvailability requireOwnWindow(UUID doctorId, UUID availabilityId) {
        DoctorAvailability availability = availabilityRepository.findById(availabilityId)
                .orElseThrow(() -> new FieldValidationException("availabilityId",
                        "No such availability window"));
        if (!availability.getDoctor().getId().equals(doctorId)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        return availability;
    }

    /** Whether the window still has appointments on it in the future. */
    private boolean hasFutureBookings(DoctorAvailability availability) {
        return futureSlotsOf(availability).stream()
                .anyMatch(slot -> slot.getStatus() == SlotStatus.BOOKED
                        || slot.getStatus() == SlotStatus.HELD);
    }

    /**
     * Drops the future slots this window produced, so a changed window does not
     * leave its old slots on sale. Booked and held slots are never touched: a
     * patient's appointment is not the doctor's to delete from here.
     */
    private void releaseFutureSlots(DoctorAvailability availability) {
        List<Slot> stale = futureSlotsOf(availability).stream()
                .filter(slot -> slot.getStatus() == SlotStatus.AVAILABLE
                        || slot.getStatus() == SlotStatus.BLOCKED)
                .toList();
        slotRepository.deleteAll(stale);
    }

    private List<Slot> futureSlotsOf(DoctorAvailability availability) {
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        return slotRepository
                .findByDoctorIdAndDateGreaterThanEqual(availability.getDoctor().getId(), today).stream()
                .filter(slot -> slot.getDate().getDayOfWeek() == availability.getDayOfWeek())
                .filter(slot -> !slot.getStartTime().isBefore(availability.getStartTime())
                        && !slot.getEndTime().isAfter(availability.getEndTime()))
                .toList();
    }

    private void validateWindow(CreateAvailabilityRequest request) {
        if (!request.startTime().isBefore(request.endTime())) {
            throw new FieldValidationException("endTime", "End time must be after start time");
        }
        long windowMinutes = Duration.between(request.startTime(), request.endTime()).toMinutes();
        if (windowMinutes < request.slotDurationMinutes()) {
            throw new FieldValidationException("slotDurationMinutes",
                    "Slot duration is longer than the availability window");
        }
    }

    private void rejectOverlap(UUID doctorId, CreateAvailabilityRequest request) {
        rejectOverlap(doctorId, request, null);
    }

    /** {@code ignoring} lets an edited window avoid colliding with itself. */
    private void rejectOverlap(UUID doctorId, CreateAvailabilityRequest request, UUID ignoring) {
        boolean overlaps = availabilityRepository
                .findByDoctorIdAndDayOfWeek(doctorId, request.dayOfWeek()).stream()
                .filter(existing -> ignoring == null || !existing.getId().equals(ignoring))
                .anyMatch(existing -> request.startTime().isBefore(existing.getEndTime())
                        && existing.getStartTime().isBefore(request.endTime()));
        if (overlaps) {
            throw new FieldValidationException("startTime",
                    "This window overlaps availability already defined for that day");
        }
    }
}
