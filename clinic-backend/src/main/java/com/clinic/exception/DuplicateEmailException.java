package com.clinic.exception;

/**
 * Registration conflict: the email is already taken (API contract 7a).
 */
public class DuplicateEmailException extends ApiException {

    public DuplicateEmailException() {
        super(ErrorCode.DUPLICATE_EMAIL);
    }
}
