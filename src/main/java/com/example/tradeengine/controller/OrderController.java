package com.example.tradeengine.controller;

import com.example.tradeengine.engine.MatchingEngine;
import com.example.tradeengine.model.Order;
import com.example.tradeengine.repository.OrderRepository;
import com.example.tradeengine.dto.CreateOrderRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final MatchingEngine engine;
    private final OrderRepository orderRepo;
    public OrderController(MatchingEngine engine, OrderRepository orderRepo) {
        this.engine = engine;
        this.orderRepo = orderRepo;
    }

    @PostMapping
    public ResponseEntity<?> createOrder(@Valid @RequestBody CreateOrderRequest req,
                                         @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) throws ExecutionException, InterruptedException {
        // convert DTO to entity
        Order order = new Order();
        order.setClientId(req.getClientId());
        order.setInstrument(req.getInstrument());
        order.setSide(req.getSide());
        order.setType(req.getType());
        order.setPrice(req.getPrice());
        order.setQuantity(req.getQuantity());
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(order.getCreatedAt());
        order.setStatus("open");
        Order processed = engine.submitOrder(order, idemKey).get();
        return ResponseEntity.ok(processed);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable UUID id) throws ExecutionException, InterruptedException {
        Order order = engine.cancelOrder(id).get();
        if (order == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(order);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOrder(@PathVariable UUID id) {
        Optional<Order> o = orderRepo.findById(id);
        return o.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}