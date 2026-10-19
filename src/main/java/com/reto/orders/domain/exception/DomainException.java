package com.reto.orders.domain.exception;

/**
 * Base de las violaciones de reglas de negocio. Cada excepción expone el código de la regla
 * (por ejemplo "RN-05") para que la respuesta HTTP y los logs sean trazables a los requisitos.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
