package com.reto.orders.domain.exception;

import java.util.UUID;

/** La orden solicitada no existe. HTTP 404. */
public class OrderNotFoundException extends DomainException {

    public OrderNotFoundException(UUID id) {
        super("ORDER_NOT_FOUND", "No existe una orden con id " + id);
    }
}
