package com.clinic.controller;

import com.clinic.config.SpeechProperties;
import com.clinic.dto.response.ApiResponse;
import com.clinic.exception.FieldValidationException;
import com.clinic.speech.SpeechClient;
import com.clinic.speech.Transcript;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Speech to text, for patients who would rather speak than type
 * (API contract 24, v1.5).
 *
 * <p>The model runs on the clinic's own hardware, so the audio never leaves
 * it. That is why this endpoint proxies to a local service rather than letting
 * the browser call a speech API directly: a browser calling out would both
 * leak the audio to a third party and expose whatever key it used.
 *
 * <p>Signed in only. Transcription costs real GPU time, and an open endpoint
 * would be free compute for anyone who found it.
 */
@RestController
@RequestMapping("/api/v1/voice")
@ConditionalOnProperty(prefix = "clinic.speech", name = "enabled", havingValue = "true")
@PreAuthorize("isAuthenticated()")
public class VoiceController {

    private final SpeechClient speechClient;
    private final SpeechProperties properties;

    public VoiceController(SpeechClient speechClient, SpeechProperties properties) {
        this.speechClient = speechClient;
        this.properties = properties;
    }

    /**
     * Turns a recording into text.
     *
     * @param audio    the recording, as the browser captured it
     * @param language an ISO code; defaults to Tamil, the language this was
     *                 built and tested for
     */
    @PostMapping(path = "/transcribe", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<Transcript>> transcribe(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(name = "language", defaultValue = "ta") String language) {

        if (audio.isEmpty()) {
            throw new FieldValidationException("audio", "Record something before sending it.");
        }
        if (audio.getSize() > properties.maxUploadBytes()) {
            throw new FieldValidationException("audio",
                    "That recording is too long. Keep it under 30 seconds.");
        }
        if (!properties.languages().contains(language)) {
            throw new FieldValidationException("language",
                    "Supported languages are: " + String.join(", ", properties.languages()));
        }

        byte[] bytes;
        try {
            bytes = audio.getBytes();
        } catch (IOException exception) {
            throw new FieldValidationException("audio", "That recording could not be read.");
        }

        String filename = audio.getOriginalFilename() == null || audio.getOriginalFilename().isBlank()
                ? "recording.webm"
                : audio.getOriginalFilename();

        Transcript transcript = speechClient.transcribe(bytes, filename, language);
        return ResponseEntity.ok(ApiResponse.success("Audio transcribed successfully", transcript));
    }

    /**
     * Which languages the browser may offer.
     *
     * <p>Read from configuration so the microphone button never offers a
     * language the service will then refuse.
     */
    @GetMapping("/languages")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> languages() {
        return ResponseEntity.ok(ApiResponse.success("Languages fetched successfully",
                Map.of("languages", properties.languages())));
    }
}
