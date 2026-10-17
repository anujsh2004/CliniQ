package com.clinic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * The clinic's reminder rules (API contract 15, v1.4).
 *
 * <p>Deliberately configuration rather than code. Changing when a reminder
 * fires, or what it says, is a clinic decision that should not need a code
 * change or a release:
 *
 * <pre>
 * clinic:
 *   notifications:
 *     reminders:
 *       - offset: 24h
 *         message: "Appointment with {doctor} tomorrow at {time}."
 *       - offset: 1h
 *         message: "Leave soon - your appointment with {doctor} is at {time}."
 * </pre>
 *
 * <p>Placeholders: {@code {doctor}}, {@code {patient}}, {@code {date}},
 * {@code {time}}, {@code {clinic}}.
 */
@ConfigurationProperties(prefix = "clinic.notifications")
public record ReminderProperties(List<Rule> reminders, Duration minimumPatientOffset) {

    public ReminderProperties {
        reminders = reminders == null || reminders.isEmpty() ? defaults() : reminders;
        minimumPatientOffset = minimumPatientOffset == null
                ? Duration.ofMinutes(15)
                : minimumPatientOffset;
    }

    private static List<Rule> defaults() {
        return List.of(
                new Rule(Duration.ofHours(24), "Appointment with {doctor} tomorrow at {time}."),
                new Rule(Duration.ofHours(1), "Leave soon - your appointment with {doctor} is at {time}."));
    }

    /**
     * One reminder rule.
     *
     * @param offset  how far before the appointment it fires
     * @param message the wording the patient reads, with placeholders
     */
    public record Rule(Duration offset, String message) {

        /** The reminder type recorded against the notification, e.g. 24_HOURS. */
        public String type() {
            long hours = offset.toHours();
            if (hours > 0 && offset.toMinutesPart() == 0) {
                return hours + (hours == 1 ? "_HOUR" : "_HOURS");
            }
            return offset.toMinutes() + "_MINUTES";
        }
    }
}
