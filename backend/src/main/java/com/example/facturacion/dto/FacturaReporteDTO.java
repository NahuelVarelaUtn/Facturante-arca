package com.example.facturacion.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * Comprobante tal como se devuelve al cliente.
 * Espejo de FacturaReporteDTO en el frontend (models/factura.dto.ts): mismos nombres de campo.
 */
public record FacturaReporteDTO(
        Long id,
        /** "0001-00000012". null mientras es borrador: lo asigna ARCA al otorgar el CAE. */
        String nroFactura,
        Long numeroComprobante,
        String letra,                   // A | B | C | X
        Integer tipoComprobante,        // código ARCA; null en Factura X
        LocalDate fecha,
        LocalDate fechaEmision,
        String estado,                  // BORRADOR | EMITIDA | ANULADA
        /** true si se puede modificar: borradores y Facturas X (sin CAE). El frontend lo usa para bloquear el formulario. */
        boolean editable,
        /** true si ARCA otorgó CAE. */
        boolean informadaArca,
        String cae,
        LocalDate fechaVtoCae,
        Long clienteId,
        String clienteNombre,
        String clienteCuit,
        String condicionIvaReceptor,            // nombre del enum CondicionFiscal
        String condicionIvaReceptorDescripcion,
        Long puntoVentaId,
        String puntoVenta,
        Long tipoMonedaId,
        String tipoMoneda,
        Long listaPrecioId,
        String listaPrecio,
        BigDecimal importeNeto,
        BigDecimal importeIva,
        BigDecimal importeTotal,
        String observaciones,
        Date fechaAnulacion,
        /** null en el listado (GET /api/facturas); completo en GET /api/facturas/{id}. */
        List<FacturaDetalleReporteDTO> detalles
) {
}
