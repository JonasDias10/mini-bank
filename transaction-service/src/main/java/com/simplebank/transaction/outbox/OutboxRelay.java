package com.simplebank.transaction.outbox;

import com.simplebank.transaction.messaging.MessagingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

// Publishes outbox rows to RabbitMQ. Delivery is at-least-once: if the app dies after publishing but before
// marking a row as sent, it is published again, which is why consumers must be idempotent.
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 50;
    private static final long CONFIRM_TIMEOUT_MS = 5_000;

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxRelay(OutboxRepository outboxRepository, RabbitTemplate rabbitTemplate) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.relay-interval-ms:500}")
    @Transactional
    public void publishPending() {
        List<OutboxMessage> batch = outboxRepository.findBatchToSend(BATCH_SIZE);

        for (OutboxMessage message : batch) {
            try {
                publish(message);
            } catch (AmqpException ex) {
                log.warn("Could not publish outbox message {}, will retry: {}", message.getId(), ex.getMessage());
                return;
            }

            message.markSent();
        }
    }

    private void publish(OutboxMessage outboxMessage) {
        Message message = MessageBuilder.withBody(outboxMessage.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();

        rabbitTemplate.invoke(operations -> {
            operations.send(MessagingConfig.EXCHANGE, outboxMessage.getRoutingKey(), message);
            operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
            return null;
        });
    }
}
