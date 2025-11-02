package com.example.tradeengine.engine;

import com.example.tradeengine.model.Order;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link OrderBook}.  These tests verify that bids are
 * sorted by descending price and ascending timestamp, asks are sorted
 * by ascending price and ascending timestamp, and orders can be
 * cancelled correctly.
 */
public class OrderBookTest {
    private Order createOrder(String side, double price, long epochMillis) {
        Order o = new Order();
        o.setOrderId(UUID.randomUUID());
        o.setClientId("client");
        o.setInstrument("BTC-USD");
        o.setSide(side);
        o.setType("limit");
        o.setPrice(BigDecimal.valueOf(price));
        o.setQuantity(BigDecimal.ONE);
        o.setFilledQuantity(BigDecimal.ZERO);
        o.setStatus("open");
        Instant ts = Instant.ofEpochMilli(epochMillis);
        o.setCreatedAt(ts);
        o.setUpdatedAt(ts);
        return o;
    }

    @Test
    public void testAddBidsSortedByPriceThenTime() {
        OrderBook book = new OrderBook();
        Order o1 = createOrder("buy", 100.0, 1000);
        Order o2 = createOrder("buy", 101.0, 2000);
        Order o3 = createOrder("buy", 100.0, 500);
        book.addLimitOrder(o1);
        book.addLimitOrder(o2);
        book.addLimitOrder(o3);
        List<Order> bids = book.getBids();
        // Highest price first
        assertEquals(o2.getOrderId(), bids.get(0).getOrderId());
        // For equal price, earlier timestamp first
        assertEquals(o3.getOrderId(), bids.get(1).getOrderId());
        assertEquals(o1.getOrderId(), bids.get(2).getOrderId());
    }

    @Test
    public void testAddAsksSortedByPriceThenTime() {
        OrderBook book = new OrderBook();
        Order o1 = createOrder("sell", 100.0, 1000);
        Order o2 = createOrder("sell", 99.0, 2000);
        Order o3 = createOrder("sell", 100.0, 500);
        book.addLimitOrder(o1);
        book.addLimitOrder(o2);
        book.addLimitOrder(o3);
        List<Order> asks = book.getAsks();
        // Lowest price first
        assertEquals(o2.getOrderId(), asks.get(0).getOrderId());
        // For equal price, earlier timestamp first
        assertEquals(o3.getOrderId(), asks.get(1).getOrderId());
        assertEquals(o1.getOrderId(), asks.get(2).getOrderId());
    }

    @Test
    public void testCancelOrderRemovesFromBook() {
        OrderBook book = new OrderBook();
        Order o1 = createOrder("buy", 100.0, 0);
        Order o2 = createOrder("sell", 101.0, 0);
        book.addLimitOrder(o1);
        book.addLimitOrder(o2);
        assertTrue(book.getBids().contains(o1));
        assertTrue(book.getAsks().contains(o2));
        assertTrue(book.cancelOrder(o1));
        assertFalse(book.getBids().contains(o1));
        assertTrue(book.cancelOrder(o2));
        assertFalse(book.getAsks().contains(o2));
    }
}