package com.example.tradeengine.service;

import com.example.tradeengine.engine.MatchingEngine;
import com.example.tradeengine.model.EventRecord;
import com.example.tradeengine.model.Order;
import com.example.tradeengine.model.Trade;
import com.example.tradeengine.repository.EventRecordRepository;
import com.example.tradeengine.repository.OrderRepository;
import com.example.tradeengine.repository.TradeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * EventReplayService can rebuild application state by replaying all
 * persisted events from the event log.  It clears existing orders and
 * trades, empties in‑memory order books, and then applies each
 * event in order to reconstruct the current state.  This method
 * should be invoked in rare recovery scenarios; normal operation
 * persists both the event log and relational tables, so recovery
 * typically only requires loading orders from the DB.
 */
@Service
public class EventReplayService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventReplayService.class);
    private final EventRecordRepository eventRepo;
    private final OrderRepository orderRepo;
    private final TradeRepository tradeRepo;
    private final MatchingEngine matchingEngine;
    private final ObjectMapper objectMapper;

    @Autowired
    public EventReplayService(EventRecordRepository eventRepo,
                              OrderRepository orderRepo,
                              TradeRepository tradeRepo,
                              MatchingEngine matchingEngine,
                              ObjectMapper objectMapper) {
        this.eventRepo = eventRepo;
        this.orderRepo = orderRepo;
        this.tradeRepo = tradeRepo;
        this.matchingEngine = matchingEngine;
        this.objectMapper = objectMapper;
    }

    /**
     * Replay all events from the event log, discarding existing order
     * and trade rows and rebuilding state.  This method is
     * transactional and should be called from an administrative
     * endpoint.
     */
    @Transactional
    public void replay() {
        LOGGER.warn("Replaying events from scratch. Dropping existing orders and trades.");
        orderRepo.deleteAll();
        tradeRepo.deleteAll();
        // Clear in‑memory order books by clearing instrument engines' books.
        matchingEngine.resetOrderBooks();
        List<EventRecord> events = eventRepo.findAll(Sort.by("id"));
        for (EventRecord ev : events) {
            String type = ev.getEventType();
            String aggId = ev.getAggregateId();
            String payload = ev.getPayload();
            try {
                switch (type) {
                    case "ORDER_CREATED":
                        Order order = objectMapper.readValue(payload, Order.class);
                        orderRepo.save(order);
                        // Only open/partial orders belong in order book
                        if ("open".equals(order.getStatus()) || "partially_filled".equals(order.getStatus())) {
                            matchingEngine.getOrderBook(order.getInstrument()).addLimitOrder(order);
                        }
                        break;
                    case "ORDER_UPDATED":
                        Order updated = objectMapper.readValue(payload, Order.class);
                        orderRepo.save(updated);
                        // Adjust order book: if filled/cancelled remove; else add or update
                        if ("open".equals(updated.getStatus()) || "partially_filled".equals(updated.getStatus())) {
                            matchingEngine.getOrderBook(updated.getInstrument()).addLimitOrder(updated);
                        } else {
                            matchingEngine.getOrderBook(updated.getInstrument()).cancelOrder(updated);
                        }
                        break;
                    case "ORDER_CANCELLED":
                        Order cancelled = objectMapper.readValue(payload, Order.class);
                        orderRepo.save(cancelled);
                        matchingEngine.getOrderBook(cancelled.getInstrument()).cancelOrder(cancelled);
                        break;
                    case "TRADE_EXECUTED":
                        Trade trade = objectMapper.readValue(payload, Trade.class);
                        tradeRepo.save(trade);
                        break;
                    default:
                        LOGGER.warn("Unknown event type {} in replay", type);
                }
            } catch (JsonProcessingException e) {
                LOGGER.warn("Failed to deserialize payload for event {} id {}: {}", type, aggId, e.getMessage());
            }
        }
        LOGGER.info("Replay complete: {} events processed", events.size());
    }

    /**
     * Apply events that occurred after the given timestamp to the current
     * state.  Unlike {@link #replay()}, this does not clear existing
     * orders or trades.  It assumes a snapshot has already restored
     * state up to the provided time and only merges newer events.
     *
     * @param timestamp lower bound timestamp (exclusive)
     */
    @Transactional
    public void replayAfter(Instant timestamp) {
        List<EventRecord> events = eventRepo.findByTimestampAfterOrderById(timestamp);
        for (EventRecord ev : events) {
            String type = ev.getEventType();
            String aggId = ev.getAggregateId();
            String payload = ev.getPayload();
            try {
                switch (type) {
                    case "ORDER_CREATED":
                        Order order = objectMapper.readValue(payload, Order.class);
                        orderRepo.save(order);
                        if ("open".equals(order.getStatus()) || "partially_filled".equals(order.getStatus())) {
                            matchingEngine.getOrderBook(order.getInstrument()).addLimitOrder(order);
                        }
                        break;
                    case "ORDER_UPDATED":
                        Order updated = objectMapper.readValue(payload, Order.class);
                        orderRepo.save(updated);
                        if ("open".equals(updated.getStatus()) || "partially_filled".equals(updated.getStatus())) {
                            matchingEngine.getOrderBook(updated.getInstrument()).addLimitOrder(updated);
                        } else {
                            matchingEngine.getOrderBook(updated.getInstrument()).cancelOrder(updated);
                        }
                        break;
                    case "ORDER_CANCELLED":
                        Order cancelled = objectMapper.readValue(payload, Order.class);
                        orderRepo.save(cancelled);
                        matchingEngine.getOrderBook(cancelled.getInstrument()).cancelOrder(cancelled);
                        break;
                    case "TRADE_EXECUTED":
                        Trade trade = objectMapper.readValue(payload, Trade.class);
                        tradeRepo.save(trade);
                        break;
                    default:
                        LOGGER.warn("Unknown event type {} in replayAfter", type);
                }
            } catch (JsonProcessingException e) {
                LOGGER.warn("Failed to deserialize payload for event {} id {} during replayAfter: {}", type, aggId, e.getMessage());
            }
        }
        LOGGER.info("ReplayAfter complete: {} events processed after {}", events.size(), timestamp);
    }
}