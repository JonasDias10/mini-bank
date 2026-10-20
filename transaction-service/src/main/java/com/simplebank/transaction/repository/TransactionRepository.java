package com.simplebank.transaction.repository;

import com.simplebank.transaction.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Page<Transaction> findBySourceAccountIdOrTargetAccountId(Long sourceAccountId, Long targetAccountId,
                                                             Pageable pageable);
}
