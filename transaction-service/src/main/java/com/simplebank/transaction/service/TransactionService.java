package com.simplebank.transaction.service;

import com.simplebank.transaction.dto.AccountOperationRequest;
import com.simplebank.transaction.dto.TransactionResponse;
import com.simplebank.transaction.dto.TransferRequest;
import com.simplebank.transaction.exception.BusinessException;
import com.simplebank.transaction.exception.ResourceNotFoundException;
import com.simplebank.transaction.model.Transaction;
import com.simplebank.transaction.repository.TransactionRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionSaga transactionSaga;

    public TransactionService(TransactionRepository transactionRepository, TransactionSaga transactionSaga) {
        this.transactionRepository = transactionRepository;
        this.transactionSaga = transactionSaga;
    }

    @Transactional
    public TransactionResponse deposit(AccountOperationRequest request) {
        return start(Transaction.deposit(request.accountId(), request.amount()));
    }

    @Transactional
    public TransactionResponse withdraw(AccountOperationRequest request) {
        return start(Transaction.withdrawal(request.accountId(), request.amount()));
    }

    @Transactional
    public TransactionResponse transfer(TransferRequest request) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new BusinessException("Source and destination accounts must be different");
        }

        return start(Transaction.transfer(request.fromAccountId(), request.toAccountId(), request.amount()));
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(Long id) {
        return transactionRepository.findById(id)
                .map(TransactionResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id " + id));
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsByAccount(Long accountId, Pageable pageable) {
        return transactionRepository.findBySourceAccountIdOrTargetAccountId(accountId, accountId, pageable)
                .map(TransactionResponse::from)
                .getContent();
    }

    // The transaction row and the first command are committed together; the money moves asynchronously.
    private TransactionResponse start(Transaction transaction) {
        Transaction saved = transactionRepository.save(transaction);
        transactionSaga.start(saved);
        return TransactionResponse.from(saved);
    }
}
