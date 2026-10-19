package com.reto.orders.infrastructure.web.dto;

import com.reto.orders.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
        @Schema(example = "PAGADA")
        @NotNull(message = "El estado es obligatorio")
        OrderStatus estado) {
}
