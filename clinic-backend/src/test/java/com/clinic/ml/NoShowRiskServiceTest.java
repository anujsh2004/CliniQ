package com.clinic.ml;

import com.clinic.dto.response.NoShowRisk;
import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import com.clinic.entity.Patient;
import com.clinic.entity.Slot;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.NotificationRepository;
import com.clinic.repository.PatientAttendance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * No-show risk on the doctor's schedule (API contract 26, v1.6): what the model
 * is sent, and that every way the ML service can fail leaves the schedule
 * intact.
 */
class NoShowRiskServiceTest {

    // A Thursday.
    private static final LocalDate DAY = LocalDate.of(2026, 8, 20);

    private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final NoShowRiskClient client = mock(NoShowRiskClient.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<NoShowRiskClient> provider = mock(ObjectProvider.class);

    private final NoShowRiskService service =
            new NoShowRiskService(provider, appointmentRepository, notificationRepository);

    @BeforeEach
    void serviceIsEnabled() {
        when(provider.getIfAvailable()).thenReturn(client);
    }

    private Appointment appointment(UUID patientId, AppointmentStatus status, OffsetDateTime bookedAt) {
        Patient patient = new Patient();
        patient.setId(patientId);

        Slot slot = new Slot();
        slot.setDate(DAY);
        slot.setStartTime(LocalTime.of(10, 0));

        Appointment appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setPatient(patient);
        appointment.setSlot(slot);
        appointment.setStatus(status);
        ReflectionTestUtils.setField(appointment, "createdAt", bookedAt);
        return appointment;
    }

    private static PatientAttendance attendance(UUID patientId, long visits, long noShows) {
        return new PatientAttendance() {
            public UUID getPatientId() { return patientId; }
            public long getVisits() { return visits; }
            public long getNoShows() { return noShows; }
        };
    }

    @Test
    void buildsFeaturesFromTheClinicsOwnRecords() {
        UUID patientId = UUID.randomUUID();
        // 08:30 UTC is 14:00 in Kolkata, nine days before the visit.
        Appointment appointment = appointment(patientId, AppointmentStatus.CONFIRMED,
                OffsetDateTime.of(2026, 8, 11, 8, 30, 0, 0, ZoneOffset.UTC));
        when(appointmentRepository.attendanceBefore(any(), any(), anyList()))
                .thenReturn(List.of(attendance(patientId, 4, 1)));
        when(notificationRepository.findAppointmentIdsWithReminders(any()))
                .thenReturn(List.of(appointment.getId()));

        RiskFeatures features = service.features(DAY, List.of(appointment)).getFirst();

        assertThat(features.leadDays()).isEqualTo(9);
        assertThat(features.appointmentWeekday()).isEqualTo(3);
        assertThat(features.bookingHour()).isEqualTo(14);
        assertThat(features.priorVisits()).isEqualTo(4);
        assertThat(features.priorNoShows()).isEqualTo(1);
        assertThat(features.reminderSent()).isTrue();
    }

    @Test
    void aFirstVisitHasNoHistory() {
        Appointment appointment = appointment(UUID.randomUUID(), AppointmentStatus.CONFIRMED,
                OffsetDateTime.of(2026, 8, 20, 4, 0, 0, 0, ZoneOffset.UTC));

        RiskFeatures features = service.features(DAY, List.of(appointment)).getFirst();

        assertThat(features.leadDays()).isZero();
        assertThat(features.priorVisits()).isZero();
        assertThat(features.reminderSent()).isFalse();
    }

    @Test
    void scoresOnlyAppointmentsThatCanStillBeMissed() {
        Appointment live = appointment(UUID.randomUUID(), AppointmentStatus.CONFIRMED, OffsetDateTime.now());
        Appointment done = appointment(UUID.randomUUID(), AppointmentStatus.COMPLETED, OffsetDateTime.now());
        when(client.score(anyList())).thenReturn(new NoShowRiskClient.ScoreResponse("v1",
                List.of(new NoShowRiskClient.Score(live.getId().toString(), 0.12, RiskBand.LOW))));

        Map<UUID, NoShowRisk> risks = service.score(DAY, List.of(live, done));

        assertThat(risks).containsOnlyKeys(live.getId());
        assertThat(risks.get(live.getId()).modelVersion()).isEqualTo("v1");
    }

    @Test
    void whenSwitchedOffNothingIsCalled() {
        when(provider.getIfAvailable()).thenReturn(null);

        Map<UUID, NoShowRisk> risks = service.score(DAY,
                List.of(appointment(UUID.randomUUID(), AppointmentStatus.CONFIRMED, OffsetDateTime.now())));

        assertThat(risks).isEmpty();
        verify(appointmentRepository, never()).attendanceBefore(any(), any(), anyList());
    }

    @Test
    void aServiceThatIsNotRunningCostsOnlyTheRisk() {
        when(client.score(anyList())).thenThrow(new ResourceAccessException("Connection refused"));

        Map<UUID, NoShowRisk> risks = service.score(DAY,
                List.of(appointment(UUID.randomUUID(), AppointmentStatus.CONFIRMED, OffsetDateTime.now())));

        assertThat(risks).isEmpty();
    }

    @Test
    void aDayWithNothingLiveDoesNotCallTheService() {
        service.score(DAY, List.of(appointment(UUID.randomUUID(), AppointmentStatus.CANCELLED, OffsetDateTime.now())));

        verify(client, never()).score(anyList());
    }
}
