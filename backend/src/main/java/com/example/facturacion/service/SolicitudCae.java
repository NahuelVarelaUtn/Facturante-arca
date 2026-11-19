package com.example.facturacion.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Todo lo que WSFE necesita para autorizar UN comprobante, ya calculado.
 * Es un objeto plano (sin entidades JPA): así la llamada a ARCA se hace FUERA de la transacción
 * de base de datos y WsfeService no depende del modelo.
 */
public record SolicitudCae(int puntoVenta,
                           int tipoComprobante,
                           int docTipo,
                           long docNro,
                           int condicionIvaReceptor,
                           LocalDate fechaComprobante,
                           BigDecimal importeNeto,
                           BigDecimal importeIva,
                           BigDecimal importeTotal,
                           /** Un elemento por cada alícuota presente. Vacía si el emisor no discrimina IVA (Factura C). */
                           List<Alicuota> alicuotas) {

    /** Subtotal de una alícuota: base imponible (neto) e importe de IVA. */
    public record Alicuota(BigDecimal porcentaje, BigDecimal baseImponible, BigDecimal importe) {
    }
}
