package com.example.tradeengine.model;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * OrderBookSnapshot stores a serialized representation of an order
 * book at a point in time.  Snapshots speed up recovery by allowing
 * the system to load the latest snapshot and then apply events that
 * occurred after the snapshot timestamp.  Each snapshot is specific
 * to a single instrument.
 */
@Entity
@Table(name = "orderbook_snapshots")
public class OrderBookSnapshot {
    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "snapshot_id", updatable = false, nullable = false)
    private UUID snapshotId;

    @Column(name = "instrument", nullable = false)
    private String instrument;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Lob
    @Column(name = "data", nullable = false, columnDefinition = "TEXT")
    private String data;

    public OrderBookSnapshot() {}

    public OrderBookSnapshot(String instrument, Instant timestamp, String data) {
        this.instrument = instrument;
        this.timestamp = timestamp;
        this.data = data;
    }

    public UUID getSnapshotId() { return snapshotId; }
    public void setSnapshotId(UUID snapshotId) { this.snapshotId = snapshotId; }
    public String getInstrument() { return instrument; }
    public void setInstrument(String instrument) { this.instrument = instrument; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
}