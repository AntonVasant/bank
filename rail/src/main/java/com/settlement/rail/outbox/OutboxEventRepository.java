package com.settlement.rail.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query(
            value = "SELECT * FROM outbox_events WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED",
            nativeQuery = true
    )
    List<OutboxEvent> findPendingForUpdate(@Param("limit") int limit);

    @Query("SELECT e FROM OutboxEvent e WHERE e.status = com.settlement.rail.outbox.OutboxEventStatus.PROCESSING AND e.processingStartedAt < :cutoff")
    List<OutboxEvent> findStuckProcessingEvents(@Param("cutoff") Instant cutoff);

    List<OutboxEvent> findAllByAggregateTypeAndAggregateId(String aggregateType, String aggregateId);
}
