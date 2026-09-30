package com.clinic.controller;

import com.clinic.security.JwtService;
import com.clinic.speech.SpeechClient;
import com.clinic.speech.SpeechUnavailableException;
import com.clinic.speech.Transcript;
import com.clinic.testsupport.SecuritySliceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Speech-to-text endpoints (API contract 24, v1.5).
 *
 * <p>The property source matters: the controller only exists when speech is
 * enabled, so without it every request here would 404 and the tests would pass
 * for the wrong reason.
 */
@WebMvcTest(VoiceController.class)
@Import(SecuritySliceTestConfig.class)
@TestPropertySource(properties = {
        "clinic.speech.enabled=true",
        "clinic.speech.languages=hi,ta,en",
})
class VoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpeechClient speechClient;

    @MockitoBean
    private JwtService jwtService;

    private static MockMultipartFile recording() {
        return new MockMultipartFile("audio", "recording.webm", "audio/webm",
                "not-really-audio-but-not-empty".getBytes());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void transcribesTamilInTheContractsEnvelope() throws Exception {
        when(speechClient.transcribe(any(), any(), eq("ta")))
                .thenReturn(new Transcript("நாளைக்கு அப்பாயிண்ட்மென்ட் வேணும்",
                        "ta", "rnnt", 2.4, 812));

        mockMvc.perform(multipart("/api/v1/voice/transcribe")
                        .file(recording())
                        .param("language", "ta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.text").value("நாளைக்கு அப்பாயிண்ட்மென்ட் வேணும்"))
                .andExpect(jsonPath("$.data.language").value("ta"));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void defaultsToHindiWhenNoLanguageIsGiven() throws Exception {
        // Hindi is the most widely spoken of the three, so it is the sensible
        // default for a patient who has not chosen.
        when(speechClient.transcribe(any(), any(), eq("hi")))
                .thenReturn(new Transcript("नमस्ते", "hi", "rnnt", 1.0, 300));

        mockMvc.perform(multipart("/api/v1/voice/transcribe").file(recording()))
                .andExpect(status().isOk());

        verify(speechClient).transcribe(any(), any(), eq("hi"));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void rejectsAnEmptyRecording() throws Exception {
        mockMvc.perform(multipart("/api/v1/voice/transcribe")
                        .file(new MockMultipartFile("audio", "recording.webm", "audio/webm", new byte[0])))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verify(speechClient, never()).transcribe(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void rejectsALanguageTheServiceCannotHandle() throws Exception {
        // Refused here rather than passed through, so the failure names the
        // real problem instead of surfacing as an error from the sidecar.
        mockMvc.perform(multipart("/api/v1/voice/transcribe")
                        .file(recording())
                        .param("language", "fr"))
                .andExpect(status().isUnprocessableEntity());

        verify(speechClient, never()).transcribe(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void reportsTheServiceBeingDownAsUnavailableRatherThanAsAServerError() throws Exception {
        // 503, not 500: nothing is wrong with the request, and the assistant
        // still answers typed questions without the model running.
        when(speechClient.transcribe(any(), any(), any()))
                .thenThrow(new SpeechUnavailableException("The speech service is not running."));

        mockMvc.perform(multipart("/api/v1/voice/transcribe").file(recording()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void refusesAnyoneWhoIsNotSignedIn() throws Exception {
        // Transcription costs real GPU time; an open endpoint would be free
        // compute for whoever found it.
        mockMvc.perform(multipart("/api/v1/voice/transcribe").file(recording()))
                .andExpect(status().isUnauthorized());

        verify(speechClient, never()).transcribe(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void listsTheLanguagesTheBrowserMayOffer() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/voice/languages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.languages[0]").value("hi"));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void voicesAHindiReply() throws Exception {
        when(speechClient.speak(eq("नमस्ते"), eq("hi"))).thenReturn(new byte[] {82, 73, 70, 70});

        mockMvc.perform(post("/api/v1/voice/speak")
                        .param("text", "नमस्ते")
                        .param("language", "hi"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/wav"));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void voicesEnglishToo() throws Exception {
        // All three languages are voiced by the clinic's own models, so a
        // patient hears the same voice whichever they pick.
        when(speechClient.speak(eq("Hello"), eq("en"))).thenReturn(new byte[] {82, 73, 70, 70});

        mockMvc.perform(post("/api/v1/voice/speak")
                        .param("text", "Hello")
                        .param("language", "en"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/wav"));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void refusesToVoiceALanguageWeHaveNoModelFor() throws Exception {
        mockMvc.perform(post("/api/v1/voice/speak")
                        .param("text", "Bonjour")
                        .param("language", "fr"))
                .andExpect(status().isUnprocessableEntity());

        verify(speechClient, never()).speak(any(), any());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void refusesToVoiceAnEntirePage() throws Exception {
        mockMvc.perform(post("/api/v1/voice/speak")
                        .param("text", "क".repeat(601))
                        .param("language", "hi"))
                .andExpect(status().isUnprocessableEntity());

        verify(speechClient, never()).speak(any(), any());
    }

    @Test
    void refusesToVoiceAnythingForAStranger() throws Exception {
        mockMvc.perform(post("/api/v1/voice/speak")
                        .param("text", "नमस्ते")
                        .param("language", "hi"))
                .andExpect(status().isUnauthorized());

        verify(speechClient, never()).speak(any(), any());
    }
}
