package com.reto.orders.domain.exception;

/** RN-05: una orden no puede ser enviada si no ha sido pagada. HTTP 400 (exigido por el reto). */
public class OrderNotPaidException extends DomainException {

    public OrderNotPaidException() {
        super("RN-05", "La orden no puede ser enviada porque no ha sido pagada");
    }
}
