package com.reto.orders.application;

import com.reto.orders.domain.Order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Puerto de persistencia (arquitectura hexagonal). La implementación actual es en memoria; cambiar
 * a JPA/Mongo solo requiere un nuevo adaptador, sin tocar dominio ni casos de uso.
 */
public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(UUID id);

    /** Órdenes ordenadas por fecha de creación ascendente. */
    List<Order> findAll();

    /**
     * Aplica la modificación de forma atómica sobre la orden con el id dado.
     * Retorna vacío si la orden no existe. Las excepciones lanzadas por {@code change} se propagan
     * y la orden queda sin cambios.
     */
    Optional<Order> update(UUID id, UnaryOperator<Order> change);
}
