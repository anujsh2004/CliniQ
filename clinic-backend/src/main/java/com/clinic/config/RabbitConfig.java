package com.clinic.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The notification event bus (tech-stack.md 6.1).
 *
 * <p>RabbitMQ is an additive side-channel: reminders are queued here and
 * delivered by a worker, and the broker being down must never affect booking
 * correctness - the database transaction remains the only thing the no
 * double-booking guarantee depends on.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "clinic.notifications";
    public static final String REMINDER_QUEUE = "clinic.notifications.reminders";
    public static final String REMINDER_ROUTING_KEY = "notification.reminder";

    /**
     * Where a message goes after it has failed every retry. Keeping it rather
     * than dropping it is what makes a failed reminder investigable
     * (product-description.md 13).
     */
    public static final String DEAD_LETTER_EXCHANGE = "clinic.notifications.dlx";
    public static final String DEAD_LETTER_QUEUE = "clinic.notifications.reminders.dlq";

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue reminderQueue() {
        return QueueBuilder.durable(REMINDER_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(REMINDER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding reminderBinding(Queue reminderQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(reminderQueue).to(notificationExchange).with(REMINDER_ROUTING_KEY);
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(REMINDER_ROUTING_KEY);
    }
}
