package com.reto.orders.infrastructure.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Mensaje de parámetro inválido")
class GlobalExceptionHandlerTest {

    @Test
    @DisplayName("Un tipo que no es UUID ni enum recibe un mensaje genérico")
    void genericTypeMessage() {
        assertThat(GlobalExceptionHandler.valorEsperado(Integer.class)).isEqualTo("tipo de dato incorrecto");
        assertThat(GlobalExceptionHandler.valorEsperado(null)).isEqualTo("tipo de dato incorrecto");
    }
}
