package com.clinic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * How long a booked-but-unpaid appointment holds its slot (API contract 12,
 * v1.4).
 *
 * <p>Configurable because it is a business decision, not a technical one: long
 * enough for a patient to finish paying, short enough that an abandoned
 * checkout does not keep a slot off the market for the rest of the day.
 */
@ConfigurationProperties(prefix = "clinic.holds")
public record HoldProperties(Duration duration, String sweepCron) {

    public HoldProperties {
        duration = duration == null ? Duration.ofMinutes(5) : duration;
        sweepCron = sweepCron == null || sweepCron.isBlank() ? "*/30 * * * * *" : sweepCron;
    }
}
