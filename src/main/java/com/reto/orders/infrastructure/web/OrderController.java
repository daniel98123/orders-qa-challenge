package com.reto.orders.infrastructure.web;

import com.reto.orders.application.OrderService;
import com.reto.orders.domain.Order;
import com.reto.orders.domain.OrderItem;
import com.reto.orders.domain.OrderStatus;
import com.reto.orders.infrastructure.web.dto.CreateOrderRequest;
import com.reto.orders.infrastructure.web.dto.OrderResponse;
import com.reto.orders.infrastructure.web.dto.UpdateStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Tag(name = "Órdenes", description = "Gestión de órdenes de compra")
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @Operation(summary = "Crear orden", description = "El total se calcula en el servidor (RN-03)")
    @ApiResponse(responseCode = "201", description = "Orden creada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (RN-01, RN-02)")
    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        List<OrderItem> items = request.productos().stream()
                .map(p -> new OrderItem(p.id(), p.cantidad(), p.valorUnitario()))
                .toList();
        Order order = service.create(request.cliente(), items);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(order.getId()).toUri();
        return ResponseEntity.created(location).body(OrderResponse.from(order));
    }

    @Operation(summary = "Consultar orden")
    @ApiResponse(responseCode = "200", description = "Orden encontrada")
    @ApiResponse(responseCode = "404", description = "Orden no existe")
    @GetMapping("/{id}")
    public OrderResponse findById(@PathVariable UUID id) {
        return OrderResponse.from(service.findById(id));
    }

    @Operation(summary = "Listar órdenes", description = "Filtro opcional por estado")
    @GetMapping
    public List<OrderResponse> findAll(@RequestParam(name = "estado", required = false) OrderStatus estado) {
        return service.findAll(estado).stream().map(OrderResponse::from).toList();
    }

    @Operation(summary = "Actualizar estado", description = "Aplica la máquina de estados (RN-04, RN-05, RN-06)")
    @ApiResponse(responseCode = "200", description = "Estado actualizado")
    @ApiResponse(responseCode = "400", description = "Envío sin pago (RN-05) o petición inválida")
    @ApiResponse(responseCode = "404", description = "Orden no existe")
    @ApiResponse(responseCode = "409", description = "Transición inválida (RN-04, RN-06)")
    @PatchMapping("/{id}/status")
    public OrderResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request) {
        return OrderResponse.from(service.changeStatus(id, request.estado()));
    }

    @Operation(summary = "Cancelar orden",
            description = "Cancelación lógica: la orden pasa a CANCELADA y se conserva para auditoría")
    @ApiResponse(responseCode = "200", description = "Orden cancelada")
    @ApiResponse(responseCode = "404", description = "Orden no existe")
    @ApiResponse(responseCode = "409", description = "Orden entregada (RN-04) o ya cancelada (RN-06)")
    @DeleteMapping("/{id}")
    public OrderResponse cancel(@PathVariable UUID id) {
        return OrderResponse.from(service.cancel(id));
    }
}
