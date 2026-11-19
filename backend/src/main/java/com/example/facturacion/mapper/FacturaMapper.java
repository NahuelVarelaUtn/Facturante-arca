package com.example.facturacion.mapper;

import java.util.List;

import com.example.facturacion.dto.FacturaDetalleReporteDTO;
import com.example.facturacion.dto.FacturaReporteDTO;
import com.example.facturacion.model.FacturaVenta;
import com.example.facturacion.model.FacturaVentaDetalle;
import com.example.facturacion.util.Cuit;

/**
 * Convierte la entidad FacturaVenta en el DTO de salida.
 * IMPORTANTE: debe llamarse DENTRO de la transacción del service, porque lee relaciones LAZY.
 */
public final class FacturaMapper {

    private FacturaMapper() {}

    /** Para el listado: sin renglones, así no se dispara una consulta de detalles por cada fila. */
    public static FacturaReporteDTO toResumen(FacturaVenta f) {
        return construir(f, null);
    }

    /** Para ver o editar UNA factura: con sus renglones. */
    public static FacturaReporteDTO toReporte(FacturaVenta f) {
        return construir(f, f.getDetalles().stream().map(FacturaMapper::toDetalleReporte).toList());
    }

    private static FacturaReporteDTO construir(FacturaVenta f, List<FacturaDetalleReporteDTO> detalles) {
        return new FacturaReporteDTO(
                f.getId(),
                f.getNroFactura(),
                f.getNumeroComprobante(),
                f.getLetra().name(),
                f.getTipoComprobante(),
                f.getFecha(),
                f.getFechaEmision(),
                f.getEstado().name(),
                f.esEditable(),
                f.tieneCae(),
                f.getCae(),
                f.getFechaVtoCae(),
                f.getCliente().getId(),
                f.getCliente().getNombre(),
                Cuit.formatear(f.getCliente().getCuit()),
                f.getCondicionFiscalReceptor().name(),
                f.getCondicionFiscalReceptor().getDescripcion(),
                f.getPuntoVenta().getId(),
                String.format("%04d", f.getPuntoVenta().getNumero()),
                f.getTipoMoneda().getId(),
                f.getTipoMoneda().getCodigo(),
                f.getListaPrecio().getId(),
                f.getListaPrecio().getNombre(),
                f.getImporteNeto(),
                f.getImporteIva(),
                f.getImporteTotal(),
                f.getObservaciones(),
                f.getFechaAnulacion(),
                detalles);
    }

    /** Usa los datos "congelados" en el renglón: no hace falta ir a buscar el artículo. */
    private static FacturaDetalleReporteDTO toDetalleReporte(FacturaVentaDetalle d) {
        return new FacturaDetalleReporteDTO(
                d.getListaPrecioArticulo().getId(),
                d.getCodigoArticulo(),
                d.getDescripcion(),
                d.getCantidad(),
                d.getPrecioUnitario(),
                d.getAlicuotaIva(),
                d.getSubtotal());
    }
}
