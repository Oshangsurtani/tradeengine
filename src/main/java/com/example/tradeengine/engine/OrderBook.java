package com.example.tradeengine.engine;

import com.example.tradeengine.model.Order;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * In‑memory order book maintaining bids and asks for a single instrument.
 */
public class OrderBook {
    private final List<Order> bids = new LinkedList<>();
    private final List<Order> asks = new LinkedList<>();

    public void addLimitOrder(Order order) {
        List<Order> side = order.getSide().equalsIgnoreCase("buy") ? bids : asks;
        int index = 0;
        for (Order existing : side) {
            int cmp;
            if (order.getSide().equalsIgnoreCase("buy")) {
                cmp = order.getPrice().compareTo(existing.getPrice());
                if (cmp > 0) break;
                if (cmp == 0 && order.getCreatedAt().isBefore(existing.getCreatedAt())) break;
            } else {
                cmp = order.getPrice().compareTo(existing.getPrice());
                if (cmp < 0) break;
                if (cmp == 0 && order.getCreatedAt().isBefore(existing.getCreatedAt())) break;
            }
            index++;
        }
        side.add(index, order);
    }

    public boolean cancelOrder(Order order) {
        if (bids.remove(order)) return true;
        if (asks.remove(order)) return true;
        return false;
    }

    public List<Order> getBids() { return bids; }
    public List<Order> getAsks() { return asks; }

    public List<Order> getAllOrders() {
        List<Order> all = new ArrayList<>(bids);
        all.addAll(asks);
        return all;
    }

    /**
     * Remove all orders from this order book.  This is used during
     * event replay to reset in‑memory state before rebuilding from
     * the event log.  It does not affect the persistent database.
     */
    public void clear() {
        bids.clear();
        asks.clear();
    }
}