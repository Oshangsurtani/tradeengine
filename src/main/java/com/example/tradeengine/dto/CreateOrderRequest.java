package com.example.tradeengine.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * DTO for creating a new order via the REST API.
 */
public class CreateOrderRequest {
    @NotBlank
    private String clientId;
    @NotBlank
    private String instrument;
    @NotBlank
    private String side;
    @NotBlank
    private String type;
    @DecimalMin(value = "0.0", inclusive = false, message = "price must be positive")
    private BigDecimal price;
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false, message = "quantity must be positive")
    private BigDecimal quantity;

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getInstrument() { return instrument; }
    public void setInstrument(String instrument) { this.instrument = instrument; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
}