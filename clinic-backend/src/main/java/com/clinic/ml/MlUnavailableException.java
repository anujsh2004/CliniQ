package com.clinic.ml;

/** The ML service is switched on but cannot answer. */
public class MlUnavailableException extends RuntimeException {

    public MlUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
