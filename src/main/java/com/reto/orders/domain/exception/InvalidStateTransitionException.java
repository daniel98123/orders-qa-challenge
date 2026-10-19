package com.reto.orders.domain.exception;

import com.reto.orders.domain.OrderStatus;

/** RN-06: transición de estado no permitida por la máquina de estados. HTTP 409. */
public class InvalidStateTransitionException extends DomainException {

    private final OrderStatus from;
    private final OrderStatus to;

    public InvalidStateTransitionException(OrderStatus from, OrderStatus to) {
        super("RN-06", "Transición de estado inválida: " + from + " -> " + to
                + ". Transiciones permitidas desde " + from + ": " + from.allowedTransitions());
        this.from = from;
        this.to = to;
    }

    public OrderStatus getFrom() {
        return from;
    }

    public OrderStatus getTo() {
        return to;
    }
}
