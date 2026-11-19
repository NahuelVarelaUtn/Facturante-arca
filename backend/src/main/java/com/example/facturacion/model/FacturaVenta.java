package com.example.facturacion.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;

/** Cabecera de la factura de venta. Los índices son los de los filtros del listado. */
@Entity
@Table(name = "facturas_venta", indexes = {
        @Index(name = "idx_factura_fecha", columnList = "fecha"),
        @Index(name = "idx_factura_estado", columnList = "estado"),
        @Index(name = "idx_factura_cliente", columnList = "cliente_id"),
        @Index(name = "idx_factura_numeracion", columnList = "punto_venta_id, letra, numero_comprobante")
})
public class FacturaVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Fecha de factura: la de la operación comercial, la carga el usuario. */
    @Column(nullable = false)
    private LocalDate fecha;

    /** Fecha de emisión: la que se informa a ARCA (CbteFch). null en un borrador = "hoy" al emitir. */
    @Column(name = "fecha_emision")
    private LocalDate fechaEmision;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private EstadoFactura estado = EstadoFactura.BORRADOR;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 1)
    private LetraComprobante letra;

    /** Código ARCA del comprobante (1 = Factura A, 6 = B, 11 = C). null en Factura X. */
    private Integer tipoComprobante;

    /**
     * Número del comprobante. NO lo carga el usuario: lo asigna ARCA junto con el CAE
     * (o el sistema, solo en Factura X). null mientras es borrador.
     */
    @Column(name = "numero_comprobante")
    private Long numeroComprobante;

    /** Número con formato de impresión: "0001-00000012". null mientras es borrador. */
    @Column(length = 30)
    private String nroFactura;

    /** Código de Autorización Electrónico devuelto por ARCA. Con CAE la factura es de solo lectura. */
    @Column(length = 14)
    private String cae;

    private LocalDate fechaVtoCae;

    /** Documento del receptor informado a ARCA (80 = CUIT, 99 = sin identificar). */
    private Integer docTipo;
    private Long docNro;

    /** Condición frente al IVA del receptor AL MOMENTO de facturar (puede diferir de la actual del cliente). */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "condicion_fiscal_receptor", nullable = false, length = 30)
    private CondicionFiscal condicionFiscalReceptor;

    @Column(length = 500)
    private String observaciones;

    @Column(precision = 15, scale = 2)
    private BigDecimal importeNeto;

    @Column(precision = 15, scale = 2)
    private BigDecimal importeIva;

    @Column(precision = 15, scale = 2)
    private BigDecimal importeTotal;

    /** Se completa al anular. */
    @Temporal(TemporalType.TIMESTAMP)
    private Date fechaAnulacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "punto_venta_id")
    private PuntoVenta puntoVenta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_moneda_id")
    private TipoMoneda tipoMoneda;

    /** Lista de precios (MAYORISTA / MINORISTA) con la que se armó toda la factura. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lista_precio_id")
    private ListaPrecio listaPrecio;

    /**
     * CascadeType.ALL: al guardar la factura se guardan sus detalles; al borrarla, se borran.
     * orphanRemoval: un detalle que se saca de la lista se elimina de la base.
     */
    @OneToMany(mappedBy = "factura", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FacturaVentaDetalle> detalles = new ArrayList<>();

    public FacturaVenta() {}

    // ---- Reglas de negocio expresadas como preguntas (las usa FacturaService) ----

    public boolean esBorrador() { return estado == EstadoFactura.BORRADOR; }

    /**
     * Se puede modificar mientras ARCA no le haya otorgado CAE y no esté anulada.
     * Eso incluye a los borradores y a las Facturas X ya emitidas (son internas: nunca tienen CAE),
     * que pueden seguir editándose o convertirse en Factura A/B/C.
     */
    public boolean esEditable() { return estado != EstadoFactura.ANULADA && !tieneCae(); }
    public boolean tieneCae() { return cae != null && !cae.isBlank(); }
    public boolean tieneNumero() { return nroFactura != null && !nroFactura.isBlank(); }

    // ---- Detalles ----

    /** Agrega un detalle y mantiene sincronizados los dos lados de la relación. */
    public void addDetalle(FacturaVentaDetalle detalle) {
        detalle.setFactura(this);
        this.detalles.add(detalle);
    }

    /**
     * Reemplaza los detalles MODIFICANDO la misma lista (no asignando una nueva):
     * con orphanRemoval, Hibernate falla si se reemplaza la colección gestionada.
     */
    public void setDetalles(List<FacturaVentaDetalle> nuevos) {
        if (nuevos == this.detalles) return;
        this.detalles.clear();
        if (nuevos != null) {
            nuevos.forEach(this::addDetalle);
        }
    }

    public List<FacturaVentaDetalle> getDetalles() { return detalles; }

    // ---- Getters y setters ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public LocalDate getFechaEmision() { return fechaEmision; }
    public void setFechaEmision(LocalDate fechaEmision) { this.fechaEmision = fechaEmision; }
    public EstadoFactura getEstado() { return estado; }
    public void setEstado(EstadoFactura estado) { this.estado = estado; }
    public LetraComprobante getLetra() { return letra; }
    public void setLetra(LetraComprobante letra) { this.letra = letra; }
    public Integer getTipoComprobante() { return tipoComprobante; }
    public void setTipoComprobante(Integer tipoComprobante) { this.tipoComprobante = tipoComprobante; }
    public Long getNumeroComprobante() { return numeroComprobante; }
    public void setNumeroComprobante(Long numeroComprobante) { this.numeroComprobante = numeroComprobante; }
    public String getNroFactura() { return nroFactura; }
    public void setNroFactura(String nroFactura) { this.nroFactura = nroFactura; }
    public String getCae() { return cae; }
    public void setCae(String cae) { this.cae = cae; }
    public LocalDate getFechaVtoCae() { return fechaVtoCae; }
    public void setFechaVtoCae(LocalDate fechaVtoCae) { this.fechaVtoCae = fechaVtoCae; }
    public Integer getDocTipo() { return docTipo; }
    public void setDocTipo(Integer docTipo) { this.docTipo = docTipo; }
    public Long getDocNro() { return docNro; }
    public void setDocNro(Long docNro) { this.docNro = docNro; }
    public CondicionFiscal getCondicionFiscalReceptor() { return condicionFiscalReceptor; }
    public void setCondicionFiscalReceptor(CondicionFiscal c) { this.condicionFiscalReceptor = c; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public BigDecimal getImporteNeto() { return importeNeto; }
    public void setImporteNeto(BigDecimal importeNeto) { this.importeNeto = importeNeto; }
    public BigDecimal getImporteIva() { return importeIva; }
    public void setImporteIva(BigDecimal importeIva) { this.importeIva = importeIva; }
    public BigDecimal getImporteTotal() { return importeTotal; }
    public void setImporteTotal(BigDecimal importeTotal) { this.importeTotal = importeTotal; }
    public Date getFechaAnulacion() { return fechaAnulacion; }
    public void setFechaAnulacion(Date fechaAnulacion) { this.fechaAnulacion = fechaAnulacion; }
    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }
    public PuntoVenta getPuntoVenta() { return puntoVenta; }
    public void setPuntoVenta(PuntoVenta puntoVenta) { this.puntoVenta = puntoVenta; }
    public TipoMoneda getTipoMoneda() { return tipoMoneda; }
    public void setTipoMoneda(TipoMoneda tipoMoneda) { this.tipoMoneda = tipoMoneda; }
    public ListaPrecio getListaPrecio() { return listaPrecio; }
    public void setListaPrecio(ListaPrecio listaPrecio) { this.listaPrecio = listaPrecio; }
}
