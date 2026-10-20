package com.simplebank.account.service;

import com.simplebank.account.messaging.AccountOperationCommand;
import com.simplebank.account.messaging.AccountOperationResult;
import com.simplebank.account.messaging.MessagingConfig;
import com.simplebank.account.model.ProcessedOperation;
import com.simplebank.account.outbox.OutboxService;
import com.simplebank.account.repository.ProcessedOperationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountOperationProcessor {

    private static final Logger log = LoggerFactory.getLogger(AccountOperationProcessor.class);

    private final AccountService accountService;
    private final ProcessedOperationRepository processedOperationRepository;
    private final OutboxService outboxService;

    public AccountOperationProcessor(AccountService accountService,
                                     ProcessedOperationRepository processedOperationRepository,
                                     OutboxService outboxService) {
        this.accountService = accountService;
        this.processedOperationRepository = processedOperationRepository;
        this.outboxService = outboxService;
    }

    // Balance change, "already processed" marker and reply are committed together or not at all.
    @Transactional
    public void process(AccountOperationCommand command) {
        if (processedOperationRepository.existsById(command.operationId())) {
            log.info("Skipping operation {}: already processed", command.operationId());
            return;
        }

        OperationResult result = command.hasValidAmount()
                ? accountService.applyOperation(command.accountId(), command.type(), command.amount())
                : OperationResult.failed("Amount must be positive with at most 2 decimal places");

        processedOperationRepository.save(new ProcessedOperation(command.operationId()));
        outboxService.enqueue(MessagingConfig.RESULTS_ROUTING_KEY,
                AccountOperationResult.from(command.operationId(), result));
    }
}
