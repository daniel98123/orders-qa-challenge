package com.reto.orders.domain.exception;

/** Datos de la orden inválidos (RN-01, RN-02 y validaciones de integridad). HTTP 400. */
public class InvalidOrderException extends DomainException {

    public InvalidOrderException(String code, String message) {
        super(code, message);
    }
}
