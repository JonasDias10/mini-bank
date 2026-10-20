package com.simplebank.account.repository;

import com.simplebank.account.model.ProcessedOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedOperationRepository extends JpaRepository<ProcessedOperation, UUID> {
}
