package com.simplebank.transaction.service;

import com.simplebank.transaction.dto.AccountOperationRequest;
import com.simplebank.transaction.dto.TransactionResponse;
import com.simplebank.transaction.dto.TransferRequest;
import com.simplebank.transaction.exception.BusinessException;
import com.simplebank.transaction.exception.ResourceNotFoundException;
import com.simplebank.transaction.model.Transaction;
import com.simplebank.transaction.model.TransactionStatus;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionSaga transactionSaga;

    @InjectMocks
    private TransactionService transactionService;

    private void stubSave() {
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Transaction startedTransaction() {
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionSaga).start(captor.capture());
        return captor.getValue();
    }

    @Test
    void depositSavesPendingTransactionThenStartsSaga() {
        stubSave();

        TransactionResponse response = transactionService.deposit(new AccountOperationRequest(1L, AMOUNT));

        InOrder order = inOrder(transactionRepository, transactionSaga);
        order.verify(transactionRepository).save(any(Transaction.class));
        order.verify(transactionSaga).start(any(Transaction.class));
        assertThat(response.status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(response.type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(response.targetAccountId()).isEqualTo(1L);
    }

    @Test
    void withdrawStartsWithdrawalSaga() {
        stubSave();

        transactionService.withdraw(new AccountOperationRequest(1L, AMOUNT));

        Transaction started = startedTransaction();
        assertThat(started.getType()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(started.getSourceAccountId()).isEqualTo(1L);
    }

    @Test
    void transferStartsTransferSaga() {
        stubSave();

        transactionService.transfer(new TransferRequest(1L, 2L, AMOUNT));

        Transaction started = startedTransaction();
        assertThat(started.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(started.getSourceAccountId()).isEqualTo(1L);
        assertThat(started.getTargetAccountId()).isEqualTo(2L);
    }

    @Test
    void transferToSameAccountIsRejectedBeforeSaving() {
        assertThatThrownBy(() -> transactionService.transfer(new TransferRequest(1L, 1L, AMOUNT)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Source and destination accounts must be different");

        verifyNoInteractions(transactionRepository, transactionSaga);
    }

    @Test
    void getTransactionThrowsWhenNotFound() {
        when(transactionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getTransaction(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Transaction not found with id 99");
    }

    @Test
    void getTransactionReturnsFailureReason() {
        Transaction failed = Transaction.withdrawal(1L, AMOUNT);
        failed.fail("Insufficient funds in account 1000000000");
        when(transactionRepository.findById(5L)).thenReturn(Optional.of(failed));

        TransactionResponse response = transactionService.getTransaction(5L);

        assertThat(response.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(response.failureReason()).isEqualTo("Insufficient funds in account 1000000000");
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
