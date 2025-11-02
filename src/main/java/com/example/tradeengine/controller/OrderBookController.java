package com.example.tradeengine.controller;

import com.example.tradeengine.engine.OrderBook;
import com.example.tradeengine.model.Order;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class OrderBookController {
    private final com.example.tradeengine.engine.MatchingEngine matchingEngine;
    public OrderBookController(com.example.tradeengine.engine.MatchingEngine matchingEngine) {
        this.matchingEngine = matchingEngine;
    }
    @GetMapping("/orderbook")
    public Map<String, Object> getOrderBook(@RequestParam(name="instrument") String instrument,
                                            @RequestParam(name="levels", defaultValue="20") int levels) {
        OrderBook ob = matchingEngine.getOrderBook(instrument);
        List<Order> bids = ob.getBids();
        List<Order> asks = ob.getAsks();
        List<Map<String, Object>> bidLevels = aggregateLevels(bids, true, levels);
        List<Map<String, Object>> askLevels = aggregateLevels(asks, false, levels);
        Map<String, Object> res = new HashMap<>();
        res.put("bids", bidLevels);
        res.put("asks", askLevels);
        return res;
    }
    private List<Map<String, Object>> aggregateLevels(List<Order> orders, boolean isBid, int levels) {
        // sort by price
        Map<BigDecimal, BigDecimal> agg = new LinkedHashMap<>();
        List<Order> sorted = orders.stream()
                .sorted((o1, o2) -> isBid ? o2.getPrice().compareTo(o1.getPrice()) : o1.getPrice().compareTo(o2.getPrice()))
                .collect(Collectors.toList());
        for (Order o : sorted) {
            BigDecimal rem = o.getQuantity().subtract(o.getFilledQuantity());
            if (rem.compareTo(BigDecimal.ZERO) <= 0) continue;
            agg.put(o.getPrice(), agg.getOrDefault(o.getPrice(), BigDecimal.ZERO).add(rem));
            if (agg.size() >= levels) break;
        }
        List<Map<String, Object>> list = new ArrayList<>();
        agg.forEach((price, qty) -> {
            Map<String, Object> m = new HashMap<>();
            m.put("price", price);
            m.put("quantity", qty);
            list.add(m);
        });
        return list;
    }
}