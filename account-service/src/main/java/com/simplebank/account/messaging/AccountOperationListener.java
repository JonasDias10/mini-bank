package com.simplebank.account.messaging;

import com.simplebank.account.service.AccountOperationProcessor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AccountOperationListener {

    private final AccountOperationProcessor processor;

    public AccountOperationListener(AccountOperationProcessor processor) {
        this.processor = processor;
    }

    @RabbitListener(queues = MessagingConfig.COMMANDS_QUEUE)
    public void onCommand(AccountOperationCommand command) {
        processor.process(command);
    }
}
