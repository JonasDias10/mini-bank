package com.simplebank.transaction.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Both services declare the same topology, so messages are never dropped because the other side has not
// started yet. Declarations are idempotent, but the names and arguments must stay identical in both services.
@Configuration
public class MessagingConfig {

    public static final String EXCHANGE = "simplebank";
    public static final String COMMANDS_ROUTING_KEY = "account.operation.command";
    public static final String RESULTS_ROUTING_KEY = "account.operation.result";
    public static final String COMMANDS_QUEUE = "account-operation-commands";
    public static final String RESULTS_QUEUE = "account-operation-results";

    private static final String DEAD_LETTER_EXCHANGE = "simplebank.dead-letter";

    @Bean
    DirectExchange exchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    Declarables commandsTopology() {
        return queueWithDeadLetterQueue(COMMANDS_QUEUE, COMMANDS_ROUTING_KEY);
    }

    @Bean
    Declarables resultsTopology() {
        return queueWithDeadLetterQueue(RESULTS_QUEUE, RESULTS_ROUTING_KEY);
    }

    @Bean
    MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    private static Declarables queueWithDeadLetterQueue(String queueName, String routingKey) {
        String deadLetterQueueName = queueName + ".dlq";
        Queue queue = QueueBuilder.durable(queueName)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(deadLetterQueueName)
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(deadLetterQueueName).build();

        return new Declarables(
                queue,
                deadLetterQueue,
                new Binding(queueName, Binding.DestinationType.QUEUE, EXCHANGE, routingKey, null),
                new Binding(deadLetterQueueName, Binding.DestinationType.QUEUE, DEAD_LETTER_EXCHANGE,
                        deadLetterQueueName, null)
        );
    }
}
