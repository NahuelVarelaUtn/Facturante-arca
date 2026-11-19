package com.example.facturacion.model;

/**
 * Ciclo de vida de un comprobante:
 *   BORRADOR -> EMITIDA -> ANULADA
 * Una factura con CAE de ARCA es de solo lectura: lo único permitido es anularla.
 * Una Factura X emitida (interna, numerada por el sistema, sin CAE) sigue siendo editable
 * y puede convertirse en Factura A/B/C: en ese caso vuelve a BORRADOR hasta que ARCA la autorice.
 * Solo un BORRADOR se puede eliminar.
 */
public enum EstadoFactura {
    BORRADOR,
    EMITIDA,
    ANULADA
}
