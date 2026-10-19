package com.reto.orders.domain;

import com.reto.orders.domain.exception.DeliveredOrderCancellationException;
import com.reto.orders.domain.exception.InvalidOrderException;
import com.reto.orders.domain.exception.InvalidStateTransitionException;
import com.reto.orders.domain.exception.OrderNotPaidException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado Orden de compra. Inmutable: cada cambio de estado produce una nueva instancia, lo que
 * permite actualizaciones atómicas en el repositorio y evita estados intermedios inconsistentes.
 */
public final class Order {

    private final UUID id;
    private final String customer;
    private final List<OrderItem> items;
    private final BigDecimal total;
    private final OrderStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Order(UUID id, String customer, List<OrderItem> items, BigDecimal total,
                  OrderStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.customer = customer;
        this.items = items;
        this.total = total;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Crea una orden nueva en estado CREADA.
     * RN-01: debe tener al menos un producto. RN-02: se valida en {@link OrderItem}.
     * RN-03: el total se calcula siempre en el servidor.
     */
    public static Order create(String customer, List<OrderItem> items, Clock clock) {
        if (customer == null || customer.isBlank()) {
            throw new InvalidOrderException("VALIDATION", "El cliente es obligatorio");
        }
        if (items == null || items.isEmpty()) {
            throw new InvalidOrderException("RN-01", "La orden debe contener al menos un producto");
        }
        Instant now = clock.instant();
        List<OrderItem> safeItems = List.copyOf(items);
        return new Order(UUID.randomUUID(), customer.trim(), safeItems, calculateTotal(safeItems),
                OrderStatus.CREADA, now, now);
    }

    /** RN-03: total = suma de (cantidad x valor unitario). */
    static BigDecimal calculateTotal(List<OrderItem> items) {
        return items.stream()
                .map(OrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Cambia el estado aplicando las reglas en orden de especificidad:
     * RN-05 (enviar sin pagar), RN-04 (cancelar entregada) y por último RN-06 (cualquier otra
     * transición inválida). El orden importa porque RN-05 y RN-04 tienen códigos HTTP propios.
     */
    public Order transitionTo(OrderStatus target, Clock clock) {
        Objects.requireNonNull(target, "El estado destino es obligatorio");
        if (target == OrderStatus.ENVIADA && status == OrderStatus.CREADA) {
            throw new OrderNotPaidException();
        }
        if (target == OrderStatus.CANCELADA && status == OrderStatus.ENTREGADA) {
            throw new DeliveredOrderCancellationException();
        }
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException(status, target);
        }
        return new Order(id, customer, items, total, target, createdAt, clock.instant());
    }

    public Order cancel(Clock clock) {
        return transitionTo(OrderStatus.CANCELADA, clock);
    }

    public UUID getId() {
        return id;
    }

    public String getCustomer() {
        return customer;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
