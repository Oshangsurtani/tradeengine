package com.example.tradeengine.service;

import com.example.tradeengine.engine.MatchingEngine;
import com.example.tradeengine.model.Order;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * BinanceWebSocketClient connects to Binance's public WebSocket trade stream
 * and forwards each trade as a market order into the matching engine.
 * This integration enables the service to consume live market data and
 * exercise the matching engine under real‑time load.  The stream and
 * instrument mapping are configurable via application.yml.
 */
@Service
@ConditionalOnProperty(prefix = "binance.websocket", name = "enabled", havingValue = "true")
public class BinanceWebSocketClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(BinanceWebSocketClient.class);
    private final MatchingEngine matchingEngine;
    private final ObjectMapper objectMapper;
    private final String wsUrl;

    @Autowired
    public BinanceWebSocketClient(MatchingEngine matchingEngine,
                                  ObjectMapper objectMapper,
                                  @Value("${binance.websocket.url}") String wsUrl) {
        this.matchingEngine = matchingEngine;
        this.objectMapper = objectMapper;
        this.wsUrl = wsUrl;
    }

    @PostConstruct
    public void start() {
        LOGGER.info("Starting Binance WebSocket client connecting to {}", wsUrl);
        HttpClient client = HttpClient.newHttpClient();
        client.newWebSocketBuilder().buildAsync(URI.create(wsUrl), new Listener());
    }

    private class Listener implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket webSocket) {
            LOGGER.info("Connected to Binance WebSocket");
            WebSocket.Listener.super.onOpen(webSocket);
        }
        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            try {
                JsonNode json = objectMapper.readTree(data.toString());
                // Extract symbol, price and quantity.  Binance trade stream JSON
                // fields: "s" (symbol), "p" (price), "q" (quantity), "m" (is maker seller).
                String symbol = json.path("s").asText();
                String priceStr = json.path("p").asText();
                String qtyStr = json.path("q").asText();
                boolean sellerIsMaker = json.path("m").asBoolean();
                if (symbol.isEmpty() || qtyStr.isEmpty()) {
                    return WebSocket.Listener.super.onText(webSocket, data, last);
                }
                BigDecimal quantity = new BigDecimal(qtyStr);
                // Determine side: if sellerIsMaker is true, the incoming trade
                // implies the taker side is buying, so we create a buy market order; otherwise sell.
                String side = sellerIsMaker ? "buy" : "sell";
                // Map Binance symbol to instrument format: e.g., BTCUSDT -> BTC-USD
                String instrument = mapInstrument(symbol);
                Order order = new Order();
                order.setOrderId(UUID.randomUUID());
                order.setClientId("binance-stream");
                order.setInstrument(instrument);
                order.setSide(side);
                order.setType("market");
                // For market orders, price is null; quantity from stream.
                order.setPrice(null);
                order.setQuantity(quantity);
                order.setCreatedAt(Instant.now());
                order.setUpdatedAt(order.getCreatedAt());
                // Submit into matching engine asynchronously.  Idempotency key is null.
                matchingEngine.submitOrder(order, null);
            } catch (Exception e) {
                LOGGER.warn("Failed to process WebSocket message: {}", e.getMessage());
            }
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }
        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            LOGGER.error("WebSocket error", error);
            WebSocket.Listener.super.onError(webSocket, error);
        }
    }

    /**
     * Convert Binance symbol (e.g. BTCUSDT) to instrument format used by
     * this service.  The default implementation returns the base asset
     * joined with the quote asset separated by a dash.  For example
     * BTCUSDT -> BTC-USD.  Override or adjust as needed if quoting
     * currency differs from USD.
     */
    private String mapInstrument(String symbol) {
        if (symbol.length() >= 6) {
            String base = symbol.substring(0, symbol.length() - 4);
            String quote = symbol.substring(symbol.length() - 4);
            if (quote.equalsIgnoreCase("USDT")) {
                quote = "USD";
            }
            return base + "-" + quote;
        }
        return symbol;
    }
}