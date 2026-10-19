package com.reto.orders.domain;

import com.reto.orders.domain.exception.DeliveredOrderCancellationException;
import com.reto.orders.domain.exception.InvalidOrderException;
import com.reto.orders.domain.exception.InvalidStateTransitionException;
import com.reto.orders.domain.exception.OrderNotPaidException;
import com.reto.orders.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static com.reto.orders.support.TestData.FIXED_CLOCK;
import static com.reto.orders.support.TestData.NOW;
import static com.reto.orders.support.TestData.item;
import static com.reto.orders.support.TestData.orderIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Orden de compra")
class OrderTest {

    @Nested
    @DisplayName("Creación")
    class Creation {

        @Test
        @DisplayName("Una orden válida inicia en CREADA con id y fechas")
        void createsOrderInCreatedState() {
            Order order = Order.create("  Juan Pérez ", List.of(item("P001", 2, 100_000)), FIXED_CLOCK);

            assertThat(order.getId()).isNotNull();
            assertThat(order.getCustomer()).isEqualTo("Juan Pérez");
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREADA);
            assertThat(order.getItems()).hasSize(1);
            assertThat(order.getCreatedAt()).isEqualTo(NOW);
            assertThat(order.getUpdatedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("Cada orden recibe un id único")
        void generatesUniqueIds() {
            assertThat(TestData.newOrder().getId()).isNotEqualTo(TestData.newOrder().getId());
        }

        @Tag("RN-03")
        @Test
        @DisplayName("RN-03: el total es la suma de los subtotales (ejemplo del enunciado)")
        void calculatesTotalFromSpecExample() {
            Order order = Order.create("Juan Pérez", List.of(item("P001", 2, 100_000)), FIXED_CLOCK);

            assertThat(order.getTotal()).isEqualByComparingTo("200000");
        }

        @Tag("RN-03")
        @Test
        @DisplayName("RN-03: total con varios productos y decimales")
        void calculatesTotalWithSeveralItems() {
            List<OrderItem> items = List.of(
                    item("P001", 2, 100_000),
                    item("P002", 3, 2_500),
                    new OrderItem("P003", 1, new BigDecimal("999.99")));

            Order order = Order.create("Ana", items, FIXED_CLOCK);

            assertThat(order.getTotal()).isEqualByComparingTo("208499.99");
        }

        @Tag("RN-03")
        @Test
        @DisplayName("RN-03: el cálculo parte de cero (identidad de la suma)")
        void totalOfSingleCheapItem() {
            assertThat(Order.calculateTotal(List.of(item("P", 1, 1)))).isEqualByComparingTo("1");
            assertThat(Order.calculateTotal(List.of())).isEqualByComparingTo("0");
        }

        @Tag("RN-01")
        @Test
        @DisplayName("RN-01: una orden sin productos es rechazada")
        void rejectsEmptyItems() {
            assertThatThrownBy(() -> Order.create("Juan", List.of(), FIXED_CLOCK))
                    .isInstanceOf(InvalidOrderException.class)
                    .hasMessage("La orden debe contener al menos un producto")
                    .extracting("code").isEqualTo("RN-01");
        }

        @Tag("RN-01")
        @Test
        @DisplayName("RN-01: una lista de productos nula es rechazada")
        void rejectsNullItems() {
            assertThatThrownBy(() -> Order.create("Juan", null, FIXED_CLOCK))
                    .isInstanceOf(InvalidOrderException.class)
                    .extracting("code").isEqualTo("RN-01");
        }

        @ParameterizedTest(name = "cliente=''{0}''")
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("El cliente es obligatorio")
        void rejectsBlankCustomer(String customer) {
            assertThatThrownBy(() -> Order.create(customer, List.of(item("P001", 1, 10)), FIXED_CLOCK))
                    .isInstanceOf(InvalidOrderException.class)
                    .hasMessage("El cliente es obligatorio")
                    .extracting("code").isEqualTo("VALIDATION");
        }

        @Test
        @DisplayName("Los productos se copian: modificar la lista original no altera la orden")
        void itemsAreDefensivelyCopied() {
            List<OrderItem> items = new ArrayList<>(List.of(item("P001", 1, 10)));
            Order order = Order.create("Juan", items, FIXED_CLOCK);

            items.add(item("P002", 1, 10));

            assertThat(order.getItems()).hasSize(1);
            assertThatThrownBy(() -> order.getItems().add(item("P003", 1, 10)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Cambios de estado")
    class Transitions {

        private final Instant later = NOW.plusSeconds(3600);
        private final Clock laterClock = Clock.fixed(later, ZoneOffset.UTC);

        @Test
        @DisplayName("Ciclo de vida feliz: CREADA -> PAGADA -> ENVIADA -> ENTREGADA")
        void happyPathLifecycle() {
            Order created = TestData.newOrder();

            Order paid = created.transitionTo(OrderStatus.PAGADA, laterClock);
            Order shipped = paid.transitionTo(OrderStatus.ENVIADA, laterClock);
            Order delivered = shipped.transitionTo(OrderStatus.ENTREGADA, laterClock);

            assertThat(paid.getStatus()).isEqualTo(OrderStatus.PAGADA);
            assertThat(shipped.getStatus()).isEqualTo(OrderStatus.ENVIADA);
            assertThat(delivered.getStatus()).isEqualTo(OrderStatus.ENTREGADA);
        }

        @Test
        @DisplayName("Un cambio de estado conserva los datos, actualiza la fecha y no muta la original")
        void transitionKeepsDataAndIsImmutable() {
            Order created = TestData.newOrder();

            Order paid = created.transitionTo(OrderStatus.PAGADA, laterClock);

            assertThat(paid.getId()).isEqualTo(created.getId());
            assertThat(paid.getCustomer()).isEqualTo(created.getCustomer());
            assertThat(paid.getItems()).isEqualTo(created.getItems());
            assertThat(paid.getTotal()).isEqualByComparingTo(created.getTotal());
            assertThat(paid.getCreatedAt()).isEqualTo(NOW);
            assertThat(paid.getUpdatedAt()).isEqualTo(later);
            assertThat(created.getStatus()).isEqualTo(OrderStatus.CREADA);
            assertThat(created.getUpdatedAt()).isEqualTo(NOW);
        }

        @Tag("RN-05")
        @Test
        @DisplayName("RN-05: no se puede enviar una orden que no ha sido pagada")
        void cannotShipUnpaidOrder() {
            assertThatThrownBy(() -> orderIn(OrderStatus.CREADA).transitionTo(OrderStatus.ENVIADA, laterClock))
                    .isInstanceOf(OrderNotPaidException.class)
                    .extracting("code").isEqualTo("RN-05");
        }

        @Tag("RN-05")
        @Test
        @DisplayName("RN-05: una orden pagada sí se puede enviar")
        void canShipPaidOrder() {
            assertThat(orderIn(OrderStatus.PAGADA).transitionTo(OrderStatus.ENVIADA, laterClock).getStatus())
                    .isEqualTo(OrderStatus.ENVIADA);
        }

        @Tag("RN-04")
        @Test
        @DisplayName("RN-04: una orden entregada no puede ser cancelada")
        void cannotCancelDeliveredOrder() {
            Order delivered = orderIn(OrderStatus.ENTREGADA);

            assertThatThrownBy(() -> delivered.cancel(laterClock))
                    .isInstanceOf(DeliveredOrderCancellationException.class)
                    .extracting("code").isEqualTo("RN-04");
        }

        @Tag("RN-04")
        @ParameterizedTest(name = "cancelar desde {0}")
        @EnumSource(value = OrderStatus.class, names = {"CREADA", "PAGADA", "ENVIADA"})
        @DisplayName("RN-04: las órdenes no entregadas ni canceladas sí se pueden cancelar")
        void canCancelNonDeliveredOrders(OrderStatus status) {
            Order cancelled = orderIn(status).cancel(laterClock);

            assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELADA);
            assertThat(cancelled.getUpdatedAt()).isEqualTo(later);
        }

        @Tag("RN-06")
        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                "CREADA,    ENTREGADA",
                "CREADA,    CREADA",
                "PAGADA,    CREADA",
                "PAGADA,    ENTREGADA",
                "ENVIADA,   PAGADA",
                "ENTREGADA, PAGADA",
                "CANCELADA, PAGADA",
                "CANCELADA, ENVIADA",
                "CANCELADA, CANCELADA"
        })
        @DisplayName("RN-06: transiciones inválidas son rechazadas con origen y destino")
        void rejectsInvalidTransitions(OrderStatus from, OrderStatus to) {
            Order order = orderIn(from);

            assertThatThrownBy(() -> order.transitionTo(to, laterClock))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining(from + " -> " + to)
                    .satisfies(ex -> {
                        InvalidStateTransitionException e = (InvalidStateTransitionException) ex;
                        assertThat(e.getCode()).isEqualTo("RN-06");
                        assertThat(e.getFrom()).isEqualTo(from);
                        assertThat(e.getTo()).isEqualTo(to);
                    });
        }

        @Test
        @DisplayName("El estado destino es obligatorio")
        void targetIsRequired() {
            assertThatThrownBy(() -> TestData.newOrder().transitionTo(null, laterClock))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("El estado destino es obligatorio");
        }
    }
}
