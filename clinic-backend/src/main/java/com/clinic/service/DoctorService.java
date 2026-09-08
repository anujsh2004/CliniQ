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
        doctor.setUser(resolveOwningUser(request.accountEmail()));

        return doctorMapper.toCreated(doctorRepository.saveAndFlush(doctor));
    }

    /**
     * Not cached, on measured evidence rather than principle.
     *
     * <p>tech-stack.md 3 nominates doctor reads for caching, which is sound
     * reasoning about a clinic with a large roster. Against the current data it
     * was the opposite: the load test put the cached list at a p95 of 624ms and
     * the uncached slot fetch at 334ms. The list is a trivial query over a
     * handful of rows, so a Redis round trip and a deserialisation cost more
     * than the query they replace. Worth revisiting when the roster is large
     * enough to change that; the Redis configuration stays in place so it is a
     * one-line change (decision D25).
     */
    @Transactional(readOnly = true)
    public PagedResponse<DoctorSummary> list(Pageable pageable) {
        return PagedResponse.from(doctorRepository.findAllBy(pageable), doctorMapper::toSummary);
    }

    /**
     * The profile belonging to the calling account (API contract 9, v1.2).
     *
     * <p>An account and a profile are separate records. Before this existed the
     * frontend matched on the account's name against the doctor list, which
     * picks the wrong profile when two doctors share a name and finds nothing
     * for a profile an administrator created.
     *
     * <p>Not cached: it is per-caller data, and the cache is shared.
     */
    @Transactional(readOnly = true)
    public DoctorResponse getOwnProfile() {
        UUID userId = CurrentUser.require().userId();
        return doctorMapper.toDetail(doctorRepository.findWithClinicByUserId(userId)
                .orElseThrow(DoctorNotFoundException::new));
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
    /**
     * Decides which account owns the new profile.
     *
     * <p>A doctor creating their own profile is linked to it. An administrator
     * names the account with {@code accountEmail}; without it the profile has no
     * account, and no doctor can manage its availability (decision D3).
     */
    private User resolveOwningUser(String accountEmail) {
        AuthenticatedUser caller = CurrentUser.require();

        if (caller.role() == Role.DOCTOR) {
            if (doctorRepository.findByUserId(caller.userId()).isPresent()) {
                throw new FieldValidationException("licenseNumber",
                        "This account already has a doctor profile");
            }
            return userRepository.findById(caller.userId()).orElse(null);
        }

        if (accountEmail == null || accountEmail.isBlank()) {
            return null;
        }

        User account = userRepository.findByEmailIgnoreCase(accountEmail.trim())
                .orElseThrow(() -> new FieldValidationException("accountEmail",
                        "No account with this email. Create one first."));
        if (account.getRole() != Role.DOCTOR) {
            throw new FieldValidationException("accountEmail",
                    "That account is not a doctor account");
        }
        if (doctorRepository.findByUserId(account.getId()).isPresent()) {
            throw new FieldValidationException("accountEmail",
                    "That account already has a doctor profile");
        }
        return account;
    }
}
