package com.reto.orders.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @Schema(example = "P001")
        @NotBlank(message = "El id del producto es obligatorio")
        @Size(max = 50, message = "El id del producto no puede superar 50 caracteres")
        String id,

        @Schema(example = "2")
        @NotNull(message = "RN-02: la cantidad es obligatoria")
        @Positive(message = "RN-02: la cantidad debe ser mayor que cero")
        Integer cantidad,

        @Schema(example = "100000")
        @NotNull(message = "El valor unitario es obligatorio")
        @Positive(message = "El valor unitario debe ser mayor que cero")
        BigDecimal valorUnitario) {
}
