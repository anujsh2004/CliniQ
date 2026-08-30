package com.clinic.service;

import com.clinic.dto.response.AppointmentDetailResponse;
import com.clinic.dto.response.AppointmentListItem;
import com.clinic.dto.response.AppointmentStatusResponse;
import com.clinic.dto.response.DoctorDayAppointment;
import com.clinic.dto.response.DoctorDayAppointmentsResponse;
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
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.DoctorRepository;
import com.clinic.security.AuthenticatedUser;
import com.clinic.security.CurrentUser;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
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
    private final AppointmentMapper appointmentMapper;

    public AppointmentQueryService(AppointmentRepository appointmentRepository,
                                   DoctorRepository doctorRepository,
                                   PatientService patientService,
                                   AppointmentMapper appointmentMapper) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.patientService = patientService;
        this.appointmentMapper = appointmentMapper;
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
        List<DoctorDayAppointment> appointments =
                appointmentRepository.findForDoctorOnDate(doctor.getId(), date).stream()
                        .map(appointmentMapper::toDayAppointment)
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
