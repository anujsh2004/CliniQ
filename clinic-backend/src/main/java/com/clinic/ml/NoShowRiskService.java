package com.clinic.ml;

import com.clinic.dto.response.NoShowRisk;
import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.NotificationRepository;
import com.clinic.repository.PatientAttendance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Scores a doctor's day for no-show risk (API contract 26, v1.6).
 *
 * <p>Builds the model's features from CliniQ's own records and asks the ML
 * service to score them. Every failure - the service switched off, not
 * running, slow, or answering nonsense - ends the same way: no risk on the
 * schedule. The schedule is what the doctor came for; the risk is a hint on top
 * of it, and a hint is never worth a failed page.
 */
@Service
public class NoShowRiskService {

    private static final Logger log = LoggerFactory.getLogger(NoShowRiskService.class);
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Kolkata");

    private final ObjectProvider<NoShowRiskClient> client;
    private final AppointmentRepository appointmentRepository;
    private final NotificationRepository notificationRepository;

    public NoShowRiskService(ObjectProvider<NoShowRiskClient> client,
                             AppointmentRepository appointmentRepository,
                             NotificationRepository notificationRepository) {
        this.client = client;
        this.appointmentRepository = appointmentRepository;
        this.notificationRepository = notificationRepository;
    }

    /**
     * @return risk for each appointment that still lies ahead and is confirmed
     *         or awaiting payment; empty when the ML service is off or failing
     */
    public Map<UUID, NoShowRisk> score(LocalDate date, List<Appointment> appointments) {
        NoShowRiskClient riskClient = client.getIfAvailable();
        if (riskClient == null) {
            return Map.of();
        }
        // Only a live appointment can still be missed. A completed, cancelled
        // or already-missed one has an outcome, and a risk badge on it would
        // be noise.
        List<Appointment> live = appointments.stream()
                .filter(appointment -> appointment.getStatus().isActive())
                .toList();
        if (live.isEmpty()) {
            return Map.of();
        }

        try {
            List<RiskFeatures> features = features(date, live);
            NoShowRiskClient.ScoreResponse response = riskClient.score(features);

            Map<UUID, NoShowRisk> risks = new HashMap<>();
            for (NoShowRiskClient.Score score : response.scores()) {
                risks.put(UUID.fromString(score.appointmentId()),
                        new NoShowRisk(score.probability(), score.band(), response.modelVersion()));
            }
            return risks;
        } catch (RuntimeException exception) {
            log.warn("No-show risk unavailable, serving the schedule without it: {}",
                    exception.getMessage());
            return Map.of();
        }
    }

    List<RiskFeatures> features(LocalDate date, List<Appointment> appointments) {
        Set<UUID> patientIds = appointments.stream()
                .map(appointment -> appointment.getPatient().getId())
                .collect(Collectors.toSet());
        Map<UUID, PatientAttendance> attendance = appointmentRepository
                .attendanceBefore(patientIds, date, List.of(AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW))
                .stream()
                .collect(Collectors.toMap(PatientAttendance::getPatientId, Function.identity()));
        Set<UUID> reminded = Set.copyOf(notificationRepository.findAppointmentIdsWithReminders(
                appointments.stream().map(Appointment::getId).toList()));

        return appointments.stream()
                .map(appointment -> {
                    PatientAttendance history = attendance.get(appointment.getPatient().getId());
                    ZonedDateTime booked = appointment.getCreatedAt().atZoneSameInstant(CLINIC_ZONE);
                    LocalDate slotDate = appointment.getSlot().getDate();
                    return new RiskFeatures(
                            appointment.getId().toString(),
                            // Never negative: a reschedule moves the slot but
                            // keeps the original booking time.
                            (int) Math.max(0, ChronoUnit.DAYS.between(booked.toLocalDate(), slotDate)),
                            slotDate.getDayOfWeek().getValue() - 1,
                            booked.getHour(),
                            history == null ? 0 : (int) history.getVisits(),
                            history == null ? 0 : (int) history.getNoShows(),
                            reminded.contains(appointment.getId()));
                })
                .toList();
    }
}
