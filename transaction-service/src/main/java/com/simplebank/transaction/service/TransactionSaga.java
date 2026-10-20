package com.simplebank.transaction.service;

import com.simplebank.transaction.messaging.AccountOperationCommand;
import com.simplebank.transaction.messaging.AccountOperationResult;
import com.simplebank.transaction.messaging.MessagingConfig;
import com.simplebank.transaction.model.SagaStep;
import com.simplebank.transaction.model.Transaction;
import com.simplebank.transaction.model.TransactionType;
import com.simplebank.transaction.outbox.OutboxService;
import com.simplebank.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/*
 * Orchestrates each transaction as a sequence of commands to account-service:
 *
 *   DEPOSIT     CREDIT_TARGET                    -> COMPLETED | FAILED
 *   WITHDRAWAL  DEBIT_SOURCE                     -> COMPLETED | FAILED
 *   TRANSFER    DEBIT_SOURCE -> CREDIT_TARGET    -> COMPLETED
 *                    |               | fails
 *                    | fails         v
 *                    v           REFUND_SOURCE   -> FAILED (money returned)
 *                  FAILED
 */
@Service
public class TransactionSaga {

    private static final Logger log = LoggerFactory.getLogger(TransactionSaga.class);

    private final TransactionRepository transactionRepository;
    private final OutboxService outboxService;

    public TransactionSaga(TransactionRepository transactionRepository, OutboxService outboxService) {
        this.transactionRepository = transactionRepository;
        this.outboxService = outboxService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void start(Transaction transaction) {
        SagaStep firstStep = transaction.getType() == TransactionType.DEPOSIT
                ? SagaStep.CREDIT_TARGET
                : SagaStep.DEBIT_SOURCE;
        startStep(transaction, firstStep);
    }

    @Transactional
    public void handleResult(AccountOperationResult result) {
        Optional<Transaction> found = transactionRepository.findByCurrentOperationIdForUpdate(result.operationId());

        // A duplicate or late reply: the step it answers has already been handled.
        if (found.isEmpty()) {
            log.info("Ignoring result for operation {}: no transaction is waiting for it", result.operationId());
            return;
        }

        Transaction transaction = found.get();

        if (result.success()) {
            onStepSucceeded(transaction);
        } else {
            onStepFailed(transaction, result.failureReason());
        }
    }

    private void onStepSucceeded(Transaction transaction) {
        switch (transaction.getStep()) {
            case DEBIT_SOURCE -> continueAfterDebit(transaction);
            case CREDIT_TARGET -> transaction.complete();
            case REFUND_SOURCE -> transaction.fail(transaction.getFailureReason() + ". Amount refunded to source account");
        }
    }

    private void onStepFailed(Transaction transaction, String reason) {
        switch (transaction.getStep()) {
            case DEBIT_SOURCE -> transaction.fail(reason);
            case CREDIT_TARGET -> handleCreditFailure(transaction, reason);
            case REFUND_SOURCE -> handleRefundFailure(transaction, reason);
        }
    }

    private void continueAfterDebit(Transaction transaction) {
        if (transaction.getType() == TransactionType.TRANSFER) {
            startStep(transaction, SagaStep.CREDIT_TARGET);
            return;
        }

        transaction.complete();
    }

    private void handleCreditFailure(Transaction transaction, String reason) {
        if (transaction.getType() == TransactionType.TRANSFER) {
            transaction.recordFailureReason(reason);
            startStep(transaction, SagaStep.REFUND_SOURCE);
            return;
        }

        transaction.fail(reason);
    }

    private void handleRefundFailure(Transaction transaction, String reason) {
        log.error("Refund failed for transaction {}: {}. Manual review required.", transaction.getId(), reason);
        transaction.fail("Refund failed: " + reason + ". Manual review required");
    }

    private void startStep(Transaction transaction, SagaStep step) {
        transaction.moveTo(step, UUID.randomUUID());
        outboxService.enqueue(MessagingConfig.COMMANDS_ROUTING_KEY, AccountOperationCommand.forCurrentStep(transaction));
    }
}
