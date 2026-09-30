package com.clinic.speech;

import com.clinic.config.SpeechProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
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
     * Asks the service to voice a reply, returning wav bytes.
     *
     * <p>Failure is not fatal anywhere upstream: the reply is already on the
     * patient's screen, so losing the audio costs them nothing.
     */
    public byte[] speak(String text, String language) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("text", text);
        form.add("language", language);

        try {
            byte[] wav = restClient.post()
                    .uri("/speak")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(byte[].class);

            if (wav == null || wav.length == 0) {
                throw new SpeechUnavailableException("The speech service returned no audio.");
            }
            return wav;
        } catch (ResourceAccessException exception) {
            throw notRunning(exception);
        } catch (RestClientResponseException exception) {
            throw rejected(exception);
        } catch (RestClientException exception) {
            throw failed("speak", exception);
        }
    }

    /**
     * The service is not listening at all.
     *
     * <p>Distinguished from a service that answered with an error, because the
     * two need completely different fixes and conflating them cost real
     * debugging time: a request the model rejected was reported as the service
     * being down, which sent everyone looking in the wrong place.
     */
    private SpeechUnavailableException notRunning(Exception cause) {
        log.warn("Speech service at {} is not reachable: {}",
                properties.baseUrl(), cause.getMessage());
        return new SpeechUnavailableException(
                "The speech service is not running. Start it and try again.", cause);
    }

    /**
     * The service refused the request and said why.
     *
     * <p>Its reason is passed through verbatim. FastAPI puts it in a
     * {@code detail} field; if that cannot be read the status line is better
     * than nothing, but the reason is what the patient actually needs.
     */
    private SpeechRejectedException rejected(RestClientResponseException exception) {
        String detail = exception.getResponseBodyAsString();
        String reason;
        try {
            JsonNode body = new ObjectMapper().readTree(detail);
            reason = body.path("detail").asText(detail);
        } catch (Exception ignored) {
            reason = detail;
        }
        if (reason == null || reason.isBlank()) {
            reason = "The speech service refused that request.";
        }
        log.warn("Speech service refused the request: {}", reason);
        return new SpeechRejectedException(reason, exception);
    }

    /** The service answered, but could not do what was asked. */
    private SpeechUnavailableException failed(String what, Exception cause) {
        log.warn("Speech service could not {}: {}", what, cause.getMessage());
        return new SpeechUnavailableException(
                "The speech service could not handle that request. "
                        + "Check the service log for details.", cause);
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
        } catch (ResourceAccessException exception) {
            throw notRunning(exception);
        } catch (RestClientResponseException exception) {
            throw rejected(exception);
        } catch (RestClientException exception) {
            throw failed("transcribe", exception);
        }
    }
}
