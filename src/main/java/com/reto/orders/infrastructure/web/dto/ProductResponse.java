package com.reto.orders.infrastructure.web.dto;

import com.reto.orders.domain.OrderItem;

import java.math.BigDecimal;

public record ProductResponse(String id, int cantidad, BigDecimal valorUnitario) {

    public static ProductResponse from(OrderItem item) {
        return new ProductResponse(item.productId(), item.quantity(), item.unitPrice());
    }
}
