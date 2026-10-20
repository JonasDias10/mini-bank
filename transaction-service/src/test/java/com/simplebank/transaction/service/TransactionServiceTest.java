package com.simplebank.transaction.service;

import com.simplebank.transaction.client.AccountClient;
import com.simplebank.transaction.dto.AccountOperationRequest;
import com.simplebank.transaction.dto.TransactionResponse;
import com.simplebank.transaction.dto.TransferRequest;
import com.simplebank.transaction.exception.AccountServiceUnavailableException;
import com.simplebank.transaction.exception.BusinessException;
import com.simplebank.transaction.exception.ResourceNotFoundException;
import com.simplebank.transaction.model.Transaction;
import com.simplebank.transaction.model.TransactionType;
import com.simplebank.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountClient accountClient;

    @InjectMocks
    private TransactionService transactionService;

    private void stubSave() {
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Transaction savedTransaction() {
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void depositCreditsAccountThenRecordsTransaction() {
        stubSave();

        TransactionResponse response = transactionService.deposit(new AccountOperationRequest(1L, AMOUNT));

        InOrder order = inOrder(accountClient, transactionRepository);
        order.verify(accountClient).credit(1L, AMOUNT);
        order.verify(transactionRepository).save(any(Transaction.class));
        assertThat(response.type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(response.targetAccountId()).isEqualTo(1L);
        assertThat(response.sourceAccountId()).isNull();
        assertThat(response.amount()).isEqualByComparingTo(AMOUNT);
    }

    @Test
    void depositRecordsNothingWhenCreditFails() {
        doThrow(new ResourceNotFoundException("Account not found with id 1")).when(accountClient).credit(1L, AMOUNT);

        assertThatThrownBy(() -> transactionService.deposit(new AccountOperationRequest(1L, AMOUNT)))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(transactionRepository);
    }

    @Test
    void withdrawDebitsAccountThenRecordsTransaction() {
        stubSave();

        TransactionResponse response = transactionService.withdraw(new AccountOperationRequest(1L, AMOUNT));

        verify(accountClient).debit(1L, AMOUNT);
        assertThat(response.type()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(response.sourceAccountId()).isEqualTo(1L);
        assertThat(response.targetAccountId()).isNull();
    }

    @Test
    void withdrawRecordsNothingWhenFundsAreInsufficient() {
        doThrow(new BusinessException("Insufficient funds")).when(accountClient).debit(1L, AMOUNT);

        assertThatThrownBy(() -> transactionService.withdraw(new AccountOperationRequest(1L, AMOUNT)))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(transactionRepository);
    }

    @Test
    void transferChecksDestinationDebitsSourceCreditsDestinationThenRecords() {
        stubSave();

        TransactionResponse response = transactionService.transfer(new TransferRequest(1L, 2L, AMOUNT));

        InOrder order = inOrder(accountClient, transactionRepository);
        order.verify(accountClient).assertAccountExists(2L);
        order.verify(accountClient).debit(1L, AMOUNT);
        order.verify(accountClient).credit(2L, AMOUNT);
        order.verify(transactionRepository).save(any(Transaction.class));
        assertThat(response.type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(response.sourceAccountId()).isEqualTo(1L);
        assertThat(response.targetAccountId()).isEqualTo(2L);
    }

    @Test
    void transferToSameAccountIsRejectedBeforeAnyCall() {
        assertThatThrownBy(() -> transactionService.transfer(new TransferRequest(1L, 1L, AMOUNT)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Source and destination accounts must be different");

        verifyNoInteractions(accountClient, transactionRepository);
    }

    @Test
    void transferToMissingAccountDoesNotMoveMoney() {
        doThrow(new ResourceNotFoundException("Account not found with id 2"))
                .when(accountClient).assertAccountExists(2L);

        assertThatThrownBy(() -> transactionService.transfer(new TransferRequest(1L, 2L, AMOUNT)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(accountClient, never()).debit(any(), any());
        verify(accountClient, never()).credit(any(), any());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void transferStopsWhenDebitFails() {
        doThrow(new BusinessException("Insufficient funds")).when(accountClient).debit(1L, AMOUNT);

        assertThatThrownBy(() -> transactionService.transfer(new TransferRequest(1L, 2L, AMOUNT)))
                .isInstanceOf(BusinessException.class);

        verify(accountClient, never()).credit(any(), any());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void transferRefundsSourceWhenCreditFails() {
        AccountServiceUnavailableException creditError = new AccountServiceUnavailableException("down");
        doThrow(creditError).when(accountClient).credit(2L, AMOUNT);

        assertThatThrownBy(() -> transactionService.transfer(new TransferRequest(1L, 2L, AMOUNT)))
                .isSameAs(creditError);

        verify(accountClient).credit(1L, AMOUNT);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void transferRethrowsOriginalErrorWhenRefundAlsoFails() {
        AccountServiceUnavailableException creditError = new AccountServiceUnavailableException("credit failed");
        doThrow(creditError).when(accountClient).credit(2L, AMOUNT);
        doThrow(new AccountServiceUnavailableException("refund failed")).when(accountClient).credit(1L, AMOUNT);

        assertThatThrownBy(() -> transactionService.transfer(new TransferRequest(1L, 2L, AMOUNT)))
                .isSameAs(creditError);

        verifyNoInteractions(transactionRepository);
    }

    @Test
    void savedTransferHasCorrectAccounts() {
        stubSave();

        transactionService.transfer(new TransferRequest(1L, 2L, AMOUNT));

        Transaction saved = savedTransaction();
        assertThat(saved.getSourceAccountId()).isEqualTo(1L);
        assertThat(saved.getTargetAccountId()).isEqualTo(2L);
        assertThat(saved.getAmount()).isEqualByComparingTo(AMOUNT);
    }

    @Test
    void getTransactionThrowsWhenNotFound() {
        when(transactionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getTransaction(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Transaction not found with id 99");
    }

    @Test
    void getTransactionReturnsTransaction() {
        when(transactionRepository.findById(5L)).thenReturn(Optional.of(Transaction.deposit(1L, AMOUNT)));

        TransactionResponse response = transactionService.getTransaction(5L);

        assertThat(response.type()).isEqualTo(TransactionType.DEPOSIT);
    }

    @Test
    void getTransactionsByAccountSearchesBothSides() {
        Pageable pageable = PageRequest.of(0, 20);
        when(transactionRepository.findBySourceAccountIdOrTargetAccountId(1L, 1L, pageable))
                .thenReturn(new PageImpl<>(List.of(
                        Transaction.transfer(1L, 2L, AMOUNT),
                        Transaction.deposit(1L, AMOUNT))));

        List<TransactionResponse> history = transactionService.getTransactionsByAccount(1L, pageable);

        assertThat(history).extracting(TransactionResponse::type)
                .containsExactly(TransactionType.TRANSFER, TransactionType.DEPOSIT);
    }
}
