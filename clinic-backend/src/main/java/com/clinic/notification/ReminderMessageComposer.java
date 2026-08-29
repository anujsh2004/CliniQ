package com.clinic.notification;

import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Turns a queued reminder into the text a patient reads.
 *
 * <p>Kept separate from delivery so the wording can be reviewed and changed
 * without touching the worker, and so it can be tested as plain text.
 */
@Component
public class ReminderMessageComposer {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH);
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    public String compose(ReminderMessage reminder) {
        return "Hello %s, this is a reminder of your appointment with %s on %s at %s. "
                .formatted(
                        reminder.patientName(),
                        reminder.doctorName(),
                        reminder.appointmentDate().format(DATE),
                        reminder.startTime().format(TIME))
                + "Please reply to this message if you need to reschedule.";
    }
}
