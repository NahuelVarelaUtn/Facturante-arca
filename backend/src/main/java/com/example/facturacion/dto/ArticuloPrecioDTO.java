package com.example.facturacion.dto;

import java.math.BigDecimal;

/**
 * Fila del explorer de artículos: un artículo con su precio en una lista.
 * "id" es el de ListaPrecioArticulo (el que se manda como articuloId en el renglón de la factura).
 */
public record ArticuloPrecioDTO(Long id,
                                Long articuloId,
                                String codigo,
                                String descripcion,
                                String categoria,
                                String unidadMedida,
                                BigDecimal alicuotaIva,
                                Long listaPrecioId,
                                String listaPrecio,
                                BigDecimal precio) {
}
