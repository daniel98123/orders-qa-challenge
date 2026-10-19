package com.reto.orders.infrastructure.web.dto;

import com.reto.orders.domain.Order;
import com.reto.orders.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Representación pública de la orden. Además del modelo pedido, expone fechas y
 * "transicionesPermitidas" para que los clientes (p. ej. el frontend) no dupliquen la máquina de estados.
 */
public record OrderResponse(
        UUID id,
        String cliente,
        List<ProductResponse> productos,
        BigDecimal total,
        OrderStatus estado,
        Set<OrderStatus> transicionesPermitidas,
        Instant fechaCreacion,
        Instant fechaActualizacion) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomer(),
                order.getItems().stream().map(ProductResponse::from).toList(),
                order.getTotal(),
                order.getStatus(),
                order.getStatus().allowedTransitions(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
