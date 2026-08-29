package com.clinic.service;

import com.clinic.dto.request.LoginRequest;
import com.clinic.dto.request.RefreshTokenRequest;
import com.clinic.dto.request.RegisterRequest;
import com.clinic.dto.response.AuthUserSummary;
import com.clinic.dto.response.LoginResponse;
import com.clinic.dto.response.RegisterResponse;
import com.clinic.dto.response.TokenRefreshResponse;
import com.clinic.entity.User;
import com.clinic.exception.DuplicateEmailException;
import com.clinic.exception.FieldValidationException;
import com.clinic.exception.InvalidCredentialsException;
import com.clinic.repository.UserRepository;
import com.clinic.security.AuthenticatedUser;
import com.clinic.security.JwtService;
import com.clinic.security.TokenType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, login and token refresh (API contract 8).
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.email().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException();
        }
        if (userRepository.existsByPhone(request.phone())) {
            throw new FieldValidationException("phone", "Phone number is already registered");
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPhone(request.phone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());

        try {
            User saved = userRepository.saveAndFlush(user);
            return new RegisterResponse(saved.getId().toString(), saved.getName(), saved.getEmail(),
                    saved.getPhone(), saved.getRole());
        } catch (DataIntegrityViolationException ex) {
            // Two registrations for the same email can pass the check above
            // concurrently; the unique constraint is what actually decides.
            throw new DuplicateEmailException();
        }
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return new LoginResponse(
                jwtService.issueAccessToken(user),
                jwtService.issueRefreshToken(user),
                jwtService.accessTokenSeconds(),
                new AuthUserSummary(user.getId().toString(), user.getName(), user.getRole()));
    }

    @Transactional(readOnly = true)
    public TokenRefreshResponse refresh(RefreshTokenRequest request) {
        AuthenticatedUser principal = jwtService.parse(request.refreshToken(), TokenType.REFRESH)
                .orElseThrow(InvalidCredentialsException::new);

        // The account must still exist, and the new access token is minted from
        // its current role rather than from the role frozen in the old token.
        User user = userRepository.findById(principal.userId())
                .orElseThrow(InvalidCredentialsException::new);

        return new TokenRefreshResponse(jwtService.issueAccessToken(user), jwtService.accessTokenSeconds());
    }
}
