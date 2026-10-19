package com.reto.orders.support;

import com.reto.orders.domain.Order;
import com.reto.orders.domain.OrderItem;
import com.reto.orders.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/** Datos de prueba reutilizables (Test Data Builder simplificado). */
public final class TestData {

    public static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    public static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private TestData() {
    }

    public static OrderItem item(String id, int quantity, long unitPrice) {
        return new OrderItem(id, quantity, BigDecimal.valueOf(unitPrice));
    }

    public static Order newOrder() {
        return Order.create("Juan Pérez", List.of(item("P001", 2, 100_000)), FIXED_CLOCK);
    }

    /** Construye una orden en el estado indicado recorriendo el camino válido de la máquina de estados. */
    public static Order orderIn(OrderStatus status) {
        Order order = newOrder();
        return switch (status) {
            case CREADA -> order;
            case PAGADA -> order.transitionTo(OrderStatus.PAGADA, FIXED_CLOCK);
            case ENVIADA -> orderIn(OrderStatus.PAGADA).transitionTo(OrderStatus.ENVIADA, FIXED_CLOCK);
            case ENTREGADA -> orderIn(OrderStatus.ENVIADA).transitionTo(OrderStatus.ENTREGADA, FIXED_CLOCK);
            case CANCELADA -> order.cancel(FIXED_CLOCK);
        };
    }
}
