package com.simplebank.transaction.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionTest {

    private static final BigDecimal AMOUNT = new BigDecimal("10.00");

    @Test
    void depositOnlyHasTargetAccount() {
        Transaction transaction = Transaction.deposit(1L, AMOUNT);

        assertThat(transaction.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(transaction.getSourceAccountId()).isNull();
        assertThat(transaction.getTargetAccountId()).isEqualTo(1L);
        assertThat(transaction.getAmount()).isEqualByComparingTo(AMOUNT);
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
}
