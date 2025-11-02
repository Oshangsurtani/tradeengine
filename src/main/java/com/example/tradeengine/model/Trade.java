package com.example.tradeengine.model;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trades")
public class Trade {
    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "trade_id", updatable = false, nullable = false)
    private UUID tradeId;

    @Column(name = "buy_order_id", nullable = false)
    private UUID buyOrderId;

    @Column(name = "sell_order_id", nullable = false)
    private UUID sellOrderId;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    public Trade() {}

    public Trade(UUID buyOrderId, UUID sellOrderId, BigDecimal price, BigDecimal quantity, Instant timestamp) {
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = timestamp;
    }

    // getters and setters
    public UUID getTradeId() { return tradeId; }
    public void setTradeId(UUID tradeId) { this.tradeId = tradeId; }
    public UUID getBuyOrderId() { return buyOrderId; }
    public void setBuyOrderId(UUID buyOrderId) { this.buyOrderId = buyOrderId; }
    public UUID getSellOrderId() { return sellOrderId; }
    public void setSellOrderId(UUID sellOrderId) { this.sellOrderId = sellOrderId; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}