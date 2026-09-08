package com.clinic.controller;

import com.clinic.dto.request.RegisterRequest;
import com.clinic.dto.response.ApiResponse;
import com.clinic.dto.response.RegisterResponse;
import com.clinic.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff account creation (API contract 8, v1.2).
 *
 * <p>This exists because self-registration no longer grants a role. Someone has
 * to be able to onboard doctors and further administrators, and that someone is
 * an administrator rather than anyone who can reach a public endpoint.
 */
@RestController
@RequestMapping("/api/v1/admin/accounts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccountController {

    private final AuthService authService;

    public AdminAccountController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RegisterResponse>> create(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Staff account created successfully",
                        authService.createStaffAccount(request)));
    }
}
