package com.clinic.exception;

/**
 * Base class for domain exceptions that map onto a canonical {@link ErrorCode}.
 * Service-layer code throws these; the global handler formats them. This keeps
 * exception generation and HTTP formatting separate, per API contract 7a.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
