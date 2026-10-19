package com.reto.orders.domain;

import com.reto.orders.domain.exception.InvalidOrderException;

import java.math.BigDecimal;

/**
 * Línea de producto de una orden. Inmutable y siempre válida: las reglas se validan al construirla
 * (defensa en profundidad, independiente de la validación de la capa web).
 */
public record OrderItem(String productId, int quantity, BigDecimal unitPrice) {

    public OrderItem {
        if (productId == null || productId.isBlank()) {
            throw new InvalidOrderException("VALIDATION", "El id del producto es obligatorio");
        }
        if (quantity <= 0) {
            throw new InvalidOrderException("RN-02",
                    "La cantidad del producto " + productId + " debe ser mayor que cero");
        }
        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw new InvalidOrderException("VALIDATION",
                    "El valor unitario del producto " + productId + " debe ser mayor que cero");
        }
        productId = productId.trim();
    }

    /** Subtotal de la línea: cantidad x valor unitario. */
    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
