package com.example.tradeengine.repository;

import com.example.tradeengine.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TradeRepository extends JpaRepository<Trade, UUID> {
}