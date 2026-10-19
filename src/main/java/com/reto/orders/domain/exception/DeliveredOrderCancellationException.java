package com.reto.orders.domain.exception;

/** RN-04: una orden entregada no puede ser cancelada. HTTP 409. */
public class DeliveredOrderCancellationException extends DomainException {

    public DeliveredOrderCancellationException() {
        super("RN-04", "Una orden entregada no puede ser cancelada");
    }
}
