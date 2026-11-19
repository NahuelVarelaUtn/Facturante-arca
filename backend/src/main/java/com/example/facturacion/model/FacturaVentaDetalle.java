package com.example.facturacion.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** ENTIDAD DE APOYO: renglón de la factura (ver FacturaVenta). */
@Entity
@Table(name = "facturas_venta_detalle") // Agregado para mantener consistencia en la DB
public class FacturaVentaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Lado dueño de la relación (es el que tiene la clave foránea). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "factura_id")
    private FacturaVenta factura;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lista_precio_articulo_id")
    private ListaPrecioArticulo listaPrecioArticulo;

    // --- NUEVOS CAMPOS REQUERIDOS PARA ARCA Y GENERACIÓN DE PDF ---

    /** 
     * Captura el nombre/descripción del artículo al momento de la venta. 
     * Si el nombre del artículo cambia en el futuro en la base de datos, 
     * la factura histórica debe mantener el nombre original (exigencia fiscal para el PDF). 
     */
    @Column(length = 255)
    private String descripcion;

    /** Código del artículo al momento de la venta (misma idea que la descripción: no cambia después). */
    @Column(name = "codigo_articulo", length = 30)
    private String codigoArticulo;

    /** 
     * Porcentaje de IVA aplicado a este ítem (Ej: 21.00, 10.50). 
     * ARCA exige que en el XML se envíen los subtotales agrupados por cada alícuota de IVA. 
     */
    @Column(precision = 5, scale = 2)
    private BigDecimal alicuotaIva;

    /** 
     * El monto de IVA calculado solo para esta línea. 
     * Guardarlo evita errores de redondeo de decimales al sumar los totales para ARCA. 
     */
    @Column(precision = 15, scale = 2)
    private BigDecimal importeIva;

    // --------------------------------------------------------------

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal cantidad;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal precioUnitario;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    public FacturaVentaDetalle() {}

    // ---- GETTERS Y SETTERS ORIGINALES Y NUEVOS ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public FacturaVenta getFactura() { return factura; }
    public void setFactura(FacturaVenta factura) { this.factura = factura; }
    public ListaPrecioArticulo getListaPrecioArticulo() { return listaPrecioArticulo; }
    public void setListaPrecioArticulo(ListaPrecioArticulo listaPrecioArticulo) { this.listaPrecioArticulo = listaPrecioArticulo; }
    public BigDecimal getCantidad() { return cantidad; }
    public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    // Getters y Setters de los campos nuevos
    public String getCodigoArticulo() { return codigoArticulo; }
    public void setCodigoArticulo(String codigoArticulo) { this.codigoArticulo = codigoArticulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public BigDecimal getAlicuotaIva() { return alicuotaIva; }
    public void setAlicuotaIva(BigDecimal alicuotaIva) { this.alicuotaIva = alicuotaIva; }
    public BigDecimal getImporteIva() { return importeIva; }
    public void setImporteIva(BigDecimal importeIva) { this.importeIva = importeIva; }
}