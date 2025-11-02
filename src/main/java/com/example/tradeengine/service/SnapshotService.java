package com.example.tradeengine.service;

import com.example.tradeengine.engine.MatchingEngine;
import com.example.tradeengine.engine.OrderBook;
import com.example.tradeengine.model.Order;
import com.example.tradeengine.model.OrderBookSnapshot;
import com.example.tradeengine.repository.OrderBookSnapshotRepository;
import com.example.tradeengine.repository.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * SnapshotService handles creation and restoration of order book
 * snapshots.  A snapshot captures the current bids and asks for an
 * instrument and can be merged with subsequent events to quickly
 * reconstruct state after a crash.  Snapshots may be created on
 * demand via an admin endpoint or periodically by a scheduler.
 */
@Service
public class SnapshotService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SnapshotService.class);
    private final OrderBookSnapshotRepository snapshotRepo;
    private final OrderRepository orderRepo;
    private final MatchingEngine matchingEngine;
    private final EventReplayService replayService;
    private final ObjectMapper objectMapper;
    private final long snapshotIntervalMillis;

    @Autowired
    public SnapshotService(OrderBookSnapshotRepository snapshotRepo,
                           OrderRepository orderRepo,
                           MatchingEngine matchingEngine,
                           EventReplayService replayService,
                           ObjectMapper objectMapper,
                           @Value("${snapshot.interval.millis:300000}") long snapshotIntervalMillis) {
        this.snapshotRepo = snapshotRepo;
        this.orderRepo = orderRepo;
        this.matchingEngine = matchingEngine;
        this.replayService = replayService;
        this.objectMapper = objectMapper;
        this.snapshotIntervalMillis = snapshotIntervalMillis;
    }

    /**
     * DTO representing a simplified order for snapshot purposes.  Contains
     * only essential fields to reconstruct the order at restore time.
     */
    public static class OrderSnapshot {
        public UUID orderId;
        public String clientId;
        public String instrument;
        public String side;
        public String type;
        public BigDecimal price;
        public BigDecimal quantity;
        public BigDecimal filledQuantity;
        public String status;
        public Instant createdAt;
        public Instant updatedAt;
    }

    /**
     * Container for bids and asks in a snapshot.  Used for JSON
     * serialization.
     */
    public static class OrderBookData {
        public List<OrderSnapshot> bids;
        public List<OrderSnapshot> asks;
    }

    /**
     * Create and persist a snapshot for the specified instrument.  The
     * snapshot captures the current order book state and can be
     * restored later.  Returns the created snapshot or null if no
     * orders are present.
     */
    @Transactional
    public OrderBookSnapshot createSnapshot(String instrument) {
        OrderBook book = matchingEngine.getOrderBook(instrument);
        List<Order> bids = book.getBids();
        List<Order> asks = book.getAsks();
        if (bids.isEmpty() && asks.isEmpty()) {
            LOGGER.info("No orders present for instrument {} – skipping snapshot", instrument);
            return null;
        }
        OrderBookData data = new OrderBookData();
        data.bids = new ArrayList<>();
        data.asks = new ArrayList<>();
        for (Order o : bids) {
            data.bids.add(toSnapshot(o));
        }
        for (Order o : asks) {
            data.asks.add(toSnapshot(o));
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to serialize snapshot for {}: {}", instrument, e.getMessage());
            return null;
        }
        OrderBookSnapshot snapshot = new OrderBookSnapshot(instrument, Instant.now(), json);
        snapshotRepo.save(snapshot);
        LOGGER.info("Created snapshot {} for instrument {} with {} bids and {} asks", snapshot.getSnapshotId(), instrument, bids.size(), asks.size());
        return snapshot;
    }

    /**
     * Convert an Order entity into an OrderSnapshot DTO.
     */
    private OrderSnapshot toSnapshot(Order order) {
        OrderSnapshot snap = new OrderSnapshot();
        snap.orderId = order.getOrderId();
        snap.clientId = order.getClientId();
        snap.instrument = order.getInstrument();
        snap.side = order.getSide();
        snap.type = order.getType();
        snap.price = order.getPrice();
        snap.quantity = order.getQuantity();
        snap.filledQuantity = order.getFilledQuantity();
        snap.status = order.getStatus();
        snap.createdAt = order.getCreatedAt();
        snap.updatedAt = order.getUpdatedAt();
        return snap;
    }

    /**
     * Create snapshots for all currently known instruments.  This method
     * iterates over the matching engine's instrument order books.  It
     * returns the list of created snapshots.
     */
    @Transactional
    public List<OrderBookSnapshot> createSnapshotsForAllInstruments() {
        List<OrderBookSnapshot> snapshots = new ArrayList<>();
        for (String instrument : matchingEngine.getInstrumentNames()) {
            OrderBookSnapshot snap = createSnapshot(instrument);
            if (snap != null) snapshots.add(snap);
        }
        return snapshots;
    }

    /**
     * Restore state from the latest snapshot of the given instrument and
     * apply any subsequent events.  If no snapshot exists, this method
     * does nothing.  Existing orders in the DB and order book for this
     * instrument will be overwritten.
     */
    @Transactional
    public void restoreLatestSnapshot(String instrument) {
        Optional<OrderBookSnapshot> optionalSnapshot = snapshotRepo.findTopByInstrumentOrderByTimestampDesc(instrument);
        if (optionalSnapshot.isEmpty()) {
            LOGGER.info("No snapshot found for {}", instrument);
            return;
        }
        OrderBookSnapshot snapshot = optionalSnapshot.get();
        restoreSnapshot(snapshot);
    }

    /**
     * Restore state from the provided snapshot and apply events that
     * occurred after the snapshot timestamp.  Clears the in‑memory
     * order book for the instrument and overwrites existing orders in
     * the database with those in the snapshot.  Trades are not
     * affected during snapshot restoration; they are updated when
     * replaying events.
     */
    @Transactional
    public void restoreSnapshot(OrderBookSnapshot snapshot) {
        String instrument = snapshot.getInstrument();
        // Clear current order book for this instrument
        matchingEngine.getOrderBook(instrument).clear();
        // Deserialize snapshot data
        OrderBookData data;
        try {
            data = objectMapper.readValue(snapshot.getData(), OrderBookData.class);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to deserialize snapshot {}: {}", snapshot.getSnapshotId(), e.getMessage());
            return;
        }
        // Restore orders from snapshot
        if (data.bids != null) {
            for (OrderSnapshot snap : data.bids) {
                Order order = toOrderEntity(snap);
                orderRepo.save(order);
                if ("open".equals(order.getStatus()) || "partially_filled".equals(order.getStatus())) {
                    matchingEngine.getOrderBook(instrument).addLimitOrder(order);
                }
            }
        }
        if (data.asks != null) {
            for (OrderSnapshot snap : data.asks) {
                Order order = toOrderEntity(snap);
                orderRepo.save(order);
                if ("open".equals(order.getStatus()) || "partially_filled".equals(order.getStatus())) {
                    matchingEngine.getOrderBook(instrument).addLimitOrder(order);
                }
            }
        }
        // Apply events after the snapshot
        replayService.replayAfter(snapshot.getTimestamp());
        LOGGER.info("Restored order book for {} from snapshot {} and merged subsequent events", instrument, snapshot.getSnapshotId());
    }

    /**
     * Restore all instruments that have snapshots.  Iterates over
     * distinct instruments in the snapshot repository and calls
     * restoreLatestSnapshot() for each.
     */
    @Transactional
    public void restoreAllLatestSnapshots() {
        for (String instrument : snapshotRepo.findDistinctInstruments()) {
            restoreLatestSnapshot(instrument);
        }
    }

    /**
     * Convert an OrderSnapshot DTO into an Order entity.  Does not set
     * version field.
     */
    private Order toOrderEntity(OrderSnapshot snap) {
        Order order = new Order();
        order.setOrderId(snap.orderId);
        order.setClientId(snap.clientId);
        order.setInstrument(snap.instrument);
        order.setSide(snap.side);
        order.setType(snap.type);
        order.setPrice(snap.price);
        order.setQuantity(snap.quantity);
        order.setFilledQuantity(snap.filledQuantity);
        order.setStatus(snap.status);
        order.setCreatedAt(snap.createdAt);
        order.setUpdatedAt(snap.updatedAt);
        return order;
    }
}