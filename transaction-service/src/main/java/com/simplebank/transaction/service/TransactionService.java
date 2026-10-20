package com.simplebank.transaction.service;

import com.simplebank.transaction.client.AccountClient;
import com.simplebank.transaction.dto.AccountOperationRequest;
import com.simplebank.transaction.dto.TransactionResponse;
import com.simplebank.transaction.dto.TransferRequest;
import com.simplebank.transaction.exception.BusinessException;
import com.simplebank.transaction.exception.ResourceNotFoundException;
import com.simplebank.transaction.model.Transaction;
import com.simplebank.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final AccountClient accountClient;

    public TransactionService(TransactionRepository transactionRepository, AccountClient accountClient) {
        this.transactionRepository = transactionRepository;
        this.accountClient = accountClient;
    }

    public TransactionResponse deposit(AccountOperationRequest request) {
        accountClient.credit(request.accountId(), request.amount());
        return save(Transaction.deposit(request.accountId(), request.amount()));
    }

    public TransactionResponse withdraw(AccountOperationRequest request) {
        accountClient.debit(request.accountId(), request.amount());
        return save(Transaction.withdrawal(request.accountId(), request.amount()));
    }

    public TransactionResponse transfer(TransferRequest request) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new BusinessException("Source and destination accounts must be different");
        }

        // Fail fast before any money moves if the destination does not exist.
        accountClient.assertAccountExists(request.toAccountId());
        accountClient.debit(request.fromAccountId(), request.amount());

        try {
            accountClient.credit(request.toAccountId(), request.amount());
        } catch (RuntimeException ex) {
            refund(request);
            throw ex;
        }

        return save(Transaction.transfer(request.fromAccountId(), request.toAccountId(), request.amount()));
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

    // The credit failed after the debit succeeded, so give the money back to the source account.
    private void refund(TransferRequest request) {
        try {
            accountClient.credit(request.fromAccountId(), request.amount());
        } catch (RuntimeException refundError) {
            log.error("Refund failed for account {} amount {}. Manual correction required.",
                    request.fromAccountId(), request.amount(), refundError);
        }
    }

    private TransactionResponse save(Transaction transaction) {
        return TransactionResponse.from(transactionRepository.save(transaction));
    }
}
