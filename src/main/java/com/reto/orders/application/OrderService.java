package com.reto.orders.application;

import com.reto.orders.domain.Order;
import com.reto.orders.domain.OrderItem;
import com.reto.orders.domain.OrderStatus;
import com.reto.orders.domain.exception.OrderNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Casos de uso de órdenes de compra. */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository repository;
    private final Clock clock;

    public OrderService(OrderRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Order create(String customer, List<OrderItem> items) {
        Order order = repository.save(Order.create(customer, items, clock));
        // No se registra el nombre del cliente (dato personal) en los logs.
        log.info("event=order_created orderId={} items={} total={}",
                order.getId(), order.getItems().size(), order.getTotal());
        return order;
    }

    public Order findById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    /** Lista todas las órdenes o solo las de un estado si {@code status} no es null. */
    public List<Order> findAll(OrderStatus status) {
        List<Order> all = repository.findAll();
        if (status == null) {
            return all;
        }
        return all.stream().filter(order -> order.getStatus() == status).toList();
    }

    public Order changeStatus(UUID id, OrderStatus target) {
        Order updated = repository.update(id, order -> order.transitionTo(target, clock))
                .orElseThrow(() -> new OrderNotFoundException(id));
        log.info("event=order_status_changed orderId={} status={}", id, updated.getStatus());
        return updated;
    }

    public Order cancel(UUID id) {
        return changeStatus(id, OrderStatus.CANCELADA);
    }
}
