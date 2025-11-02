package com.example.tradeengine.controller;

import com.example.tradeengine.dto.CreateOrderRequest;
import com.example.tradeengine.engine.MatchingEngine;
import com.example.tradeengine.model.Order;
import com.example.tradeengine.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link OrderController} using MockMvc.  These
 * tests validate that the REST API performs input validation and
 * delegates to the matching engine correctly.  Repository and engine
 * beans are mocked to isolate controller behaviour.
 */
@WebMvcTest(OrderController.class)
public class OrderControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private MatchingEngine matchingEngine;
    @MockBean
    private OrderRepository orderRepository;

    @Test
    public void testCreateOrderValid() throws Exception {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setClientId("client-A");
        req.setInstrument("BTC-USD");
        req.setSide("buy");
        req.setType("limit");
        req.setPrice(new BigDecimal("100.0"));
        req.setQuantity(new BigDecimal("0.5"));
        Order saved = new Order();
        saved.setOrderId(UUID.randomUUID());
        saved.setClientId(req.getClientId());
        saved.setInstrument(req.getInstrument());
        saved.setSide(req.getSide());
        saved.setType(req.getType());
        saved.setPrice(req.getPrice());
        saved.setQuantity(req.getQuantity());
        saved.setFilledQuantity(BigDecimal.ZERO);
        saved.setStatus("open");
        // stub matching engine to return completed future with saved order
        when(matchingEngine.submitOrder(any(Order.class), eq(null))).thenReturn(CompletableFuture.completedFuture(saved));
        String json = objectMapper.writeValueAsString(req);
        mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("client-A"))
                .andExpect(jsonPath("$.status").value("open"));
    }

    @Test
    public void testCreateOrderInvalidMissingFields() throws Exception {
        // Missing price for limit order
        String body = "{\"clientId\":\"c\",\"instrument\":\"BTC-USD\",\"side\":\"buy\",\"type\":\"limit\",\"quantity\":1}";
        mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testGetOrderFound() throws Exception {
        UUID id = UUID.randomUUID();
        Order order = new Order();
        order.setOrderId(id);
        order.setClientId("c");
        order.setInstrument("BTC-USD");
        order.setSide("buy");
        order.setType("limit");
        order.setPrice(new BigDecimal("10"));
        order.setQuantity(new BigDecimal("1"));
        order.setFilledQuantity(BigDecimal.ZERO);
        order.setStatus("open");
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));
        mockMvc.perform(get("/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("c"));
    }

    @Test
    public void testGetOrderNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());
        mockMvc.perform(get("/orders/" + id))
                .andExpect(status().isNotFound());
    }
}