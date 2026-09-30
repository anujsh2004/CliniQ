package com.clinic.speech;

/**
 * What the speech service heard.
 *
 * @param text            the recognised words, in the language's own script
 * @param language        the ISO code the audio was decoded as
 * @param decoder         {@code rnnt} or {@code ctc}
 * @param durationSeconds how long the recording was
 * @param elapsedMs       how long recognition took, useful for the demo
 */
public record Transcript(
        String text,
        String language,
        String decoder,
        double durationSeconds,
        int elapsedMs) {
}
