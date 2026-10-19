package com.reto.orders.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("RN-06")
@DisplayName("Máquina de estados (RN-06)")
class OrderStatusTest {

    /** Matriz completa 5x5: cada par origen/destino tiene un resultado esperado explícito. */
    @ParameterizedTest(name = "{0} -> {1} permitido={2}")
    @CsvSource({
            "CREADA,    CREADA,    false",
            "CREADA,    PAGADA,    true",
            "CREADA,    ENVIADA,   false",
            "CREADA,    ENTREGADA, false",
            "CREADA,    CANCELADA, true",
            "PAGADA,    CREADA,    false",
            "PAGADA,    PAGADA,    false",
            "PAGADA,    ENVIADA,   true",
            "PAGADA,    ENTREGADA, false",
            "PAGADA,    CANCELADA, true",
            "ENVIADA,   CREADA,    false",
            "ENVIADA,   PAGADA,    false",
            "ENVIADA,   ENVIADA,   false",
            "ENVIADA,   ENTREGADA, true",
            "ENVIADA,   CANCELADA, true",
            "ENTREGADA, CREADA,    false",
            "ENTREGADA, PAGADA,    false",
            "ENTREGADA, ENVIADA,   false",
            "ENTREGADA, ENTREGADA, false",
            "ENTREGADA, CANCELADA, false",
            "CANCELADA, CREADA,    false",
            "CANCELADA, PAGADA,    false",
            "CANCELADA, ENVIADA,   false",
            "CANCELADA, ENTREGADA, false",
            "CANCELADA, CANCELADA, false"
    })
    void transitionMatrix(OrderStatus from, OrderStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
        assertThat(from.allowedTransitions().contains(to)).isEqualTo(allowed);
    }

    @Test
    @DisplayName("ENTREGADA y CANCELADA son estados terminales; el resto no")
    void terminalStates() {
        assertThat(OrderStatus.ENTREGADA.isTerminal()).isTrue();
        assertThat(OrderStatus.CANCELADA.isTerminal()).isTrue();
        assertThat(OrderStatus.CREADA.isTerminal()).isFalse();
        assertThat(OrderStatus.PAGADA.isTerminal()).isFalse();
        assertThat(OrderStatus.ENVIADA.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("Las transiciones expuestas no se pueden modificar desde fuera")
    void allowedTransitionsAreUnmodifiable() {
        assertThatThrownBy(() -> OrderStatus.CREADA.allowedTransitions().add(OrderStatus.ENTREGADA))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(OrderStatus.CREADA.canTransitionTo(OrderStatus.ENTREGADA)).isFalse();
    }
}
