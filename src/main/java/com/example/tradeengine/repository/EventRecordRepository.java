package com.example.tradeengine.repository;

import com.example.tradeengine.model.EventRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for persisting domain events.  This append‑only log stores
 * all order and trade events for auditability and recovery.  Events
 * should never be deleted or updated once written.
 */
@Repository
public interface EventRecordRepository extends JpaRepository<EventRecord, Long> {

    /**
     * Fetch events that occurred after the given timestamp ordered by ID.  This
     * is used for merging snapshots with subsequent events during
     * recovery.
     *
     * @param timestamp the exclusive lower bound for event timestamps
     * @return list of events after the timestamp
     */
    List<EventRecord> findByTimestampAfterOrderById(Instant timestamp);
}