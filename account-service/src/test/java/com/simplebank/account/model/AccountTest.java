package com.simplebank.account.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AccountTest {

    private Account account;

    @BeforeEach
    void setUp() {
        account = new Account("1000000000", new Customer("Ana Souza", "ana@example.com"));
    }

    @Test
    void newAccountStartsWithZeroBalance() {
        assertThat(account.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void creditAddsToBalance() {
        account.credit(new BigDecimal("100.50"));
        account.credit(new BigDecimal("0.50"));

        assertThat(account.getBalance()).isEqualByComparingTo("101.00");
    }

    @Test
    void debitSubtractsFromBalance() {
        account.credit(new BigDecimal("100.00"));

        account.debit(new BigDecimal("30.25"));

        assertThat(account.getBalance()).isEqualByComparingTo("69.75");
    }

    @Test
    void hasEnoughBalanceIsTrueWhenBalanceCoversAmount() {
        account.credit(new BigDecimal("50.00"));

        assertThat(account.hasEnoughBalance(new BigDecimal("49.99"))).isTrue();
        assertThat(account.hasEnoughBalance(new BigDecimal("50.00"))).isTrue();
    }

    @Test
    void hasEnoughBalanceIsFalseWhenAmountExceedsBalance() {
        account.credit(new BigDecimal("50.00"));

        assertThat(account.hasEnoughBalance(new BigDecimal("50.01"))).isFalse();
    }
}
