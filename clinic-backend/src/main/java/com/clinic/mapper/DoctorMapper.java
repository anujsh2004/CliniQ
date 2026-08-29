package com.clinic.mapper;

import com.clinic.dto.response.ClinicSummary;
import com.clinic.dto.response.DoctorResponse;
import com.clinic.dto.response.DoctorSummary;
import com.clinic.entity.Clinic;
import com.clinic.entity.Doctor;
import org.springframework.stereotype.Component;

/**
 * Entity to DTO mapping for doctors. Entities are never returned directly
 * (API contract 19).
 */
@Component
public class DoctorMapper {

    /** Detail view: full clinic block including the clinic's phone number. */
    public DoctorResponse toDetail(Doctor doctor) {
        Clinic clinic = doctor.getClinic();
        return new DoctorResponse(
                doctor.getId().toString(),
                doctor.getName(),
                doctor.getSpecialization(),
                doctor.getConsultationFee(),
                new ClinicSummary(clinic.getId().toString(), clinic.getName(), clinic.getAddress(),
                        clinic.getPhone()));
    }

    /** Creation response: clinic block without the phone number, per contract 9. */
    public DoctorResponse toCreated(Doctor doctor) {
        Clinic clinic = doctor.getClinic();
        return new DoctorResponse(
                doctor.getId().toString(),
                doctor.getName(),
                doctor.getSpecialization(),
                doctor.getConsultationFee(),
                ClinicSummary.withoutPhone(clinic.getId().toString(), clinic.getName(), clinic.getAddress()));
    }

    /** List item: no clinic block at all, per contract 9. */
    public DoctorSummary toSummary(Doctor doctor) {
        return new DoctorSummary(
                doctor.getId().toString(),
                doctor.getName(),
                doctor.getSpecialization(),
                doctor.getConsultationFee());
    }
}
