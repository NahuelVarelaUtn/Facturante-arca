package com.example.facturacion.dto;

import java.time.LocalDate;
import java.util.List;

import com.example.facturacion.model.CondicionFiscal;
import com.example.facturacion.model.LetraComprobante;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /api/facturas (crea un BORRADOR) y PUT /api/facturas/{id} (actualiza un borrador).
 * Lo que NO viaja acá porque no lo decide el usuario: número, CAE, estado e importes.
 * Espejo en el frontend: FacturaRequestDTO (models/factura.dto.ts).
 */
public record FacturaRequestDTO(

        /** Para ventas sin identificar se manda el cliente genérico (GET /api/clientes/consumidor-final). */
        @NotNull(message = "clienteId es obligatorio")
        Long clienteId,

        /** Opcional: si no viene se usa la condición frente al IVA que tiene cargada el cliente. */
        CondicionFiscal condicionIvaReceptor,

        @NotNull(message = "puntoVentaId es obligatorio")
        Long puntoVentaId,

        @NotNull(message = "tipoMonedaId es obligatorio")
        Long tipoMonedaId,

        /** Lista de precios de toda la factura. Opcional: si no viene se toma la del primer renglón. */
        Long listaPrecioId,

        /** Fecha de factura. Opcional (ISO "2026-10-15"): por defecto hoy. */
        LocalDate fecha,

        /** Fecha de emisión (la que se informa a ARCA). Opcional: por defecto el día en que se emite. */
        LocalDate fechaEmision,

        /** A, B, C o X. Opcional: si no viene se calcula con la condición de IVA de emisor y receptor. */
        LetraComprobante letra,

        @Size(max = 500, message = "observaciones admite hasta 500 caracteres")
        String observaciones,

        @NotEmpty(message = "La factura debe tener al menos un detalle")
        @Valid
        List<FacturaDetalleRequestDTO> detalles
) {
}
