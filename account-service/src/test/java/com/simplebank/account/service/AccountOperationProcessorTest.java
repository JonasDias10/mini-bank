package com.simplebank.account.service;

import com.simplebank.account.messaging.AccountOperationCommand;
import com.simplebank.account.messaging.AccountOperationResult;
import com.simplebank.account.messaging.MessagingConfig;
import com.simplebank.account.model.OperationType;
import com.simplebank.account.model.ProcessedOperation;
import com.simplebank.account.outbox.OutboxService;
import com.simplebank.account.repository.ProcessedOperationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountOperationProcessorTest {

    private static final UUID OPERATION_ID = UUID.fromString("7f3c2a3e-4c2d-4b1a-9a59-0b3c1d2e4f50");

    @Mock
    private AccountService accountService;

    @Mock
    private ProcessedOperationRepository processedOperationRepository;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private AccountOperationProcessor processor;

    private AccountOperationResult enqueuedResult() {
        ArgumentCaptor<AccountOperationResult> captor = ArgumentCaptor.forClass(AccountOperationResult.class);
        verify(outboxService).enqueue(eq(MessagingConfig.RESULTS_ROUTING_KEY), captor.capture());
        return captor.getValue();
    }

    @Test
    void appliesNewOperationMarksItProcessedAndRepliesSuccess() {
        AccountOperationCommand command = new AccountOperationCommand(
                OPERATION_ID, 1L, OperationType.CREDIT, new BigDecimal("50.00"));
        when(processedOperationRepository.existsById(OPERATION_ID)).thenReturn(false);
        when(accountService.applyOperation(1L, OperationType.CREDIT, new BigDecimal("50.00")))
                .thenReturn(OperationResult.succeeded());

        processor.process(command);

        ArgumentCaptor<ProcessedOperation> processed = ArgumentCaptor.forClass(ProcessedOperation.class);
        verify(processedOperationRepository).save(processed.capture());
        assertThat(processed.getValue().getOperationId()).isEqualTo(OPERATION_ID);
        assertThat(enqueuedResult()).isEqualTo(new AccountOperationResult(OPERATION_ID, true, null));
    }

    @Test
    void repliesBusinessFailureAsResult() {
        AccountOperationCommand command = new AccountOperationCommand(
                OPERATION_ID, 1L, OperationType.DEBIT, new BigDecimal("500.00"));
        when(processedOperationRepository.existsById(OPERATION_ID)).thenReturn(false);
        when(accountService.applyOperation(any(), any(), any()))
                .thenReturn(OperationResult.failed("Insufficient funds in account 1000000000"));

        processor.process(command);

        assertThat(enqueuedResult())
                .isEqualTo(new AccountOperationResult(OPERATION_ID, false, "Insufficient funds in account 1000000000"));
    }

    @Test
    void skipsOperationThatWasAlreadyProcessed() {
        AccountOperationCommand command = new AccountOperationCommand(
                OPERATION_ID, 1L, OperationType.CREDIT, new BigDecimal("50.00"));
        when(processedOperationRepository.existsById(OPERATION_ID)).thenReturn(true);

        processor.process(command);

        verifyNoInteractions(accountService, outboxService);
    }

    @Test
    void rejectsInvalidAmountWithoutTouchingTheAccount() {
        AccountOperationCommand command = new AccountOperationCommand(
                OPERATION_ID, 1L, OperationType.CREDIT, new BigDecimal("-5.00"));
        when(processedOperationRepository.existsById(OPERATION_ID)).thenReturn(false);

        processor.process(command);

        verifyNoInteractions(accountService);
        assertThat(enqueuedResult().success()).isFalse();
    }
}
