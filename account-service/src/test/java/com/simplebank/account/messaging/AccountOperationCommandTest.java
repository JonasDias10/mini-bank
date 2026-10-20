package com.simplebank.account.messaging;

import com.simplebank.account.model.OperationType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccountOperationCommandTest {

    @ParameterizedTest
    @CsvSource({
            "0.01, true",
            "100, true",
            "100.50, true",
            "100.500, true",
            "0, false",
            "-1.00, false",
            "10.123, false"
    })
    void validatesAmount(String amount, boolean expected) {
        AccountOperationCommand command = new AccountOperationCommand(
                UUID.randomUUID(), 1L, OperationType.CREDIT, new BigDecimal(amount));

        assertThat(command.hasValidAmount()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"CREDIT", "DEBIT"})
    void missingAmountIsInvalid(OperationType type) {
        assertThat(new AccountOperationCommand(UUID.randomUUID(), 1L, type, null).hasValidAmount()).isFalse();
    }
}
