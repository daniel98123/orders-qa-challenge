package com.reto.orders.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estados de una orden y su máquina de estados (RN-06).
 *
 * <pre>
 * CREADA  -> PAGADA | CANCELADA
 * PAGADA  -> ENVIADA | CANCELADA
 * ENVIADA -> ENTREGADA | CANCELADA
 * ENTREGADA (terminal)
 * CANCELADA (terminal)
 * </pre>
 *
 * Esta tabla es la única fuente de verdad de las transiciones; la herramienta de trazabilidad
 * (ai-tools/traceability) la lee para detectar transiciones sin pruebas.
 */
public enum OrderStatus {
    CREADA,
    PAGADA,
    ENVIADA,
    ENTREGADA,
    CANCELADA;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(CREADA, EnumSet.of(PAGADA, CANCELADA));
        ALLOWED.put(PAGADA, EnumSet.of(ENVIADA, CANCELADA));
        ALLOWED.put(ENVIADA, EnumSet.of(ENTREGADA, CANCELADA));
        ALLOWED.put(ENTREGADA, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(CANCELADA, EnumSet.noneOf(OrderStatus.class));
    }

    public boolean canTransitionTo(OrderStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public Set<OrderStatus> allowedTransitions() {
        return Collections.unmodifiableSet(ALLOWED.get(this));
    }

    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }
}
