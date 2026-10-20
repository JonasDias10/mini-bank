package com.simplebank.transaction.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.net.ConnectException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OutboxRelay outboxRelay;

    @Test
    void marksMessagesAsSentAfterPublishing() {
        OutboxMessage first = new OutboxMessage("key", "{}");
        OutboxMessage second = new OutboxMessage("key", "{}");
        when(outboxRepository.findBatchToSend(50)).thenReturn(List.of(first, second));

        outboxRelay.publishPending();

        verify(rabbitTemplate, times(2)).invoke(any());
        assertThat(first.getSentAt()).isNotNull();
        assertThat(second.getSentAt()).isNotNull();
    }

    @Test
    void keepsMessagesUnsentWhenBrokerIsDown() {
        OutboxMessage first = new OutboxMessage("key", "{}");
        OutboxMessage second = new OutboxMessage("key", "{}");
        when(outboxRepository.findBatchToSend(50)).thenReturn(List.of(first, second));
        when(rabbitTemplate.invoke(any()))
                .thenThrow(new AmqpConnectException(new ConnectException("Connection refused")));

        outboxRelay.publishPending();

        verify(rabbitTemplate, times(1)).invoke(any());
        assertThat(first.getSentAt()).isNull();
        assertThat(second.getSentAt()).isNull();
    }
}
