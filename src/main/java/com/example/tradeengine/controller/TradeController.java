package com.example.tradeengine.controller;

import com.example.tradeengine.model.Trade;
import com.example.tradeengine.repository.TradeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TradeController {
    private final TradeRepository tradeRepo;
    public TradeController(TradeRepository tradeRepo) {
        this.tradeRepo = tradeRepo;
    }
    @GetMapping("/trades")
    public List<Trade> getTrades(@RequestParam(name="limit", defaultValue="50") int limit) {
        PageRequest page = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "timestamp"));
        return tradeRepo.findAll(page).getContent();
    }

    @GetMapping("/analytics/vwap")
    public java.util.Map<String, Object> getVWAP(@RequestParam(name="minutes", defaultValue="5") int minutes) {
        java.time.Instant cutoff = java.time.Instant.now().minusSeconds(minutes * 60L);
        List<Trade> trades = tradeRepo.findAll();
        java.math.BigDecimal totalPriceVolume = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalVolume = java.math.BigDecimal.ZERO;
        for (Trade t : trades) {
            if (t.getTimestamp().isAfter(cutoff)) {
                java.math.BigDecimal price = t.getPrice();
                java.math.BigDecimal qty = t.getQuantity();
                totalPriceVolume = totalPriceVolume.add(price.multiply(qty));
                totalVolume = totalVolume.add(qty);
            }
        }
        java.math.BigDecimal vwap = java.math.BigDecimal.ZERO;
        if (totalVolume.compareTo(java.math.BigDecimal.ZERO) > 0) {
            vwap = totalPriceVolume.divide(totalVolume, java.math.MathContext.DECIMAL64);
        }
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("minutes", minutes);
        map.put("trade_count", trades.size());
        map.put("total_volume", totalVolume);
        map.put("vwap", vwap);
        return map;
    }
}