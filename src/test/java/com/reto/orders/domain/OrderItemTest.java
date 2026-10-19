package com.reto.orders.domain;

import com.reto.orders.domain.exception.InvalidOrderException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Línea de producto")
class OrderItemTest {

    @Test
    @DisplayName("Crea una línea válida y normaliza el id")
    void createsValidItem() {
        OrderItem item = new OrderItem("  P001 ", 2, new BigDecimal("100000"));

        assertThat(item.productId()).isEqualTo("P001");
        assertThat(item.quantity()).isEqualTo(2);
        assertThat(item.unitPrice()).isEqualByComparingTo("100000");
    }

    @Tag("RN-03")
    @Test
    @DisplayName("RN-03: subtotal = cantidad x valor unitario (incluye decimales)")
    void subtotal() {
        assertThat(new OrderItem("P1", 3, new BigDecimal("1500.50")).subtotal()).isEqualByComparingTo("4501.50");
        assertThat(new OrderItem("P2", 1, new BigDecimal("0.01")).subtotal()).isEqualByComparingTo("0.01");
    }

    @Tag("RN-02")
    @ParameterizedTest(name = "cantidad={0}")
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    @DisplayName("RN-02: la cantidad debe ser mayor que cero")
    void rejectsNonPositiveQuantity(int quantity) {
        assertThatThrownBy(() -> new OrderItem("P001", quantity, BigDecimal.TEN))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("mayor que cero")
                .extracting("code").isEqualTo("RN-02");
    }

    @Tag("RN-02")
    @Test
    @DisplayName("RN-02 (valor límite): cantidad 1 es válida")
    void acceptsQuantityOne() {
        assertThat(new OrderItem("P001", 1, BigDecimal.TEN).quantity()).isEqualTo(1);
    }

    @ParameterizedTest(name = "id=''{0}''")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("El id del producto es obligatorio")
    void rejectsBlankProductId(String productId) {
        assertThatThrownBy(() -> new OrderItem(productId, 1, BigDecimal.TEN))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessage("El id del producto es obligatorio")
                .extracting("code").isEqualTo("VALIDATION");
    }

    @ParameterizedTest(name = "valorUnitario={0}")
    @ValueSource(strings = {"0", "-1", "-0.01"})
    @DisplayName("El valor unitario debe ser mayor que cero")
    void rejectsNonPositiveUnitPrice(String price) {
        assertThatThrownBy(() -> new OrderItem("P001", 1, new BigDecimal(price)))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("valor unitario")
                .extracting("code").isEqualTo("VALIDATION");
    }

    @Test
    @DisplayName("El valor unitario no puede ser nulo")
    void rejectsNullUnitPrice() {
        assertThatThrownBy(() -> new OrderItem("P001", 1, null))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("valor unitario");
    }

    @Test
    @DisplayName("Valor unitario mínimo positivo es válido (valor límite)")
    void acceptsSmallestPositivePrice() {
        assertThat(new OrderItem("P001", 1, new BigDecimal("0.01")).unitPrice()).isEqualByComparingTo("0.01");
    }
}
