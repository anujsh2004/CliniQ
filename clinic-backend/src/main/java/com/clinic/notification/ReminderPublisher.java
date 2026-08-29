package com.clinic.notification;

/**
 * Publishes a reminder onto the notification bus.
 *
 * <p>An interface so the API side never depends on the broker directly: the
 * queue is an additive side-channel, and booking correctness must not rest on
 * it (tech-stack.md 6.1).
 */
public interface ReminderPublisher {

    void publish(ReminderMessage message);
}
