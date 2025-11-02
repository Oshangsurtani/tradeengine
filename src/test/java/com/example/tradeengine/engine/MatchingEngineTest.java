package com.example.tradeengine.engine;

import com.example.tradeengine.model.Order;
import com.example.tradeengine.model.Trade;
import com.example.tradeengine.repository.OrderRepository;
import com.example.tradeengine.repository.TradeRepository;
import com.example.tradeengine.service.EventService;
import com.example.tradeengine.service.StreamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the {@link MatchingEngine}.  This test
 * exercises the matching logic by submitting limit and market orders
 * and asserting that trades are produced and persisted correctly.
 * The Spring application context is started with an in‑memory
 * database and Redis auto‑configured so that repositories work as
 * expected.  EventService and StreamService are mocked to isolate
 * side effects.
 */
@SpringBootTest
public class MatchingEngineTest {
    @Autowired
    private MatchingEngine matchingEngine;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private TradeRepository tradeRepository;
    @Autowired
    private RedisTemplate<String, Order> redisTemplate;
    @Autowired
    private EventService eventService;
    @Autowired
    private StreamService streamService;

    @Test
    public void testLimitOrderMatchesMarketOrder() throws Exception {
        // Clean database
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        // 1. Submit a limit ask (sell) order of 1 BTC at price 100
        Order ask = new Order();
        ask.setOrderId(UUID.randomUUID());
        ask.setClientId("seller");
        ask.setInstrument("BTC-USD");
        ask.setSide("sell");
        ask.setType("limit");
        ask.setPrice(new BigDecimal("100"));
        ask.setQuantity(new BigDecimal("1"));
        // Submit the order and wait for completion
        CompletableFuture<Order> fut1 = matchingEngine.submitOrder(ask, null);
        Order savedAsk = fut1.get(2, TimeUnit.SECONDS);
        assertEquals("open", savedAsk.getStatus());
        // 2. Submit a market buy order for 1 BTC; should match with ask
        Order buy = new Order();
        buy.setOrderId(UUID.randomUUID());
        buy.setClientId("buyer");
        buy.setInstrument("BTC-USD");
        buy.setSide("buy");
        buy.setType("market");
        buy.setQuantity(new BigDecimal("1"));
        CompletableFuture<Order> fut2 = matchingEngine.submitOrder(buy, null);
        Order filledBuy = fut2.get(2, TimeUnit.SECONDS);
        // After matching, both orders should be filled
        assertEquals("filled", filledBuy.getStatus());
        Order updatedAsk = orderRepository.findById(savedAsk.getOrderId()).orElseThrow();
        assertEquals("filled", updatedAsk.getStatus());
        // A trade should have been recorded
        assertEquals(1, tradeRepository.count());
        Trade trade = tradeRepository.findAll().get(0);
        // The buy order ID should be first in trade when buyer is taker
        assertEquals(filledBuy.getOrderId(), trade.getBuyOrderId());
        assertEquals(updatedAsk.getOrderId(), trade.getSellOrderId());
        assertEquals(new BigDecimal("100"), trade.getPrice());
        assertEquals(new BigDecimal("1"), trade.getQuantity());
    }

    @Test
    public void testIdempotentSubmission() throws Exception {
        // Clean
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        // Create a simple limit order
        Order order = new Order();
        order.setOrderId(UUID.randomUUID());
        order.setClientId("c");
        order.setInstrument("BTC-USD");
        order.setSide("buy");
        order.setType("limit");
        order.setPrice(new BigDecimal("50"));
        order.setQuantity(new BigDecimal("1"));
        String idKey = "idem-1";
        CompletableFuture<Order> f1 = matchingEngine.submitOrder(order, idKey);
        Order first = f1.get(2, TimeUnit.SECONDS);
        // Submit again with the same idempotency key; should return the same order
        CompletableFuture<Order> f2 = matchingEngine.submitOrder(order, idKey);
        Order second = f2.get(2, TimeUnit.SECONDS);
        assertEquals(first.getOrderId(), second.getOrderId());
        assertEquals(1, orderRepository.count());
    }
}