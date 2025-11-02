package com.example.tradeengine.engine;

import com.example.tradeengine.model.Order;
import com.example.tradeengine.model.Trade;
import com.example.tradeengine.repository.OrderRepository;
import com.example.tradeengine.repository.TradeRepository;
import com.example.tradeengine.service.StreamService;
import com.example.tradeengine.service.EventService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Matching engine that supports multiple instruments.  Each instrument
 * has its own OrderBook, queue and worker thread.  Submissions are
 * partitioned by instrument so that matching for different instruments
 * proceeds concurrently.
 */
@Service
public class MatchingEngine {
    private final OrderRepository orderRepo;
    private final TradeRepository tradeRepo;
    private final RedisTemplate<String, Order> redisTemplate;
    private final MeterRegistry meterRegistry;
    private final StreamService streamService;
    private final EventService eventService;
    private final Counter ordersReceived;
    private final Counter ordersMatched;
    private final Counter ordersRejected;
    private final Timer orderLatency;
    private final Gauge orderBookDepth;
    private final ConcurrentMap<String, InstrumentEngine> instrumentEngines = new ConcurrentHashMap<>();

    @Autowired
    public MatchingEngine(OrderRepository orderRepo,
                          TradeRepository tradeRepo,
                          RedisTemplate<String, Order> redisTemplate,
                          MeterRegistry meterRegistry,
                          StreamService streamService,
                          EventService eventService) {
        this.orderRepo = orderRepo;
        this.tradeRepo = tradeRepo;
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        this.streamService = streamService;
        this.eventService = eventService;
        this.ordersReceived = meterRegistry.counter("orders_received_total");
        this.ordersMatched = meterRegistry.counter("orders_matched_total");
        this.ordersRejected = meterRegistry.counter("orders_rejected_total");
        this.orderLatency = meterRegistry.timer("order_latency_seconds");
        // Gauge for current order book depth (sum of bids and asks across all instruments)
        this.orderBookDepth = Gauge.builder("current_orderbook_depth", instrumentEngines,
            map -> map.values().stream()
                .mapToInt(engine -> engine.getOrderBook().getBids().size() + engine.getOrderBook().getAsks().size())
                .sum())
            .register(meterRegistry);
    }

    @PostConstruct
    public void init() {
        // On startup, load open orders grouped by instrument into their respective engines
        orderRepo.findAll().stream()
            .filter(o -> "open".equals(o.getStatus()) || "partially_filled".equals(o.getStatus()))
            .forEach(o -> {
                InstrumentEngine eng = instrumentEngines.computeIfAbsent(o.getInstrument(), this::createEngine);
                eng.loadOpenOrder(o);
            });
    }

    /**
     * Submit a new order.  Partitions by instrument.
     */
    public CompletableFuture<Order> submitOrder(Order order, String idempotencyKey) {
        ordersReceived.increment();
        InstrumentEngine eng = instrumentEngines.computeIfAbsent(order.getInstrument(), this::createEngine);
        return eng.submitOrder(order, idempotencyKey);
    }

    /**
     * Cancel an existing order.  Looks up the order to determine its instrument.
     */
    public CompletableFuture<Order> cancelOrder(UUID orderId) {
        CompletableFuture<Order> fut = new CompletableFuture<>();
        orderRepo.findById(orderId).ifPresentOrElse(order -> {
            InstrumentEngine eng = instrumentEngines.computeIfAbsent(order.getInstrument(), this::createEngine);
            eng.cancelOrder(order, fut);
        }, () -> fut.complete(null));
        return fut;
    }

    /**
     * Get the order book for a specific instrument.  Creates one if absent.
     */
    public OrderBook getOrderBook(String instrument) {
        return instrumentEngines.computeIfAbsent(instrument, this::createEngine).getOrderBook();
    }

    private InstrumentEngine createEngine(String instrument) {
        return new InstrumentEngine(instrument);
    }

    /**
     * Inner class handling matching for a single instrument.
     */
    private class InstrumentEngine {
        private final String instrument;
        private final OrderBook orderBook;
        private final BlockingQueue<InstrumentEvent> queue;
        private final Thread worker;
        InstrumentEngine(String instrument) {
            this.instrument = instrument;
            this.orderBook = new OrderBook();
            this.queue = new LinkedBlockingQueue<>();
            this.worker = new Thread(this::processLoop, "matching-engine-" + instrument);
            this.worker.setDaemon(true);
            this.worker.start();
        }

        /**
         * Clear this instrument's in‑memory order book.  Used during
         * recovery to reset state.
         */
        void clearOrderBook() {
            this.orderBook.clear();
        }
        void loadOpenOrder(Order order) {
            // Insert into order book for this instrument
            orderBook.addLimitOrder(order);
        }
        OrderBook getOrderBook() {
            return orderBook;
        }
        CompletableFuture<Order> submitOrder(Order order, String idempotencyKey) {
            CompletableFuture<Order> fut = new CompletableFuture<>();
            queue.add(new SubmitEvent(order, idempotencyKey, fut));
            return fut;
        }
        void cancelOrder(Order order, CompletableFuture<Order> fut) {
            queue.add(new CancelEvent(order, fut));
        }
        private void processLoop() {
            while (true) {
                try {
                    InstrumentEvent ev = queue.take();
                    ev.run();
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
        private abstract class InstrumentEvent { abstract void run(); }
        private class SubmitEvent extends InstrumentEvent {
            final Order order;
            final String idKey;
            final CompletableFuture<Order> fut;
            SubmitEvent(Order order, String idKey, CompletableFuture<Order> fut) {
                this.order = order;
                this.idKey = idKey;
                this.fut = fut;
            }
            @Override
            void run() {
                long start = System.nanoTime();
                // Idempotency check in Redis
                if (idKey != null && !idKey.isEmpty()) {
                    Order cached = redisTemplate.opsForValue().get(idKey);
                    if (cached != null) {
                        fut.complete(cached);
                        return;
                    }
                }
                // Persist order
                order.setStatus("open");
                order.setFilledQuantity(BigDecimal.ZERO);
                order.setCreatedAt(Instant.now());
                order.setUpdatedAt(order.getCreatedAt());
                Order saved = orderRepo.save(order);
                // Record creation event
                eventService.recordEvent("ORDER_CREATED", saved.getOrderId().toString(), saved);
                // Matching logic
                List<Order> opposite = order.getSide().equalsIgnoreCase("buy") ? orderBook.getAsks() : orderBook.getBids();
                BigDecimal remaining = order.getQuantity();
                while (remaining.compareTo(BigDecimal.ZERO) > 0 && !opposite.isEmpty()) {
                    Order best = opposite.get(0);
                    // Price check for limit orders
                    boolean priceOk = true;
                    if (order.getType().equalsIgnoreCase("limit")) {
                        if (order.getSide().equalsIgnoreCase("buy") && order.getPrice().compareTo(best.getPrice()) < 0) {
                            priceOk = false;
                        }
                        if (order.getSide().equalsIgnoreCase("sell") && order.getPrice().compareTo(best.getPrice()) > 0) {
                            priceOk = false;
                        }
                    }
                    if (!priceOk) break;
                    BigDecimal bestRem = best.getQuantity().subtract(best.getFilledQuantity());
                    BigDecimal tradeQty = remaining.min(bestRem);
                    Trade trade = new Trade(
                        order.getSide().equalsIgnoreCase("buy") ? saved.getOrderId() : best.getOrderId(),
                        order.getSide().equalsIgnoreCase("buy") ? best.getOrderId() : saved.getOrderId(),
                        best.getPrice(),
                        tradeQty,
                        Instant.now()
                    );
                    tradeRepo.save(trade);
                    // Record trade event
                    eventService.recordEvent("TRADE_EXECUTED", trade.getTradeId().toString(), trade);
                    ordersMatched.increment();
                    streamService.sendEvent(trade);
                    remaining = remaining.subtract(tradeQty);
                    order.setFilledQuantity(order.getFilledQuantity().add(tradeQty));
                    best.setFilledQuantity(best.getFilledQuantity().add(tradeQty));
                    if (best.getFilledQuantity().compareTo(best.getQuantity()) >= 0) {
                        best.setStatus("filled");
                        orderBook.cancelOrder(best);
                    } else {
                        best.setStatus("partially_filled");
                    }
                    if (order.getFilledQuantity().compareTo(order.getQuantity()) >= 0) {
                        order.setStatus("filled");
                    } else {
                        order.setStatus("partially_filled");
                    }
                    best.setUpdatedAt(Instant.now());
                    Order persistedBest = orderRepo.save(best);
                    // Record best order update event
                    eventService.recordEvent("ORDER_UPDATED", persistedBest.getOrderId().toString(), persistedBest);
                    streamService.sendEvent(persistedBest);
                }
                if (order.getType().equalsIgnoreCase("limit") && order.getFilledQuantity().compareTo(order.getQuantity()) < 0) {
                    orderBook.addLimitOrder(order);
                }
                saved = orderRepo.save(order);
                eventService.recordEvent("ORDER_UPDATED", saved.getOrderId().toString(), saved);
                streamService.sendEvent(saved);
                if (idKey != null && !idKey.isEmpty()) {
                    redisTemplate.opsForValue().set(idKey, saved);
                }
                long end = System.nanoTime();
                orderLatency.record((end - start), TimeUnit.NANOSECONDS);
                fut.complete(saved);
            }
        }
        private class CancelEvent extends InstrumentEvent {
            final Order order;
            final CompletableFuture<Order> fut;
            CancelEvent(Order order, CompletableFuture<Order> fut) {
                this.order = order;
                this.fut = fut;
            }
            @Override
            void run() {
                if ("open".equals(order.getStatus()) || "partially_filled".equals(order.getStatus())) {
                    order.setStatus("cancelled");
                    order.setUpdatedAt(Instant.now());
                    Order persisted = orderRepo.save(order);
                    // Record cancellation event
                    eventService.recordEvent("ORDER_CANCELLED", persisted.getOrderId().toString(), persisted);
                    orderBook.cancelOrder(order);
                    streamService.sendEvent(order);
                    fut.complete(order);
                } else {
                    fut.complete(order);
                }
            }
        }
    }

    /**
     * Return the set of instrument names currently known to the matching engine.
     * This includes instruments that have active order books or have had
     * orders submitted previously.  Useful for tasks like snapshot creation.
     */
    public java.util.Set<String> getInstrumentNames() {
        return instrumentEngines.keySet();
    }

    /**
     * Clear all in‑memory order books and instrument queues.  This should
     * only be called during recovery scenarios when the database is
     * being rebuilt from an event log.  Existing instrument engines
     * remain but their order books are emptied.
     */
    public void resetOrderBooks() {
        instrumentEngines.forEach((instr, eng) -> eng.clearOrderBook());
    }
}