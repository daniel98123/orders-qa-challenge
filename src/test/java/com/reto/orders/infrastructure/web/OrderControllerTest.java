package com.reto.orders.infrastructure.web;

import com.reto.orders.application.OrderService;
import com.reto.orders.domain.Order;
import com.reto.orders.domain.OrderItem;
import com.reto.orders.domain.OrderStatus;
import com.reto.orders.domain.exception.DeliveredOrderCancellationException;
import com.reto.orders.domain.exception.InvalidOrderException;
import com.reto.orders.domain.exception.InvalidStateTransitionException;
import com.reto.orders.domain.exception.OrderNotFoundException;
import com.reto.orders.domain.exception.OrderNotPaidException;
import com.reto.orders.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static com.reto.orders.support.TestData.orderIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de componente de la capa HTTP (slice @WebMvcTest): contrato, validaciones, mapeo de
 * errores a códigos HTTP y observabilidad. El servicio se simula con Mockito.
 */
@WebMvcTest(OrderController.class)
@DisplayName("API REST de órdenes")
class OrderControllerTest {

    private static final String VALID_BODY = """
            {"cliente":"Juan Pérez","productos":[{"id":"P001","cantidad":2,"valorUnitario":100000}]}
            """;
    private static final String UUID_REGEX =
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrderService service;

    @Nested
    @DisplayName("POST /orders")
    class Create {

        @Tag("RN-03")
        @Test
        @DisplayName("201 con Location, total calculado y transiciones permitidas")
        void createsOrder() throws Exception {
            Order order = TestData.newOrder();
            when(service.create(anyString(), anyList())).thenReturn(order);

            mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", endsWith("/orders/" + order.getId())))
                    .andExpect(jsonPath("$.id", is(order.getId().toString())))
                    .andExpect(jsonPath("$.cliente", is("Juan Pérez")))
                    .andExpect(jsonPath("$.productos", hasSize(1)))
                    .andExpect(jsonPath("$.productos[0].id", is("P001")))
                    .andExpect(jsonPath("$.productos[0].cantidad", is(2)))
                    .andExpect(jsonPath("$.productos[0].valorUnitario", is(100000)))
                    .andExpect(jsonPath("$.total", is(200000)))
                    .andExpect(jsonPath("$.estado", is("CREADA")))
                    .andExpect(jsonPath("$.transicionesPermitidas", hasSize(2)))
                    .andExpect(jsonPath("$.fechaCreacion", is(TestData.NOW.toString())));
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("Mapea los productos del request al dominio")
        void mapsRequestToDomain() throws Exception {
            when(service.create(anyString(), anyList())).thenReturn(TestData.newOrder());

            mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("""
                            {"cliente":"Ana","productos":[
                              {"id":"P001","cantidad":2,"valorUnitario":100000},
                              {"id":"P002","cantidad":1,"valorUnitario":5000.5}],
                             "total":1,"estado":"ENTREGADA"}
                            """))
                    .andExpect(status().isCreated());

            ArgumentCaptor<List<OrderItem>> items = ArgumentCaptor.forClass(List.class);
            verify(service).create(eq("Ana"), items.capture());
            assertThat(items.getValue()).hasSize(2);
            assertThat(items.getValue().get(1).productId()).isEqualTo("P002");
            assertThat(items.getValue().get(1).quantity()).isEqualTo(1);
            assertThat(items.getValue().get(1).unitPrice()).isEqualByComparingTo("5000.5");
        }

        @Tag("RN-01")
        @Test
        @DisplayName("RN-01: 400 si la orden no tiene productos")
        void rejectsEmptyProducts() throws Exception {
            mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"cliente\":\"Juan\",\"productos\":[]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title", is("Petición inválida")))
                    .andExpect(jsonPath("$.code", is("VALIDATION")))
                    .andExpect(jsonPath("$.errors[0].campo", is("productos")))
                    .andExpect(jsonPath("$.errors[0].mensaje", containsString("RN-01")));
            verify(service, never()).create(anyString(), anyList());
        }

        @Tag("RN-01")
        @Test
        @DisplayName("RN-01: 400 si el campo productos no se envía")
        void rejectsMissingProducts() throws Exception {
            mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("{\"cliente\":\"Juan\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].campo", is("productos")));
        }

        @Tag("RN-02")
        @Test
        @DisplayName("RN-02: 400 si la cantidad es cero")
        void rejectsZeroQuantity() throws Exception {
            mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("""
                            {"cliente":"Juan","productos":[{"id":"P001","cantidad":0,"valorUnitario":10}]}
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].campo", is("productos[0].cantidad")))
                    .andExpect(jsonPath("$.errors[0].mensaje", containsString("RN-02")));
        }

        @Test
        @DisplayName("400 con un error por cada campo inválido")
        void reportsAllFieldErrors() throws Exception {
            mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("""
                            {"cliente":" ","productos":[{"id":"","cantidad":-1,"valorUnitario":0}]}
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors", hasSize(4)));
        }

        @Test
        @DisplayName("400 si un producto es null")
        void rejectsNullProduct() throws Exception {
            mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"cliente\":\"Juan\",\"productos\":[null]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("VALIDATION")));
        }

        @Test
        @DisplayName("400 si el JSON está mal formado")
        void rejectsMalformedJson() throws Exception {
            mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("{\"cliente\":"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("MALFORMED_REQUEST")))
                    .andExpect(jsonPath("$.title", is("Petición mal formada")));
        }

        @Test
        @DisplayName("400 si el dominio rechaza la orden (defensa en profundidad)")
        void mapsDomainValidationTo400() throws Exception {
            when(service.create(anyString(), anyList()))
                    .thenThrow(new InvalidOrderException("RN-01", "La orden debe contener al menos un producto"));

            mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("RN-01")))
                    .andExpect(jsonPath("$.title", is("Orden inválida")))
                    .andExpect(jsonPath("$.detail", is("La orden debe contener al menos un producto")));
        }

        @Test
        @DisplayName("415 si el content-type no es JSON")
        void rejectsUnsupportedMediaType() throws Exception {
            mvc.perform(post("/orders").contentType(MediaType.TEXT_PLAIN).content("hola"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.code", is("HTTP_415")));
        }
    }

    @Nested
    @DisplayName("GET /orders")
    class Read {

        @Test
        @DisplayName("200 al consultar una orden existente")
        void findsOrder() throws Exception {
            Order order = orderIn(OrderStatus.PAGADA);
            when(service.findById(order.getId())).thenReturn(order);

            mvc.perform(get("/orders/{id}", order.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(order.getId().toString())))
                    .andExpect(jsonPath("$.estado", is("PAGADA")));
        }

        @Test
        @DisplayName("404 si la orden no existe")
        void notFound() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.findById(id)).thenThrow(new OrderNotFoundException(id));

            mvc.perform(get("/orders/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code", is("ORDER_NOT_FOUND")))
                    .andExpect(jsonPath("$.title", is("Orden no encontrada")))
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(jsonPath("$.timestamp", notNullValue()));
        }

        @Test
        @DisplayName("400 si el id no es un UUID")
        void invalidId() throws Exception {
            mvc.perform(get("/orders/{id}", "1ae80ede"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code", is("INVALID_PARAMETER")))
                    .andExpect(jsonPath("$.title", is("Parámetro inválido")))
                    .andExpect(jsonPath("$.detail", containsString("'1ae80ede' no es válido para 'id'")))
                    .andExpect(jsonPath("$.detail", containsString("UUID completo")))
                    .andExpect(jsonPath("$.correlationId", notNullValue()));
        }

        @Test
        @DisplayName("200 al listar sin filtro")
        void listsOrders() throws Exception {
            when(service.findAll(isNull())).thenReturn(List.of(orderIn(OrderStatus.CREADA), orderIn(OrderStatus.ENVIADA)));

            mvc.perform(get("/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[1].estado", is("ENVIADA")));
        }

        @Test
        @DisplayName("200 al listar filtrando por estado")
        void listsOrdersByStatus() throws Exception {
            when(service.findAll(OrderStatus.ENTREGADA)).thenReturn(List.of(orderIn(OrderStatus.ENTREGADA)));

            mvc.perform(get("/orders").param("estado", "ENTREGADA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].transicionesPermitidas", hasSize(0)));
        }

        @Test
        @DisplayName("400 si el filtro de estado no existe")
        void invalidStatusFilter() throws Exception {
            mvc.perform(get("/orders").param("estado", "PERDIDA"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("INVALID_PARAMETER")))
                    .andExpect(jsonPath("$.detail", containsString("'PERDIDA' no es válido para 'estado'")))
                    .andExpect(jsonPath("$.detail", containsString("[CREADA, PAGADA, ENVIADA, ENTREGADA, CANCELADA]")));
        }
    }

    @Nested
    @DisplayName("PATCH /orders/{id}/status")
    class ChangeStatus {

        @Test
        @DisplayName("200 con el nuevo estado")
        void changesStatus() throws Exception {
            Order paid = orderIn(OrderStatus.PAGADA);
            when(service.changeStatus(paid.getId(), OrderStatus.PAGADA)).thenReturn(paid);

            mvc.perform(patch("/orders/{id}/status", paid.getId())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"PAGADA\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado", is("PAGADA")));
        }

        @Tag("RN-05")
        @Test
        @DisplayName("RN-05: 400 al enviar una orden no pagada")
        void shippingUnpaidOrderIs400() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.changeStatus(id, OrderStatus.ENVIADA)).thenThrow(new OrderNotPaidException());

            mvc.perform(patch("/orders/{id}/status", id)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"ENVIADA\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("RN-05")))
                    .andExpect(jsonPath("$.title", is("Orden no pagada")));
        }

        @Tag("RN-06")
        @Test
        @DisplayName("RN-06: 409 ante una transición inválida")
        void invalidTransitionIs409() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.changeStatus(id, OrderStatus.ENTREGADA))
                    .thenThrow(new InvalidStateTransitionException(OrderStatus.CREADA, OrderStatus.ENTREGADA));

            mvc.perform(patch("/orders/{id}/status", id)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"ENTREGADA\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code", is("RN-06")))
                    .andExpect(jsonPath("$.title", is("Transición de estado inválida")))
                    .andExpect(jsonPath("$.detail", containsString("CREADA -> ENTREGADA")));
        }

        @Test
        @DisplayName("400 si el estado no es uno de los permitidos")
        void unknownStatusValue() throws Exception {
            mvc.perform(patch("/orders/{id}/status", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"PERDIDA\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("MALFORMED_REQUEST")))
                    .andExpect(jsonPath("$.detail", containsString("CANCELADA")));
        }

        @Test
        @DisplayName("400 si no se envía el estado")
        void missingStatus() throws Exception {
            mvc.perform(patch("/orders/{id}/status", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].campo", is("estado")));
            verify(service, never()).changeStatus(any(), any());
        }

        @Test
        @DisplayName("404 si la orden no existe")
        void notFound() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.changeStatus(id, OrderStatus.PAGADA)).thenThrow(new OrderNotFoundException(id));

            mvc.perform(patch("/orders/{id}/status", id)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"PAGADA\"}"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /orders/{id}")
    class Cancel {

        @Test
        @DisplayName("200 con la orden en CANCELADA")
        void cancelsOrder() throws Exception {
            Order cancelled = orderIn(OrderStatus.CANCELADA);
            when(service.cancel(cancelled.getId())).thenReturn(cancelled);

            mvc.perform(delete("/orders/{id}", cancelled.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado", is("CANCELADA")));
        }

        @Tag("RN-04")
        @Test
        @DisplayName("RN-04: 409 al cancelar una orden entregada")
        void cancellingDeliveredOrderIs409() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.cancel(id)).thenThrow(new DeliveredOrderCancellationException());

            mvc.perform(delete("/orders/{id}", id))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code", is("RN-04")))
                    .andExpect(jsonPath("$.title", is("Orden entregada")));
        }
    }

    @Nested
    @DisplayName("Errores transversales y observabilidad")
    class CrossCutting {

        @Test
        @DisplayName("500 genérico sin exponer detalles internos")
        void unexpectedErrorDoesNotLeakDetails() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.findById(id)).thenThrow(new IllegalStateException("password=secreto"));

            mvc.perform(get("/orders/{id}", id))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code", is("INTERNAL_ERROR")))
                    .andExpect(jsonPath("$.title", is("Error interno")))
                    .andExpect(jsonPath("$.detail", not(containsString("secreto"))));
        }

        @Test
        @DisplayName("405 si el método no está soportado")
        void methodNotAllowed() throws Exception {
            mvc.perform(post("/orders/{id}", UUID.randomUUID()))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.code", is("HTTP_405")));
        }

        @Test
        @DisplayName("Propaga el X-Correlation-Id recibido a la respuesta y al cuerpo de error")
        void propagatesCorrelationId() throws Exception {
            UUID id = UUID.randomUUID();
            when(service.findById(id)).thenThrow(new OrderNotFoundException(id));

            mvc.perform(get("/orders/{id}", id).header(CorrelationIdFilter.HEADER, "abc-123"))
                    .andExpect(header().string(CorrelationIdFilter.HEADER, "abc-123"))
                    .andExpect(jsonPath("$.correlationId", is("abc-123")));
        }

        @Test
        @DisplayName("Genera un X-Correlation-Id si no llega o si es inseguro (inyección de logs)")
        void generatesCorrelationId() throws Exception {
            when(service.findAll(isNull())).thenReturn(List.of());

            mvc.perform(get("/orders"))
                    .andExpect(header().string(CorrelationIdFilter.HEADER, matchesPattern(UUID_REGEX)));
            mvc.perform(get("/orders").header(CorrelationIdFilter.HEADER, "x\r\nFAKE LOG"))
                    .andExpect(header().string(CorrelationIdFilter.HEADER, matchesPattern(UUID_REGEX)));
        }
    }
}
