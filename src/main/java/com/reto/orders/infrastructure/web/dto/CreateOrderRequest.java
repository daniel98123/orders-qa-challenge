package com.reto.orders.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Petición de creación. No incluye "total" ni "estado": RN-03 exige que el total lo calcule el
 * servidor, así que cualquier valor enviado por el cliente se ignora.
 */
public record CreateOrderRequest(
        @Schema(example = "Juan Pérez")
        @NotBlank(message = "El cliente es obligatorio")
        @Size(max = 120, message = "El cliente no puede superar 120 caracteres")
        String cliente,

        @NotEmpty(message = "RN-01: la orden debe contener al menos un producto")
        @Size(max = 100, message = "La orden no puede tener más de 100 productos")
        List<@NotNull(message = "Los productos no pueden ser nulos") @Valid ProductRequest> productos) {
}
