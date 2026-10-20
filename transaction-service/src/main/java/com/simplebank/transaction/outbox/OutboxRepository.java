package com.simplebank.transaction.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {

    // SKIP LOCKED lets several instances of the service relay in parallel without sending the same row twice.
    @Query(value = """
            select * from outbox_messages
            where sent_at is null
            order by id
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<OutboxMessage> findBatchToSend(@Param("limit") int limit);
}
