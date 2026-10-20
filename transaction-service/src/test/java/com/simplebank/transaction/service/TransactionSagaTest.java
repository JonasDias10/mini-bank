package com.simplebank.transaction.service;

import com.simplebank.transaction.messaging.AccountOperationCommand;
import com.simplebank.transaction.messaging.AccountOperationResult;
import com.simplebank.transaction.messaging.MessagingConfig;
import com.simplebank.transaction.model.OperationType;
import com.simplebank.transaction.model.SagaStep;
import com.simplebank.transaction.model.Transaction;
import com.simplebank.transaction.model.TransactionStatus;
import com.simplebank.transaction.outbox.OutboxService;
import com.simplebank.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionSagaTest {

    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private TransactionSaga saga;

    private AccountOperationCommand lastCommand() {
        ArgumentCaptor<AccountOperationCommand> captor = ArgumentCaptor.forClass(AccountOperationCommand.class);
        verify(outboxService).enqueue(eq(MessagingConfig.COMMANDS_ROUTING_KEY), captor.capture());
        return captor.getValue();
    }

    // Simulates account-service replying to the step the transaction is currently waiting on.
    private void reply(Transaction transaction, boolean success, String reason) {
        UUID operationId = transaction.getCurrentOperationId();
        when(transactionRepository.findByCurrentOperationIdForUpdate(operationId)).thenReturn(Optional.of(transaction));
        clearInvocations(outboxService);
        saga.handleResult(new AccountOperationResult(operationId, success, reason));
    }

    private Transaction startedTransfer() {
        Transaction transfer = Transaction.transfer(1L, 2L, AMOUNT);
        saga.start(transfer);
        return transfer;
    }

    @Test
    void depositStartsByCreditingTarget() {
        Transaction deposit = Transaction.deposit(1L, AMOUNT);

        saga.start(deposit);

        AccountOperationCommand command = lastCommand();
        assertThat(deposit.getStep()).isEqualTo(SagaStep.CREDIT_TARGET);
        assertThat(command.operationId()).isEqualTo(deposit.getCurrentOperationId());
        assertThat(command.accountId()).isEqualTo(1L);
        assertThat(command.type()).isEqualTo(OperationType.CREDIT);
        assertThat(command.amount()).isEqualByComparingTo(AMOUNT);
    }

    @Test
    void withdrawalStartsByDebitingSource() {
        Transaction withdrawal = Transaction.withdrawal(1L, AMOUNT);

        saga.start(withdrawal);

        assertThat(lastCommand().type()).isEqualTo(OperationType.DEBIT);
        assertThat(withdrawal.getStep()).isEqualTo(SagaStep.DEBIT_SOURCE);
    }

    @Test
    void depositCompletesWhenCreditSucceeds() {
        Transaction deposit = Transaction.deposit(1L, AMOUNT);
        saga.start(deposit);

        reply(deposit, true, null);

        assertThat(deposit.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        verifyNoInteractions(outboxService);
    }

    @Test
    void depositFailsWhenCreditFails() {
        Transaction deposit = Transaction.deposit(99L, AMOUNT);
        saga.start(deposit);

        reply(deposit, false, "Account not found with id 99");

        assertThat(deposit.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(deposit.getFailureReason()).isEqualTo("Account not found with id 99");
    }

    @Test
    void withdrawalCompletesWhenDebitSucceeds() {
        Transaction withdrawal = Transaction.withdrawal(1L, AMOUNT);
        saga.start(withdrawal);

        reply(withdrawal, true, null);

        assertThat(withdrawal.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void transferCreditsTargetAfterDebitSucceeds() {
        Transaction transfer = startedTransfer();
        UUID debitOperation = transfer.getCurrentOperationId();

        reply(transfer, true, null);

        AccountOperationCommand credit = lastCommand();
        assertThat(transfer.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(transfer.getStep()).isEqualTo(SagaStep.CREDIT_TARGET);
        assertThat(credit.operationId()).isNotEqualTo(debitOperation);
        assertThat(credit.accountId()).isEqualTo(2L);
        assertThat(credit.type()).isEqualTo(OperationType.CREDIT);
    }

    @Test
    void transferCompletesWhenBothStepsSucceed() {
        Transaction transfer = startedTransfer();
        reply(transfer, true, null);

        reply(transfer, true, null);

        assertThat(transfer.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void transferFailsWithoutRefundWhenDebitFails() {
        Transaction transfer = startedTransfer();

        reply(transfer, false, "Insufficient funds in account 1000000000");

        assertThat(transfer.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(transfer.getFailureReason()).isEqualTo("Insufficient funds in account 1000000000");
        verifyNoInteractions(outboxService);
    }

    @Test
    void transferRefundsSourceWhenCreditFails() {
        Transaction transfer = startedTransfer();
        reply(transfer, true, null);

        reply(transfer, false, "Account not found with id 2");

        AccountOperationCommand refund = lastCommand();
        assertThat(transfer.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(transfer.getStep()).isEqualTo(SagaStep.REFUND_SOURCE);
        assertThat(refund.accountId()).isEqualTo(1L);
        assertThat(refund.type()).isEqualTo(OperationType.CREDIT);
        assertThat(refund.amount()).isEqualByComparingTo(AMOUNT);
    }

    @Test
    void transferFailsWithReasonOnceRefundSucceeds() {
        Transaction transfer = startedTransfer();
        reply(transfer, true, null);
        reply(transfer, false, "Account not found with id 2");

        reply(transfer, true, null);

        assertThat(transfer.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(transfer.getFailureReason())
                .isEqualTo("Account not found with id 2. Amount refunded to source account");
    }

    @Test
    void transferNeedsManualReviewWhenRefundFails() {
        Transaction transfer = startedTransfer();
        reply(transfer, true, null);
        reply(transfer, false, "Account not found with id 2");

        reply(transfer, false, "Account not found with id 1");

        assertThat(transfer.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(transfer.getFailureReason()).contains("Refund failed").contains("Manual review required");
    }

    @Test
    void ignoresResultNoTransactionIsWaitingFor() {
        when(transactionRepository.findByCurrentOperationIdForUpdate(any())).thenReturn(Optional.empty());

        saga.handleResult(new AccountOperationResult(UUID.randomUUID(), true, null));

        verifyNoInteractions(outboxService);
    }
}
