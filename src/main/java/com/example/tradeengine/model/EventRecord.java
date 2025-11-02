package com.example.tradeengine.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * EventRecord represents a domain event stored in the append‑only event log.
 * Each event has a type, aggregate identifier (e.g. order ID), optional
 * payload encoded as JSON, and timestamp.  Events are ordered by their
 * generated ID and timestamp.
 */
@Entity
@Table(name = "events")
public class EventRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_type", nullable = false)
    private String eventType;
    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;
    @Lob
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;
    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;
    public EventRecord() {}
    public EventRecord(String eventType, String aggregateId, String payload) {
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}