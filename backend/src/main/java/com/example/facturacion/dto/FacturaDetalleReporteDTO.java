package com.example.facturacion.dto;

import java.math.BigDecimal;

/** Renglón tal como se devuelve al cliente. Espejo de FacturaDetalleReporteDTO (TypeScript). */
public record FacturaDetalleReporteDTO(
        /** ID de ListaPrecioArticulo (el mismo que se envía al guardar). */
        Long articuloId,
        String articuloCodigo,
        String articuloDescripcion,
        BigDecimal cantidad,
        BigDecimal precioUnitario,
        BigDecimal alicuotaIva,
        BigDecimal subtotal
) {
}
