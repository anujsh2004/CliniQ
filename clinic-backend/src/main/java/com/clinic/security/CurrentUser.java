package com.clinic.security;

import com.clinic.exception.ApiException;
import com.clinic.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Reads the authenticated caller out of the security context. Service-layer
 * ownership checks ("is this the patient's own appointment?") start here, since
 * a role check alone cannot express them.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthenticatedUser require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        return user;
    }
}
