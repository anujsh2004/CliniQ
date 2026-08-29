package com.clinic.service;

import com.clinic.dto.request.UpdatePatientRequest;
import com.clinic.dto.response.PatientProfileResponse;
import com.clinic.dto.response.PatientUpdateResponse;
import com.clinic.entity.Patient;
import com.clinic.entity.User;
import com.clinic.exception.ApiException;
import com.clinic.exception.ErrorCode;
import com.clinic.exception.FieldValidationException;
import com.clinic.repository.PatientRepository;
import com.clinic.repository.UserRepository;
import com.clinic.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Patient profile APIs (API contract 10). Every operation is scoped to the
 * caller: a patient can only ever read or change their own profile.
 */
@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;

    public PatientService(PatientRepository patientRepository, UserRepository userRepository) {
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
    }

    // Not read-only: the patient row is materialised on first access.
    @Transactional
    public PatientProfileResponse getOwnProfile() {
        Patient patient = requireOwnPatient();
        User user = patient.getUser();
        return new PatientProfileResponse(patient.getId().toString(), user.getName(), user.getEmail(),
                user.getPhone());
    }

    @Transactional
    public PatientUpdateResponse updateOwnProfile(UpdatePatientRequest request) {
        Patient patient = requireOwnPatient();
        User user = patient.getUser();

        String phone = request.phone();
        if (!phone.equals(user.getPhone()) && userRepository.existsByPhone(phone)) {
            throw new FieldValidationException("phone", "Phone number is already registered");
        }

        user.setName(request.name().trim());
        user.setPhone(phone);
        userRepository.save(user);

        return new PatientUpdateResponse(patient.getId().toString(), user.getName(), user.getPhone());
    }

    /**
     * Returns the caller's patient record, creating it on first use.
     *
     * <p>Registration only creates a {@code User}; the patient row is
     * materialised the first time the account acts as a patient, which also
     * covers accounts that registered before this table existed.
     */
    @Transactional
    public Patient requireOwnPatient() {
        UUID userId = CurrentUser.require().userId();
        return patientRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED_ACCESS));
                    Patient patient = new Patient();
                    patient.setUser(user);
                    return patientRepository.saveAndFlush(patient);
                });
    }
}
