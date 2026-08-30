package com.clinic.service;

import com.clinic.dto.request.CreateAvailabilityRequest;
import com.clinic.dto.response.AvailabilityResponse;
import com.clinic.dto.response.DoctorSlotsResponse;
import com.clinic.dto.response.SlotSummary;
import com.clinic.entity.Doctor;
import com.clinic.entity.DoctorAvailability;
import com.clinic.entity.Role;
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
import java.util.List;
import java.util.UUID;

/**
 * Doctor availability and slot fetch (API contract 11).
 */
@Service
public class AvailabilityService {

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
        boolean overlaps = availabilityRepository
                .findByDoctorIdAndDayOfWeek(doctorId, request.dayOfWeek()).stream()
                .anyMatch(existing -> request.startTime().isBefore(existing.getEndTime())
                        && existing.getStartTime().isBefore(request.endTime()));
        if (overlaps) {
            throw new FieldValidationException("startTime",
                    "This window overlaps availability already defined for that day");
        }
    }
}
