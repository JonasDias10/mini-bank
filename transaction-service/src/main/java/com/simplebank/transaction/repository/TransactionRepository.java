package com.simplebank.transaction.repository;

import com.simplebank.transaction.model.Transaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Page<Transaction> findBySourceAccountIdOrTargetAccountId(Long sourceAccountId, Long targetAccountId,
                                                             Pageable pageable);

    // Row lock so a redelivered reply processed in parallel cannot advance the saga twice.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaction t where t.currentOperationId = :operationId")
    Optional<Transaction> findByCurrentOperationIdForUpdate(@Param("operationId") UUID operationId);
}
