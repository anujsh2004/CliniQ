package com.clinic.notification;

import com.clinic.config.RabbitConfig;
import com.clinic.entity.NotificationChannel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitReminderPublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final RabbitReminderPublisher publisher =
            new RabbitReminderPublisher(rabbitTemplate, new ObjectMapper());

    private ReminderMessage message() {
        return new ReminderMessage(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Anjali Verma", "+919876543210", "Dr. Sharma",
                LocalDate.of(2026, 8, 31), LocalTime.of(10, 0),
                NotificationChannel.WHATSAPP, "24_HOURS");
    }

    @Test
    void publishesToTheReminderRoutingKeyAsJson() {
        publisher.publish(message());

        var payload = forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.EXCHANGE),
                eq(RabbitConfig.REMINDER_ROUTING_KEY), payload.capture());
        assertThat(payload.getValue().toString())
                .contains("Anjali Verma")
                .contains("+919876543210")
                .contains("24_HOURS");
    }

    @Test
    void aBrokerOutageIsSwallowedRatherThanFailingTheCaller() {
        // The reminder is already saved as QUEUED, so it is recoverable. An
        // unreachable broker must not fail the booking or sweep that triggered
        // it - the queue is a side-channel, not part of the critical path.
        doThrow(new AmqpConnectException(new RuntimeException("broker down")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), (Object) org.mockito.ArgumentMatchers.any());

        assertThatCode(() -> publisher.publish(message())).doesNotThrowAnyException();
    }
}
