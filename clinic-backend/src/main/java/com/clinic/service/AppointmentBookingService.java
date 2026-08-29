package com.clinic.service;

import com.clinic.dto.request.CancelAppointmentRequest;
import com.clinic.dto.request.CreateAppointmentRequest;
import com.clinic.dto.request.RescheduleAppointmentRequest;
import com.clinic.dto.response.AppointmentCreatedResponse;
import com.clinic.dto.response.AppointmentRescheduledResponse;
import com.clinic.dto.response.AppointmentStatusResponse;
import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import com.clinic.entity.Doctor;
import com.clinic.entity.Patient;
import com.clinic.entity.PaymentStatus;
import com.clinic.entity.Role;
import com.clinic.entity.Slot;
import com.clinic.entity.SlotStatus;
import com.clinic.exception.ApiException;
import com.clinic.exception.AppointmentNotFoundException;
import com.clinic.exception.DoctorNotFoundException;
import com.clinic.exception.ErrorCode;
import com.clinic.exception.FieldValidationException;
import com.clinic.exception.SlotAlreadyBookedException;
import com.clinic.exception.SlotNotFoundException;
import com.clinic.mapper.AppointmentMapper;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.DoctorRepository;
import com.clinic.repository.SlotRepository;
import com.clinic.security.AuthenticatedUser;
import com.clinic.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Booking, cancellation and rescheduling - everything that changes a slot's
 * status (API contract 12, ownership per contract 22).
 *
 * <p>The product's one non-negotiable guarantee is that a slot is never booked
 * twice (product-description.md G1, NFR-1). Three mechanisms enforce it, in
 * order of who acts first:
 *
 * <ol>
 *   <li>every slot read on a write path takes a row-level write lock
 *       ({@code SELECT ... FOR UPDATE}), so concurrent bookings for one slot
 *       serialise rather than interleave;</li>
 *   <li>the status is re-checked while that lock is held, so the loser of the
 *       race sees BOOKED and is rejected with SLOT_ALREADY_BOOKED;</li>
 *   <li>a partial unique index on appointments (one live appointment per slot)
 *       backs both up, so even a code path that forgot the lock cannot corrupt
 *       the data.</li>
 * </ol>
 *
 * <p>Under contention this rejects rather than queues, which is the trade the
 * product asks for: consistency over availability (NFR-1).
 */
@Service
public class AppointmentBookingService {

    private static final List<AppointmentStatus> LIVE_STATUSES =
            List.of(AppointmentStatus.PENDING_PAYMENT, AppointmentStatus.CONFIRMED);

    private final AppointmentRepository appointmentRepository;
    private final SlotRepository slotRepository;
    private final DoctorRepository doctorRepository;
    private final PatientService patientService;
    private final AppointmentMapper appointmentMapper;

    public AppointmentBookingService(AppointmentRepository appointmentRepository,
                                     SlotRepository slotRepository,
                                     DoctorRepository doctorRepository,
                                     PatientService patientService,
                                     AppointmentMapper appointmentMapper) {
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
        this.doctorRepository = doctorRepository;
        this.patientService = patientService;
        this.appointmentMapper = appointmentMapper;
    }

    /**
     * Books a slot for the calling patient. The appointment starts in
     * PENDING_PAYMENT and only becomes CONFIRMED once a verified payment
     * arrives (API contract 12 and 14).
     */
    @Transactional
    public AppointmentCreatedResponse create(CreateAppointmentRequest request) {
        Patient patient = patientService.requireOwnPatient();
        Doctor doctor = doctorRepository.findById(request.doctorId())
                .orElseThrow(DoctorNotFoundException::new);

        Slot slot = lockSlot(request.slotId());
        requireSlotBelongsTo(slot, doctor);
        requireSlotIsBookable(slot);

        slot.setStatus(SlotStatus.BOOKED);
        slotRepository.save(slot);

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setSlot(slot);
        appointment.setStatus(AppointmentStatus.PENDING_PAYMENT);
        appointment.setPaymentStatus(PaymentStatus.PENDING);
        appointment.setReason(request.reason());

        try {
            return appointmentMapper.toCreated(appointmentRepository.saveAndFlush(appointment));
        } catch (DataIntegrityViolationException ex) {
            // The partial unique index refused a second live appointment for
            // this slot. Report it as the race it is, not as a server error.
            throw new SlotAlreadyBookedException();
        }
    }

    /**
     * Cancels the caller's appointment and releases the slot so someone else
     * can take it (API contract 12).
     */
    @Transactional
    public AppointmentStatusResponse cancel(UUID appointmentId, CancelAppointmentRequest request) {
        Appointment appointment = appointmentRepository.findWithDetailsById(appointmentId)
                .orElseThrow(AppointmentNotFoundException::new);
        requireOwnAppointment(appointment);
        requireStillLive(appointment);

        // Lock before touching status: a concurrent booking attempt on this
        // slot must not interleave with its release.
        Slot slot = lockSlot(appointment.getSlot().getId());
        releaseSlot(slot);

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancellationReason(request.reason().trim());
        appointmentRepository.save(appointment);

        return AppointmentStatusResponse.of(appointment.getId().toString(), appointment.getStatus());
    }

    /**
     * Moves an appointment to a different slot. The old slot is released and
     * the new one booked inside one transaction, so the appointment is never
     * holding two slots or none (API contract 12).
     */
    @Transactional
    public AppointmentRescheduledResponse reschedule(UUID appointmentId,
                                                     RescheduleAppointmentRequest request) {
        Appointment appointment = appointmentRepository.findWithDetailsById(appointmentId)
                .orElseThrow(AppointmentNotFoundException::new);
        requireOwnAppointment(appointment);
        requireStillLive(appointment);

        UUID currentSlotId = appointment.getSlot().getId();
        UUID newSlotId = request.newSlotId();
        if (currentSlotId.equals(newSlotId)) {
            throw new FieldValidationException("newSlotId", "The appointment is already in this slot");
        }

        // Both slots are locked, always in the same order, so two reschedules
        // swapping the same pair of slots cannot deadlock against each other.
        Slot first;
        Slot second;
        if (currentSlotId.compareTo(newSlotId) < 0) {
            first = lockSlot(currentSlotId);
            second = lockSlot(newSlotId);
        } else {
            second = lockSlot(newSlotId);
            first = lockSlot(currentSlotId);
        }
        Slot currentSlot = first.getId().equals(currentSlotId) ? first : second;
        Slot newSlot = first.getId().equals(newSlotId) ? first : second;

        requireSlotBelongsTo(newSlot, appointment.getDoctor());
        requireSlotIsBookable(newSlot);

        releaseSlot(currentSlot);
        newSlot.setStatus(SlotStatus.BOOKED);
        slotRepository.save(newSlot);

        appointment.setSlot(newSlot);
        try {
            appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException ex) {
            throw new SlotAlreadyBookedException();
        }

        return appointmentMapper.toRescheduled(appointment);
    }

    private Slot lockSlot(UUID slotId) {
        return slotRepository.findByIdForUpdate(slotId).orElseThrow(SlotNotFoundException::new);
    }

    private void requireSlotBelongsTo(Slot slot, Doctor doctor) {
        if (!slot.getDoctor().getId().equals(doctor.getId())) {
            throw new FieldValidationException("slotId", "This slot does not belong to the selected doctor");
        }
    }

    /**
     * Decided while holding the slot's write lock, so the answer cannot go
     * stale between the check and the update.
     */
    private void requireSlotIsBookable(Slot slot) {
        switch (slot.getStatus()) {
            case BOOKED, HELD -> throw new SlotAlreadyBookedException();
            case BLOCKED -> throw new SlotNotFoundException("This slot is not open for booking");
            case EXPIRED -> throw new SlotNotFoundException("This slot has expired");
            case AVAILABLE -> {
                if (isInThePast(slot)) {
                    throw new SlotNotFoundException("This slot is in the past");
                }
            }
        }
    }

    /**
     * A released slot goes back on the market only if it is still in the
     * future; a past slot becomes EXPIRED so it can never be rebooked.
     */
    private void releaseSlot(Slot slot) {
        slot.setStatus(isInThePast(slot) ? SlotStatus.EXPIRED : SlotStatus.AVAILABLE);
        slotRepository.save(slot);
    }

    private boolean isInThePast(Slot slot) {
        LocalDate today = LocalDate.now();
        return slot.getDate().isBefore(today)
                || (slot.getDate().isEqual(today) && !slot.getStartTime().isAfter(LocalTime.now()));
    }

    private void requireStillLive(Appointment appointment) {
        if (!appointment.getStatus().isActive()) {
            throw new FieldValidationException("status",
                    "This appointment is already " + appointment.getStatus().name().toLowerCase());
        }
    }

    /**
     * A patient may only act on their own appointment (API contract 19). An
     * admin acts on the clinic's behalf.
     */
    private void requireOwnAppointment(Appointment appointment) {
        AuthenticatedUser caller = CurrentUser.require();
        if (caller.role() == Role.ADMIN) {
            return;
        }
        boolean isOwnAppointment = appointment.getPatient().getUser().getId().equals(caller.userId());
        if (!isOwnAppointment) {
            throw new ApiException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
    }
}
