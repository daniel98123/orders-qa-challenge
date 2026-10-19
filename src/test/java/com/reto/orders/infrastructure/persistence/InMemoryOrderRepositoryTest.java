package com.reto.orders.infrastructure.persistence;

import com.reto.orders.domain.Order;
import com.reto.orders.domain.OrderStatus;
import com.reto.orders.domain.exception.InvalidStateTransitionException;
import com.reto.orders.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.reto.orders.support.TestData.FIXED_CLOCK;
import static com.reto.orders.support.TestData.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Repositorio en memoria")
class InMemoryOrderRepositoryTest {

    private final InMemoryOrderRepository repository = new InMemoryOrderRepository();

    @Test
    @DisplayName("Guarda y consulta por id")
    void saveAndFind() {
        Order order = TestData.newOrder();

        assertThat(repository.save(order)).isSameAs(order);
        assertThat(repository.findById(order.getId())).containsSame(order);
    }

    @Test
    @DisplayName("Consultar un id inexistente retorna vacío")
    void findMissing() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("Lista las órdenes por fecha de creación ascendente")
    void findAllSortedByCreation() {
        Order newer = Order.create("B", List.of(item("P", 1, 1)),
                Clock.fixed(Instant.parse("2026-02-01T00:00:00Z"), ZoneOffset.UTC));
        Order older = Order.create("A", List.of(item("P", 1, 1)),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        repository.save(newer);
        repository.save(older);

        assertThat(repository.findAll()).containsExactly(older, newer);
    }

    @Test
    @DisplayName("Con la misma fecha de creación el orden es determinista (desempate por id)")
    void findAllTieBreakById() {
        Order first = TestData.newOrder();
        Order second = TestData.newOrder();
        repository.save(first);
        repository.save(second);

        List<Order> expected = first.getId().toString().compareTo(second.getId().toString()) < 0
                ? List.of(first, second) : List.of(second, first);
        assertThat(repository.findAll()).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("Actualiza una orden existente y persiste el nuevo estado")
    void updateExisting() {
        Order order = repository.save(TestData.newOrder());

        Optional<Order> updated = repository.update(order.getId(),
                current -> current.transitionTo(OrderStatus.PAGADA, FIXED_CLOCK));

        assertThat(updated).isPresent();
        assertThat(updated.get().getStatus()).isEqualTo(OrderStatus.PAGADA);
        assertThat(repository.findById(order.getId())).get()
                .extracting(Order::getStatus).isEqualTo(OrderStatus.PAGADA);
    }

    @Test
    @DisplayName("Actualizar un id inexistente retorna vacío sin crear la orden")
    void updateMissing() {
        UUID id = UUID.randomUUID();

        assertThat(repository.update(id, current -> current)).isEmpty();
        assertThat(repository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("Si la transición falla, la orden queda sin cambios")
    void failedUpdateKeepsOriginal() {
        Order order = repository.save(TestData.newOrder());

        assertThatThrownBy(() -> repository.update(order.getId(),
                current -> current.transitionTo(OrderStatus.ENTREGADA, FIXED_CLOCK)))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(repository.findById(order.getId())).containsSame(order);
    }

    @Tag("RN-06")
    @Test
    @DisplayName("Concurrencia: de N pagos simultáneos sobre la misma orden solo uno tiene éxito")
    void concurrentTransitionsAreSerialized() throws InterruptedException {
        Order order = repository.save(TestData.newOrder());
        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    repository.update(order.getId(), current -> current.transitionTo(OrderStatus.PAGADA, FIXED_CLOCK));
                    successes.incrementAndGet();
                } catch (InvalidStateTransitionException e) {
                    rejected.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(threads - 1);
    }
}
