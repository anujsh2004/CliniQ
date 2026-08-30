package com.clinic.exception;

/**
 * Bad login (API contract 7a). The message never says whether it was the email
 * or the password that was wrong, so the endpoint cannot be used to enumerate
 * registered accounts.
 */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(ErrorCode.INVALID_CREDENTIALS);
    }
}
