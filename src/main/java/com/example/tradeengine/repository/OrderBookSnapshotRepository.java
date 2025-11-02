package com.example.tradeengine.repository;

import com.example.tradeengine.model.OrderBookSnapshot;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for order book snapshots.  Provides methods to
 * fetch the latest snapshot per instrument and to list distinct
 * instruments that have snapshots.
 */
@Repository
public interface OrderBookSnapshotRepository extends JpaRepository<OrderBookSnapshot, UUID> {
    /**
     * Find the most recent snapshot for a given instrument.
     */
    Optional<OrderBookSnapshot> findTopByInstrumentOrderByTimestampDesc(String instrument);

    /**
     * Find all snapshots created after a given timestamp for an instrument.
     */
    List<OrderBookSnapshot> findByInstrumentAndTimestampAfter(String instrument, Instant timestamp);

    /**
     * List distinct instruments that have at least one snapshot.
     */
    @Query("select distinct s.instrument from OrderBookSnapshot s")
    List<String> findDistinctInstruments();
}