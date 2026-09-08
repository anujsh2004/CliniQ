package com.clinic.notification;

import com.clinic.entity.Appointment;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Fills a reminder template with the appointment's own details.
 *
 * <p>Kept separate from both scheduling and delivery so the wording can be
 * changed in configuration and reviewed as plain text.
 */
@Component
public class ReminderComposer {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH);
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    /**
     * Replaces the placeholders a clinic may use in a reminder message.
     *
     * <p>Unknown placeholders are left as they are rather than blanked: a
     * message reading "{docter}" is a visible typo someone will fix, whereas an
     * empty gap looks like a bug in the product.
     */
    public String compose(String template, Appointment appointment) {
        return template
                .replace("{doctor}", appointment.getDoctor().getName())
                .replace("{patient}", appointment.getPatient().getUser().getName())
                .replace("{date}", appointment.getSlot().getDate().format(DATE))
                .replace("{time}", appointment.getSlot().getStartTime().format(TIME))
                .replace("{clinic}", appointment.getDoctor().getClinic() == null
                        ? "the clinic"
                        : appointment.getDoctor().getClinic().getName());
    }
}
