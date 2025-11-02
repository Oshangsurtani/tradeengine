package com.example.tradeengine.service;

import com.example.tradeengine.model.EventRecord;
import com.example.tradeengine.repository.EventRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * EventService encapsulates logic for publishing domain events to the
 * append‑only event store.  It serializes event payloads to JSON and
 * writes a new {@link EventRecord} for each occurrence.  If serialization
 * fails, the event is still recorded with a null payload and a warning
 * is logged.  This service provides a single place to handle event
 * recording so matching logic remains focused on domain behavior.
 */
@Service
public class EventService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventService.class);
    private final EventRecordRepository eventRepo;
    private final ObjectMapper objectMapper;

    @Autowired
    public EventService(EventRecordRepository eventRepo, ObjectMapper objectMapper) {
        this.eventRepo = eventRepo;
        this.objectMapper = objectMapper;
    }

    /**
     * Persist a domain event with the given type, aggregate ID and payload object.
     * The payload is serialized to JSON for storage.  Any exceptions
     * encountered during serialization are logged.
     *
     * @param eventType   type of event (e.g. ORDER_CREATED, TRADE_EXECUTED)
     * @param aggregateId ID of the aggregate (order or trade) this event relates to
     * @param payload     optional object representing additional event data
     */
    public void recordEvent(String eventType, String aggregateId, Object payload) {
        String payloadJson = null;
        if (payload != null) {
            try {
                payloadJson = objectMapper.writeValueAsString(payload);
            } catch (JsonProcessingException e) {
                LOGGER.warn("Failed to serialize event payload for {}: {}", eventType, e.getMessage());
            }
        }
        EventRecord record = new EventRecord(eventType, aggregateId, payloadJson);
        eventRepo.save(record);
    }
}