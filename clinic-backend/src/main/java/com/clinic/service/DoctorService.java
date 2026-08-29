package com.clinic.service;

import com.clinic.dto.request.ClinicRequest;
import com.clinic.dto.request.CreateDoctorRequest;
import com.clinic.dto.response.DoctorResponse;
import com.clinic.dto.response.DoctorSummary;
import com.clinic.dto.response.PagedResponse;
import com.clinic.entity.Clinic;
import com.clinic.entity.Doctor;
import com.clinic.entity.Role;
import com.clinic.entity.User;
import com.clinic.exception.DoctorNotFoundException;
import com.clinic.exception.FieldValidationException;
import com.clinic.mapper.DoctorMapper;
import com.clinic.repository.ClinicRepository;
import com.clinic.repository.DoctorRepository;
import com.clinic.repository.UserRepository;
import com.clinic.security.AuthenticatedUser;
import com.clinic.security.CurrentUser;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Doctor profile and clinic management (API contract 9).
 */
@Service
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final ClinicRepository clinicRepository;
    private final UserRepository userRepository;
    private final DoctorMapper doctorMapper;

    public DoctorService(DoctorRepository doctorRepository, ClinicRepository clinicRepository,
                         UserRepository userRepository, DoctorMapper doctorMapper) {
        this.doctorRepository = doctorRepository;
        this.clinicRepository = clinicRepository;
        this.userRepository = userRepository;
        this.doctorMapper = doctorMapper;
    }

    @Transactional
    public DoctorResponse create(CreateDoctorRequest request) {
        if (doctorRepository.existsByLicenseNumberIgnoreCase(request.licenseNumber())) {
            throw new FieldValidationException("licenseNumber", "License number is already registered");
        }

        Doctor doctor = new Doctor();
        doctor.setClinic(resolveClinic(request.clinic()));
        doctor.setName(request.name().trim());
        doctor.setSpecialization(request.specialization().trim());
        doctor.setLicenseNumber(request.licenseNumber().trim());
        doctor.setConsultationFee(request.consultationFee());
        doctor.setUser(resolveOwningUser());

        return doctorMapper.toCreated(doctorRepository.saveAndFlush(doctor));
    }

    @Transactional(readOnly = true)
    public PagedResponse<DoctorSummary> list(Pageable pageable) {
        return PagedResponse.from(doctorRepository.findAllBy(pageable), doctorMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public DoctorResponse get(UUID doctorId) {
        return doctorMapper.toDetail(doctorRepository.findWithClinicById(doctorId)
                .orElseThrow(DoctorNotFoundException::new));
    }

    /**
     * Returns the doctor profile belonging to the caller, for the
     * {@code /doctors/me} endpoints.
     */
    @Transactional(readOnly = true)
    public Doctor requireOwnProfile() {
        return doctorRepository.findByUserId(CurrentUser.require().userId())
                .orElseThrow(DoctorNotFoundException::new);
    }

    /**
     * Several doctors at the same clinic send the same clinic block, so an
     * existing clinic with that name and address is reused rather than
     * duplicated.
     */
    private Clinic resolveClinic(ClinicRequest request) {
        String name = request.name().trim();
        String address = request.address().trim();
        return clinicRepository.findByNameAndAddressIgnoringCase(name, address)
                .orElseGet(() -> {
                    Clinic clinic = new Clinic();
                    clinic.setName(name);
                    clinic.setAddress(address);
                    clinic.setPhone(request.phone());
                    return clinicRepository.save(clinic);
                });
    }

    /**
     * A DOCTOR creating their own profile is linked to it immediately, which is
     * what makes /doctors/me work for them. An ADMIN setting up the roster
     * creates an unlinked profile, since the contract's payload carries no way
     * to name the account it belongs to.
     */
    private User resolveOwningUser() {
        AuthenticatedUser caller = CurrentUser.require();
        if (caller.role() != Role.DOCTOR) {
            return null;
        }
        if (doctorRepository.findByUserId(caller.userId()).isPresent()) {
            throw new FieldValidationException("licenseNumber", "This account already has a doctor profile");
        }
        return userRepository.findById(caller.userId()).orElse(null);
    }
}
