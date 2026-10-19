package com.reto.orders.application;

import com.reto.orders.domain.Order;
import com.reto.orders.domain.OrderStatus;
import com.reto.orders.domain.exception.DeliveredOrderCancellationException;
import com.reto.orders.domain.exception.InvalidOrderException;
import com.reto.orders.domain.exception.OrderNotFoundException;
import com.reto.orders.domain.exception.OrderNotPaidException;
import com.reto.orders.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static com.reto.orders.support.TestData.FIXED_CLOCK;
import static com.reto.orders.support.TestData.item;
import static com.reto.orders.support.TestData.orderIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Casos de uso de órdenes")
class OrderServiceTest {

    @Mock
    private OrderRepository repository;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(repository, FIXED_CLOCK);
    }

    /** Simula la actualización atómica del repositorio aplicando la función sobre {@code current}. */
    @SuppressWarnings("unchecked")
    private void givenStoredOrder(Order current) {
        when(repository.update(eq(current.getId()), any())).thenAnswer(invocation ->
                Optional.of(((UnaryOperator<Order>) invocation.getArgument(1)).apply(current)));
    }

    @Tag("RN-03")
    @Test
    @DisplayName("Crear: persiste la orden en CREADA con el total calculado")
    void createPersistsOrder() {
        when(repository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order created = service.create("Juan Pérez", List.of(item("P001", 2, 100_000)));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(created);
        assertThat(created.getStatus()).isEqualTo(OrderStatus.CREADA);
        assertThat(created.getTotal()).isEqualByComparingTo("200000");
        assertThat(created.getCreatedAt()).isEqualTo(TestData.NOW);
    }

    @Tag("RN-01")
    @Test
    @DisplayName("Crear: una orden inválida no se persiste")
    void createInvalidOrderIsNotPersisted() {
        assertThatThrownBy(() -> service.create("Juan", List.of()))
                .isInstanceOf(InvalidOrderException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Consultar: retorna la orden existente")
    void findByIdReturnsOrder() {
        Order order = TestData.newOrder();
        when(repository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThat(service.findById(order.getId())).isSameAs(order);
    }

    @Test
    @DisplayName("Consultar: orden inexistente lanza OrderNotFoundException con el id")
    void findByIdNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(id.toString())
                .extracting("code").isEqualTo("ORDER_NOT_FOUND");
    }

    @Test
    @DisplayName("Listar: sin filtro retorna todas las órdenes")
    void findAllWithoutFilter() {
        List<Order> all = List.of(orderIn(OrderStatus.CREADA), orderIn(OrderStatus.PAGADA));
        when(repository.findAll()).thenReturn(all);

        assertThat(service.findAll(null)).containsExactlyElementsOf(all);
    }

    @Test
    @DisplayName("Listar: con filtro retorna solo las órdenes en ese estado")
    void findAllWithFilter() {
        Order created = orderIn(OrderStatus.CREADA);
        Order paid = orderIn(OrderStatus.PAGADA);
        when(repository.findAll()).thenReturn(List.of(created, paid));

        assertThat(service.findAll(OrderStatus.PAGADA)).containsExactly(paid);
        assertThat(service.findAll(OrderStatus.ENTREGADA)).isEmpty();
    }

    @Test
    @DisplayName("Cambiar estado: aplica la transición de forma atómica en el repositorio")
    void changeStatusAppliesTransition() {
        Order created = orderIn(OrderStatus.CREADA);
        givenStoredOrder(created);

        Order paid = service.changeStatus(created.getId(), OrderStatus.PAGADA);

        assertThat(paid.getStatus()).isEqualTo(OrderStatus.PAGADA);
        assertThat(paid.getId()).isEqualTo(created.getId());
    }

    @Tag("RN-05")
    @Test
    @DisplayName("Cambiar estado: propaga la violación de RN-05")
    void changeStatusPropagatesBusinessRule() {
        Order created = orderIn(OrderStatus.CREADA);
        givenStoredOrder(created);

        assertThatThrownBy(() -> service.changeStatus(created.getId(), OrderStatus.ENVIADA))
                .isInstanceOf(OrderNotPaidException.class);
    }

    @Test
    @DisplayName("Cambiar estado: orden inexistente lanza OrderNotFoundException")
    void changeStatusNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.update(eq(id), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus(id, OrderStatus.PAGADA))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("Cancelar: deja la orden en CANCELADA")
    void cancelOrder() {
        Order paid = orderIn(OrderStatus.PAGADA);
        givenStoredOrder(paid);

        assertThat(service.cancel(paid.getId()).getStatus()).isEqualTo(OrderStatus.CANCELADA);
    }

    @Tag("RN-04")
    @Test
    @DisplayName("Cancelar: una orden entregada no se puede cancelar")
    void cancelDeliveredOrder() {
        Order delivered = orderIn(OrderStatus.ENTREGADA);
        givenStoredOrder(delivered);

        assertThatThrownBy(() -> service.cancel(delivered.getId()))
                .isInstanceOf(DeliveredOrderCancellationException.class);
    }
}
