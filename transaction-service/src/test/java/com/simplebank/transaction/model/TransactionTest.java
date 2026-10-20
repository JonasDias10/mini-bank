package com.simplebank.transaction.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionTest {

    private static final BigDecimal AMOUNT = new BigDecimal("10.00");

    @Test
    void depositOnlyHasTargetAccountAndStartsPending() {
        Transaction transaction = Transaction.deposit(1L, AMOUNT);

        assertThat(transaction.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(transaction.getSourceAccountId()).isNull();
        assertThat(transaction.getTargetAccountId()).isEqualTo(1L);
        assertThat(transaction.getAmount()).isEqualByComparingTo(AMOUNT);
    }

    @Test
    void amountAlwaysHasTwoDecimalPlaces() {
        assertThat(Transaction.deposit(1L, new BigDecimal("100")).getAmount()).hasToString("100.00");
        assertThat(Transaction.deposit(1L, new BigDecimal("12.5")).getAmount()).hasToString("12.50");
    }

    @Test
    void withdrawalOnlyHasSourceAccount() {
        Transaction transaction = Transaction.withdrawal(1L, AMOUNT);

        assertThat(transaction.getType()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(transaction.getSourceAccountId()).isEqualTo(1L);
        assertThat(transaction.getTargetAccountId()).isNull();
    }

    @Test
    void transferHasBothAccounts() {
        Transaction transaction = Transaction.transfer(1L, 2L, AMOUNT);

        assertThat(transaction.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(transaction.getSourceAccountId()).isEqualTo(1L);
        assertThat(transaction.getTargetAccountId()).isEqualTo(2L);
    }

    @Test
    void moveToTracksStepAndOperation() {
        Transaction transaction = Transaction.transfer(1L, 2L, AMOUNT);
        UUID operationId = UUID.randomUUID();

        transaction.moveTo(SagaStep.DEBIT_SOURCE, operationId);

        assertThat(transaction.getStep()).isEqualTo(SagaStep.DEBIT_SOURCE);
        assertThat(transaction.getCurrentOperationId()).isEqualTo(operationId);
    }

    @Test
    void completeClearsStep() {
        Transaction transaction = Transaction.deposit(1L, AMOUNT);
        transaction.moveTo(SagaStep.CREDIT_TARGET, UUID.randomUUID());

        transaction.complete();

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(transaction.getStep()).isNull();
        assertThat(transaction.getCurrentOperationId()).isNull();
    }

    @Test
    void failStoresReasonAndClearsStep() {
        Transaction transaction = Transaction.withdrawal(1L, AMOUNT);
        transaction.moveTo(SagaStep.DEBIT_SOURCE, UUID.randomUUID());

        transaction.fail("Insufficient funds");

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(transaction.getFailureReason()).isEqualTo("Insufficient funds");
        assertThat(transaction.getCurrentOperationId()).isNull();
    }

    @Test
    void sagaStepsTargetTheRightAccount() {
        Transaction transfer = Transaction.transfer(1L, 2L, AMOUNT);

        assertThat(SagaStep.DEBIT_SOURCE.accountOf(transfer)).isEqualTo(1L);
        assertThat(SagaStep.CREDIT_TARGET.accountOf(transfer)).isEqualTo(2L);
        assertThat(SagaStep.REFUND_SOURCE.accountOf(transfer)).isEqualTo(1L);
        assertThat(SagaStep.REFUND_SOURCE.getOperationType()).isEqualTo(OperationType.CREDIT);
    }
}
