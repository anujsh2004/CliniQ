package com.clinic.speech;

/**
 * The speech service understood the request and refused it.
 *
 * <p>Distinct from {@link SpeechUnavailableException}, which means the service
 * could not be reached at all. This one carries the service's own explanation
 * — a silent recording, an unsupported language — because that explanation is
 * the only thing that tells a patient what to do differently. Replacing it
 * with a generic message hid a dead microphone behind "could not handle that
 * request", which cost real debugging time.
 */
public class SpeechRejectedException extends RuntimeException {

    public SpeechRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
