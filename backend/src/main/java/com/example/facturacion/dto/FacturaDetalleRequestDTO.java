package com.example.facturacion.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * DTO de entrada para un renglón (detalle) de la factura.
 * Se vincula al artículo por ID; NO se envían entidades anidadas.
 * Espejo en el frontend: FacturaDetalleRequestDTO (models/factura.dto.ts).
 */
public record FacturaDetalleRequestDTO(

        /** ID del ListaPrecioArticulo (de donde se toma el precio unitario). */
        @NotNull(message = "articuloId es obligatorio")
        Long articuloId,

        @NotNull(message = "cantidad es obligatoria")
        @Positive(message = "cantidad debe ser mayor a 0")
        BigDecimal cantidad
) {
}
