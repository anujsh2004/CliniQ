package com.clinic.ml;

import com.clinic.dto.response.ConditionHints;
import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Condition hints on the doctor's schedule (API contract 27, v1.6): who is
 * asked about, and that a failing ML service costs only the hints.
 */
class ConditionHintServiceTest {

    private final ConditionClient client = mock(ConditionClient.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<ConditionClient> provider = mock(ObjectProvider.class);

    private final ConditionHintService service = new ConditionHintService(provider);

    @BeforeEach
    void serviceIsEnabled() {
        when(provider.getIfAvailable()).thenReturn(client);
    }

    private Appointment appointment(AppointmentStatus status, String... symptoms) {
        Appointment appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setStatus(status);
        appointment.getSymptoms().addAll(List.of(symptoms));
        return appointment;
    }

    private static ConditionClient.ConditionsResponse answer(Appointment appointment, String condition) {
        return new ConditionClient.ConditionsResponse("conditions-v1", List.of(
                new ConditionClient.CaseResult(appointment.getId().toString(), List.of(),
                        List.of(new ConditionClient.Condition(condition, 0.62)))));
    }

    @Test
    void asksOnlyAboutLiveAppointmentsWithEnoughSymptoms() {
        Appointment asked = appointment(AppointmentStatus.CONFIRMED, "high_fever", "joint_pain");
        Appointment oneSymptom = appointment(AppointmentStatus.CONFIRMED, "itching");
        Appointment finished = appointment(AppointmentStatus.COMPLETED, "itching", "skin_rash");
        when(client.conditions(anyList())).thenReturn(answer(asked, "Dengue"));

        Map<UUID, ConditionHints> hints = service.hints(List.of(asked, oneSymptom, finished));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ConditionClient.Case>> sent = ArgumentCaptor.forClass(List.class);
        verify(client).conditions(sent.capture());
        assertThat(sent.getValue()).extracting(ConditionClient.Case::caseId)
                .containsExactly(asked.getId().toString());
        assertThat(hints.get(asked.getId()).conditions().getFirst().name()).isEqualTo("Dengue");
        assertThat(hints.get(asked.getId()).modelVersion()).isEqualTo("conditions-v1");
    }

    @Test
    void aCaseTheModelDeclinesGetsNoHint() {
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED, "made_up", "also_made_up");
        when(client.conditions(anyList())).thenReturn(new ConditionClient.ConditionsResponse("v1", List.of(
                new ConditionClient.CaseResult(appointment.getId().toString(),
                        List.of("also_made_up", "made_up"), List.of()))));

        assertThat(service.hints(List.of(appointment))).isEmpty();
    }

    @Test
    void whenSwitchedOffNothingIsCalled() {
        when(provider.getIfAvailable()).thenReturn(null);

        assertThat(service.hints(List.of(appointment(AppointmentStatus.CONFIRMED, "a", "b")))).isEmpty();
    }

    @Test
    void aServiceThatIsNotRunningCostsOnlyTheHints() {
        when(client.conditions(anyList())).thenThrow(new ResourceAccessException("Connection refused"));

        assertThat(service.hints(List.of(appointment(AppointmentStatus.CONFIRMED, "a", "b")))).isEmpty();
    }

    @Test
    void aDayWithNoSymptomsDoesNotCallTheService() {
        service.hints(List.of(appointment(AppointmentStatus.CONFIRMED)));

        verify(client, never()).conditions(anyList());
    }
}
