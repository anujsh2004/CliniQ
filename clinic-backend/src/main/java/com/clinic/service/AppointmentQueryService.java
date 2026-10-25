package com.clinic.service;

import com.clinic.dto.response.AppointmentDetailResponse;
import com.clinic.dto.response.AppointmentListItem;
import com.clinic.dto.response.AppointmentStatusResponse;
import com.clinic.dto.response.ConditionHints;
import com.clinic.dto.response.DoctorDayAppointment;
import com.clinic.dto.response.DoctorDayAppointmentsResponse;
import com.clinic.dto.response.NoShowRisk;
import com.clinic.dto.response.PagedResponse;
import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import com.clinic.entity.Doctor;
import com.clinic.entity.Role;
import com.clinic.exception.ApiException;
import com.clinic.exception.AppointmentNotFoundException;
import com.clinic.exception.ErrorCode;
import com.clinic.exception.FieldValidationException;
import com.clinic.mapper.AppointmentMapper;
import com.clinic.ml.ConditionHintService;
import com.clinic.ml.NoShowRiskService;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.DoctorRepository;
import com.clinic.security.AuthenticatedUser;
import com.clinic.security.CurrentUser;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reading appointments and closing them out (API contract 12 and 13).
 *
 * <p>This is the read and validation half of the appointment module. Anything
 * that changes a slot's status - booking, cancelling, rescheduling - lives in
 * the booking service instead, per the ownership split in API contract 22.
 *
 * <p>Every method here answers the same question first: may this caller see
 * this appointment? A role check alone cannot express that, so ownership is
 * checked per record.
 */
@Service
public class AppointmentQueryService {

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final PatientService patientService;
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Kolkata");

    private final AppointmentMapper appointmentMapper;
    private final NoShowRiskService noShowRiskService;
    private final ConditionHintService conditionHintService;

    public AppointmentQueryService(AppointmentRepository appointmentRepository,
                                   DoctorRepository doctorRepository,
                                   PatientService patientService,
                                   AppointmentMapper appointmentMapper,
                                   NoShowRiskService noShowRiskService,
                                   ConditionHintService conditionHintService) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.patientService = patientService;
        this.appointmentMapper = appointmentMapper;
        this.noShowRiskService = noShowRiskService;
        this.conditionHintService = conditionHintService;
    }

    @Transactional
    public AppointmentDetailResponse get(UUID appointmentId) {
        Appointment appointment = appointmentRepository.findWithDetailsById(appointmentId)
                .orElseThrow(AppointmentNotFoundException::new);
        requireCanView(appointment);
        return appointmentMapper.toDetail(appointment);
    }

    @Transactional
    public PagedResponse<AppointmentListItem> listOwn(Pageable pageable) {
        UUID patientId = patientService.requireOwnPatient().getId();
        return PagedResponse.from(
                appointmentRepository.findByPatientIdOrderByCreatedAtDesc(patientId, pageable),
                appointmentMapper::toListItem);
    }

    @Transactional(readOnly = true)
    public DoctorDayAppointmentsResponse listOwnDay(LocalDate date) {
        Doctor doctor = requireOwnDoctorProfile();
        List<Appointment> found = appointmentRepository.findForDoctorOnDate(doctor.getId(), date);
        // Empty whenever the ML service is off or failing, which leaves the
        // response exactly as it was before v1.6.
        Map<UUID, NoShowRisk> risks = noShowRiskService.score(date, found);
        Map<UUID, ConditionHints> hints = conditionHintService.hints(found);
        List<DoctorDayAppointment> appointments = found.stream()
                .map(appointment -> appointmentMapper.toDayAppointment(appointment,
                        risks.get(appointment.getId()), hints.get(appointment.getId())))
                .toList();
        // date once, at the top level, as with the slot response.
        return new DoctorDayAppointmentsResponse(date, appointments);
    }

    /**
     * Marks a visit finished. This changes appointment status only - the slot
     * has already been consumed by the visit - so it belongs on this side of
     * the ownership split.
     */
    @Transactional
    public AppointmentStatusResponse complete(UUID appointmentId) {
        Appointment appointment = appointmentRepository.findWithDetailsById(appointmentId)
                .orElseThrow(AppointmentNotFoundException::new);
        requireIsTreatingDoctor(appointment);

        if (!appointment.getStatus().isActive()) {
            throw new FieldValidationException("status",
                    "Only a pending or confirmed appointment can be completed");
        }

        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointmentRepository.save(appointment);

        // A completed visit is what makes the patient eligible for the
        // post-visit follow-up the AI phase will send.
        return new AppointmentStatusResponse(appointment.getId().toString(), appointment.getStatus(), true);
    }

    /**
     * Records that the patient did not come (v1.6).
     *
     * <p>Before this nothing could set {@code NO_SHOW}, so a missed visit was
     * indistinguishable from one nobody closed out - and the no-show model's
     * strongest per-patient signal, their history of missing visits, was always
     * zero. Only a confirmed appointment whose start time has passed qualifies:
     * an unpaid hold is cancelled by the sweep instead, and nobody has missed
     * an appointment that has not begun.
     */
    @Transactional
    public AppointmentStatusResponse markNoShow(UUID appointmentId) {
        Appointment appointment = appointmentRepository.findWithDetailsById(appointmentId)
                .orElseThrow(AppointmentNotFoundException::new);
        requireIsTreatingDoctor(appointment);

        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new FieldValidationException("status",
                    "Only a confirmed appointment can be marked as a no-show");
        }
        LocalDateTime start = LocalDateTime.of(appointment.getSlot().getDate(), appointment.getSlot().getStartTime());
        if (start.isAfter(LocalDateTime.now(CLINIC_ZONE))) {
            throw new FieldValidationException("status",
                    "An appointment cannot be marked as a no-show before it starts");
        }

        appointment.setStatus(AppointmentStatus.NO_SHOW);
        appointmentRepository.save(appointment);
        return AppointmentStatusResponse.of(appointment.getId().toString(), appointment.getStatus());
    }

    /**
     * A patient may see their own appointments; the treating doctor may see
     * theirs; an admin oversees the clinic. Anyone else gets 403.
     */
    private void requireCanView(Appointment appointment) {
        AuthenticatedUser caller = CurrentUser.require();
        if (caller.role() == Role.ADMIN) {
            return;
        }
        if (caller.role() == Role.PATIENT) {
            if (!appointment.getPatient().getUser().getId().equals(caller.userId())) {
                throw new ApiException(ErrorCode.UNAUTHORIZED_ACCESS);
            }
            return;
        }
        requireIsTreatingDoctor(appointment);
    }

    private void requireIsTreatingDoctor(Appointment appointment) {
        AuthenticatedUser caller = CurrentUser.require();
        if (caller.role() == Role.ADMIN) {
            return;
        }
        Doctor doctor = appointment.getDoctor();
        boolean isTreatingDoctor = doctor.getUser() != null
                && doctor.getUser().getId().equals(caller.userId());
        if (!isTreatingDoctor) {
            throw new ApiException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
    }

    private Doctor requireOwnDoctorProfile() {
        return doctorRepository.findByUserId(CurrentUser.require().userId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED_ACCESS,
                        "This account is not linked to a doctor profile"));
    }
}
