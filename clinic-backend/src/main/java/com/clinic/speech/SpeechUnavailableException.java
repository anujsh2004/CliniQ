package com.clinic.speech;

/**
 * The speech service could not be reached or could not answer.
 *
 * <p>Separate from a validation failure on purpose: nothing is wrong with what
 * the patient sent, so the honest answer is that the feature is unavailable,
 * not that they did something wrong.
 */
public class SpeechUnavailableException extends RuntimeException {

    public SpeechUnavailableException(String message) {
        super(message);
    }

    public SpeechUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
