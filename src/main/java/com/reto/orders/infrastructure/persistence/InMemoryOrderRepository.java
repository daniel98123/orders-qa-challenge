package com.reto.orders.infrastructure.persistence;

import com.reto.orders.application.OrderRepository;
import com.reto.orders.domain.Order;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.UnaryOperator;

/**
 * Adaptador en memoria, thread-safe. {@link ConcurrentHashMap#computeIfPresent} garantiza que dos
 * cambios de estado concurrentes sobre la misma orden se serialicen (no hay "lost updates").
 */
@Repository
public class InMemoryOrderRepository implements OrderRepository {

    private final ConcurrentMap<UUID, Order> storage = new ConcurrentHashMap<>();

    @Override
    public Order save(Order order) {
        storage.put(order.getId(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<Order> findAll() {
        return storage.values().stream()
                .sorted(Comparator.comparing(Order::getCreatedAt).thenComparing(order -> order.getId().toString()))
                .toList();
    }

    @Override
    public Optional<Order> update(UUID id, UnaryOperator<Order> change) {
        return Optional.ofNullable(storage.computeIfPresent(id, (key, current) -> change.apply(current)));
    }
}
