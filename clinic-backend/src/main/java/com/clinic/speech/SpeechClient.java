package com.clinic.speech;

import com.clinic.config.SpeechProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Talks to the Python speech service that hosts the IndicConformer model.
 *
 * <p>The model is AI4Bharat's, MIT licensed, and runs on our own machine. That
 * is a deliberate choice over a hosted speech API: a patient describing a
 * symptom is health information, and self-hosting means the audio never leaves
 * the clinic's own hardware.
 *
 * <p>Note the deliberate absence of a retry. Transcription is expensive and a
 * patient who has waited once for a slow answer is better served by being told
 * to try again than by silently waiting twice as long.
 */
public class SpeechClient {

    private static final Logger log = LoggerFactory.getLogger(SpeechClient.class);

    private final RestClient restClient;
    private final SpeechProperties properties;

    public SpeechClient(SpeechProperties properties) {
        this.properties = properties;

        // RestClient.Builder is not auto-configured in Spring Boot 4, so the
        // request factory is built by hand to apply the timeout.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.timeout().toMillis());
        factory.setReadTimeout((int) properties.timeout().toMillis());

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * Transcribes a recording.
     *
     * @param audio    the raw upload, in whatever format the browser recorded
     * @param filename kept only so the service can read the extension
     * @param language an ISO code such as {@code ta}
     */
    public Transcript transcribe(byte[] audio, String filename, String language) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("audio", new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        form.add("language", language);

        try {
            Transcript transcript = restClient.post()
                    .uri("/transcribe")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(Transcript.class);

            if (transcript == null || transcript.text() == null) {
                throw new SpeechUnavailableException("The speech service returned nothing.");
            }
            log.info("Transcribed {} bytes of {} in {}ms",
                    audio.length, language, transcript.elapsedMs());
            return transcript;
        } catch (RestClientException exception) {
            // The commonest cause by far is the sidecar simply not running,
            // which is a deployment state rather than a bug, so it is logged
            // as a warning and surfaced as a clear message.
            log.warn("Speech service at {} did not answer: {}",
                    properties.baseUrl(), exception.getMessage());
            throw new SpeechUnavailableException(
                    "The speech service is not running. Start it and try again.", exception);
        }
    }
}
